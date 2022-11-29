package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.error.DeletingFailureException
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.util.RowVersion
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LayoutTrackNumberService(
    dao: LayoutTrackNumberDao,
    private val referenceLineService: ReferenceLineService
) : DraftableObjectService<LayoutTrackNumber, LayoutTrackNumberDao>(dao) {

    @Transactional
    fun insert(saveRequest: TrackNumberSaveRequest): IntId<LayoutTrackNumber> {
        logger.serviceCall("insert", "trackNumber" to saveRequest.number)
        val trackNumberId = saveDraftInternal(
            LayoutTrackNumber(
                number = saveRequest.number,
                description = saveRequest.description,
                state = saveRequest.state,
                externalId = null,
            )
        ).id
        referenceLineService.addTrackNumberReferenceLine(trackNumberId, saveRequest.startAddress)
        return trackNumberId
    }

    @Transactional
    fun update(id: IntId<LayoutTrackNumber>, saveRequest: TrackNumberSaveRequest): IntId<LayoutTrackNumber> {
        logger.serviceCall("update", "trackNumber" to saveRequest.number)
        val original = getInternalOrThrow(DRAFT, id)
        val trackNumberId = saveDraftInternal(
            original.copy(
                number = saveRequest.number,
                description = saveRequest.description,
                state = saveRequest.state,
            )
        ).id
        referenceLineService.updateTrackNumberReferenceLine(id, saveRequest.startAddress)
        return trackNumberId
    }


    @Transactional
    fun updateExternalId(
        id: IntId<LayoutTrackNumber>,
        oid: Oid<LayoutTrackNumber>
    ): RowVersion<LayoutTrackNumber> {
        logger.serviceCall("updateExternalIdForTrackNumber", "id" to id, "oid" to oid)

        val original = getDraft(id)
        val trackLayoutTrackNumber = original.copy(externalId = oid)

        return saveDraftInternal(trackLayoutTrackNumber)
    }

    @Transactional
    fun deleteDraftOnlyTrackNumberAndReferenceLine(id: IntId<LayoutTrackNumber>):
            IntId<LayoutTrackNumber> {
        val trackNumber = getDraft(id)
        val referenceLine = referenceLineService.getByTrackNumber(DRAFT, id)
            ?: throw IllegalStateException("Found Track Number without Reference Line $id")

        referenceLineService.deleteDraftOnlyReferenceLine(referenceLine)
        deleteDraftOnlyTrackNumber(trackNumber)
        return id
    }

    private fun deleteDraftOnlyTrackNumber(trackNumber: LayoutTrackNumber) {
        if (trackNumber.getDraftType() != DraftType.NEW_DRAFT)
            throw DeletingFailureException("Trying to delete non-draft Track Number")
        require(trackNumber.id is IntId) { "Trying to delete or reset track number not yet saved to database" }
        dao.deleteDrafts(trackNumber.draft!!.draftRowId as IntId<LayoutTrackNumber>)
    }

    fun mapById(publishType: PublishType) = list(publishType).associateBy { tn -> tn.id as IntId }

    fun mapByNumber(publishType: PublishType) = list(publishType).associateBy(LayoutTrackNumber::number)

    override fun createDraft(item: LayoutTrackNumber) = draft(item)

    override fun createPublished(item: LayoutTrackNumber) = published(item)

    fun find(trackNumber: TrackNumber, publishType: PublishType): List<LayoutTrackNumber> {
        logger.serviceCall("find", "trackNumber" to trackNumber, "publishType" to publishType)
        return dao.findVersions(trackNumber, publishType).map(dao::fetch)
    }
}
