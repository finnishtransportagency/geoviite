package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.error.DeletingFailureException
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.logging.serviceCall
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LayoutTrackNumberService(
    dao: LayoutTrackNumberDao,
    private val referenceLineService: ReferenceLineService
) : DraftableObjectService<TrackLayoutTrackNumber, LayoutTrackNumberDao>(dao) {

    @Transactional
    fun insert(saveRequest: TrackNumberSaveRequest): RowVersion<TrackLayoutTrackNumber> {
        logger.serviceCall("insert", "trackNumber" to saveRequest.number)
        val trackNumberVersion = saveDraftInternal(
            TrackLayoutTrackNumber(
                number = saveRequest.number,
                description = saveRequest.description,
                state = saveRequest.state,
                externalId = null,
            )
        )
        referenceLineService.addTrackNumberReferenceLine(trackNumberVersion.id, saveRequest.startAddress)
        return trackNumberVersion
    }

    @Transactional
    fun update(
        id: IntId<TrackLayoutTrackNumber>,
        saveRequest: TrackNumberSaveRequest,
    ): RowVersion<TrackLayoutTrackNumber> {
        logger.serviceCall("update", "trackNumber" to saveRequest.number)
        val original = getInternalOrThrow(DRAFT, id)
        val trackNumberVersion = saveDraftInternal(
            original.copy(
                number = saveRequest.number,
                description = saveRequest.description,
                state = saveRequest.state,
            )
        )
        referenceLineService.updateTrackNumberReferenceLine(id, saveRequest.startAddress)
        return trackNumberVersion
    }


    @Transactional
    fun updateExternalId(
        id: IntId<TrackLayoutTrackNumber>,
        oid: Oid<TrackLayoutTrackNumber>,
    ): RowVersion<TrackLayoutTrackNumber> {
        logger.serviceCall("updateExternalIdForTrackNumber", "id" to id, "oid" to oid)

        val original = getDraft(id)
        val trackLayoutTrackNumber = original.copy(externalId = oid)

        return saveDraftInternal(trackLayoutTrackNumber)
    }

    @Transactional
    fun deleteDraftOnlyTrackNumberAndReferenceLine(
        id: IntId<TrackLayoutTrackNumber>,
    ): IntId<TrackLayoutTrackNumber> {
        val trackNumber = getDraft(id)
        val referenceLine = referenceLineService.getByTrackNumber(DRAFT, id)
            ?: throw IllegalStateException("Found Track Number without Reference Line $id")

        referenceLineService.deleteDraftOnlyReferenceLine(referenceLine)
        deleteDraftOnlyTrackNumber(trackNumber)
        return id
    }

    private fun deleteDraftOnlyTrackNumber(trackNumber: TrackLayoutTrackNumber) {
        if (trackNumber.getDraftType() != DraftType.NEW_DRAFT)
            throw DeletingFailureException("Trying to delete non-draft Track Number")
        require(trackNumber.id is IntId) { "Trying to delete or reset track number not yet saved to database" }
        dao.deleteDrafts(trackNumber.draft!!.draftRowId as IntId<TrackLayoutTrackNumber>)
    }

    fun mapById(publishType: PublishType) = list(publishType).associateBy { tn -> tn.id as IntId }

    fun mapByNumber(publishType: PublishType) = list(publishType).associateBy(TrackLayoutTrackNumber::number)

    override fun createDraft(item: TrackLayoutTrackNumber) = draft(item)

    override fun createPublished(item: TrackLayoutTrackNumber) = published(item)

    fun find(trackNumber: TrackNumber, publishType: PublishType): List<TrackLayoutTrackNumber> {
        logger.serviceCall("find", "trackNumber" to trackNumber, "publishType" to publishType)
        return dao.fetchVersions(publishType, false, trackNumber).map(dao::fetch)
    }
}
