package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
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
    fun insertKmPost(request: TrackLayoutKmPostSaveRequest): IntId<TrackLayoutKmPost> {
        logger.serviceCall("insertKmPost", "request" to request)
        val kmPost = TrackLayoutKmPost(
            kmNumber = request.kmNumber,
            location = null,
            state = request.state,
            trackNumberId = request.trackNumberId,
            sourceId = null,
            // TODO: GVT-2401
            contextData = LayoutContextData.newDraft(LayoutBranch.main),
        )
        // TODO: GVT-2401
        return saveDraftInternal(LayoutBranch.main, kmPost).id
    }

    @Transactional
    fun updateKmPost(id: IntId<TrackLayoutKmPost>, kmPost: TrackLayoutKmPostSaveRequest): IntId<TrackLayoutKmPost> {
        logger.serviceCall("updateKmPost", "id" to id, "kmPost" to kmPost)
        val trackLayoutKmPost = dao.getOrThrow(DRAFT, id).copy(
            kmNumber = kmPost.kmNumber,
            state = kmPost.state,
        )
        // TODO: GVT-2401
        return saveDraftInternal(LayoutBranch.main, trackLayoutKmPost).id
    }

    fun list(
        publicationState: PublicationState,
        filter: ((kmPost: TrackLayoutKmPost) -> Boolean)?,
    ): List<TrackLayoutKmPost> {
        logger.serviceCall("list", "publicationState" to publicationState, "filter" to (filter != null))
        val all = dao.list(publicationState, false)
        return filter?.let(all::filter) ?: all
    }

    fun list(publicationState: PublicationState, trackNumberId: IntId<TrackLayoutTrackNumber>): List<TrackLayoutKmPost> {
        logger.serviceCall(
            "list", "publicationState" to publicationState, "trackNumberId" to trackNumberId
        )
        return dao.list(publicationState, false, trackNumberId)
    }

    fun list(publicationState: PublicationState, boundingBox: BoundingBox, step: Int): List<TrackLayoutKmPost> {
        logger.serviceCall(
            "getKmPosts", "publicationState" to publicationState, "boundingBox" to boundingBox, "step" to step
        )
        return dao
            .list(publicationState, false, bbox = boundingBox)
            .filter { p -> (step <= 1 || (p.kmNumber.isPrimary() && p.kmNumber.number % step == 0)) }
    }

    fun getByKmNumber(
        publicationState: PublicationState,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        kmNumber: KmNumber,
        includeDeleted: Boolean,
    ): TrackLayoutKmPost? {
        logger.serviceCall(
            "getByKmNumber",
            "trackNumberId" to trackNumberId,
            "kmNumber" to kmNumber,
            "includeDeleted" to includeDeleted
        )
        return dao.fetchVersion(publicationState, trackNumberId, kmNumber, includeDeleted)?.let(dao::fetch)
    }

    fun listNearbyOnTrackPaged(
        publicationState: PublicationState,
        location: Point,
        trackNumberId: IntId<TrackLayoutTrackNumber>?,
        offset: Int,
        limit: Int?,
    ): List<TrackLayoutKmPost> {
        logger.serviceCall(
            "getNearbyKmPostsOnTrackPaged",
            "publicationState" to publicationState,
            "location" to location,
            "trackNumberId" to trackNumberId,
            "offset" to offset,
            "limit" to limit
        )
        val allPosts = dao.list(publicationState, false, trackNumberId)
        val postsByDistance = allPosts.map { post -> associateByDistance(post, location) }
        return pageToList(postsByDistance, offset, limit, ::compareByDistanceNullsFirst).map { (kmPost, _) -> kmPost }
    }

    @Transactional(readOnly = true)
    fun getSingleKmPostLength(
        publicationState: PublicationState,
        id: IntId<TrackLayoutKmPost>,
    ): Double? = dao.get(publicationState, id)?.getAsIntegral()?.let { kmPost ->
        referenceLineService
            .getByTrackNumberWithAlignment(publicationState, kmPost.trackNumberId)
            ?.let { (_, referenceLineAlignment) ->
                val kmPostM = referenceLineAlignment.getClosestPointM(kmPost.location)?.first
                val kmEndM = getKmEndM(publicationState, kmPost.trackNumberId, kmPost.kmNumber, referenceLineAlignment)
                if (kmPostM == null || kmEndM == null) null else kmEndM - kmPostM
            }
    }

    private fun getKmEndM(
        publicationState: PublicationState,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        kmNumber: KmNumber,
        referenceLineAlignment: LayoutAlignment,
    ): Double? {
        val nextKmPost = dao
            .fetchNextWithLocationAfter(trackNumberId, kmNumber, publicationState, LayoutState.IN_USE)
            ?.let(dao::fetch)
            ?.getAsIntegral()
        return if (nextKmPost == null) referenceLineAlignment.length
        else referenceLineAlignment.getClosestPointM(nextKmPost.location)?.first
    }
}

fun associateByDistance(kmPost: TrackLayoutKmPost, comparisonPoint: Point): Pair<TrackLayoutKmPost, Double?> =
    kmPost to kmPost.location?.let { l -> calculateDistance(LAYOUT_SRID, comparisonPoint, l) }
