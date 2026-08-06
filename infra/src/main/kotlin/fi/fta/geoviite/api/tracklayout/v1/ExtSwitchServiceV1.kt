package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
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
import fi.fta.geoviite.infra.tracklayout.LayoutDesign
import fi.fta.geoviite.infra.tracklayout.LayoutDesignService
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
    private val layoutDesignService: LayoutDesignService,
) {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getExtSwitchCollection(
        designOid: ExtOidV1<LayoutDesign>?,
        layoutVersion: ExtLayoutVersionV1?,
        extCoordinateSystem: ExtSridV1?,
        switchNameFilter: String? = null,
    ): ExtSwitchCollectionResponseV1 {
        val branch = branchByDesignOid(layoutDesignService, designOid)
        val publication = publicationService.getPublicationByUuidOrLatest(branch, layoutVersion?.value)
        return createSwitchCollectionResponse(
            publication,
            branch,
            coordinateSystem(extCoordinateSystem),
            switchNameFilter,
        )
    }

    fun getExtSwitchCollectionModifications(
        layoutVersionFrom: ExtLayoutVersionV1,
        layoutVersionTo: ExtLayoutVersionV1?,
        designOid: ExtOidV1<LayoutDesign>?,
        extCoordinateSystem: ExtSridV1?,
        switchNameFilter: String? = null,
    ): ExtModifiedSwitchCollectionResponseV1? {
        val branch = branchByDesignOid(layoutDesignService, designOid)
        val publications =
            publicationService.getPublicationsToCompare(
                layoutVersionFrom.value,
                layoutVersionTo?.value,
                branch = branch,
            )
        return if (publications.areDifferent()) {
            createSwitchCollectionModificationResponse(
                publications,
                branch,
                coordinateSystem(extCoordinateSystem),
                switchNameFilter,
            )
        } else {
            publicationsAreTheSame(layoutVersionFrom.value)
        }
    }

    fun getExtSwitch(
        oid: ExtOidV1<LayoutSwitch>,
        layoutVersion: ExtLayoutVersionV1?,
        designOid: ExtOidV1<LayoutDesign>?,
        extCoordinateSystem: ExtSridV1?,
    ): ExtSwitchResponseV1? {
        val branch = branchByDesignOid(layoutDesignService, designOid)
        val publication = publicationService.getPublicationByUuidOrLatest(branch, layoutVersion?.value)
        val id = idLookup(switchDao, oid.value)
        val oids = branchOids(switchDao, branch, oid.value, id)
        return createExtSwitchResponse(oids, id, publication, branch, coordinateSystem(extCoordinateSystem))
    }

    fun getExtSwitchModifications(
        oid: ExtOidV1<LayoutSwitch>,
        layoutVersionFrom: ExtLayoutVersionV1,
        layoutVersionTo: ExtLayoutVersionV1?,
        designOid: ExtOidV1<LayoutDesign>?,
        extCoordinateSystem: ExtSridV1?,
    ): ExtModifiedSwitchResponseV1? {
        val branch = branchByDesignOid(layoutDesignService, designOid)
        val publications =
            publicationService.getPublicationsToCompare(
                layoutVersionFrom.value,
                layoutVersionTo?.value,
                branch = branch,
            )
        // Lookup before change check to produce consistent error if oid is not found
        val id = idLookup(switchDao, oid.value)
        val oids = branchOids(switchDao, branch, oid.value, id)
        return if (publications.areDifferent()) {
            createSwitchModificationResponse(oids, id, publications, branch, coordinateSystem(extCoordinateSystem))
        } else {
            publicationsAreTheSame(layoutVersionFrom.value)
        }
    }

    private fun createExtSwitchResponse(
        oids: BranchOidsV1<LayoutSwitch>,
        id: IntId<LayoutSwitch>,
        publication: Publication,
        branch: LayoutBranch,
        coordinateSystem: Srid,
    ): ExtSwitchResponseV1? {
        val moment = publication.publicationTime
        return switchDao.getOfficialAtMoment(branch, id, moment)?.let { switch ->
            ExtSwitchResponseV1(
                layoutVersion = ExtLayoutVersionV1(publication),
                coordinateSystem = ExtSridV1(coordinateSystem),
                switch = createExtSwitch(getSwitchData(oids, switch, branch, moment), coordinateSystem),
            )
        }
    }

    private fun createSwitchModificationResponse(
        oids: BranchOidsV1<LayoutSwitch>,
        id: IntId<LayoutSwitch>,
        publications: PublicationComparison,
        branch: LayoutBranch,
        coordinateSystem: Srid,
    ): ExtModifiedSwitchResponseV1? {
        val startMoment = publications.from.publicationTime
        val endMoment = publications.to.publicationTime
        return publicationDao
            .fetchPublishedSwitchBetween(id, startMoment, endMoment, branch)
            ?.let(switchDao::fetch)
            ?.let { switch ->
                ExtModifiedSwitchResponseV1(
                    layoutVersionFrom = ExtLayoutVersionV1(publications.from),
                    layoutVersionTo = ExtLayoutVersionV1(publications.to),
                    coordinateSystem = ExtSridV1(coordinateSystem),
                    switch = createExtSwitch(getSwitchData(oids, switch, branch, endMoment), coordinateSystem),
                )
            } ?: layoutAssetVersionsAreTheSame(id, publications)
    }

    private fun createSwitchCollectionResponse(
        publication: Publication,
        branch: LayoutBranch,
        coordinateSystem: Srid,
        nameFilter: String?,
    ): ExtSwitchCollectionResponseV1 {
        val moment = publication.publicationTime
        val switches =
            switchDao.listOfficialAtMoment(branch, moment).filter {
                it.exists && (nameFilter == null || it.name.contains(nameFilter, ignoreCase = true))
            }
        val filteredSwitches = filterToDesignBranchSwitches(branch, switches)
        return ExtSwitchCollectionResponseV1(
            layoutVersion = ExtLayoutVersionV1(publication),
            coordinateSystem = ExtSridV1(coordinateSystem),
            switchCollection = createExtSwitches(branch, moment, coordinateSystem, filteredSwitches),
        )
    }

    private fun createSwitchCollectionModificationResponse(
        publications: PublicationComparison,
        branch: LayoutBranch,
        coordinateSystem: Srid,
        nameFilter: String?,
    ): ExtModifiedSwitchCollectionResponseV1? {
        val startMoment = publications.from.publicationTime
        val endMoment = publications.to.publicationTime
        return publicationDao
            .fetchPublishedSwitchesBetween(startMoment, endMoment, branch)
            .takeIf { versions -> versions.isNotEmpty() }
            ?.let(switchDao::fetchMany)
            ?.let { all -> nameFilter?.let { all.filter { s -> s.name.contains(it, ignoreCase = true) } } ?: all }
            ?.let { all -> filterToDesignBranchSwitches(branch, all) }
            ?.takeIf { it.isNotEmpty() }
            ?.let { modifiedSwitches ->
                ExtModifiedSwitchCollectionResponseV1(
                    layoutVersionFrom = ExtLayoutVersionV1(publications.from),
                    layoutVersionTo = ExtLayoutVersionV1(publications.to),
                    coordinateSystem = ExtSridV1(coordinateSystem),
                    switchCollection = createExtSwitches(branch, endMoment, coordinateSystem, modifiedSwitches),
                )
            }
    }

    /**
     * A switch with no OID in a design branch is not part of the design's externally published state and is not listed
     * by the design's collection routes.
     */
    private fun filterToDesignBranchSwitches(branch: LayoutBranch, switches: List<LayoutSwitch>): List<LayoutSwitch> =
        if (branch == LayoutBranch.main) switches
        else {
            val branchSwitchIds = switchDao.fetchExternalIds(branch, switches.map { it.id as IntId }).keys
            switches.filter { switch -> switch.id in branchSwitchIds }
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
            officialSwitchOid = data.officialOid?.let(::ExtOidV1),
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
        val officialOid: Oid<LayoutSwitch>?,
        val switch: LayoutSwitch,
        val structure: SwitchStructure,
        val owner: SwitchOwner,
        val trackLinks: List<SwitchTrackJoints>,
    )

    private fun getSwitchData(
        oids: BranchOidsV1<LayoutSwitch>,
        switch: LayoutSwitch,
        branch: LayoutBranch,
        moment: Instant,
    ): SwitchData {
        val id = switch.id as IntId
        return SwitchData(
            oid = oids.oid,
            officialOid = oids.officialOid,
            switch = switch,
            structure = switchLibraryService.getSwitchStructure(switch.switchStructureId),
            owner = switchLibraryService.getSwitchOwner(switch.ownerId),
            trackLinks = getSwitchTrackLinks(branch, moment, setOf(id))[id] ?: emptyList(),
        )
    }

    private fun getSwitchData(switches: List<LayoutSwitch>, branch: LayoutBranch, moment: Instant): List<SwitchData> {
        val switchIds = switches.map { it.id as IntId }
        val switchExtIds = switchDao.fetchExternalIds(branch, switchIds)
        val officialExtIdsIfBranch =
            if (branch == LayoutBranch.main) mapOf() else switchDao.fetchExternalIds(LayoutBranch.main, switchIds)
        val trackLinks = getSwitchTrackLinks(branch, moment, switchIds.toSet())
        return switches.map { switch ->
            val id = switch.id as IntId
            SwitchData(
                oid = switchExtIds[id]?.oid ?: throwOidNotFound(branch, id),
                officialOid = officialExtIdsIfBranch[id]?.oid,
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
        val trackOidRefs = oidReferences(locationTrackDao, branch, tracksAndGeoms.map { it.first.id as IntId })
        val getGeocodingContext = geocodingService.getLazyGeocodingContextsAtMoment(branch, moment)
        return tracksAndGeoms
            .flatMap { (track, geom) ->
                val trackOid = trackOidRefs.get(track.id)
                track.switchIds.mapNotNull { switchId ->
                    produceIf(switchIds.contains(switchId)) {
                        val geocodingContext =
                            getGeocodingContext(track.trackNumberId)
                                ?: throwGeocodingContextNotFound(branch, moment, track.trackNumberId)
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
