package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.publication.ValidationVersion
import fi.fta.geoviite.infra.util.FreeText
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

abstract class DraftableObjectService<ObjectType : Draftable<ObjectType>, DaoType : IDraftableObjectDao<ObjectType>>(
    protected open val dao: DaoType,
) {

    protected open val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun list(publishType: PublishType, includeDeleted: Boolean = false): List<ObjectType> {
        logger.serviceCall("list", "publishType" to publishType)
        return dao.list(publishType, includeDeleted)
    }

    fun list(
        publishType: PublishType,
        searchTerm: FreeText,
        limit: Int?,
    ): List<ObjectType> {
        logger.serviceCall(
            "list", "publishType" to publishType, "searchTerm" to searchTerm, "limit" to limit
        )

        return searchTerm.toString().trim().takeIf(String::isNotEmpty)
            ?.let { term -> dao.list(publishType, true)
                .filter { item ->
                    idMatches(term, item) ||
                    contentMatches(term, item)
                }
                .let { list -> sortSearchResult(list)}
                .let { list -> if (limit != null) list.take(limit) else list }
            } ?: listOf()
    }

    fun get(publishType: PublishType, id: IntId<ObjectType>): ObjectType? {
        logger.serviceCall("get", "publishType" to publishType, "id" to id)
        return dao.get(publishType, id)
    }

    fun getMany(publishType: PublishType, ids: List<IntId<ObjectType>>): List<ObjectType> {
        logger.serviceCall("getMany", "publishType" to publishType, "ids" to ids)
        return dao.getMany(publishType, ids)
    }

    fun getOfficialAtMoment(id: IntId<ObjectType>, moment: Instant): ObjectType? {
        logger.serviceCall("get", "id" to id, "moment" to moment)
        return dao.getOfficialAtMoment(id, moment)
    }

    fun getOrThrow(publishType: PublishType, id: IntId<ObjectType>): ObjectType {
        logger.serviceCall("get", "publishType" to publishType, "id" to id)
        return dao.getOrThrow(publishType, id)
    }

    fun getChangeTime(): Instant {
        logger.serviceCall("getChangeTime")
        return dao.fetchChangeTime()
    }

    fun getDraftableChangeInfo(id: IntId<ObjectType>): DraftableChangeInfo {
        logger.serviceCall("getChangeTimes", "id" to id)
        return dao.fetchDraftableChangeInfo(id)
    }

    fun draftExists(id: IntId<ObjectType>): Boolean {
        logger.serviceCall("draftExists", "id" to id)
        return dao.draftExists(id)
    }

    fun officialExists(id: IntId<ObjectType>): Boolean {
        logger.serviceCall("officialExists", "id" to id)
        return dao.officialExists(id)
    }

    protected abstract fun createDraft(item: ObjectType): ObjectType

    protected abstract fun createPublished(item: ObjectType): ObjectType

    protected open fun sortSearchResult(list: List<ObjectType>): List<ObjectType> = list

    protected open fun idMatches(term: String, item: ObjectType): Boolean = false

    protected open fun contentMatches(term: String, item: ObjectType): Boolean = false

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
            val previousVersion = requireNotNull(draft.version) { "Updating item without rowVersion: $item" }
            dao.update(draft).also { response -> verifyUpdateResponse(officialId, previousVersion, response) }
        }
    }

    @Transactional
    open fun deleteDraft(id: IntId<ObjectType>): DaoResponse<ObjectType> {
        logger.serviceCall("deleteDraft")
        return dao.deleteDraft(id)
    }

    @Transactional
    open fun publish(version: ValidationVersion<ObjectType>): DaoResponse<ObjectType> {
        logger.serviceCall("Publish", "version" to version)
        return publishInternal(VersionPair(dao.fetchOfficialVersion(version.officialId), version.validatedAssetVersion))
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
        if (officialId != null) require(response.id == officialId) {
            "Insert response ID doesn't match object: officialId=$officialId updated=$response"
        } else require(response.id == response.rowVersion.id) {
            "Inserted new object refers to another official row: inserted=$response"
        }
        require(response.rowVersion.version == 1) {
            "Inserted new row has a version over 1: inserted=$response"
        }
    }

    private fun verifyUpdateResponse(
        id: IntId<ObjectType>,
        previousVersion: RowVersion<ObjectType>,
        response: DaoResponse<ObjectType>,
    ) {
        require(response.id == id) {
            "Update response ID doesn't match object: id=$id updated=$response"
        }
        require(response.rowVersion.id == previousVersion.id) {
            "Updated the wrong row (draft vs official): id=$id previous=$previousVersion updated=$response"
        }
        if (response.rowVersion.version != previousVersion.version + 1) {
            // We could do optimistic locking here by throwing
            logger.warn(
                "Updated version isn't the next one: a concurrent change may have been overwritten: "
                        + "id=$id previous=$previousVersion updated=$response"
            )
        }
    }
}
