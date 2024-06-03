package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.linking.TrackLayoutKmPostSaveRequest
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.util.pageToList
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LayoutKmPostService(
    dao: LayoutKmPostDao,
    private val referenceLineService: ReferenceLineService,
) : LayoutAssetService<TrackLayoutKmPost, LayoutKmPostDao>(dao) {

    @Transactional
    fun insertKmPost(branch: LayoutBranch, request: TrackLayoutKmPostSaveRequest): IntId<TrackLayoutKmPost> {
        logger.serviceCall("insertKmPost", "branch" to branch, "request" to request)
        val kmPost = TrackLayoutKmPost(
            kmNumber = request.kmNumber,
            location = null,
            state = request.state,
            trackNumberId = request.trackNumberId,
            sourceId = null,
            contextData = LayoutContextData.newDraft(branch),
        )
        return saveDraftInternal(branch, kmPost).id
    }

    @Transactional
    fun updateKmPost(
        branch: LayoutBranch,
        id: IntId<TrackLayoutKmPost>,
        kmPost: TrackLayoutKmPostSaveRequest,
    ): IntId<TrackLayoutKmPost> {
        logger.serviceCall("updateKmPost", "branch" to branch, "id" to id, "kmPost" to kmPost)
        val trackLayoutKmPost = dao.getOrThrow(branch.draft, id).copy(
            kmNumber = kmPost.kmNumber,
            state = kmPost.state,
        )
        return saveDraftInternal(branch, trackLayoutKmPost).id
    }

    fun list(layoutContext: LayoutContext, filter: ((kmPost: TrackLayoutKmPost) -> Boolean)?): List<TrackLayoutKmPost> {
        logger.serviceCall("list", "layoutContext" to layoutContext, "filter" to (filter != null))
        val all = dao.list(layoutContext, false)
        return filter?.let(all::filter) ?: all
    }

    fun list(layoutContext: LayoutContext, trackNumberId: IntId<TrackLayoutTrackNumber>): List<TrackLayoutKmPost> {
        logger.serviceCall("list", "layoutContext" to layoutContext, "trackNumberId" to trackNumberId)
        return dao.list(layoutContext, false, trackNumberId)
    }

    fun list(layoutContext: LayoutContext, boundingBox: BoundingBox, step: Int): List<TrackLayoutKmPost> {
        logger.serviceCall(
            "getKmPosts",
            "layoutContext" to layoutContext,
            "boundingBox" to boundingBox,
            "step" to step,
        )
        return dao
            .list(layoutContext, false, bbox = boundingBox)
            .filter { p -> (step <= 1 || (p.kmNumber.isPrimary() && p.kmNumber.number % step == 0)) }
    }

    fun getByKmNumber(
        layoutContext: LayoutContext,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        kmNumber: KmNumber,
        includeDeleted: Boolean,
    ): TrackLayoutKmPost? {
        logger.serviceCall(
            "getByKmNumber",
            "layoutContext" to layoutContext,
            "kmNumber" to kmNumber,
            "includeDeleted" to includeDeleted
        )
        return dao.fetchVersion(layoutContext, trackNumberId, kmNumber, includeDeleted)?.let(dao::fetch)
    }

    fun listNearbyOnTrackPaged(
        layoutContext: LayoutContext,
        location: Point,
        trackNumberId: IntId<TrackLayoutTrackNumber>?,
        offset: Int,
        limit: Int?,
    ): List<TrackLayoutKmPost> {
        logger.serviceCall(
            "getNearbyKmPostsOnTrackPaged",
            "layoutContext" to layoutContext,
            "location" to location,
            "trackNumberId" to trackNumberId,
            "offset" to offset,
            "limit" to limit
        )
        val allPosts = dao.list(layoutContext, false, trackNumberId)
        val postsByDistance = allPosts.map { post -> associateByDistance(post, location) }
        return pageToList(postsByDistance, offset, limit, ::compareByDistanceNullsFirst).map { (kmPost, _) -> kmPost }
    }

    @Transactional(readOnly = true)
    fun getSingleKmPostLength(layoutContext: LayoutContext, id: IntId<TrackLayoutKmPost>): Double? {
        logger.serviceCall("getSingleKmPostLength", "layoutContext" to layoutContext, "id" to id)
        return dao.get(layoutContext, id)?.getAsIntegral()?.let { kmPost ->
            referenceLineService.getByTrackNumberWithAlignment(layoutContext, kmPost.trackNumberId)
                ?.let { (_, alignment) ->
                    val kmPostM = alignment.getClosestPointM(kmPost.location)?.first
                    val kmEndM = getKmEndM(layoutContext, kmPost.trackNumberId, kmPost.kmNumber, alignment)
                    if (kmPostM == null || kmEndM == null) null else kmEndM - kmPostM
                }
        }
    }

    private fun getKmEndM(
        layoutContext: LayoutContext,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        kmNumber: KmNumber,
        referenceLineAlignment: LayoutAlignment,
    ): Double? {
        val nextKmPost = dao
            .fetchNextWithLocationAfter(layoutContext, trackNumberId, kmNumber, LayoutState.IN_USE)
            ?.let(dao::fetch)
            ?.getAsIntegral()
        return if (nextKmPost == null) {
            referenceLineAlignment.length
        } else {
            referenceLineAlignment.getClosestPointM(nextKmPost.location)?.first
        }
    }
}

fun associateByDistance(kmPost: TrackLayoutKmPost, comparisonPoint: Point): Pair<TrackLayoutKmPost, Double?> =
    kmPost to kmPost.location?.let { l -> calculateDistance(LAYOUT_SRID, comparisonPoint, l) }
