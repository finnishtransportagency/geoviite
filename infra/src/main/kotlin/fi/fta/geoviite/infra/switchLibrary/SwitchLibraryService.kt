package fi.fta.geoviite.infra.switchLibrary

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.logging.serviceCall
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SwitchLibraryService(
    private val switchStructureDao: SwitchStructureDao,
    private val switchOwnerDao: SwitchOwnerDao,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val structuresById: Map<IntId<SwitchStructure>, SwitchStructure> by lazy {
        switchStructureDao.fetchSwitchStructures().associateBy { switchStructure ->
            switchStructure.id as IntId
        }
    }

    fun getSwitchStructures(): List<SwitchStructure> {
        logger.serviceCall("getSwitchStructures")
        return switchStructureDao.fetchSwitchStructures()
    }

    fun getSwitchStructuresById(): Map<IntId<SwitchStructure>, SwitchStructure> {
        logger.serviceCall("getSwitchStructuresById")
        return structuresById
    }

    fun getSwitchStructureByType(type: SwitchType):SwitchStructure? {
        logger.serviceCall("getSwitchStructureByType","type" to type)
        return getSwitchStructures().find { switchStructure -> switchStructure.type==type }
    }

    fun getSwitchStructure(id: IntId<SwitchStructure>): SwitchStructure {
        logger.serviceCall("getSwitchStructure", "id" to id)
        return getOrThrow(id)
    }

    fun getPresentationJointNumber(id: IntId<SwitchStructure>): JointNumber {
        logger.serviceCall("getPresentationJointNumber", "id" to id)
        return getOrThrow(id).presentationJointNumber
    }

    fun getSwitchType(id: IntId<SwitchStructure>): SwitchType {
        logger.serviceCall("getSwitchType", "id" to id)
        return getOrThrow(id).type
    }

    fun getSwitchOwners(): List<SwitchOwner> {
        logger.serviceCall("getSwitchOwners")
        return switchOwnerDao.fetchSwitchOwners()
    }

    fun getSwitchOwner(ownerId: IntId<SwitchOwner>): SwitchOwner? {
        logger.serviceCall("getSwitchOwner", "ownerId" to ownerId)
        return switchOwnerDao.fetchSwitchOwners().find { it.id == ownerId }
    }

    fun getInframodelAliases(): Map<String, String> {
        logger.serviceCall("getInframodelAliases")
        return switchStructureDao.getInframodelAliases()
    }

    private fun getOrThrow(id: IntId<SwitchStructure>) =
        structuresById[id] ?: throw NoSuchEntityException(SwitchStructure::class, id)

    @Transactional
    fun upsertSwitchStructure(switchStructure: SwitchStructure) {
        logger.serviceCall("upsertSwitchStructure", "switchStructure" to switchStructure)
        val existingSwitchStructure = getSwitchStructureByType(switchStructure.type)
        if (existingSwitchStructure==null) {
            switchStructureDao.insertSwitchStructure(switchStructure)
        } else {
            val switchStructureWithExistingId = switchStructure.copy(id =existingSwitchStructure.id)
            if (!existingSwitchStructure.isSame(switchStructureWithExistingId)) {
                switchStructureDao.updateSwitchStructure(switchStructureWithExistingId)
            }
        }
    }

    @Transactional
    fun replaceExistingSwitchStructures(newSwitchStructures: List<SwitchStructure>) {
        logger.serviceCall("replaceExistingSwitchStructures", "newSwitchStructures" to newSwitchStructures)

        val existingSwitchStructures = getSwitchStructures()
        existingSwitchStructures.forEach { existingSwitchStructure ->
            val existsInNewSet = newSwitchStructures.any { newSwitchStructure ->
                newSwitchStructure.type==existingSwitchStructure.type
            }
            if (!existsInNewSet) {
                switchStructureDao.delete(existingSwitchStructure.id as IntId)
            }
        }

        newSwitchStructures.forEach { switchStructure -> upsertSwitchStructure(switchStructure) }
    }
}
