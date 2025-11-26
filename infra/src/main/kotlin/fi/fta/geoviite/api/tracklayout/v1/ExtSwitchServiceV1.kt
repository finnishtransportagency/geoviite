package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationComparison
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchOwner
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.AlignmentPoint
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackM
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import fi.fta.geoviite.infra.tracklayout.SwitchLink
import fi.fta.geoviite.infra.util.produceIf
import java.time.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class ExtSwitchServiceV1
@Autowired
constructor(
    private val publicationService: PublicationService,
    private val publicationDao: PublicationDao,
    private val geocodingService: GeocodingService,
    private val switchDao: LayoutSwitchDao,
    private val locationTrackDao: LocationTrackDao,
    private val locationTrackService: LocationTrackService,
    private val switchLibraryService: SwitchLibraryService,
) {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getExtSwitchCollection(
        layoutVersion: ExtLayoutVersionV1?,
        extCoordinateSystem: ExtSridV1?,
    ): ExtSwitchCollectionResponseV1 {
        val publication = publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, layoutVersion?.value)
        return createSwitchCollectionResponse(publication, coordinateSystem(extCoordinateSystem))
    }

    fun getExtSwitchCollectionModifications(
        layoutVersionFrom: ExtLayoutVersionV1,
        layoutVersionTo: ExtLayoutVersionV1?,
        extCoordinateSystem: ExtSridV1?,
    ): ExtModifiedSwitchCollectionResponseV1? {
        val publications = publicationService.getPublicationsToCompare(layoutVersionFrom.value, layoutVersionTo?.value)
        return if (publications.areDifferent()) {
            createSwitchCollectionModificationResponse(publications, coordinateSystem(extCoordinateSystem))
        } else {
            publicationsAreTheSame(layoutVersionFrom.value)
        }
    }

    fun getExtSwitch(
        oid: ExtOidV1<LayoutSwitch>,
        layoutVersion: ExtLayoutVersionV1?,
        extCoordinateSystem: ExtSridV1?,
    ): ExtSwitchResponseV1? {
        val publication = publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, layoutVersion?.value)
        val id = idLookup(switchDao, oid.value)
        return createExtSwitchResponse(oid.value, id, publication, coordinateSystem(extCoordinateSystem))
    }

    fun getExtSwitchModifications(
        oid: ExtOidV1<LayoutSwitch>,
        layoutVersionFrom: ExtLayoutVersionV1,
        layoutVersionTo: ExtLayoutVersionV1?,
        extCoordinateSystem: ExtSridV1?,
    ): ExtModifiedSwitchResponseV1? =
        publicationService.getPublicationsToCompare(layoutVersionFrom.value, layoutVersionTo?.value).let { publications
            ->
            // Lookup before change check to produce consistent error if oid is not found
            val id = idLookup(switchDao, oid.value)
            if (publications.areDifferent()) {
                createSwitchModificationResponse(oid.value, id, publications, coordinateSystem(extCoordinateSystem))
            } else {
                publicationsAreTheSame(layoutVersionFrom.value)
            }
        }

    private fun createExtSwitchResponse(
        oid: Oid<LayoutSwitch>,
        id: IntId<LayoutSwitch>,
        publication: Publication,
        coordinateSystem: Srid,
    ): ExtSwitchResponseV1? {
        val branch = publication.layoutBranch.branch
        val moment = publication.publicationTime
        return switchDao.getOfficialAtMoment(branch, id, moment)?.let { switch ->
            ExtSwitchResponseV1(
                layoutVersion = ExtLayoutVersionV1(publication),
                coordinateSystem = ExtSridV1(coordinateSystem),
                switch = createExtSwitch(getSwitchData(oid, switch, branch, moment), coordinateSystem),
            )
        }
    }

    private fun createSwitchModificationResponse(
        oid: Oid<LayoutSwitch>,
        id: IntId<LayoutSwitch>,
        publications: PublicationComparison,
        coordinateSystem: Srid,
    ): ExtModifiedSwitchResponseV1? {
        val branch = publications.to.layoutBranch.branch
        val startMoment = publications.from.publicationTime
        val endMoment = publications.to.publicationTime
        return publicationDao.fetchPublishedSwitchBetween(id, startMoment, endMoment)?.let(switchDao::fetch)?.let {
            switch ->
            ExtModifiedSwitchResponseV1(
                layoutVersionFrom = ExtLayoutVersionV1(publications.from),
                layoutVersionTo = ExtLayoutVersionV1(publications.to),
                coordinateSystem = ExtSridV1(coordinateSystem),
                switch = createExtSwitch(getSwitchData(oid, switch, branch, endMoment), coordinateSystem),
            )
        } ?: layoutAssetVersionsAreTheSame(id, publications)
    }

    private fun createSwitchCollectionResponse(
        publication: Publication,
        coordinateSystem: Srid,
    ): ExtSwitchCollectionResponseV1 {
        val branch = publication.layoutBranch.branch
        val moment = publication.publicationTime
        val switches = switchDao.listOfficialAtMoment(branch, moment).filter { it.exists }
        return ExtSwitchCollectionResponseV1(
            layoutVersion = ExtLayoutVersionV1(publication),
            coordinateSystem = ExtSridV1(coordinateSystem),
            switchCollection = createExtSwitches(branch, moment, coordinateSystem, switches),
        )
    }

    private fun createSwitchCollectionModificationResponse(
        publications: PublicationComparison,
        coordinateSystem: Srid,
    ): ExtModifiedSwitchCollectionResponseV1? {
        val branch = publications.to.layoutBranch.branch
        val startMoment = publications.from.publicationTime
        val endMoment = publications.to.publicationTime
        return publicationDao
            .fetchPublishedSwitchesBetween(startMoment, endMoment)
            .takeIf { versions -> versions.isNotEmpty() }
            ?.let(switchDao::fetchMany)
            ?.let { modifiedSwitches ->
                ExtModifiedSwitchCollectionResponseV1(
                    layoutVersionFrom = ExtLayoutVersionV1(publications.from),
                    layoutVersionTo = ExtLayoutVersionV1(publications.to),
                    coordinateSystem = ExtSridV1(coordinateSystem),
                    switchCollection = createExtSwitches(branch, endMoment, coordinateSystem, modifiedSwitches),
                )
            }
    }

    private fun createExtSwitches(
        branch: LayoutBranch,
        moment: Instant,
        coordinateSystem: Srid,
        switches: List<LayoutSwitch>,
    ): List<ExtSwitchV1> {
        return getSwitchData(switches, branch, moment)
            .parallelStream()
            .map { switchData -> createExtSwitch(switchData, coordinateSystem) }
            .toList()
    }

    private fun createExtSwitch(data: SwitchData, coordinateSystem: Srid): ExtSwitchV1 {
        return ExtSwitchV1(
            switchOid = ExtOidV1(data.oid),
            switchName = data.switch.name,
            type = data.structure.type,
            hand = ExtSwitchHandV1.of(data.structure.hand),
            presentationJointNumber = data.structure.presentationJointNumber.intValue,
            stateCategory = ExtSwitchStateV1.of(data.switch.stateCategory),
            owner = data.owner.name,
            trapPoint = ExtSwitchTrapPointV1.of(data.switch.trapPoint),
            switchJoints =
                data.switch.joints.map { joint ->
                    ExtSwitchJointV1(
                        jointNumber = joint.number.intValue,
                        location = toExtCoordinate(joint.location, coordinateSystem),
                    )
                },
            trackLinks =
                data.trackLinks.map { trackJoints ->
                    ExtSwitchTrackLinkV1(
                        locationTrackOid = ExtOidV1(trackJoints.locationTrackOid),
                        joints =
                            trackJoints.jointLocations.map { (link, location) ->
                                val addressPoint =
                                    requireNotNull(trackJoints.geocodingContext.toAddressPoint(location)?.first) {
                                        "Address calculation failed: trackNumber=${trackJoints.geocodingContext.trackNumber} location=$location switchOid=${data.oid}"
                                    }
                                ExtSwitchTrackJointV1(
                                    jointNumber = link.jointNumber.intValue,
                                    location = toExtAddressPoint(addressPoint, coordinateSystem),
                                )
                            },
                    )
                },
        )
    }

    data class SwitchTrackJoints(
        val locationTrackOid: Oid<LocationTrack>,
        val geocodingContext: GeocodingContext<ReferenceLineM>,
        val jointLocations: List<Pair<SwitchLink, AlignmentPoint<LocationTrackM>>>,
    )

    data class SwitchData(
        val oid: Oid<LayoutSwitch>,
        val switch: LayoutSwitch,
        val structure: SwitchStructure,
        val owner: SwitchOwner,
        val trackLinks: List<SwitchTrackJoints>,
    )

    private fun getSwitchData(
        oid: Oid<LayoutSwitch>,
        switch: LayoutSwitch,
        branch: LayoutBranch,
        moment: Instant,
    ): SwitchData {
        val id = switch.id as IntId
        return SwitchData(
            oid = oid,
            switch = switch,
            structure = switchLibraryService.getSwitchStructure(switch.switchStructureId),
            owner = switchLibraryService.getSwitchOwner(switch.ownerId),
            trackLinks = getSwitchTrackLinks(branch, moment, setOf(id))[id] ?: emptyList(),
        )
    }

    private fun getSwitchData(switches: List<LayoutSwitch>, branch: LayoutBranch, moment: Instant): List<SwitchData> {
        val switchExtIds = switchDao.fetchExternalIds(branch)
        val trackLinks = getSwitchTrackLinks(branch, moment, switches.map { it.id as IntId }.toSet())
        return switches.map { switch ->
            val id = switch.id as IntId
            SwitchData(
                oid = switchExtIds[id]?.oid ?: throwOidNotFound(branch, id),
                switch = switch,
                structure = switchLibraryService.getSwitchStructure(switch.switchStructureId),
                owner = switchLibraryService.getSwitchOwner(switch.ownerId),
                trackLinks = trackLinks[id] ?: emptyList(),
            )
        }
    }

    private fun getSwitchTrackLinks(
        branch: LayoutBranch,
        moment: Instant,
        switchIds: Set<IntId<LayoutSwitch>>,
    ): Map<IntId<LayoutSwitch>, List<SwitchTrackJoints>> {
        // This naive implementation simply iterates all tracks on the given moment
        // However, due to caching (of tracks & switch-links inside track geometries), it's faster than re-resolving
        // the versions from nodes in DB
        // For small switch-counts, this might not actually be the case, but those fetches are fast anyhow
        val tracksAndGeoms =
            locationTrackService.listOfficialWithGeometryAtMoment(branch, moment, includeDeleted = false).filter {
                (t, _) ->
                t.switchIds.any(switchIds::contains)
            }
        val trackExtIds = locationTrackDao.fetchExternalIds(branch, tracksAndGeoms.map { it.first.id as IntId })
        val getGeocodingContext = geocodingService.getLazyGeocodingContextsAtMoment(branch, moment)
        return tracksAndGeoms
            .flatMap { (track, geom) ->
                val trackOid = trackExtIds[track.id]?.oid ?: throwOidNotFound(branch, track.id)
                track.switchIds.mapNotNull { switchId ->
                    produceIf(switchIds.contains(switchId)) {
                        val geocodingContext =
                            requireNotNull(getGeocodingContext(track.trackNumberId)) {
                                "Geocoding context not found: trackNumberId=${track.trackNumberId} linkedTrackId=${track.id}"
                            }
                        switchId to
                            SwitchTrackJoints(
                                locationTrackOid = trackOid,
                                geocodingContext = geocodingContext,
                                jointLocations = geom.getSwitchLocations(switchId),
                            )
                    }
                }
            }
            .groupBy({ it.first }, { it.second })
    }
}
