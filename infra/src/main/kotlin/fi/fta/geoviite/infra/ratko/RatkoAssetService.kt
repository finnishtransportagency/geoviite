package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.integration.SwitchJointChange
import fi.fta.geoviite.infra.publication.PublishedSwitch
import fi.fta.geoviite.infra.ratko.model.RatkoAssetLocation
import fi.fta.geoviite.infra.ratko.model.RatkoNodeType
import fi.fta.geoviite.infra.ratko.model.RatkoOid
import fi.fta.geoviite.infra.ratko.model.RatkoPointStates
import fi.fta.geoviite.infra.ratko.model.RatkoSwitchAsset
import fi.fta.geoviite.infra.ratko.model.convertToRatkoAssetGeometries
import fi.fta.geoviite.infra.ratko.model.convertToRatkoAssetLocations
import fi.fta.geoviite.infra.ratko.model.convertToRatkoSwitch
import fi.fta.geoviite.infra.ratko.model.mapGeometryTypeToNodeType
import fi.fta.geoviite.infra.ratko.model.mapJointNumberToGeometryType
import fi.fta.geoviite.infra.switchLibrary.SwitchBaseType
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitchJoint
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import java.time.Instant

@GeoviiteService
@ConditionalOnBean(RatkoClientConfiguration::class)
class RatkoAssetService @Autowired constructor(
    private val ratkoClient: RatkoClient,
    private val switchLibraryService: SwitchLibraryService,
    private val switchDao: LayoutSwitchDao,
) {

    fun pushSwitchChangesToRatko(
        layoutBranch: LayoutBranch,
        publishedSwitches: Collection<PublishedSwitch>,
        publicationTime: Instant,
    ) {
        publishedSwitches
            .groupBy { it.id }
            .map { (_, switches) ->
                val newestVersion = switches.maxBy { it.version.version }.version
                switchDao.fetch(newestVersion) to switches
                    .flatMap { it.changedJoints }
                    .reversed()
                    //We assume that publishedSwitches are ordered by publication time
                    //therefore if there are multiple changes for the same joint, the "last" one is what we want
                    .distinctBy { it.number to it.locationTrackId }
                    .reversed()
            }
            .sortedBy { (switch, _) -> sortByDeletedStateFirst(switch.stateCategory) }
            .forEach { (layoutSwitch, changedJoints) ->
                try {
                    requireNotNull(layoutSwitch.externalId) { "OID required for switch, sw=${layoutSwitch.id}" }
                        .let { oid -> ratkoClient.getSwitchAsset(RatkoOid<RatkoSwitchAsset>(oid)) }
                        ?.also { existingRatkoSwitch ->
                            updateSwitch(
                                layoutBranch = layoutBranch,
                                layoutSwitch = layoutSwitch,
                                existingRatkoSwitch = existingRatkoSwitch,
                                jointChanges = changedJoints,
                                moment = publicationTime
                            )
                        }
                        ?: createSwitch(layoutSwitch, changedJoints)
                } catch (ex: RatkoPushException) {
                    throw RatkoSwitchPushException(ex, layoutSwitch)
                }
            }
    }

    private fun updateSwitch(
        layoutBranch: LayoutBranch,
        layoutSwitch: TrackLayoutSwitch,
        existingRatkoSwitch: RatkoSwitchAsset,
        jointChanges: List<SwitchJointChange>,
        moment: Instant,
    ) {
        require(layoutSwitch.id is IntId) { "Cannot push draft switches to Ratko, $layoutSwitch" }
        requireNotNull(layoutSwitch.externalId) { "Cannot update switch without oid, id=${layoutSwitch.id}" }
        val switchOid = RatkoOid<RatkoSwitchAsset>(layoutSwitch.externalId)

        val switchStructure = switchLibraryService.getSwitchStructure(layoutSwitch.switchStructureId)
        val switchOwner = layoutSwitch.ownerId?.let { switchLibraryService.getSwitchOwner(layoutSwitch.ownerId) }

        val updatedRatkoSwitch = convertToRatkoSwitch(
            layoutSwitch = layoutSwitch,
            switchStructure = switchStructure,
            switchOwner = switchOwner,
            existingRatkoSwitch = existingRatkoSwitch,
        )

        val existingLocations = existingRatkoSwitch.locations ?: emptyList()

        val includeBaseLocations = layoutSwitch.joints.any { lj ->
            jointChanges.none { jc -> jc.number == lj.number && !jc.isRemoved }
        }

        val baseRatkoLocations =
            if (includeBaseLocations && existingLocations.isNotEmpty())
                getBaseRatkoSwitchLocations(
                    layoutBranch = layoutBranch,
                    switchId = layoutSwitch.id,
                    existingRatkoLocations = existingLocations,
                    jointChanges = jointChanges,
                    switchStructure = switchStructure,
                    moment = moment,
                )
            else emptyList()

        updateSwitchProperties(
            switchOid = switchOid,
            currentRatkoSwitch = existingRatkoSwitch,
            updatedRatkoSwitch = updatedRatkoSwitch,
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
        layoutBranch: LayoutBranch,
        switchId: IntId<TrackLayoutSwitch>,
        existingRatkoLocations: List<RatkoAssetLocation>,
        jointChanges: Collection<SwitchJointChange>,
        switchStructure: SwitchStructure,
        moment: Instant,
    ): List<RatkoAssetLocation> {
        val linkedLocationTracks = switchDao.findLocationTracksLinkedToSwitchAtMoment(
            layoutBranch = layoutBranch,
            switchId = switchId,
            topologyJointNumber = switchStructure.presentationJointNumber,
            moment = moment
        ).map { ids ->
            checkNotNull(ids.externalId) { "Official LocationTrack must have an external ID, id=${ids.rowVersion}" }
        }

        return existingRatkoLocations
            .map { location ->
                location.nodecollection.nodes
                    .filter { node -> linkedLocationTracks.any { it.toString() == node.point.locationtrack?.id } }
                    .filter { node -> node.point.state?.name == RatkoPointStates.VALID }
                    .filterNot { node ->
                        jointChanges.any { jointChange ->
                            val nodeType = mapGeometryTypeToNodeType(
                                mapJointNumberToGeometryType(jointChange.number, switchStructure.baseType)
                            )

                            checkNotNull(jointChange.locationTrackExternalId) {
                                "Cannot push switch changes with missing location track oid, $jointChange"
                            }

                            jointChange.locationTrackExternalId.toString() == node.point.locationtrack?.id
                                    && nodeType == node.nodeType
                        }
                    }
                    .let { nodes ->
                        location.copy(nodecollection = location.nodecollection.copy(nodes = nodes))
                    }
            }
            .filter { location -> location.nodecollection.nodes.isNotEmpty() }
    }

    private fun updateSwitchGeoms(
        switchOid: RatkoOid<RatkoSwitchAsset>,
        switchBaseType: SwitchBaseType,
        joints: Collection<TrackLayoutSwitchJoint>,
    ) {
        val switchGeometries = convertToRatkoAssetGeometries(joints, switchBaseType)
        ratkoClient.replaceAssetGeoms(switchOid, switchGeometries)
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
                (baseRatkoLocations + changedSwitchLocations)
                    .sortedBy (::sortJointAToTop)
                    .mapIndexed { index, ratkoAssetLocation ->
                        ratkoAssetLocation.copy(priority = index + 1)
                    }

            ratkoClient.replaceAssetLocations(switchOid, ratkoSwitchLocations)
        }
    }

    private fun sortJointAToTop(location: RatkoAssetLocation):Int {
        val containsJointA = location.nodecollection.nodes.any { node ->
            node.nodeType == RatkoNodeType.JOINT_A
        }
        return if (containsJointA) -1 else 1
    }

    private fun updateSwitchProperties(
        switchOid: RatkoOid<RatkoSwitchAsset>,
        currentRatkoSwitch: RatkoSwitchAsset,
        updatedRatkoSwitch: RatkoSwitchAsset
    ) {
        ratkoClient.updateAssetProperties(switchOid, updatedRatkoSwitch.properties)

        //Switch state is only touched by Geoviite when the category is different
        if (updatedRatkoSwitch.state != currentRatkoSwitch.state) {
            ratkoClient.updateAssetState(switchOid, updatedRatkoSwitch.state)
        }
    }

    private fun createSwitch(layoutSwitch: TrackLayoutSwitch, jointChanges: List<SwitchJointChange>) {
        val switchStructure = switchLibraryService.getSwitchStructure(layoutSwitch.switchStructureId)
        val switchOwner = layoutSwitch.ownerId?.let { switchLibraryService.getSwitchOwner(layoutSwitch.ownerId) }

        val ratkoSwitch = convertToRatkoSwitch(
            layoutSwitch = layoutSwitch,
            switchStructure = switchStructure,
            switchOwner = switchOwner,
        )

        val switchOid = ratkoClient.newAsset<RatkoSwitchAsset>(ratkoSwitch)
        checkNotNull(switchOid) { "Did not receive oid from Ratko for switch $ratkoSwitch" }

        generateSwitchLocations(jointChanges, switchStructure).also { switchLocations ->
            ratkoClient.replaceAssetLocations(switchOid, switchLocations)
        }

        updateSwitchGeoms(
            switchOid = switchOid,
            switchBaseType = switchStructure.baseType,
            joints = layoutSwitch.joints,
        )
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
}
