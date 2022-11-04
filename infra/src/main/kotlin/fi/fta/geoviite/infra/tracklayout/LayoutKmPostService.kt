package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.linking.TrackLayoutKmPostSaveRequest
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.util.pageToList
import org.springframework.stereotype.Service

@Service
class LayoutKmPostService(dao: LayoutKmPostDao) : DraftableObjectService<TrackLayoutKmPost, LayoutKmPostDao>(dao) {

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

    fun list(
        publishType: PublishType,
        filter: ((kmPost: TrackLayoutKmPost) -> Boolean)?,
    ): List<TrackLayoutKmPost> {
        logger.serviceCall("getKmPosts", "publishType" to publishType, "filter" to (filter != null))
        val all = listInternal(publishType)
        return filter?.let(all::filter) ?: all
    }

    fun list(
        publishType: PublishType,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): List<TrackLayoutKmPost> {
        logger.serviceCall("getKmPosts", "trackNumberId" to trackNumberId)
        return dao.fetchVersions(publishType, trackNumberId = trackNumberId).map(dao::fetch)
    }

    fun list(
        publishType: PublishType,
        boundingBox: BoundingBox,
        step: Int,
    ): List<TrackLayoutKmPost> {
        logger.serviceCall(
            "getKmPosts",
            "publishType" to publishType,
            "boundingBox" to boundingBox,
            "step" to step
        )
        return dao.fetchVersions(publishType, bbox = boundingBox)
            .map(dao::fetch)
            .filter { p -> (step <= 1 || (p.kmNumber.isPrimary() && p.kmNumber.number % step == 0)) }
    }

    fun getKmPostExistsAtKmNumber(
        publishType: PublishType,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        kmNumber: KmNumber,
    ): Boolean = list(publishType, trackNumberId).find { it.kmNumber == kmNumber } != null

    fun listNearbyOnTrackPaged(
        publishType: PublishType,
        location: Point,
        trackNumberId: DomainId<TrackLayoutTrackNumber>,
        offset: Int,
        limit: Int?,
    ): List<TrackLayoutKmPost> {
        logger.serviceCall(
            "getNearbyKmPostsOnTrackPaged",
            "publishType" to publishType,
            "location" to location,
            "trackNumberId" to trackNumberId,
            "offset" to offset,
            "limit" to limit
        )
        val kmPosts = list(publishType, trackNumberId as IntId<TrackLayoutTrackNumber>)
            .map { associateByDistance(it, location) { item -> item.location } }

        // Returns kmPosts with null location first, after that it compares by distance to the point given in
        // the location parameter. Shortest distance first.
        return pageToList(kmPosts, offset, limit, ::compareByDistanceNullsFirst)
            .map { (kmPost, _) -> kmPost }
    }

    fun deleteDraft(kmPostId: IntId<TrackLayoutKmPost>): IntId<TrackLayoutKmPost> {
        logger.serviceCall("deleteDraftKmPost", "kmPostId" to kmPostId)
        return dao.deleteUnpublishedDraft(kmPostId).id
    }

    fun officialKmPostExists(
        kmPostId: IntId<TrackLayoutKmPost>,
    ): Boolean {
        return dao.officialKmPostExists(kmPostId)
    }

}
