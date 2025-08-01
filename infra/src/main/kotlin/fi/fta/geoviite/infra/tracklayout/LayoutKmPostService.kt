package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.linking.LayoutKmPostSaveRequest
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.util.pageToList
import org.springframework.transaction.annotation.Transactional

@GeoviiteService
class LayoutKmPostService(
    dao: LayoutKmPostDao,
    private val referenceLineService: ReferenceLineService,
    private val geometryDao: GeometryDao,
) : LayoutAssetService<LayoutKmPost, NoParams, LayoutKmPostDao>(dao) {

    @Transactional
    fun insertKmPost(branch: LayoutBranch, request: LayoutKmPostSaveRequest): IntId<LayoutKmPost> {
        val kmPost =
            LayoutKmPost(
                kmNumber = request.kmNumber,
                state = request.state,
                trackNumberId = request.trackNumberId,
                sourceId = null,
                gkLocation = request.gkLocation,
                contextData = LayoutContextData.newDraft(branch, dao.createId()),
            )
        return saveDraft(branch, kmPost).id
    }

    @Transactional
    fun updateKmPost(
        branch: LayoutBranch,
        id: IntId<LayoutKmPost>,
        request: LayoutKmPostSaveRequest,
    ): IntId<LayoutKmPost> {
        val kmPost =
            dao.getOrThrow(branch.draft, id)
                .copy(
                    kmNumber = request.kmNumber,
                    state = request.state,
                    gkLocation = request.gkLocation,
                    sourceId = request.sourceId,
                )
        return saveDraft(branch, kmPost).id
    }

    fun list(layoutContext: LayoutContext, filter: ((kmPost: LayoutKmPost) -> Boolean)?): List<LayoutKmPost> {
        val all = dao.list(layoutContext, false)
        return filter?.let(all::filter) ?: all
    }

    fun list(layoutContext: LayoutContext, trackNumberId: IntId<LayoutTrackNumber>): List<LayoutKmPost> {
        return dao.list(layoutContext, false, trackNumberId)
    }

    fun list(layoutContext: LayoutContext, boundingBox: BoundingBox, step: Int): List<LayoutKmPost> {
        return dao.list(layoutContext, false, bbox = boundingBox).filter { p ->
            (step <= 1 || (p.kmNumber.isPrimary() && p.kmNumber.number % step == 0))
        }
    }

    fun getByKmNumber(
        layoutContext: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>,
        kmNumber: KmNumber,
        includeDeleted: Boolean,
    ): LayoutKmPost? {
        return dao.fetchVersion(layoutContext, trackNumberId, kmNumber, includeDeleted)?.let(dao::fetch)
    }

    fun listNearbyOnTrackPaged(
        layoutContext: LayoutContext,
        location: Point,
        trackNumberId: IntId<LayoutTrackNumber>?,
        offset: Int,
        limit: Int?,
    ): List<LayoutKmPost> {
        val allPosts = dao.list(layoutContext, false, trackNumberId)
        val postsByDistance = allPosts.map { post -> associateByDistance(post, location) }
        return pageToList(postsByDistance, offset, limit, ::compareByDistanceNullsFirst).map { (kmPost, _) -> kmPost }
    }

    @Transactional(readOnly = true)
    fun getKmPostInfoboxExtras(layoutContext: LayoutContext, id: IntId<LayoutKmPost>): KmPostInfoboxExtras {
        val kmPost = dao.get(layoutContext, id)
        val length = getSingleKmPostLength(layoutContext, id)
        val geometryPlanId = if (kmPost?.sourceId is IntId) geometryDao.getPlanIdForKmPost(kmPost.sourceId) else null

        return KmPostInfoboxExtras(length, geometryPlanId)
    }

    fun getSingleKmPostLength(layoutContext: LayoutContext, id: IntId<LayoutKmPost>): Double? {
        return dao.get(layoutContext, id)?.getAsIntegral()?.let { kmPost ->
            referenceLineService.getByTrackNumberWithAlignment(layoutContext, kmPost.trackNumberId)?.let {
                (_, alignment) ->
                val kmPostM = alignment.getClosestPointM(kmPost.location)?.first
                val kmEndM = getKmEndM(layoutContext, kmPost.trackNumberId, kmPost.kmNumber, alignment)
                if (kmPostM == null || kmEndM == null) null else (kmEndM - kmPostM).distance
            }
        }
    }

    private fun getKmEndM(
        layoutContext: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>,
        kmNumber: KmNumber,
        referenceLineAlignment: LayoutAlignment,
    ): LineM<ReferenceLineM>? {
        val nextKmPost =
            dao.fetchNextWithLocationAfter(layoutContext, trackNumberId, kmNumber, LayoutState.IN_USE)
                ?.let(dao::fetch)
                ?.getAsIntegral()
        return if (nextKmPost == null) {
            referenceLineAlignment.length
        } else {
            referenceLineAlignment.getClosestPointM(nextKmPost.location)?.first
        }
    }

    @Transactional
    fun saveDraft(branch: LayoutBranch, draftAsset: LayoutKmPost): LayoutRowVersion<LayoutKmPost> =
        saveDraftInternal(branch, draftAsset, NoParams.instance)
}

fun associateByDistance(kmPost: LayoutKmPost, comparisonPoint: Point): Pair<LayoutKmPost, Double?> =
    kmPost to kmPost.layoutLocation?.let { l -> calculateDistance(LAYOUT_SRID, comparisonPoint, l) }
