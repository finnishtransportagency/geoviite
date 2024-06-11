package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
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

    fun getOfficialAtMoment(branch: LayoutBranch, id: IntId<ObjectType>, moment: Instant): ObjectType? {
        logger.serviceCall("get", "branch" to branch, "id" to id, "moment" to moment)
        return dao.getOfficialAtMoment(branch, id, moment)
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
            dao.update(draft).also { response -> verifyDraftUpdateResponse(officialId, previousVersion, response) }
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
        return publishInternal(branch, version.validatedAssetVersion)
    }

    protected fun publishInternal(branch: LayoutBranch, version: LayoutRowVersion<ObjectType>): DaoResponse<ObjectType> {
        val draft = dao.fetch(version)
        require(branch == draft.branch) { "Draft branch does not match the publishing operation: branch=$branch draft=$draft" }
        require(draft.isDraft) { "Object to publish is not a draft: version=$version context=${draft.contextData}" }
        val published = asOfficial(branch, draft)
        require(!published.isDraft) { "Published object is still a draft: context=${published.contextData}" }
        verifyObjectIsExisting(published)

        val publicationResponse = dao.update(published).also { r ->
            require(r.id == draft.id) { "Publication response ID doesn't match object: id=${draft.id} updated=$r" }
        }
        // If draft row-id changed, the data was updated to the official row -> delete the now-redundant draft row
        if (version.rowId != publicationResponse.rowVersion.rowId) {
            dao.deleteRow(version.rowId)
        }
        // If main-draft was published and it came from a design row that wasn't updated, then that row is redundant too
        if (draft.branch == LayoutBranch.main && draft.contextData.designRowId != publicationResponse.rowVersion.rowId) {
            draft.contextData.designRowId?.let(dao::deleteRow)
        }
        return publicationResponse
    }

    private fun verifyInsertResponse(officialId: IntId<ObjectType>?, response: DaoResponse<ObjectType>) {
        if (officialId != null) require(response.id == officialId) {
            "Insert response ID doesn't match object: officialId=$officialId updated=$response"
        } else require(response.id.intValue == response.rowVersion.rowId.intValue) {
            "Inserted new object refers to another official row: inserted=$response"
        }
        require(response.rowVersion.version == 1) {
            "Inserted new row has a version over 1: inserted=$response"
        }
    }

    private fun verifyDraftUpdateResponse(
        id: IntId<ObjectType>,
        previousVersion: LayoutRowVersion<ObjectType>,
        response: DaoResponse<ObjectType>,
    ) {
        require(response.id == id) {
            "Update response ID doesn't match object: id=$id updated=$response"
        }
        require(response.rowVersion.rowId == previousVersion.rowId) {
            "Updated the wrong row (wrong context): id=$id previous=$previousVersion updated=$response"
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
