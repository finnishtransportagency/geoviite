package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationComparison
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.tracklayout.AlignmentPoint
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
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
import org.springframework.transaction.annotation.Transactional

@GeoviiteService
class ExtSwitchServiceV1
@Autowired
constructor(
    private val publicationService: PublicationService,
    private val geocodingService: GeocodingService,
    private val switchDao: LayoutSwitchDao,
    private val locationTrackDao: LocationTrackDao,
    private val locationTrackService: LocationTrackService,
    private val switchLibraryService: SwitchLibraryService,
) {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @Transactional(readOnly = true)
    fun getExtSwitchCollection(
        trackLayoutVersion: Uuid<Publication>?,
        coordinateSystem: Srid?,
    ): ExtSwitchCollectionResponseV1 =
        publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, trackLayoutVersion).let { publication ->
            createSwitchCollectionResponse(publication, coordinateSystem ?: LAYOUT_SRID)
        }

    fun getExtSwitchCollectionModifications(
        trackLayoutVersionFrom: Uuid<Publication>,
        trackLayoutVersionTo: Uuid<Publication>?,
        coordinateSystem: Srid?,
    ): ExtModifiedSwitchCollectionResponseV1? =
        publicationService.getPublicationsToCompare(trackLayoutVersionFrom, trackLayoutVersionTo).let { publications ->
            if (publications.areDifferent()) {
                createSwitchCollectionModificationResponse(publications, coordinateSystem ?: LAYOUT_SRID)
            } else {
                publicationsAreTheSame(trackLayoutVersionFrom)
            }
        }

    fun getExtSwitch(
        oid: Oid<LayoutSwitch>,
        trackLayoutVersion: Uuid<Publication>?,
        coordinateSystem: Srid?,
    ): ExtSwitchResponseV1? =
        publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, trackLayoutVersion).let { publication ->
            createSwitchResponse(oid, idLookup(oid), publication, coordinateSystem ?: LAYOUT_SRID)
        }

    fun getExtSwitchModifications(
        oid: Oid<LayoutSwitch>,
        trackLayoutVersionFrom: Uuid<Publication>,
        trackLayoutVersionTo: Uuid<Publication>?,
        coordinateSystem: Srid?,
    ): ExtModifiedSwitchResponseV1? =
        publicationService.getPublicationsToCompare(trackLayoutVersionFrom, trackLayoutVersionTo).let { publications ->
            val id = idLookup(oid) // Lookup before change check to produce consistent error if oid is not found
            if (publications.areDifferent()) {
                createSwitchModificationResponse(oid, id, publications, coordinateSystem ?: LAYOUT_SRID)
            } else {
                publicationsAreTheSame(trackLayoutVersionFrom)
            }
        }

    private fun idLookup(oid: Oid<LayoutSwitch>): IntId<LayoutSwitch> =
        switchDao.lookupByExternalId(oid)?.id ?: throw ExtOidNotFoundExceptionV1("switch lookup failed, oid=$oid")

    private fun createSwitchCollectionResponse(publication: Publication, srid: Srid): ExtSwitchCollectionResponseV1 {
        val branch = publication.layoutBranch.branch
        val moment = publication.publicationTime
        val getGeocodingContext = geocodingService.getLazyGeocodingContextsAtMoment(branch, moment)
        val switches =
            switchDao.listOfficialAtMoment(branch, moment).filter { it.exists }.associateBy { it.id as IntId }
        val externalSwitchIds = switchDao.fetchExternalIds(branch)
        val switchTrackLinks = getSwitchTrackLinks(branch, moment)
        val extSwitches =
            switches.entries
                .map { (id, switch) ->
                    val oid = requireNotNull(externalSwitchIds[id]?.oid) { "Switch oid not found for id=$id" }
                    val locationTrackLinks = switchTrackLinks[id] ?: emptyList()
                    createExtSwitch(oid, switch, srid, locationTrackLinks, getGeocodingContext)
                }
                .sortedBy { it.switchOid.toString() }
        return ExtSwitchCollectionResponseV1(
            trackLayoutVersion = publication.uuid,
            coordinateSystem = srid,
            switchCollection = extSwitches,
        )
    }

    data class SwitchTrackJoints(
        val locationTrackOid: Oid<LocationTrack>,
        val trackNumberId: IntId<LayoutTrackNumber>,
        val jointLocations: List<Pair<SwitchLink, AlignmentPoint<LocationTrackM>>>,
    )

    private fun createSwitchResponse(
        oid: Oid<LayoutSwitch>,
        id: IntId<LayoutSwitch>,
        publication: Publication,
        srid: Srid,
    ): ExtSwitchResponseV1? {
        val branch = publication.layoutBranch.branch
        val moment = publication.publicationTime
        val getGeocodingContext = geocodingService.getLazyGeocodingContextsAtMoment(branch, moment)
        return switchDao.getOfficialAtMoment(branch, id, moment)?.let { switch ->
            val locationTrackJoints = getSwitchTrackLinks(branch, moment, setOf(id))[id] ?: emptyList()
            ExtSwitchResponseV1(
                trackLayoutVersion = publication.uuid,
                coordinateSystem = srid,
                switch = createExtSwitch(oid, switch, srid, locationTrackJoints, getGeocodingContext),
            )
        }
    }

    private fun getSwitchTrackLinks(
        branch: LayoutBranch,
        moment: Instant,
        switchIds: Set<IntId<LayoutSwitch>>? = null,
    ): Map<IntId<LayoutSwitch>, List<SwitchTrackJoints>> {
        // This naive implementation simply iterates all tracks on the given moment
        // However, due to caching (of tracks & switch-links inside track geometries), it's faster than re-resolving
        // the versions from nodes in DB
        // For small switch-counts, this might not actually be the case, but those fetches are fast anyhow
        val tracksAndGeoms = locationTrackService.listOfficialWithGeometryAtMoment(branch, moment)
        val trackOids = locationTrackDao.fetchExternalIds(branch)
        return tracksAndGeoms
            .flatMap { (track, geom) ->
                val trackOid =
                    requireNotNull(trackOids[track.id]?.oid) { "Location track oid not found for id=${track.id}" }
                track.switchIds.mapNotNull { switchId ->
                    produceIf(switchIds == null || switchIds.contains(switchId)) {
                        switchId to
                            SwitchTrackJoints(
                                locationTrackOid = trackOid,
                                trackNumberId = track.trackNumberId,
                                jointLocations = geom.getSwitchLocations(switchId),
                            )
                    }
                }
            }
            .groupBy({ it.first }, { it.second })
    }

    private fun createExtSwitch(
        oid: Oid<LayoutSwitch>,
        switch: LayoutSwitch,
        srid: Srid,
        locationTrackJoints: List<SwitchTrackJoints>,
        getGeocodingContext: (IntId<LayoutTrackNumber>) -> GeocodingContext<ReferenceLineM>?,
    ): ExtSwitchV1 {
        val structure = switchLibraryService.getSwitchStructure(switch.switchStructureId)
        val owner =
            requireNotNull(switchLibraryService.getSwitchOwner(switch.ownerId)) {
                "Switch owner not found for id=${switch.ownerId}"
            }
        return ExtSwitchV1(
            switchOid = oid,
            switchName = switch.name,
            type = structure.type,
            hand = ExtSwitchHandV1.of(structure.hand),
            presentationJointNumber = structure.presentationJointNumber.intValue,
            stateCategory = ExtSwitchStateV1.of(switch.stateCategory),
            owner = owner.name,
            trapPoint = ExtSwitchTrapPointV1.of(switch.trapPoint),
            switchJoints =
                switch.joints
                    .sortedBy { it.number.intValue }
                    .map { joint ->
                        ExtSwitchJointV1(
                            jointNumber = joint.number.intValue,
                            location = toExtCoordinate(joint.location, srid),
                        )
                    },
            trackLinks =
                locationTrackJoints
                    .sortedBy { it.locationTrackOid.toString() }
                    .map { trackJoints ->
                        val geocodingContext =
                            requireNotNull(getGeocodingContext(trackJoints.trackNumberId)) {
                                "Geocoding context not found for track number id=${trackJoints.trackNumberId} linked to switch oid=$oid"
                            }
                        ExtSwitchTrackLinkV1(
                            locationTrackOid = trackJoints.locationTrackOid,
                            joints =
                                trackJoints.jointLocations.map { (link, location) ->
                                    val addressPoint =
                                        requireNotNull(geocodingContext.toAddressPoint(location)?.first) {
                                            "Address calculation failed for location=$location, trackNumberId=${trackJoints.trackNumberId} linked to switch oid=$oid"
                                        }
                                    ExtSwitchTrackJointV1(
                                        jointNumber = link.jointNumber.intValue,
                                        location = toExtAddressPoint(addressPoint, srid),
                                    )
                                },
                        )
                    },
        )
    }

    private fun createSwitchCollectionModificationResponse(
        publications: PublicationComparison,
        coordinateSystem: Srid,
    ): ExtModifiedSwitchCollectionResponseV1? {
        TODO()
    }

    private fun createSwitchModificationResponse(
        oid: Oid<LayoutSwitch>,
        id: IntId<LayoutSwitch>,
        publications: PublicationComparison,
        srid: Srid,
    ): ExtModifiedSwitchResponseV1? {
        TODO()
    }
}
