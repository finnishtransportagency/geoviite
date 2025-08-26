package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.integration.CalculatedChanges
import fi.fta.geoviite.infra.publication.PublicationCause
import fi.fta.geoviite.infra.publication.PublicationInDesign
import fi.fta.geoviite.infra.publication.PublicationMessage
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.publication.ValidateContext
import fi.fta.geoviite.infra.publication.ValidateTransition
import fi.fta.geoviite.infra.publication.ValidationVersions
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional

@GeoviiteService
class LayoutDesignService(
    private val dao: LayoutDesignDao,
    private val publicationService: PublicationService,
    private val trackNumberService: LayoutTrackNumberService,
    private val referenceLineService: ReferenceLineService,
    private val locationTrackService: LocationTrackService,
    private val switchService: LayoutSwitchService,
    private val kmPostService: LayoutKmPostService,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val referenceLineDao: ReferenceLineDao,
    private val locationTrackDao: LocationTrackDao,
    private val switchDao: LayoutSwitchDao,
    private val kmPostDao: LayoutKmPostDao,
) {
    fun list(includeCompleted: Boolean, includeDeleted: Boolean): List<LayoutDesign> {
        return dao.list(includeCompleted = includeCompleted, includeDeleted = includeDeleted)
    }

    fun getOrThrow(id: IntId<LayoutDesign>): LayoutDesign {
        return dao.fetch(id)
    }

    @Transactional
    fun update(id: IntId<LayoutDesign>, request: LayoutDesignSaveRequest): IntId<LayoutDesign> =
        try {
            val designBranch = DesignBranch.of(id)
            if (request.designState == DesignState.DELETED) {
                deleteDraftsInDesign(designBranch)
            }
            if (dao.designHasPublications(id)) {
                if (request.designState == DesignState.DELETED) {
                    makeEmptyPublication(designBranch, PublicationCause.LAYOUT_DESIGN_DELETE)
                    cancelUnpublishedObjectsInDesign(designBranch)
                } else {
                    makeEmptyPublication(designBranch, PublicationCause.LAYOUT_DESIGN_CHANGE)
                }
            }
            dao.update(id, request)
        } catch (e: DataIntegrityViolationException) {
            throw asDuplicateNameException(e) ?: e
        }

    @Transactional
    fun insert(request: LayoutDesignSaveRequest): IntId<LayoutDesign> =
        try {
            dao.insert(request)
        } catch (e: DataIntegrityViolationException) {
            throw asDuplicateNameException(e) ?: e
        }

    private fun deleteDraftsInDesign(branch: DesignBranch) {
        kmPostDao.deleteDrafts(branch)
        switchDao.deleteDrafts(branch)
        locationTrackDao.deleteDrafts(branch)
        referenceLineDao.deleteDrafts(branch)
        trackNumberDao.deleteDrafts(branch)
    }

    private fun cancelUnpublishedObjectsInDesign(branch: DesignBranch) {
        val trackNumberExtIds = trackNumberDao.fetchExternalIds(branch)
        val trackNumbers =
            trackNumberService.list(branch.official, true).mapNotNull { trackNumber ->
                if (trackNumber.layoutContext.branch == branch || trackNumberExtIds.containsKey(trackNumber.id)) {
                    trackNumberService.cancel(branch, trackNumber.id as IntId)
                } else null
            }
        val referenceLines =
            referenceLineService.list(branch.official, true).mapNotNull { referenceLine ->
                if (referenceLine.layoutContext.branch == branch) {
                    referenceLineService.cancel(branch, referenceLine.id as IntId)
                } else null
            }
        val locationTrackExtIds = locationTrackDao.fetchExternalIds(branch)
        val locationTracks =
            locationTrackService.list(branch.official, true).mapNotNull { locationTrack ->
                if (locationTrack.layoutContext.branch == branch || locationTrackExtIds.containsKey(locationTrack.id)) {
                    locationTrackService.cancel(branch, locationTrack.id as IntId)
                } else null
            }
        val switchExtIds = switchDao.fetchExternalIds(branch)
        val switches =
            switchService.list(branch.official, true).mapNotNull { switch ->
                if (switch.layoutContext.branch == branch || switchExtIds.containsKey(switch.id)) {
                    switchService.cancel(branch, switch.id as IntId)
                } else null
            }
        val kmPosts =
            kmPostService.list(branch.official, true).mapNotNull { kmPost ->
                if (kmPost.layoutContext.branch == branch) {
                    kmPostService.cancel(branch, kmPost.id as IntId)
                } else null
            }

        val cancellationVersions =
            ValidationVersions(
                ValidateTransition(PublicationInDesign(branch)),
                trackNumbers,
                locationTracks,
                referenceLines,
                switches,
                kmPosts,
                listOf(),
            )

        publicationService.publishChanges(
            branch,
            cancellationVersions,
            publicationService.getCalculatedChanges(cancellationVersions),
            PublicationMessage.of(""),
            PublicationCause.LAYOUT_DESIGN_CANCELLATION,
        )
    }

    private fun makeEmptyPublication(branchBranch: DesignBranch, cause: PublicationCause) =
        publicationService.publishChanges(
            branchBranch,
            ValidationVersions.emptyWithTarget(ValidateContext(branchBranch.official)),
            CalculatedChanges.empty(),
            PublicationMessage.of(""),
            cause,
        )
}
