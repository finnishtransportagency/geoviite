package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.publication.PublicationResultVersions
import fi.fta.geoviite.infra.util.FreeText
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@GeoviiteService
abstract class LayoutAssetService<
    ObjectType : LayoutAsset<ObjectType>,
    SaveParamsType,
    DaoType : ILayoutAssetDao<ObjectType, SaveParamsType>,
>(protected open val dao: DaoType) {

    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

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
            saveDraftInternal(branch, cancelInternal(dao.fetch(version), branch), dao.getBaseSaveParams(version))
        }

    protected fun cancelInternal(asset: ObjectType, designBranch: DesignBranch) =
        cancelled(asset, designBranch.designId)

    protected fun contentMatches(term: String, item: ObjectType): Boolean = false

    protected fun saveDraftInternal(
        branch: LayoutBranch,
        draftAsset: ObjectType,
        params: SaveParamsType,
    ): LayoutRowVersion<ObjectType> {
        val draft = asDraft(branch, draftAsset)
        require(draft.isDraft) { "Item is not a draft: id=${draft.id}" }
        return dao.save(draft, params)
    }

    @Transactional
    fun deleteDraft(branch: LayoutBranch, id: IntId<ObjectType>): LayoutRowVersion<ObjectType> {
        return dao.deleteDraft(branch, id)
    }

    @Transactional
    fun publish(branch: LayoutBranch, version: LayoutRowVersion<ObjectType>): PublicationResultVersions<ObjectType> {
        return publishInternal(branch, version)
    }

    protected fun publishInternal(
        branch: LayoutBranch,
        draftVersion: LayoutRowVersion<ObjectType>,
    ): PublicationResultVersions<ObjectType> {
        val draft = dao.fetch(draftVersion)
        require(branch == draft.branch) {
            "Draft branch does not match the publishing operation: branch=$branch draft=$draft"
        }
        require(draft.isDraft) {
            "Object to publish is not a draft: draftVersion=$draftVersion context=${draft.contextData}"
        }
        val published = asOfficial(branch, draft)
        require(!published.isDraft) { "Published object is still a draft: context=${published.contextData}" }

        val publishedSaveParams = dao.getBaseSaveParams(draftVersion)
        val publicationVersion =
            dao.save(published, publishedSaveParams).also { r ->
                require(r.id == draft.id) { "Publication response ID doesn't match object: id=${draft.id} updated=$r" }
            }
        dao.deleteRow(draftVersion.rowId)

        if (
            draft.designAssetState == DesignAssetState.COMPLETED || draft.designAssetState == DesignAssetState.CANCELLED
        ) {
            dao.deleteRow(publicationVersion.rowId)
        }

        val completedVersion =
            (draft.contextData as? MainDraftContextData)?.originBranch?.let { originBranch ->
                completeMergeToMain(draftVersion.id, originBranch, publishedSaveParams)
            }

        return PublicationResultVersions(published = publicationVersion, completed = completedVersion)
    }

    private fun completeMergeToMain(
        id: IntId<ObjectType>,
        originBranch: LayoutBranch,
        saveParams: SaveParamsType,
    ): Pair<DesignBranch, LayoutRowVersion<ObjectType>>? =
        if (originBranch is DesignBranch) {
            val designOfficial = dao.fetchVersion(originBranch.official, id)
            if (designOfficial != null && designOfficial.context == originBranch.official) {
                val completedVersion = dao.save(completed(dao.fetch(designOfficial)), saveParams)
                originBranch to completedVersion
            } else null
        } else null

    protected fun fetchAndCheckForMerging(
        fromBranch: DesignBranch,
        id: IntId<ObjectType>,
    ): Pair<ObjectType, SaveParamsType> {
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
        return branchObject to dao.getBaseSaveParams(branchOfficialVersion)
    }

    fun mergeToMainBranch(fromBranch: DesignBranch, id: IntId<ObjectType>): LayoutRowVersion<ObjectType> =
        fetchAndCheckForMerging(fromBranch, id).let { (item, params) -> dao.save(asMainDraft(item), params) }
}
