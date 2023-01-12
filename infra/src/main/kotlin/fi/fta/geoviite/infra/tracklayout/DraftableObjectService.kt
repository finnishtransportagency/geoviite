package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.linking.PublicationVersion
import fi.fta.geoviite.infra.logging.serviceCall
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

abstract class DraftableObjectService<ObjectType: Draftable<ObjectType>, DaoType: IDraftableObjectDao<ObjectType>>(
    protected open val dao: DaoType,
) {

    protected open val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun listOfficial(): List<ObjectType> = list(OFFICIAL)

    fun listDraft(): List<ObjectType> = list(DRAFT)

    fun list(publishType: PublishType): List<ObjectType> {
        logger.serviceCall("list", "publishType" to publishType)
        return listInternal(publishType, false)
    }

    fun getOfficial(id: IntId<ObjectType>) = get(OFFICIAL, id)

    fun getDraft(id: IntId<ObjectType>) = getOrThrow(DRAFT, id)


    fun get(publishType: PublishType, id: IntId<ObjectType>): ObjectType? {
        logger.serviceCall("get", "publishType" to publishType, "id" to id)
        return getInternal(publishType, id)
    }

    fun get(rowVersion: RowVersion<ObjectType>): ObjectType {
        logger.serviceCall("get", "rowVersion" to rowVersion)
        return dao.fetch(rowVersion)
    }

    fun getOfficialAtMoment(id: IntId<ObjectType>, moment: Instant): ObjectType? {
        logger.serviceCall("get", "id" to id, "moment" to moment)
        return dao.fetchOfficialVersionAtMoment(id, moment)?.let(dao::fetch)
    }

    fun getOrThrow(publishType: PublishType, id: IntId<ObjectType>): ObjectType {
        logger.serviceCall("get", "publishType" to publishType, "id" to id)
        return getInternalOrThrow(publishType, id)
    }

    fun getChangeTime(): Instant {
        logger.serviceCall("getChangeTime")
        return dao.fetchChangeTime()
    }

    fun getChangeTimes(id: IntId<ObjectType>): ChangeTimes {
        logger.serviceCall("getChangeTimes", "id" to id)
        return dao.fetchChangeTimes(id)
    }

    protected fun listInternal(publishType: PublishType, includeDeleted: Boolean) =
        dao.fetchVersions(publishType, includeDeleted).map(dao::fetch)

    protected fun getInternal(publishType: PublishType, id: IntId<ObjectType>): ObjectType? = when (publishType) {
        DRAFT -> dao.fetchDraftVersion(id)?.let(dao::fetch)
        OFFICIAL -> dao.fetchOfficialVersion(id)?.let(dao::fetch)
    }

    protected fun getDraftInternal(id: IntId<ObjectType>): ObjectType = getInternalOrThrow(DRAFT, id)

    protected fun getInternalOrThrow(publishType: PublishType, id: IntId<ObjectType>): ObjectType = when (publishType) {
        DRAFT -> dao.fetch(dao.fetchDraftVersionOrThrow(id))
        OFFICIAL -> dao.fetch(dao.fetchOfficialVersionOrThrow(id))
    }

    protected abstract fun createDraft(item: ObjectType): ObjectType

    protected abstract fun createPublished(item: ObjectType): ObjectType

    @Transactional
    open fun saveDraft(draft: ObjectType): DaoResponse<ObjectType> {
        logger.serviceCall("saveDraft", "id" to draft.id)
        return saveDraftInternal(draft)
    }

    protected fun saveDraftInternal(item: ObjectType): DaoResponse<ObjectType> {
        val draft = createDraft(item)
        require(draft.draft != null) { "Item is not a draft: id=${draft.id}" }
        val officialId = if (item.id is IntId) item.id as IntId else null
        return if (draft.dataType == DataType.TEMP) {
            dao.insert(draft).also { response -> verifyInsertResponse(officialId, response) }
        } else {
            requireNotNull(officialId) { "Updating item that has no known official ID" }
            val previousVersion = requireNotNull(draft.rowVersion) { "Updating item without rowVersion: $item" }
            dao.update(draft).also { response -> verifyUpdateResponse(officialId, previousVersion, response) }
        }
    }

    @Deprecated("Should only be used for cleaning up before/after tests")
    @Transactional
    open fun deleteAllDrafts(): List<DaoResponse<ObjectType>> {
        logger.serviceCall("deleteDrafts")
        return dao.deleteDrafts()
    }

    @Transactional
    open fun deleteDraft(id: IntId<ObjectType>): DaoResponse<ObjectType> {
        logger.serviceCall("deleteDraft")
        return dao.deleteDraft(id)
    }

    @Transactional
    open fun deleteUnpublishedDraft(id: IntId<ObjectType>): DaoResponse<ObjectType> {
        logger.serviceCall("deleteUnpublishedDraft", "id" to id)
        return dao.deleteUnpublishedDraft(id)
    }

    @Transactional
    open fun publish(version: PublicationVersion<ObjectType>): DaoResponse<ObjectType> {
        logger.serviceCall("Publish", "version" to version)
        return publishInternal(VersionPair(dao.fetchOfficialVersion(version.officialId), version.draftVersion))
    }

    protected fun publishInternal(versions: VersionPair<ObjectType>): DaoResponse<ObjectType> {
        val draft = versions.draft?.let(dao::fetch)

        if (draft?.draft == null) throw IllegalStateException("Object to publish is not a draft $versions $draft")
        val published = createPublished(draft)
        if (published.draft != null) throw IllegalStateException("Published object is still a draft")
        val publishResponse = dao.update(published)
        verifyUpdateResponse(draft.id as IntId, versions.official ?: versions.draft, publishResponse)
        if (versions.official != null && versions.draft.id != versions.official.id) {
            // Draft data is saved on official id -> delete the draft row
            dao.deleteDraft(versions.draft.id)
        }
        return publishResponse
    }

    private fun verifyInsertResponse(officialId: IntId<ObjectType>?, response: DaoResponse<ObjectType>) {
        if (officialId != null) require (response.id == officialId) {
            "Insert response ID doesn't match object: officialId=$officialId updated=$response"
        } else require(response.id == response.rowVersion.id) {
            "Inserted new object refers to another official row: inserted=$response"
        }
        require (response.rowVersion.version == 1) {
            "Inserted new row has a version over 1: inserted=$response"
        }
    }

    private fun verifyUpdateResponse(
        id: IntId<ObjectType>,
        previousVersion: RowVersion<ObjectType>,
        response: DaoResponse<ObjectType>,
    ) {
        require (response.id == id) {
            "Update response ID doesn't match object: id=$id updated=$response"
        }
        require (response.rowVersion.id == previousVersion.id) {
            "Updated the wrong row (draft vs official): id=$id previous=$previousVersion updated=$response"
        }
        if (response.rowVersion.version != previousVersion.version+1) {
            // We could do optimistic locking here by throwing
            logger.warn("Updated version isn't the next one: a concurrent change may have been overwritten: " +
                    "id=$id previous=$previousVersion updated=$response")
        }
    }

}
