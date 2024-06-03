package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.error.LinkingFailureException
import fi.fta.geoviite.infra.geography.CoordinateTransformationService
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometryPlanLinkStatus
import fi.fta.geoviite.infra.geometry.GeometryService
import fi.fta.geoviite.infra.geometry.PlanLayoutService
import fi.fta.geoviite.infra.linking.LocationTrackPointUpdateType.END_POINT
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.split.SplitService
import fi.fta.geoviite.infra.tracklayout.DaoResponse
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.PlanLayoutAlignment
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineService
import fi.fta.geoviite.infra.tracklayout.TrackLayoutKmPost
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

fun isAlignmentConnected(
    location: Point,
    updateType: LocationTrackPointUpdateType,
    alignment: LayoutAlignment,
    distanceTolerance: Double,
): Boolean {
    val comparePoint = if (updateType == END_POINT) alignment.firstSegmentStart else alignment.lastSegmentEnd
    return comparePoint?.let { p -> calculateDistance(LAYOUT_SRID, p, location) <= distanceTolerance } ?: false
}

@Service
class LinkingService @Autowired constructor(
    private val geometryService: GeometryService,
    private val planLayoutService: PlanLayoutService,
    private val referenceLineService: ReferenceLineService,
    private val locationTrackService: LocationTrackService,
    private val layoutKmPostService: LayoutKmPostService,
    private val linkingDao: LinkingDao,
    private val coordinateTransformationService: CoordinateTransformationService,
    private val splitService: SplitService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getSuggestedAlignments(
        branch: LayoutBranch,
        locationTrackId: IntId<LocationTrack>,
        location: Point,
        locationTrackPointUpdateType: LocationTrackPointUpdateType,
        bbox: BoundingBox,
    ): List<LocationTrack> {
        return locationTrackService
            .listNearWithAlignments(branch.draft, bbox)
            .filter { (first, _) -> first.id != locationTrackId }
            .filter { (_, second) -> isAlignmentConnected(location, locationTrackPointUpdateType, second, 2.0) }
            .map { (first, _) -> first }
    }

    @Transactional
    fun saveReferenceLineLinking(
        branch: LayoutBranch,
        parameters: LinkingParameters<ReferenceLine>,
    ): IntId<ReferenceLine> {
        verifyPlanNotHidden(parameters.geometryPlanId)

        val referenceLineId = parameters.layoutInterval.alignmentId
        logger.serviceCall(
            "saveReferenceLineLinking",
            "branch" to branch,
            "geometryAlignmentId" to parameters.geometryInterval.alignmentId,
            "referenceLineId" to referenceLineId,
        )

        val (referenceLine, alignment) = referenceLineService.getWithAlignmentOrThrow(branch.draft, referenceLineId)

        val newAlignment = linkGeometry(alignment, parameters)
        return referenceLineService.saveDraft(branch, referenceLine, newAlignment).id
    }

    @Transactional
    fun saveLocationTrackLinking(
        branch: LayoutBranch,
        parameters: LinkingParameters<LocationTrack>,
    ): IntId<LocationTrack> {
        val locationTrackId = parameters.layoutInterval.alignmentId
        val (locationTrack, alignment) = locationTrackService.getWithAlignmentOrThrow(branch.draft, locationTrackId)

        verifyLocationTrackNotDeleted(locationTrack)
        verifyPlanNotHidden(parameters.geometryPlanId)
        verifyAllSplitsDone(branch, parameters.layoutInterval.alignmentId)

        logger.serviceCall(
            "saveLocationTrackLinking",
            "branch" to branch,
            "geometryAlignmentId" to parameters.geometryInterval.alignmentId,
            "locationTrackId" to locationTrackId,
        )

        val newAlignment = linkGeometry(alignment, parameters)
        val newLocationTrack = updateTopology(branch, locationTrack, alignment, newAlignment)

        return locationTrackService.saveDraft(branch, newLocationTrack, newAlignment).id
    }

    private fun <T> linkGeometry(layoutAlignment: LayoutAlignment, parameters: LinkingParameters<T>): LayoutAlignment {
        val geometryInterval = parameters.geometryInterval
        val geometryAlignment = getAlignmentLayout(parameters.geometryPlanId, geometryInterval.alignmentId)
        val layoutRange = parameters.layoutInterval.mRange
        val geometryRange = parameters.geometryInterval.mRange
        return linkLayoutGeometrySection(layoutAlignment, layoutRange, geometryAlignment, geometryRange)
    }

    private fun updateTopology(
        branch: LayoutBranch,
        track: LocationTrack,
        oldAlignment: LayoutAlignment,
        newAlignment: LayoutAlignment,
    ): LocationTrack {
        val startChanged = startChanged(oldAlignment, newAlignment)
        val endChanged = endChanged(oldAlignment, newAlignment)
        return if (startChanged || endChanged) {
            locationTrackService.fetchNearbyTracksAndCalculateLocationTrackTopology(
                layoutContext = branch.draft,
                track = track,
                alignment = newAlignment,
                startChanged = startChanged,
                endChanged = endChanged,
            )
        } else {
            track
        }
    }

    private fun startChanged(oldAlignment: LayoutAlignment, newAlignment: LayoutAlignment) =
        !equalsXY(oldAlignment.firstSegmentStart, newAlignment.firstSegmentStart)

    private fun endChanged(oldAlignment: LayoutAlignment, newAlignment: LayoutAlignment) =
        !equalsXY(oldAlignment.lastSegmentEnd, newAlignment.lastSegmentEnd)

    private fun equalsXY(point1: IPoint?, point2: IPoint?) = point1?.x == point2?.x && point1?.y == point2?.y

    @Transactional
    fun saveReferenceLineLinking(
        branch: LayoutBranch,
        parameters: EmptyAlignmentLinkingParameters<ReferenceLine>,
    ): IntId<ReferenceLine> {
        verifyPlanNotHidden(parameters.geometryPlanId)

        val referenceLineId = parameters.layoutAlignmentId
        val geometryInterval = parameters.geometryInterval

        logger.serviceCall(
            "saveReferenceLineLinking",
            "branch" to branch,
            "geometryAlignmentId" to parameters.geometryInterval.alignmentId,
            "referenceLineId" to referenceLineId,
        )

        val (referenceLine, layoutAlignment) = referenceLineService.getWithAlignmentOrThrow(branch.draft, referenceLineId)
        val geometryAlignment = getAlignmentLayout(parameters.geometryPlanId, geometryInterval.alignmentId)

        val newAlignment = replaceLayoutGeometry(layoutAlignment, geometryAlignment, geometryInterval.mRange)

        return referenceLineService.saveDraft(branch, referenceLine, newAlignment).id
    }

    @Transactional
    fun saveLocationTrackLinking(
        branch: LayoutBranch,
        parameters: EmptyAlignmentLinkingParameters<LocationTrack>,
    ): IntId<LocationTrack> {
        verifyPlanNotHidden(parameters.geometryPlanId)

        val locationTrackId = parameters.layoutAlignmentId
        val geometryInterval = parameters.geometryInterval

        logger.serviceCall(
            "saveLocationTrackLinking",
            "branch" to branch,
            "geometryAlignmentId" to parameters.geometryInterval.alignmentId,
            "locationTrackId" to locationTrackId,
        )

        val (locationTrack, alignment) = locationTrackService.getWithAlignmentOrThrow(branch.draft, locationTrackId)
        val geometryAlignment = getAlignmentLayout(parameters.geometryPlanId, geometryInterval.alignmentId)

        val newAlignment = replaceLayoutGeometry(alignment, geometryAlignment, geometryInterval.mRange)
        val newLocationTrack = updateTopology(branch, locationTrack, alignment, newAlignment)

        return locationTrackService.saveDraft(branch, newLocationTrack, newAlignment).id
    }

    private fun getAlignmentLayout(
        planId: IntId<GeometryPlan>,
        alignmentId: IntId<GeometryAlignment>,
    ): PlanLayoutAlignment {
        val (geometryPlan, transformationError) = planLayoutService.getLayoutPlan(planId)
        if (geometryPlan == null) {
            throw LinkingFailureException("Could not create plan layout: plan=$planId error=$transformationError")
        }
        return geometryPlan.alignments.find { alignment -> alignment.id == alignmentId }
            ?: throw LinkingFailureException("Geometry alignment not found: plan=$planId alignment=$alignmentId")
    }

    @Transactional
    fun updateReferenceLineGeometry(
        branch: LayoutBranch,
        referenceLineId: IntId<ReferenceLine>,
        mRange: Range<Double>,
    ): IntId<ReferenceLine> {
        logger.serviceCall(
            "updateReferenceLineGeometry",
            "branch" to branch,
            "referenceLineId" to referenceLineId,
            "mRange" to mRange,
        )

        val (referenceLine, alignment) = referenceLineService.getWithAlignmentOrThrow(branch.draft, referenceLineId)
        val updatedAlignment = cutLayoutGeometry(alignment, mRange)

        return referenceLineService.saveDraft(branch, referenceLine, updatedAlignment).id
    }

    @Transactional
    fun updateLocationTrackGeometry(
        branch: LayoutBranch,
        locationTrackId: IntId<LocationTrack>,
        mRange: Range<Double>,
    ): IntId<LocationTrack> {
        logger.serviceCall(
            "updateLocationTrackGeometry",
            "branch" to branch,
            "locationTrackId" to locationTrackId,
            "mRange" to mRange,
        )

        verifyAllSplitsDone(branch, locationTrackId)
        val (locationTrack, alignment) = locationTrackService.getWithAlignmentOrThrow(branch.draft, locationTrackId)
        val updatedAlignment = cutLayoutGeometry(alignment, mRange)
        val updatedLocationTrack = updateTopology(branch, locationTrack, alignment, updatedAlignment)

        return locationTrackService.saveDraft(branch, updatedLocationTrack, updatedAlignment).id
    }

    fun getGeometryPlanLinkStatus(layoutContext: LayoutContext, planId: IntId<GeometryPlan>): GeometryPlanLinkStatus {
        logger.serviceCall(
            "getGeometryPlanLinkStatus",
            "layoutContext" to layoutContext,
            "planId" to planId,
        )
        return linkingDao.fetchPlanLinkStatus(layoutContext, planId)
    }

    fun getGeometryPlanLinkStatuses(
        layoutContext: LayoutContext,
        planIds: List<IntId<GeometryPlan>>,
    ): List<GeometryPlanLinkStatus> {
        logger.serviceCall(
            "getGeometryPlanLinkStatuses",
            "planIds" to planIds,
            "layoutContext" to layoutContext,
        )
        return planIds.map { planId -> linkingDao.fetchPlanLinkStatus(layoutContext, planId) }
    }

    @Transactional
    fun saveKmPostLinking(branch: LayoutBranch, parameters: KmPostLinkingParameters): DaoResponse<TrackLayoutKmPost> {
        verifyPlanNotHidden(parameters.geometryPlanId)

        val geometryKmPost = geometryService.getKmPost(parameters.geometryKmPostId)
        val kmPostSrid = geometryService.getKmPostSrid(parameters.geometryKmPostId)
            ?: throw IllegalArgumentException("Cannot link a geometry km post with an unknown coordinate system!")
        requireNotNull(geometryKmPost.location) { "Cannot link a geometry km post without a location!" }

        val layoutKmPost = layoutKmPostService.getOrThrow(branch.draft, parameters.layoutKmPostId)

        val newLocationInLayoutSpace =
            coordinateTransformationService.transformCoordinate(kmPostSrid, LAYOUT_SRID, geometryKmPost.location)
        val modifiedLayoutKmPost = layoutKmPost.copy(
            location = newLocationInLayoutSpace, sourceId = geometryKmPost.id
        )

        return layoutKmPostService.saveDraft(branch, modifiedLayoutKmPost)
    }

    fun verifyPlanNotHidden(id: IntId<GeometryPlan>) {
        if (geometryService.getPlanHeader(id).isHidden) throw LinkingFailureException(
            message = "Cannot link a plan that is hidden", localizedMessageKey = "plan-hidden"
        )
    }

    fun verifyAllSplitsDone(branch: LayoutBranch, id: IntId<LocationTrack>) {
        if (splitService.findUnfinishedSplits(branch, locationTrackIds = listOf(id)).isNotEmpty()) {
            throw LinkingFailureException(
                message = "Cannot link a location track that has unfinished splits",
                localizedMessageKey = "unfinished-splits",
            )
        }
    }

    fun verifyLocationTrackNotDeleted(locationTrack: LocationTrack) {
        if (!locationTrack.exists) {
            throw LinkingFailureException(
                message = "Cannot link a location track that is deleted",
                localizedMessageKey = "location-track-deleted",
            )
        }
    }
}
