package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.aspects.GeoviiteService
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
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.MultiPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.split.SplitService
import fi.fta.geoviite.infra.tracklayout.AlignmentM
import fi.fta.geoviite.infra.tracklayout.DbLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.IAlignment
import fi.fta.geoviite.infra.tracklayout.KmPostGkLocationSource
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostGkLocation
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostService
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LineM
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackM
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.PlanLayoutAlignment
import fi.fta.geoviite.infra.tracklayout.PlanLayoutAlignmentM
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import fi.fta.geoviite.infra.tracklayout.ReferenceLineService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

fun isAlignmentConnected(
    location: Point,
    updateType: LocationTrackPointUpdateType,
    alignment: IAlignment<*>,
    distanceTolerance: Double,
): Boolean {
    val comparePoint = if (updateType == END_POINT) alignment.firstSegmentStart else alignment.lastSegmentEnd
    return comparePoint?.let { p -> calculateDistance(LAYOUT_SRID, p, location) <= distanceTolerance } ?: false
}

@GeoviiteService
class LinkingService
@Autowired
constructor(
    private val geometryService: GeometryService,
    private val planLayoutService: PlanLayoutService,
    private val referenceLineService: ReferenceLineService,
    private val locationTrackService: LocationTrackService,
    private val layoutKmPostService: LayoutKmPostService,
    private val linkingDao: LinkingDao,
    private val splitService: SplitService,
    private val coordinateTransformationService: CoordinateTransformationService,
) {

    fun getSuggestedAlignments(
        branch: LayoutBranch,
        locationTrackId: IntId<LocationTrack>,
        location: Point,
        locationTrackPointUpdateType: LocationTrackPointUpdateType,
        bbox: BoundingBox,
    ): List<LocationTrack> {
        return locationTrackService
            .listNearWithGeometries(branch.draft, bbox)
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
        val (referenceLine, alignment) = referenceLineService.getWithAlignmentOrThrow(branch.draft, referenceLineId)

        val newAlignment = linkGeometry(alignment, parameters)
        return referenceLineService.saveDraft(branch, referenceLine, newAlignment).id
    }

    @Transactional
    fun saveLocationTrackLinking(
        branch: LayoutBranch,
        parameters: LinkingParameters<LocationTrack>,
    ): LayoutRowVersion<LocationTrack> {
        val locationTrackId = parameters.layoutInterval.alignmentId
        val (track, geometry) = locationTrackService.getWithGeometryOrThrow(branch.draft, locationTrackId)

        verifyLocationTrackNotDeleted(track)
        verifyPlanNotHidden(parameters.geometryPlanId)
        verifyAllSplitsDone(branch, parameters.layoutInterval.alignmentId)

        val geomWithNewSegments = linkGeometry(geometry, parameters)
        return saveAndUpdateTopology(branch, track, geometry, geomWithNewSegments)
    }

    private fun <T> linkGeometry(
        layoutGeometry: LocationTrackGeometry,
        parameters: LinkingParameters<T>,
    ): LocationTrackGeometry {
        val geometryInterval = parameters.geometryInterval
        val geometryAlignment = getAlignmentLayout(parameters.geometryPlanId, geometryInterval.alignmentId)
        val layoutRange: Range<LineM<LocationTrackM>> = parameters.layoutInterval.mRange.map(::LineM)
        val geometryRange: Range<LineM<PlanLayoutAlignmentM>> = parameters.geometryInterval.mRange.map(::LineM)
        return linkLocationTrackGeometrySection(layoutGeometry, layoutRange, geometryAlignment, geometryRange)
    }

    private fun <T> linkGeometry(layoutAlignment: LayoutAlignment, parameters: LinkingParameters<T>): LayoutAlignment {
        val geometryInterval = parameters.geometryInterval
        val geometryAlignment = getAlignmentLayout(parameters.geometryPlanId, geometryInterval.alignmentId)
        val layoutRange: Range<LineM<ReferenceLineM>> = parameters.layoutInterval.mRange.map(::LineM)
        val geometryRange: Range<LineM<PlanLayoutAlignmentM>> = parameters.geometryInterval.mRange.map(::LineM)
        return linkLayoutGeometrySection(layoutAlignment, layoutRange, geometryAlignment, geometryRange)
    }

    private fun saveAndUpdateTopology(
        branch: LayoutBranch,
        track: LocationTrack,
        oldGeometry: DbLocationTrackGeometry,
        newGeometry: LocationTrackGeometry,
    ): LayoutRowVersion<LocationTrack> {
        val changedTopologyPoints =
            listOfNotNull(
                    newGeometry.firstSegmentStart.takeIf { startChanged(oldGeometry, newGeometry) },
                    newGeometry.lastSegmentEnd.takeIf { endChanged(oldGeometry, newGeometry) },
                )
                .map { MultiPoint(it) }
        return if (changedTopologyPoints.isEmpty()) {
            locationTrackService.saveDraft(branch, track, newGeometry)
        } else {
            locationTrackService
                .recalculateTopology(
                    branch.draft,
                    listOf(track to newGeometry),
                    changedTopologyPoints,
                    onlySwitchId = null,
                )
                .map { (t, g) -> locationTrackService.saveDraft(branch, t, g) }
                .first { it.id == track.id }
        }
    }

    private fun <M : AlignmentM<M>> startChanged(oldAlignment: IAlignment<M>, newAlignment: IAlignment<M>) =
        !equalsXY(oldAlignment.firstSegmentStart, newAlignment.firstSegmentStart)

    private fun <M : AlignmentM<M>> endChanged(oldAlignment: IAlignment<M>, newAlignment: IAlignment<M>) =
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

        val (referenceLine, layoutAlignment) =
            referenceLineService.getWithAlignmentOrThrow(branch.draft, referenceLineId)
        val geometryAlignment = getAlignmentLayout(parameters.geometryPlanId, geometryInterval.alignmentId)

        val newAlignment =
            replaceLayoutGeometry(layoutAlignment, geometryAlignment, geometryInterval.mRange.map(::LineM))

        return referenceLineService.saveDraft(branch, referenceLine, newAlignment).id
    }

    @Transactional
    fun saveLocationTrackLinking(
        branch: LayoutBranch,
        parameters: EmptyAlignmentLinkingParameters<LocationTrack>,
    ): LayoutRowVersion<LocationTrack> {
        verifyPlanNotHidden(parameters.geometryPlanId)

        val trackId = parameters.layoutAlignmentId
        val geometryInterval = parameters.geometryInterval

        val (track, geometry) = locationTrackService.getWithGeometryOrThrow(branch.draft, trackId)
        val geometryAlignment = getAlignmentLayout(parameters.geometryPlanId, geometryInterval.alignmentId)

        val geomWithNewSegments =
            replaceLocationTrackGeometry(geometryAlignment, geometryInterval.mRange.map(::LineM), trackId)
        return saveAndUpdateTopology(branch, track, geometry, geomWithNewSegments)
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
    fun shortenReferenceLineGeometry(
        branch: LayoutBranch,
        referenceLineId: IntId<ReferenceLine>,
        mRange: Range<Double>,
    ): IntId<ReferenceLine> {
        val (referenceLine, alignment) = referenceLineService.getWithAlignmentOrThrow(branch.draft, referenceLineId)
        val updatedAlignment = cutLayoutGeometry(alignment, mRange.map(::LineM))

        return referenceLineService.saveDraft(branch, referenceLine, updatedAlignment).id
    }

    @Transactional
    fun shortenLocationTrackGeometry(
        branch: LayoutBranch,
        trackId: IntId<LocationTrack>,
        mRange: Range<Double>,
    ): LayoutRowVersion<LocationTrack> {
        verifyAllSplitsDone(branch, trackId)
        val (track, geometry) = locationTrackService.getWithGeometryOrThrow(branch.draft, trackId)
        val geometryWithNewSegments = cutLocationTrackGeometry(geometry, mRange.map(::LineM))
        return saveAndUpdateTopology(branch, track, geometry, geometryWithNewSegments)
    }

    fun getGeometryPlanLinkStatus(layoutContext: LayoutContext, planId: IntId<GeometryPlan>): GeometryPlanLinkStatus {
        return linkingDao.fetchPlanLinkStatuses(layoutContext, listOf(planId))[0]
    }

    fun getGeometryPlanLinkStatuses(
        layoutContext: LayoutContext,
        planIds: List<IntId<GeometryPlan>>,
    ): List<GeometryPlanLinkStatus> {
        return linkingDao.fetchPlanLinkStatuses(layoutContext, planIds)
    }

    @Transactional
    fun saveKmPostLinking(branch: LayoutBranch, parameters: KmPostLinkingParameters): LayoutRowVersion<LayoutKmPost> {
        verifyPlanNotHidden(parameters.geometryPlanId)

        val geometryKmPost = geometryService.getKmPost(parameters.geometryKmPostId)
        val kmPostSrid =
            requireNotNull(geometryService.getKmPostSrid(parameters.geometryKmPostId)) {
                "Cannot link a geometry km post with an unknown coordinate system!"
            }
        requireNotNull(geometryKmPost.location) { "Cannot link a geometry km post without a location!" }

        val layoutKmPost = layoutKmPostService.getOrThrow(branch.draft, parameters.layoutKmPostId)

        val newGkLocation =
            coordinateTransformationService.getTransformationToGkFin(kmPostSrid).transform(geometryKmPost.location)
        val modifiedLayoutKmPost =
            layoutKmPost.copy(
                gkLocation =
                    LayoutKmPostGkLocation(
                        location = newGkLocation,
                        source = KmPostGkLocationSource.FROM_GEOMETRY,
                        confirmed = true,
                    ),
                sourceId = geometryKmPost.id,
            )

        return layoutKmPostService.saveDraft(branch, modifiedLayoutKmPost)
    }

    fun verifyPlanNotHidden(id: IntId<GeometryPlan>) {
        if (geometryService.getPlanHeader(id).isHidden) {
            throw LinkingFailureException(
                message = "Cannot link a plan that is hidden",
                localizedMessageKey = "plan-hidden",
            )
        }
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
