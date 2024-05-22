package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.publication.ValidationVersion
import fi.fta.geoviite.infra.util.FreeText
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

abstract class LayoutAssetService<ObjectType : LayoutAsset<ObjectType>, DaoType : ILayoutAssetDao<ObjectType>>(
    protected open val dao: DaoType,
) {

    protected open val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun list(context: LayoutContext, includeDeleted: Boolean = false): List<ObjectType> {
        logger.serviceCall("list", "context" to context, "includeDeleted" to includeDeleted)
        return dao.list(context, includeDeleted)
    }

    fun get(context: LayoutContext, id: IntId<ObjectType>): ObjectType? {
        logger.serviceCall("get", "context" to context, "id" to id)
        return dao.get(context, id)
    }

    fun getMany(context: LayoutContext, ids: List<IntId<ObjectType>>): List<ObjectType> {
        logger.serviceCall("getMany", "context" to context, "ids" to ids)
        return dao.getMany(context, ids)
    }

    fun getOfficialAtMoment(id: IntId<ObjectType>, moment: Instant): ObjectType? {
        logger.serviceCall("get", "id" to id, "moment" to moment)
        return dao.getOfficialAtMoment(id, moment)
    }

    fun getOrThrow(context: LayoutContext, id: IntId<ObjectType>): ObjectType {
        logger.serviceCall("get", "context" to context, "id" to id)
        return dao.getOrThrow(context, id)
    }

    fun getChangeTime(): Instant {
        logger.serviceCall("getChangeTime")
        return dao.fetchChangeTime()
    }

    fun getLayoutAssetChangeInfo(context: LayoutContext, id: IntId<ObjectType>): LayoutAssetChangeInfo? {
        logger.serviceCall("getLayoutAssetChangeInfo", "context" to context, "id" to id)
        return dao.fetchLayoutAssetChangeInfo(context, id)
    }

    fun filterBySearchTerm(list: List<ObjectType>, searchTerm: FreeText): List<ObjectType> =
        searchTerm.toString().trim().takeIf(String::isNotEmpty)?.let { term ->
            list.filter { item -> idMatches(term, item) || contentMatches(term, item) }
        } ?: listOf()

    protected open fun idMatches(term: String, item: ObjectType): Boolean = false

    protected open fun contentMatches(term: String, item: ObjectType): Boolean = false

    @Transactional
    open fun saveDraft(branch: LayoutBranch, draftAsset: ObjectType): DaoResponse<ObjectType> {
        logger.serviceCall("saveDraft", "branch" to branch, "draftAsset" to draftAsset)
        return saveDraftInternal(branch, draftAsset)
    }

    protected fun saveDraftInternal(branch: LayoutBranch, draftAsset: ObjectType): DaoResponse<ObjectType> {
        val draft = asDraft(branch, draftAsset)
        require(draft.isDraft) { "Item is not a draft: id=${draft.id}" }
        val officialId = if (draftAsset.id is IntId) draftAsset.id as IntId else null
        return if (draft.dataType == DataType.TEMP) {
            verifyObjectIsNew(draft)
            dao.insert(draft).also { response -> verifyInsertResponse(officialId, response) }
        } else {
            requireNotNull(officialId) { "Updating item that has no known official ID" }
            verifyObjectIsExisting(draft)
            val previousVersion = requireNotNull(draft.version) { "Updating item without rowVersion: $draftAsset" }
            dao.update(draft).also { response -> verifyUpdateResponse(officialId, previousVersion, response) }
        }
    }

    @Transactional
    open fun deleteDraft(branch: LayoutBranch, id: IntId<ObjectType>): DaoResponse<ObjectType> {
        logger.serviceCall("deleteDraft", "branch" to branch, "id" to id)
        return dao.deleteDraft(branch, id)
    }

    @Transactional
    open fun publish(branch: LayoutBranch, version: ValidationVersion<ObjectType>): DaoResponse<ObjectType> {
        logger.serviceCall("Publish", "branch" to branch, "version" to version)
        val versions = VersionPair(dao.fetchOfficialVersion(branch, version.officialId), version.validatedAssetVersion)
        return publishInternal(branch, versions)
    }

    protected fun publishInternal(branch: LayoutBranch, versions: VersionPair<ObjectType>): DaoResponse<ObjectType> {
        val draftVersion = requireNotNull(versions.draft) { "No draft to publish: versions=$versions" }
        val draft = dao.fetch(draftVersion)
        require(draft.isDraft) { "Object to publish is not a draft: versions=$versions context=${draft.contextData}" }
        val published = asOfficial(branch, draft)
        require(!published.isDraft) { "Published object is still a draft: context=${published.contextData}" }
        verifyObjectIsExisting(published)

        val publishResponse = dao.update(published)
        verifyUpdateResponse(draft.id as IntId, versions.official ?: draftVersion, publishResponse)
        if (versions.official != null && versions.draft.id != versions.official.id) {
            // Draft data is saved on official id -> delete the draft row
            dao.deleteDraft(branch, versions.draft.id)
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
                "Updated version isn't the next one: a concurrent change may have been overwritten: " +
                        "id=$id previous=$previousVersion updated=$response"
            )
        }
    }
}
