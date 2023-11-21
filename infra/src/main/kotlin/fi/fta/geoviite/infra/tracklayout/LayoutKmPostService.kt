package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.PublishType.DRAFT
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
    private val referenceLineDao: ReferenceLineDao,
    private val alignmentDao: LayoutAlignmentDao,
) : DraftableObjectService<TrackLayoutKmPost, LayoutKmPostDao>(dao) {

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
        val trackLayoutKmPost = dao.getOrThrow(DRAFT, id).copy(
            kmNumber = kmPost.kmNumber,
            state = kmPost.state,
        )
        return saveDraftInternal(trackLayoutKmPost).id
    }

    override fun createDraft(item: TrackLayoutKmPost) = draft(item)

    override fun createPublished(item: TrackLayoutKmPost) = published(item)

    fun list(
        publicationState: PublishType,
        filter: ((kmPost: TrackLayoutKmPost) -> Boolean)?,
    ): List<TrackLayoutKmPost> {
        logger.serviceCall("list", "publicationState" to publicationState, "filter" to (filter != null))
        val all = dao.list(publicationState, false)
        return filter?.let(all::filter) ?: all
    }

    fun list(publicationState: PublishType, trackNumberId: IntId<TrackLayoutTrackNumber>): List<TrackLayoutKmPost> {
        logger.serviceCall(
            "list", "publicationState" to publicationState, "trackNumberId" to trackNumberId
        )
        return dao.list(publicationState, false, trackNumberId)
    }

    fun list(publicationState: PublishType, boundingBox: BoundingBox, step: Int): List<TrackLayoutKmPost> {
        logger.serviceCall(
            "getKmPosts", "publicationState" to publicationState, "boundingBox" to boundingBox, "step" to step
        )
        return dao
            .list(publicationState, false, bbox = boundingBox)
            .filter { p -> (step <= 1 || (p.kmNumber.isPrimary() && p.kmNumber.number % step == 0)) }
    }

    fun getByKmNumber(
        publishType: PublishType,
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
        return dao.fetchVersion(publishType, trackNumberId, kmNumber, includeDeleted)?.let(dao::fetch)
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
        val allPosts = dao.list(publicationState, false, trackNumberId)
        val postsByDistance = allPosts.map { post -> associateByDistance(post, location) { item -> item.location } }
        return pageToList(postsByDistance, offset, limit, ::compareByDistanceNullsFirst).map { (kmPost, _) -> kmPost }
    }

    @Transactional(readOnly = true)
    fun getSingleKmPostLength(
        publishType: PublishType,
        id: IntId<TrackLayoutKmPost>,
    ): Double? = dao.fetchVersionOrThrow(id, publishType).let(dao::fetch).getAsIntegral()?.let { kmPost ->
        getReferenceLineAlignment(publishType, kmPost.trackNumberId)?.let { referenceLineAlignment ->
            val kmPostM = referenceLineAlignment.getClosestPointM(kmPost.location)?.first
            val kmEndM = getKmEndM(publishType, kmPost.trackNumberId, kmPost.kmNumber, referenceLineAlignment)
            if (kmPostM == null || kmEndM == null) null else kmEndM - kmPostM
        }
    }

    private fun getKmEndM(
        publishType: PublishType,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        kmNumber: KmNumber,
        referenceLineAlignment: LayoutAlignment,
    ): Double? {
        val nextKmPost = dao
            .fetchNextWithLocationAfter(trackNumberId, kmNumber, publishType, LayoutState.IN_USE)
            ?.let(dao::fetch)
            ?.getAsIntegral()
        return if (nextKmPost == null) referenceLineAlignment.length
        else referenceLineAlignment.getClosestPointM(nextKmPost.location)?.first
    }

    private fun getReferenceLineAlignment(publishType: PublishType, trackNumberId: IntId<TrackLayoutTrackNumber>) =
        referenceLineDao.fetchVersion(publishType, trackNumberId)
            ?.let(referenceLineDao::fetch)
            ?.let(ReferenceLine::alignmentVersion)
            ?.let(alignmentDao::fetch)
}
