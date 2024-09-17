package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.publication.ValidationVersion
import fi.fta.geoviite.infra.util.FreeText
import java.time.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional

@GeoviiteService
abstract class LayoutAssetService<ObjectType : LayoutAsset<ObjectType>, DaoType : ILayoutAssetDao<ObjectType>>(
    protected open val dao: DaoType
) {

    protected open val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun list(context: LayoutContext, includeDeleted: Boolean = false): List<ObjectType> {
        return dao.list(context, includeDeleted)
    }

    fun get(context: LayoutContext, id: IntId<ObjectType>): ObjectType? {
        return dao.get(context, id)
    }

    fun getMany(context: LayoutContext, ids: List<IntId<ObjectType>>): List<ObjectType> {
        return dao.getMany(context, ids)
    }

    fun getOfficialAtMoment(branch: LayoutBranch, id: IntId<ObjectType>, moment: Instant): ObjectType? {
        return dao.getOfficialAtMoment(branch, id, moment)
    }

    fun getOrThrow(context: LayoutContext, id: IntId<ObjectType>): ObjectType {
        return dao.getOrThrow(context, id)
    }

    fun getChangeTime(): Instant {
        return dao.fetchChangeTime()
    }

    fun getLayoutAssetChangeInfo(context: LayoutContext, id: IntId<ObjectType>): LayoutAssetChangeInfo? {
        return dao.fetchLayoutAssetChangeInfo(context, id)
    }

    fun filterBySearchTerm(list: List<ObjectType>, searchTerm: FreeText): List<ObjectType> =
        searchTerm.toString().trim().takeIf(String::isNotEmpty)?.let { term ->
            list.filter { item -> idMatches(term, item) || contentMatches(term, item) }
        } ?: listOf()

    protected open fun idMatches(term: String, item: ObjectType): Boolean = false

    protected open fun contentMatches(term: String, item: ObjectType): Boolean = false

    @Transactional
    open fun saveDraft(branch: LayoutBranch, draftAsset: ObjectType): LayoutDaoResponse<ObjectType> {
        return saveDraftInternal(branch, draftAsset)
    }

    protected fun saveDraftInternal(branch: LayoutBranch, draftAsset: ObjectType): LayoutDaoResponse<ObjectType> {
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
    open fun deleteDraft(branch: LayoutBranch, id: IntId<ObjectType>): LayoutDaoResponse<ObjectType> {
        return dao.deleteDraft(branch, id)
    }

    @Transactional
    open fun publish(branch: LayoutBranch, version: ValidationVersion<ObjectType>): LayoutDaoResponse<ObjectType> {
        return publishInternal(branch, version.validatedAssetVersion)
    }

    protected fun publishInternal(
        branch: LayoutBranch,
        draftVersion: LayoutRowVersion<ObjectType>,
    ): LayoutDaoResponse<ObjectType> {
        val draft = dao.fetch(draftVersion)
        require(branch == draft.branch) {
            "Draft branch does not match the publishing operation: branch=$branch draft=$draft"
        }
        require(draft.isDraft) {
            "Object to publish is not a draft: draftVersion=$draftVersion context=${draft.contextData}"
        }
        val published = asOfficial(branch, draft)
        require(!published.isDraft) { "Published object is still a draft: context=${published.contextData}" }
        verifyObjectIsExisting(published)

        val publicationResponse =
            dao.update(published).also { r ->
                require(r.id == draft.id) { "Publication response ID doesn't match object: id=${draft.id} updated=$r" }
            }
        val publishedRowId = publicationResponse.rowVersion.rowId
        // If draft row-id changed, the data was updated to the official row -> delete the
        // now-redundant draft row
        if (draftVersion.rowId != publishedRowId) {
            dao.deleteRow(draftVersion.rowId)
        }

        // When a design row is implemented into main-official, it ceases to be a part of the design
        draft.contextData.designRowId
            ?.takeIf { draft.branch == LayoutBranch.main }
            ?.let { designRowId ->
                // Update potential draft-design references to point to the new main row
                dao.updateImplementedDesignDraftReferences(designRowId, publishedRowId)
                // If the design row didn't become the main-row, it's redundant
                if (designRowId != publishedRowId) dao.deleteRow(designRowId)
            }

        return publicationResponse
    }

    protected inner class VersionsForMerging(
        val branchOfficialVersion: LayoutRowVersion<ObjectType>,
        val mainOfficialVersion: LayoutRowVersion<ObjectType>?,
        val mainDraftVersion: LayoutRowVersion<ObjectType>?,
    )

    protected fun fetchAndCheckVersionsForMerging(
        fromBranch: DesignBranch,
        id: IntId<ObjectType>,
    ): Pair<VersionsForMerging, ObjectType> {
        val branchOfficialVersion = dao.fetchVersion(fromBranch.official, id)
        require(branchOfficialVersion != null) {
            "Object must exist to merge to main branch: fromBranch=$fromBranch id=$id"
        }

        val mainOfficialVersion = dao.fetchVersion(MainLayoutContext.official, id)
        val mainDraftVersion = dao.fetchVersion(MainLayoutContext.draft, id)

        val branchObject = dao.fetch(branchOfficialVersion)
        require(branchObject.branch == fromBranch) {
            "Object must have branch-official version to merge to main: fromBranch=$fromBranch id=$id"
        }
        require(dao.fetchVersion(fromBranch.draft, id) == branchOfficialVersion) {
            "Object must not have branch-draft version when merging to main: fromBranch=$fromBranch id=$id"
        }
        return VersionsForMerging(branchOfficialVersion, mainOfficialVersion, mainDraftVersion) to branchObject
    }

    fun mergeToMainBranch(fromBranch: DesignBranch, id: IntId<ObjectType>): LayoutDaoResponse<ObjectType> =
        fetchAndCheckVersionsForMerging(fromBranch, id).let { (versions, branchObject) ->
            mergeToMainBranchInternal(versions, branchObject)
        }

    protected fun mergeToMainBranchInternal(
        versions: VersionsForMerging,
        objectFromBranch: ObjectType,
    ): LayoutDaoResponse<ObjectType> =
        // The merge should overwrite any pre-existing main-draft row, or otherwise be saved as a
        // new one
        if (versions.mainDraftVersion == null || versions.mainDraftVersion == versions.mainOfficialVersion) {
            dao.insert(asMainDraft(objectFromBranch))
        } else {
            dao.update(asOverwritingMainDraft(objectFromBranch, versions.mainDraftVersion.rowId))
        }

    private fun verifyInsertResponse(officialId: IntId<ObjectType>?, response: LayoutDaoResponse<ObjectType>) {
        if (officialId != null)
            require(response.id == officialId) {
                "Insert response ID doesn't match object: officialId=$officialId updated=$response"
            }
        else
            require(response.id.intValue == response.rowVersion.rowId.intValue) {
                "Inserted new object refers to another official row: inserted=$response"
            }
        require(response.rowVersion.version == 1) { "Inserted new row has a version over 1: inserted=$response" }
    }

    private fun verifyDraftUpdateResponse(
        id: IntId<ObjectType>,
        previousVersion: LayoutRowVersion<ObjectType>,
        response: LayoutDaoResponse<ObjectType>,
    ) {
        require(response.id == id) { "Update response ID doesn't match object: id=$id updated=$response" }
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
