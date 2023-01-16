package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.linking.TrackLayoutKmPostSaveRequest
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.util.pageToList
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LayoutKmPostService(dao: LayoutKmPostDao) : DraftableObjectService<TrackLayoutKmPost, LayoutKmPostDao>(dao) {

    @Transactional
    fun insertKmPost(request: TrackLayoutKmPostSaveRequest): IntId<TrackLayoutKmPost> {
        logger.serviceCall("insertKmPost", "request" to request)
        val kmPost = TrackLayoutKmPost(
            kmNumber = request.kmNumber,
            location = null,
            state = request.state,
            trackNumberId = request.trackNumberId,
            sourceId = null,
        )
        return saveDraftInternal(kmPost).id
    }

    @Transactional
    fun updateKmPost(id: IntId<TrackLayoutKmPost>, kmPost: TrackLayoutKmPostSaveRequest): IntId<TrackLayoutKmPost> {
        logger.serviceCall("updateKmPost", "id" to id, "kmPost" to kmPost)
        val trackLayoutKmPost = getDraftInternal(id).copy(
            kmNumber = kmPost.kmNumber,
            state = kmPost.state,
        )
        return saveDraftInternal(trackLayoutKmPost).id
    }

    override fun createDraft(item: TrackLayoutKmPost) = draft(item)

    override fun createPublished(item: TrackLayoutKmPost) = published(item)

    fun list(publicationState: PublishType, filter: ((kmPost: TrackLayoutKmPost) -> Boolean)?): List<TrackLayoutKmPost> {
        logger.serviceCall("getKmPosts", "publicationState" to publicationState, "filter" to (filter != null))
        val all = listInternal(publicationState, false)
        return filter?.let(all::filter) ?: all
    }

    fun list(publicationState: PublishType, trackNumberId: IntId<TrackLayoutTrackNumber>): List<TrackLayoutKmPost> {
        logger.serviceCall("getKmPosts",
            "publicationState" to publicationState, "trackNumberId" to trackNumberId)
        return listInternal(publicationState, false, trackNumberId)
    }

    private fun listInternal(
        publicationState: PublishType,
        includeDeleted: Boolean,
        trackNumberId: IntId<TrackLayoutTrackNumber>? = null,
        boundingBox: BoundingBox? = null,
    ) = dao.fetchVersions(publicationState, includeDeleted, trackNumberId, boundingBox).map(dao::fetch)

    fun list(publicationState: PublishType, boundingBox: BoundingBox, step: Int): List<TrackLayoutKmPost> {
        logger.serviceCall("getKmPosts",
            "publicationState" to publicationState, "boundingBox" to boundingBox, "step" to step)
        return listInternal(publicationState, false, boundingBox = boundingBox)
            .filter { p -> (step <= 1 || (p.kmNumber.isPrimary() && p.kmNumber.number % step == 0)) }
    }

    fun getByKmNumber(
        publishType: PublishType,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        kmNumber: KmNumber,
    ): TrackLayoutKmPost? {
        logger.serviceCall("getByKmNumber", "trackNumberId" to trackNumberId, "kmNumber" to kmNumber)
        return dao.fetchVersion(publishType, trackNumberId, kmNumber)?.let(dao::fetch)
    }

    fun listNearbyOnTrackPaged(
        publicationState: PublishType,
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
        val allPosts = listInternal(publicationState, false, trackNumberId)
        val postsByDistance = allPosts.map { post -> associateByDistance(post, location) { item -> item.location } }
        return pageToList(postsByDistance, offset, limit, ::compareByDistanceNullsFirst).map { (kmPost, _) -> kmPost }
    }
}
