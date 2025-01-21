package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
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

    fun filterBySearchTerm(
        list: List<ObjectType>,
        searchTerm: FreeText,
        idMatches: (String, ObjectType) -> Boolean,
    ): List<ObjectType> =
        searchTerm.toString().trim().takeIf(String::isNotEmpty)?.let { term ->
            list.filter { item -> idMatches(term, item) || contentMatches(term, item) }
        } ?: listOf()

    @Transactional
    fun cancel(branch: DesignBranch, id: IntId<ObjectType>): LayoutRowVersion<ObjectType>? =
        dao.fetchVersion(branch.official, id)?.let { version ->
            saveDraft(branch, cancelInternal(dao.fetch(version), branch))
        }

    protected fun cancelInternal(asset: ObjectType, designBranch: DesignBranch) =
        cancelled(asset, designBranch.designId)

    protected open fun contentMatches(term: String, item: ObjectType): Boolean = false

    @Transactional
    open fun saveDraft(branch: LayoutBranch, draftAsset: ObjectType): LayoutRowVersion<ObjectType> {
        return saveDraftInternal(branch, draftAsset)
    }

    protected fun saveDraftInternal(branch: LayoutBranch, draftAsset: ObjectType): LayoutRowVersion<ObjectType> {
        val draft = asDraft(branch, draftAsset)
        require(draft.isDraft) { "Item is not a draft: id=${draft.id}" }
        return dao.save(draft)
    }

    @Transactional
    open fun deleteDraft(branch: LayoutBranch, id: IntId<ObjectType>): LayoutRowVersion<ObjectType> {
        return dao.deleteDraft(branch, id)
    }

    @Transactional
    open fun publish(branch: LayoutBranch, version: LayoutRowVersion<ObjectType>): LayoutRowVersion<ObjectType> {
        return publishInternal(branch, version)
    }

    protected fun publishInternal(
        branch: LayoutBranch,
        draftVersion: LayoutRowVersion<ObjectType>,
    ): LayoutRowVersion<ObjectType> {
        val draft = dao.fetch(draftVersion)
        require(branch == draft.branch) {
            "Draft branch does not match the publishing operation: branch=$branch draft=$draft"
        }
        require(draft.isDraft) {
            "Object to publish is not a draft: draftVersion=$draftVersion context=${draft.contextData}"
        }
        val published = asOfficial(branch, draft)
        require(!published.isDraft) { "Published object is still a draft: context=${published.contextData}" }

        val publicationVersion =
            dao.save(published).also { r ->
                require(r.id == draft.id) { "Publication response ID doesn't match object: id=${draft.id} updated=$r" }
            }
        dao.deleteRow(draftVersion.rowId)

        if (draft.isCancelled) {
            dao.deleteRow(publicationVersion.rowId)
        }

        (draft.contextData as? MainDraftContextData)?.originBranch?.let { originBranch ->
            if (originBranch is DesignBranch) {
                dao.deleteRow(LayoutRowId(draftVersion.id, originBranch.official))
            }
        }

        return publicationVersion
    }

    protected fun fetchAndCheckForMerging(fromBranch: DesignBranch, id: IntId<ObjectType>): ObjectType {
        val branchOfficialVersion = dao.fetchVersion(fromBranch.official, id)
        require(branchOfficialVersion != null) {
            "Object must exist to merge to main branch: fromBranch=$fromBranch id=$id"
        }

        val branchObject = dao.fetch(branchOfficialVersion)
        require(branchObject.branch == fromBranch) {
            "Object must have branch-official version to merge to main: fromBranch=$fromBranch id=$id"
        }
        require(dao.fetchVersion(fromBranch.draft, id) == branchOfficialVersion) {
            "Object must not have branch-draft version when merging to main: fromBranch=$fromBranch id=$id"
        }
        return branchObject
    }

    fun mergeToMainBranch(fromBranch: DesignBranch, id: IntId<ObjectType>): LayoutRowVersion<ObjectType> =
        dao.save(asMainDraft(fetchAndCheckForMerging(fromBranch, id)))
}
