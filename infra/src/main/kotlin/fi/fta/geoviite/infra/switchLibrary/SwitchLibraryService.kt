package fi.fta.geoviite.infra.switchLibrary

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.logging.serviceCall
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

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
}
