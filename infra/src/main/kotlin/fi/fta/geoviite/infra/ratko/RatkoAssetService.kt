package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.FullRatkoExternalId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.integration.SwitchJointChange
import fi.fta.geoviite.infra.publication.PublishedSwitch
import fi.fta.geoviite.infra.ratko.model.PushableLayoutBranch
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
import fi.fta.geoviite.infra.tracklayout.DesignAssetState
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchJoint
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import java.time.Instant

@GeoviiteService
@ConditionalOnBean(RatkoClientConfiguration::class)
class RatkoAssetService
@Autowired
constructor(
    private val ratkoClient: RatkoClient,
    private val switchLibraryService: SwitchLibraryService,
    private val switchDao: LayoutSwitchDao,
) {

    fun pushSwitchChangesToRatko(
        layoutBranch: PushableLayoutBranch,
        publishedSwitches: Collection<PublishedSwitch>,
        publicationTime: Instant,
    ) {
        publishedSwitches
            .groupBy { it.id }
            .map { (_, switches) ->
                val newestVersion = switches.maxBy { it.version.version }.version
                switchDao.fetch(newestVersion) to
                    switches
                        .flatMap { it.changedJoints }
                        .reversed()
                        // We assume that publishedSwitches are ordered by publication time
                        // therefore if there are multiple changes for the same joint, the "last"
                        // one is what we want
                        .distinctBy { it.number to it.locationTrackId }
                        .reversed()
            }
            .sortedBy { (switch, _) -> sortByDeletedStateFirst(switch.stateCategory) }
            .forEach { (layoutSwitch, changedJoints) ->
                val externalId =
                    getFullExtIdAndManagePlanItem(
                        layoutBranch,
                        layoutSwitch.id as IntId,
                        layoutSwitch.designAssetState,
                        ratkoClient,
                        switchDao::fetchExternalId,
                        switchDao::savePlanItemId,
                    )
                requireNotNull(externalId) { "OID required for switch, sw=${layoutSwitch.id}" }
                val existingRatkoSwitch = ratkoClient.getSwitchAsset(RatkoOid(externalId.oid))
                if (existingRatkoSwitch != null) {
                    updateSwitch(
                        layoutBranch = layoutBranch.branch,
                        layoutSwitch = layoutSwitch,
                        layoutSwitchExternalId = externalId,
                        existingRatkoSwitch = existingRatkoSwitch,
                        jointChanges = changedJoints,
                        moment = publicationTime,
                    )
                } else if (
                    layoutSwitch.stateCategory == LayoutStateCategory.EXISTING &&
                        layoutSwitch.designAssetState != DesignAssetState.CANCELLED
                ) {
                    createSwitch(layoutSwitch, externalId, changedJoints)
                }
            }
    }

    private fun updateSwitch(
        layoutBranch: LayoutBranch,
        layoutSwitch: LayoutSwitch,
        layoutSwitchExternalId: FullRatkoExternalId<LayoutSwitch>,
        existingRatkoSwitch: RatkoSwitchAsset,
        jointChanges: List<SwitchJointChange>,
        moment: Instant,
    ) {
        require(layoutSwitch.id is IntId) { "Cannot push draft switches to Ratko, $layoutSwitch" }
        val switchOid = RatkoOid<LayoutSwitch>(layoutSwitchExternalId.oid)

        val switchStructure = switchLibraryService.getSwitchStructure(layoutSwitch.switchStructureId)
        val switchOwner = switchLibraryService.getSwitchOwner(layoutSwitch.ownerId)

        val updatedRatkoSwitch =
            convertToRatkoSwitch(
                layoutSwitch = layoutSwitch,
                layoutSwitchExternalId = layoutSwitchExternalId,
                switchStructure = switchStructure,
                switchOwner = switchOwner,
                existingRatkoSwitch = existingRatkoSwitch,
            )

        val existingLocations = existingRatkoSwitch.locations ?: emptyList()

        val includeBaseLocations =
            layoutSwitch.joints.any { lj -> jointChanges.none { jc -> jc.number == lj.number && !jc.isRemoved } }

        val baseRatkoLocations =
            if (includeBaseLocations && existingLocations.isNotEmpty())
                getBaseRatkoSwitchLocations(
                    layoutBranch = layoutBranch,
                    switchId = layoutSwitch.id as IntId,
                    existingRatkoLocations = existingLocations,
                    jointChanges = jointChanges,
                    switchStructure = switchStructure,
                    moment = moment,
                )
            else emptyList()

        updateSwitchProperties(switchOid, existingRatkoSwitch, updatedRatkoSwitch)

        updateSwitchLocations(switchOid, baseRatkoLocations, jointChanges, switchStructure)

        updateSwitchGeoms(switchOid, switchStructure.baseType, layoutSwitch.joints)
    }

    private fun getBaseRatkoSwitchLocations(
        layoutBranch: LayoutBranch,
        switchId: IntId<LayoutSwitch>,
        existingRatkoLocations: List<RatkoAssetLocation>,
        jointChanges: Collection<SwitchJointChange>,
        switchStructure: SwitchStructure,
        moment: Instant,
    ): List<RatkoAssetLocation> {
        val linkedLocationTracks =
            switchDao
                .findLocationTracksLinkedToSwitchAtMoment(
                    layoutBranch = layoutBranch,
                    switchId = switchId,
                    moment = moment,
                )
                .map { ids ->
                    checkNotNull(ids.externalId) {
                        "Official LocationTrack must have an external ID, id=${ids.rowVersion}"
                    }
                }

        return existingRatkoLocations
            .map { location ->
                location.nodecollection.nodes
                    .filter { node -> linkedLocationTracks.any { it.toString() == node.point.locationtrack?.id } }
                    .filter { node -> node.point.state?.name == RatkoPointStates.VALID }
                    .filterNot { node ->
                        jointChanges.any { jointChange ->
                            val nodeType =
                                mapGeometryTypeToNodeType(
                                    mapJointNumberToGeometryType(jointChange.number, switchStructure.baseType)
                                )

                            checkNotNull(jointChange.locationTrackExternalId) {
                                "Cannot push switch changes with missing location track oid, $jointChange"
                            }

                            jointChange.locationTrackExternalId.toString() == node.point.locationtrack?.id &&
                                nodeType == node.nodeType
                        }
                    }
                    .let { nodes -> location.copy(nodecollection = location.nodecollection.copy(nodes = nodes)) }
            }
            .filter { location -> location.nodecollection.nodes.isNotEmpty() }
    }

    private fun updateSwitchGeoms(
        switchOid: RatkoOid<LayoutSwitch>,
        switchBaseType: SwitchBaseType,
        joints: Collection<LayoutSwitchJoint>,
    ) {
        val switchGeometries = convertToRatkoAssetGeometries(joints, switchBaseType)
        ratkoClient.replaceSwitchGeoms(switchOid, switchGeometries)
    }

    private fun updateSwitchLocations(
        switchOid: RatkoOid<LayoutSwitch>,
        baseRatkoLocations: List<RatkoAssetLocation>,
        jointChanges: List<SwitchJointChange>,
        switchStructure: SwitchStructure,
    ) {
        if (jointChanges.isNotEmpty()) {
            val changedSwitchLocations = generateSwitchLocations(jointChanges, switchStructure)

            val ratkoSwitchLocations =
                (baseRatkoLocations + changedSwitchLocations).sortedBy(::sortJointAToTop).mapIndexed {
                    index,
                    ratkoAssetLocation ->
                    ratkoAssetLocation.copy(priority = index + 1)
                }

            ratkoClient.replaceSwitchLocations(switchOid, ratkoSwitchLocations)
        }
    }

    private fun sortJointAToTop(location: RatkoAssetLocation): Int {
        val containsJointA = location.nodecollection.nodes.any { node -> node.nodeType == RatkoNodeType.JOINT_A }
        return if (containsJointA) -1 else 1
    }

    private fun updateSwitchProperties(
        switchOid: RatkoOid<LayoutSwitch>,
        currentRatkoSwitch: RatkoSwitchAsset,
        updatedRatkoSwitch: RatkoSwitchAsset,
    ) {
        ratkoClient.updateSwitchProperties(switchOid, updatedRatkoSwitch.properties)

        // Switch state is only touched by Geoviite when the category is different
        if (updatedRatkoSwitch.state != currentRatkoSwitch.state) {
            ratkoClient.updateSwitchState(switchOid, updatedRatkoSwitch.state)
        }
    }

    private fun createSwitch(
        layoutSwitch: LayoutSwitch,
        layoutSwitchExternalId: FullRatkoExternalId<LayoutSwitch>?,
        jointChanges: List<SwitchJointChange>,
    ) {
        val switchStructure = switchLibraryService.getSwitchStructure(layoutSwitch.switchStructureId)
        val switchOwner = switchLibraryService.getSwitchOwner(layoutSwitch.ownerId)

        val ratkoSwitch =
            convertToRatkoSwitch(
                layoutSwitch = layoutSwitch,
                layoutSwitchExternalId = layoutSwitchExternalId,
                switchStructure = switchStructure,
                switchOwner = switchOwner,
            )

        val switchOid = ratkoClient.newSwitch(ratkoSwitch)
        checkNotNull(switchOid) { "Did not receive oid from Ratko for switch $ratkoSwitch" }
        assert(!isFakeOID(switchOid)) { "Cannot push fake OID $switchOid into Ratko" }

        val switchLocations = generateSwitchLocations(jointChanges, switchStructure)
        ratkoClient.replaceSwitchLocations(switchOid, switchLocations)

        updateSwitchGeoms(switchOid, switchStructure.baseType, layoutSwitch.joints)

        // Update asset locations again to make Ratko to locate the switch correctly on map.
        // Ratko will eventually fix this problem on their side, but until then, use this
        // workaround.
        ratkoClient.replaceSwitchLocations(switchOid, switchLocations)
    }

    private fun generateSwitchLocations(
        jointChanges: List<SwitchJointChange>,
        switchStructure: SwitchStructure,
    ): List<RatkoAssetLocation> {
        val changedJointsOnExistingTracksOnly = jointChanges.filterNot { it.isRemoved }

        val assetLocations =
            convertToRatkoAssetLocations(
                    jointChanges = changedJointsOnExistingTracksOnly,
                    switchType = switchStructure.baseType,
                )
                .sortedBy(::sortJointAToTop)
                .mapIndexed { index, ratkoAssetLocation -> ratkoAssetLocation.copy(priority = index + 1) }
        return assetLocations
    }
}
