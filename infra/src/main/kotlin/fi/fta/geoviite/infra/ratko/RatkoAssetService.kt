package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.integration.SwitchChange
import fi.fta.geoviite.infra.integration.SwitchJointChange
import fi.fta.geoviite.infra.linking.LinkingDao
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.ratko.model.*
import fi.fta.geoviite.infra.switchLibrary.SwitchBaseType
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Service

@Service
@ConditionalOnBean(RatkoClientConfiguration::class)
class RatkoAssetService @Autowired constructor(
    private val ratkoClient: RatkoClient,
    private val switchService: LayoutSwitchService,
    private val switchLibraryService: SwitchLibraryService,
    private val linkingDao: LinkingDao,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun pushSwitchChangesToRatko(switchChanges: List<SwitchChange>) {
        switchChanges
            .map { change -> change to switchService.getOrThrow(PublishType.OFFICIAL, change.switchId) }
            .sortedBy { sortByDeletedStateFirst(it.second.stateCategory) }
            .forEach { (switchChange, layoutSwitch) ->
                try {
                    layoutSwitch.externalId
                        ?.let { oid -> ratkoClient.getSwitchAsset(RatkoOid<RatkoSwitchAsset>(oid)) }
                        ?.also { existingRatkoSwitch ->
                            updateSwitch(
                                layoutSwitch = layoutSwitch,
                                existingRatkoSwitch = existingRatkoSwitch,
                                jointChanges = switchChange.changedJoints,
                            )
                        }
                        ?: createSwitch(layoutSwitch, switchChange.changedJoints)
                } catch (ex: RatkoPushException) {
                    throw RatkoSwitchPushException(ex, layoutSwitch)
                }
            }
    }

    private fun updateSwitch(
        layoutSwitch: TrackLayoutSwitch,
        existingRatkoSwitch: RatkoSwitchAsset,
        jointChanges: List<SwitchJointChange>,
    ) {
        logger.serviceCall(
            "updateRatkoSwitch",
            "layoutSwitch" to layoutSwitch,
            "existingRatkoSwitch" to existingRatkoSwitch,
            "jointChanges" to jointChanges,
        )

        require(layoutSwitch.id is IntId)
        requireNotNull(layoutSwitch.externalId) { "Cannot update switch without oid $layoutSwitch" }
        val switchOid = RatkoOid<RatkoSwitchAsset>(layoutSwitch.externalId)

        val switchStructure = switchLibraryService.getSwitchStructure(layoutSwitch.switchStructureId)
        val switchOwner = layoutSwitch.ownerId?.let { switchLibraryService.getSwitchOwner(layoutSwitch.ownerId) }

        val updatedRatkoSwitch = convertToRatkoSwitch(
            layoutSwitch = layoutSwitch,
            switchStructure = switchStructure,
            switchOwner = switchOwner,
            existingRatkoSwitch = existingRatkoSwitch,
        )

        updateSwitchProperties(switchOid, existingRatkoSwitch, updatedRatkoSwitch)

        val baseRatkoLocations = getBaseRatkoSwitchLocations(
            switchId = layoutSwitch.id,
            existingRatkoLocations = existingRatkoSwitch.locations ?: emptyList(),
            jointChanges = jointChanges,
            switchStructure = switchStructure,
        )

        updateSwitchLocations(
            switchOid = switchOid,
            baseRatkoLocations = baseRatkoLocations,
            jointChanges = jointChanges,
            switchStructure = switchStructure,
        )

        updateSwitchGeoms(
            switchOid = switchOid,
            switchBaseType = switchStructure.baseType,
            joints = layoutSwitch.joints,
        )
    }

    private fun getBaseRatkoSwitchLocations(
        switchId: IntId<TrackLayoutSwitch>,
        existingRatkoLocations: List<RatkoAssetLocation>,
        jointChanges: List<SwitchJointChange>,
        switchStructure: SwitchStructure,
    ): List<RatkoAssetLocation> {
        return if (existingRatkoLocations.isNotEmpty()) {
            val linkedLocationTracks =
                linkingDao.findLocationTracksLinkedToSwitch(switchId).map { it.second }.distinct()

            existingRatkoLocations
                .map { location ->
                    location.nodecollection.nodes
                        .filter { node -> linkedLocationTracks.any { it.stringValue == node.point.locationtrack?.id } }
                        .filter { node -> node.point.state?.name == RatkoPointStates.VALID }
                        .filterNot { node ->
                            jointChanges.any { jointChange ->
                                val nodeType = mapGeometryTypeToNodeType(
                                    mapJointNumberToGeometryType(jointChange.number, switchStructure.baseType)
                                )

                                checkNotNull(jointChange.locationTrackExternalId) {
                                    "Cannot push switch changes with missing location track oid ${jointChange.locationTrackExternalId}"
                                }

                                jointChange.locationTrackExternalId.stringValue == node.point.locationtrack?.id
                                        && nodeType == node.nodeType
                            }
                        }
                        //Just to ensure that switch alignments unknown to Geoviite are within location track end points
                        //This should be unnecessary in the future
                        .filter { node ->
                            val locationTrackOid = checkNotNull(node.point.locationtrack) {
                                "Found unknown location track oid for switch $node at joint ${node.nodeType}"
                            }

                            val (startTrackMeter, endTrackMeter) = getRatkoLocationTrackEndTrackMeters(
                                locationTrackOid
                            )

                            if (startTrackMeter != null && endTrackMeter != null)
                                node.point.kmM in startTrackMeter..endTrackMeter
                            else false
                        }.let { nodes ->
                            location.copy(nodecollection = location.nodecollection.copy(nodes = nodes))
                        }
                }
                .filter { location -> location.nodecollection.nodes.isNotEmpty() }
        } else emptyList()

    }

    private fun updateSwitchGeoms(
        switchOid: RatkoOid<RatkoSwitchAsset>,
        switchBaseType: SwitchBaseType,
        joints: List<TrackLayoutSwitchJoint>
    ) {
        val switchGeometries = convertToRatkoAssetGeometries(joints, switchBaseType)
        if (switchGeometries.isNotEmpty()) {
            ratkoClient.replaceAssetGeoms(switchOid, switchGeometries)
        }
    }

    private fun updateSwitchLocations(
        switchOid: RatkoOid<RatkoSwitchAsset>,
        baseRatkoLocations: List<RatkoAssetLocation>,
        jointChanges: List<SwitchJointChange>,
        switchStructure: SwitchStructure,
    ) {
        if (jointChanges.isNotEmpty()) {
            val changedSwitchLocations = generateSwitchLocations(jointChanges, switchStructure)

            val ratkoSwitchLocations =
                (baseRatkoLocations + changedSwitchLocations).mapIndexed { index, ratkoAssetLocation ->
                    ratkoAssetLocation.copy(priority = index + 1)
                }

            getNewLocationTrackPoints(changedSwitchLocations).forEach { (locationTrackOid, newPoints) ->
                ratkoClient.updateLocationTrackPoints(locationTrackOid, newPoints)
            }

            ratkoClient.replaceAssetLocations(switchOid, ratkoSwitchLocations)
        }
    }

    private fun updateSwitchProperties(
        switchOid: RatkoOid<RatkoSwitchAsset>,
        currentRatkoSwitch: RatkoSwitchAsset,
        updatedRatkoSwitch: RatkoSwitchAsset
    ) {
        ratkoClient.updateAssetProperties(switchOid, updatedRatkoSwitch.properties)

        if (updatedRatkoSwitch.state != currentRatkoSwitch.state) {
            ratkoClient.updateAssetState(switchOid, updatedRatkoSwitch.state)
        }
    }

    private fun createSwitch(layoutSwitch: TrackLayoutSwitch, jointChanges: List<SwitchJointChange>) {
        logger.serviceCall(
            "createRatkoSwitch",
            "layoutSwitch" to layoutSwitch,
            "jointChanges" to jointChanges
        )
        val switchStructure = switchLibraryService.getSwitchStructure(layoutSwitch.switchStructureId)
        val switchOwner = layoutSwitch.ownerId?.let { switchLibraryService.getSwitchOwner(layoutSwitch.ownerId) }

        val ratkoSwitch = convertToRatkoSwitch(
            layoutSwitch = layoutSwitch,
            switchStructure = switchStructure,
            switchOwner = switchOwner,
        )

        val switchOid = ratkoClient.newAsset<RatkoSwitchAsset>(ratkoSwitch)
        checkNotNull(switchOid) { "Did not receive oid from Ratko $ratkoSwitch" }

        generateSwitchLocations(jointChanges, switchStructure).also { switchLocations ->
            if (switchLocations.isNotEmpty()) {
                getNewLocationTrackPoints(switchLocations).forEach { (locationTrackOid, newPoints) ->
                    ratkoClient.updateLocationTrackPoints(locationTrackOid, newPoints)
                }

                ratkoClient.replaceAssetLocations(switchOid, switchLocations)
            }
        }

        updateSwitchGeoms(
            switchOid = switchOid,
            switchBaseType = switchStructure.baseType,
            joints = layoutSwitch.joints,
        )
    }

    private fun getRatkoLocationTrackEndTrackMeters(
        locationTrackOid: RatkoOid<RatkoLocationTrack>
    ): Pair<RatkoTrackMeter?, RatkoTrackMeter?> {
        val ratkoLocationTrack = ratkoClient.getLocationTrack(locationTrackOid)
        val existingStartTrackMeter = ratkoLocationTrack?.nodecollection?.getStartNode()?.point?.kmM
        val existingEndTrackMeter = ratkoLocationTrack?.nodecollection?.getEndNode()?.point?.kmM

        return existingStartTrackMeter to existingEndTrackMeter
    }

    private fun generateSwitchLocations(
        jointChanges: List<SwitchJointChange>,
        switchStructure: SwitchStructure,
    ): List<RatkoAssetLocation> {
        val changedJointsOnly = jointChanges.filterNot { it.isRemoved }

        return convertToRatkoAssetLocations(
            jointChanges = changedJointsOnly,
            switchType = switchStructure.baseType,
        )
    }

    private fun getNewLocationTrackPoints(
        locations: List<RatkoAssetLocation>
    ): Map<RatkoOid<RatkoLocationTrack>, List<RatkoPoint>> {
        return locations
            .flatMap { location -> location.nodecollection.nodes.map { node -> node.point } }
            .filter { it.locationtrack != null }
            .groupBy { it.locationtrack!! }
            .mapValues { (locationTrackOid, points) ->
                val (startTrackMeter, endTrackMeter) = getRatkoLocationTrackEndTrackMeters(locationTrackOid)

                if (startTrackMeter == null || endTrackMeter == null) emptyList()
                else points
                    .filter { point -> startTrackMeter < point.kmM && point.kmM < endTrackMeter }
                    .sortedBy { it.kmM }
            }
            .filter { it.value.isNotEmpty() }
    }
}
