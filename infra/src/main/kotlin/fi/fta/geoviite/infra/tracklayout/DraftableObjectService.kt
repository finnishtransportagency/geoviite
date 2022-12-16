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
        OFFICIAL ->  dao.fetchOfficialVersion(id)?.let(dao::fetch)
    }

    protected fun getDraftInternal(id: IntId<ObjectType>): ObjectType = getInternalOrThrow(DRAFT, id)

    protected fun getInternalOrThrow(publishType: PublishType, id: IntId<ObjectType>): ObjectType = when (publishType) {
        DRAFT -> dao.fetch(dao.fetchDraftVersionOrThrow(id))
        OFFICIAL -> dao.fetch(dao.fetchOfficialVersionOrThrow(id))
    }

    protected abstract fun createDraft(item: ObjectType): ObjectType

    protected abstract fun createPublished(item: ObjectType): ObjectType

    open fun saveDraft(draft: ObjectType): RowVersion<ObjectType> {
        logger.serviceCall("saveDraft", "id" to draft.id)
        return saveDraftInternal(draft)
    }

    protected fun saveDraftInternal(item: ObjectType): RowVersion<ObjectType> {
        val draft = createDraft(item)
        require(draft.draft != null) { "Item is not a draft: id=${draft.id}" }
        return if (draft.dataType == DataType.TEMP) {
            dao.insert(draft)
        } else {
            dao.update(draft)
        }
    }

    fun deleteDrafts(): List<Pair<IntId<ObjectType>, IntId<ObjectType>?>> {
        logger.serviceCall("deleteDrafts")
        return dao.deleteDrafts()
    }

    open fun deleteUnpublishedDraft(id: IntId<ObjectType>): RowVersion<ObjectType> {
        logger.serviceCall("deleteUnpublishedDraft", "id" to id)
        return dao.deleteUnpublishedDraft(id)
    }

    @Transactional
    open fun publish(version: PublicationVersion<ObjectType>): RowVersion<ObjectType> {
        logger.serviceCall("Publish", "version" to version)
        return publishInternal(VersionPair(dao.fetchOfficialVersion(version.officialId), version.draftVersion))
    }

    protected fun publishInternal(versions: VersionPair<ObjectType>): RowVersion<ObjectType> {
        val draft = versions.draft?.let(dao::fetch)

        if (draft?.draft == null) throw IllegalStateException("Object to publish is not a draft $versions $draft")
        val published = createPublished(draft)
        if (published.draft != null) throw IllegalStateException("Published object is still a draft")

        val publishedVersion = dao.updateAndVerifyVersion(versions.official ?: versions.draft, published)
        if (versions.official != null && versions.draft.id != versions.official.id) {
            // Draft data is saved on official id -> delete the draft row
            dao.deleteDrafts(versions.draft.id)
        }
        return publishedVersion
    }
}
