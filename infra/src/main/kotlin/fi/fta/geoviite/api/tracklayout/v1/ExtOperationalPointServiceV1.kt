package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationComparison
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.ratko.model.OperationalPointRaideType
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.OperationalPoint
import fi.fta.geoviite.infra.tracklayout.OperationalPointDao
import fi.fta.geoviite.infra.tracklayout.OperationalPointRinfType
import java.time.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class ExtOperationalPointServiceV1
@Autowired
constructor(
    private val publicationService: PublicationService,
    private val publicationDao: PublicationDao,
    private val operationalPointDao: OperationalPointDao,
    private val locationTrackDao: LocationTrackDao,
    private val switchDao: LayoutSwitchDao,
) {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getExtOperationalPointCollection(
        layoutVersion: ExtLayoutVersionV1?,
        extCoordinateSystem: ExtSridV1?,
    ): ExtOperationalPointCollectionResponseV1 {
        val publication = publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, layoutVersion?.value)
        return createOperationalPointCollectionResponse(publication, coordinateSystem(extCoordinateSystem))
    }

    fun getExtOperationalPointCollectionModifications(
        layoutVersionFrom: ExtLayoutVersionV1,
        layoutVersionTo: ExtLayoutVersionV1?,
        extCoordinateSystem: ExtSridV1?,
    ): ExtModifiedOperationalPointCollectionResponseV1? {
        val publications = publicationService.getPublicationsToCompare(layoutVersionFrom.value, layoutVersionTo?.value)
        return if (publications.areDifferent()) {
            createOperationalPointCollectionModificationResponse(publications, coordinateSystem(extCoordinateSystem))
        } else {
            publicationsAreTheSame(layoutVersionFrom.value)
        }
    }

    fun getExtOperationalPoint(
        oid: ExtOidV1<OperationalPoint>,
        layoutVersion: ExtLayoutVersionV1?,
        extCoordinateSystem: ExtSridV1?,
    ): ExtOperationalPointResponseV1? {
        val publication = publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, layoutVersion?.value)
        val id = idLookup(operationalPointDao, oid.value)
        return createExtOperationalPointResponse(oid.value, id, publication, coordinateSystem(extCoordinateSystem))
    }

    fun getExtOperationalPointModifications(
        oid: ExtOidV1<OperationalPoint>,
        layoutVersionFrom: ExtLayoutVersionV1,
        layoutVersionTo: ExtLayoutVersionV1?,
        extCoordinateSystem: ExtSridV1?,
    ): ExtModifiedOperationalPointResponseV1? =
        publicationService.getPublicationsToCompare(layoutVersionFrom.value, layoutVersionTo?.value).let { publications
            ->
            val id = idLookup(operationalPointDao, oid.value)
            if (publications.areDifferent()) {
                createOperationalPointModificationResponse(
                    oid.value,
                    id,
                    publications,
                    coordinateSystem(extCoordinateSystem)
                )
            } else {
                publicationsAreTheSame(layoutVersionFrom.value)
            }
        }

    private fun createExtOperationalPointResponse(
        oid: Oid<OperationalPoint>,
        id: IntId<OperationalPoint>,
        publication: Publication,
        coordinateSystem: Srid,
    ): ExtOperationalPointResponseV1? {
        val branch = publication.layoutBranch.branch
        val moment = publication.publicationTime
        return operationalPointDao.getOfficialAtMoment(branch, id, moment)?.let { operationalPoint ->
            ExtOperationalPointResponseV1(
                layoutVersion = ExtLayoutVersionV1(publication),
                coordinateSystem = ExtSridV1(coordinateSystem),
                operationalPoint =
                    createExtOperationalPoint(
                        getOperationalPointData(oid, operationalPoint, branch, moment),
                        coordinateSystem
                    ),
            )
        }
    }

    private fun createOperationalPointModificationResponse(
        oid: Oid<OperationalPoint>,
        id: IntId<OperationalPoint>,
        publications: PublicationComparison,
        coordinateSystem: Srid,
    ): ExtModifiedOperationalPointResponseV1? {
        val branch = publications.to.layoutBranch.branch
        val startMoment = publications.from.publicationTime
        val endMoment = publications.to.publicationTime
        return publicationDao
            .fetchPublishedOperationalPointBetween(id, startMoment, endMoment)
            ?.let(operationalPointDao::fetch)
            ?.let { operationalPoint ->
                ExtModifiedOperationalPointResponseV1(
                    layoutVersionFrom = ExtLayoutVersionV1(publications.from),
                    layoutVersionTo = ExtLayoutVersionV1(publications.to),
                    coordinateSystem = ExtSridV1(coordinateSystem),
                    operationalPoint =
                        createExtOperationalPoint(
                            getOperationalPointData(oid, operationalPoint, branch, endMoment),
                            coordinateSystem
                        ),
                )
            } ?: layoutAssetVersionsAreTheSame(id, publications)
    }

    private fun createOperationalPointCollectionResponse(
        publication: Publication,
        coordinateSystem: Srid,
    ): ExtOperationalPointCollectionResponseV1 {
        val branch = publication.layoutBranch.branch
        val moment = publication.publicationTime
        val operationalPoints = operationalPointDao.listOfficialAtMoment(branch, moment).filter { it.exists }
        return ExtOperationalPointCollectionResponseV1(
            layoutVersion = ExtLayoutVersionV1(publication),
            coordinateSystem = ExtSridV1(coordinateSystem),
            operationalPointCollection = createExtOperationalPoints(branch, moment, coordinateSystem, operationalPoints),
        )
    }

    private fun createOperationalPointCollectionModificationResponse(
        publications: PublicationComparison,
        coordinateSystem: Srid,
    ): ExtModifiedOperationalPointCollectionResponseV1? {
        val branch = publications.to.layoutBranch.branch
        val startMoment = publications.from.publicationTime
        val endMoment = publications.to.publicationTime
        return publicationDao
            .fetchPublishedOperationalPointsBetween(startMoment, endMoment)
            .takeIf { versions -> versions.isNotEmpty() }
            ?.let(operationalPointDao::fetchMany)
            ?.let { modifiedOperationalPoints ->
                ExtModifiedOperationalPointCollectionResponseV1(
                    layoutVersionFrom = ExtLayoutVersionV1(publications.from),
                    layoutVersionTo = ExtLayoutVersionV1(publications.to),
                    coordinateSystem = ExtSridV1(coordinateSystem),
                    operationalPointCollection =
                        createExtOperationalPoints(branch, endMoment, coordinateSystem, modifiedOperationalPoints),
                )
            }
    }

    private fun createExtOperationalPoints(
        branch: LayoutBranch,
        moment: Instant,
        coordinateSystem: Srid,
        operationalPoints: List<OperationalPoint>,
    ): List<ExtOperationalPointV1> {
        return getOperationalPointData(operationalPoints, branch, moment)
            .parallelStream()
            .map { data -> createExtOperationalPoint(data, coordinateSystem) }
            .toList()
    }

    private fun createExtOperationalPoint(data: OperationalPointData, coordinateSystem: Srid): ExtOperationalPointV1 {
        return ExtOperationalPointV1(
            operationalPointOid = ExtOidV1(data.oid),
            rinfId = null,
            name = data.operationalPoint.name,
            abbreviation = data.operationalPoint.abbreviation,
            state = ExtOperationalPointStateV1.of(data.operationalPoint.state),
            source = ExtOperationalPointOriginV1.of(data.operationalPoint.origin),
            typeRato = data.operationalPoint.raideType?.let { toExtOperationalPointRatoType(it) },
            typeRinf = data.operationalPoint.rinfType?.let { toExtOperationalPointRinfType(it) },
            uicCode = data.operationalPoint.uicCode?.toString(),
            location = data.operationalPoint.location?.let { toExtCoordinate(it, coordinateSystem) },
            tracks =
                data.trackOids.map { trackOid ->
                    ExtOperationalPointTrackV1(locationTrackOid = ExtOidV1(trackOid))
                },
            switches = data.switchOids.map { switchOid -> ExtOperationalPointSwitchV1(switchOid = ExtOidV1(switchOid)) },
            area =
                data.operationalPoint.polygon?.let { polygon ->
                    ExtPolygonV1(
                        type = "Polygoni",
                        points = polygon.points.map { point -> toExtCoordinate(point, coordinateSystem) },
                    )
                },
        )
    }

    data class OperationalPointData(
        val oid: Oid<OperationalPoint>,
        val operationalPoint: OperationalPoint,
        val trackOids: List<Oid<LocationTrack>>,
        val switchOids: List<Oid<LayoutSwitch>>,
    )

    private fun getOperationalPointData(
        oid: Oid<OperationalPoint>,
        operationalPoint: OperationalPoint,
        branch: LayoutBranch,
        moment: Instant,
    ): OperationalPointData {
        val id = operationalPoint.id as IntId
        val (trackOids, switchOids) = getOperationalPointReferences(branch, moment, setOf(id))
        return OperationalPointData(
            oid = oid,
            operationalPoint = operationalPoint,
            trackOids = trackOids[id] ?: emptyList(),
            switchOids = switchOids[id] ?: emptyList(),
        )
    }

    private fun getOperationalPointData(
        operationalPoints: List<OperationalPoint>,
        branch: LayoutBranch,
        moment: Instant,
    ): List<OperationalPointData> {
        val operationalPointExtIds = operationalPointDao.fetchExternalIds(branch)
        val operationalPointIds = operationalPoints.map { it.id as IntId }.toSet()
        val (trackOids, switchOids) = getOperationalPointReferences(branch, moment, operationalPointIds)

        return operationalPoints.map { operationalPoint ->
            val id = operationalPoint.id as IntId
            OperationalPointData(
                oid = operationalPointExtIds[id]?.oid ?: throwOidNotFound(branch, id),
                operationalPoint = operationalPoint,
                trackOids = trackOids[id] ?: emptyList(),
                switchOids = switchOids[id] ?: emptyList(),
            )
        }
    }

    private fun getOperationalPointReferences(
        branch: LayoutBranch,
        moment: Instant,
        operationalPointIds: Set<IntId<OperationalPoint>>,
    ): Pair<Map<IntId<OperationalPoint>, List<Oid<LocationTrack>>>, Map<IntId<OperationalPoint>, List<Oid<LayoutSwitch>>>> {
        val locationTracks = locationTrackDao.listOfficialAtMoment(branch, moment)
        val trackExtIds = locationTrackDao.fetchExternalIds(branch, locationTracks.map { it.id as IntId })

        val tracksByOperationalPoint =
            locationTracks
                .filter { track -> track.operationalPointIds.any(operationalPointIds::contains) }
                .flatMap { track ->
                    val trackOid = trackExtIds[track.id]?.oid ?: throwOidNotFound(branch, track.id)
                    track.operationalPointIds.filter(operationalPointIds::contains).map { opId -> opId to trackOid }
                }
                .groupBy({ it.first }, { it.second })

        val switches = switchDao.listOfficialAtMoment(branch, moment).filter { it.exists }
        val switchExtIds = switchDao.fetchExternalIds(branch, switches.map { it.id as IntId })

        val switchesByOperationalPoint =
            switches
                .filter { switch -> switch.operationalPointId?.let(operationalPointIds::contains) == true }
                .mapNotNull { switch ->
                    switch.operationalPointId?.let { opId ->
                        val switchOid = switchExtIds[switch.id]?.oid ?: throwOidNotFound(branch, switch.id)
                        opId to switchOid
                    }
                }
                .groupBy({ it.first }, { it.second })

        return tracksByOperationalPoint to switchesByOperationalPoint
    }
}
