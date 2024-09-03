package fi.fta.geoviite.infra.switchLibrary

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.error.NoSuchEntityException
import org.springframework.transaction.annotation.Transactional

@GeoviiteService
class SwitchLibraryService(
    private val switchStructureDao: SwitchStructureDao,
    private val switchOwnerDao: SwitchOwnerDao,
) {
    private val structures: List<SwitchStructure> by lazy { switchStructureDao.fetchSwitchStructures() }

    private val structuresById: Map<IntId<SwitchStructure>, SwitchStructure> by lazy {
        structures.associateBy { switchStructure -> switchStructure.id as IntId }
    }

    fun getSwitchStructures(): List<SwitchStructure> = structures

    fun getSwitchStructuresById(): Map<IntId<SwitchStructure>, SwitchStructure> = structuresById

    fun getSwitchStructure(id: IntId<SwitchStructure>): SwitchStructure {
        return getOrThrow(id)
    }

    fun getPresentationJointNumber(id: IntId<SwitchStructure>): JointNumber {
        return getOrThrow(id).presentationJointNumber
    }

    fun getSwitchOwners(): List<SwitchOwner> {
        return switchOwnerDao.fetchSwitchOwners()
    }

    fun getSwitchOwner(ownerId: IntId<SwitchOwner>): SwitchOwner? {
        return switchOwnerDao.fetchSwitchOwners().find { it.id == ownerId }
    }

    fun getInframodelAliases(): Map<String, String> {
        return switchStructureDao.getInframodelAliases()
    }

    private fun getOrThrow(id: IntId<SwitchStructure>) =
        structuresById[id] ?: throw NoSuchEntityException(SwitchStructure::class, id)

    @Transactional
    fun upsertSwitchStructure(newSwitchStructure: SwitchStructure, existingSwitchStructure: SwitchStructure?) {
        if (existingSwitchStructure == null) {
            switchStructureDao.insertSwitchStructure(newSwitchStructure)
        } else {
            val switchStructureWithExistingId = newSwitchStructure.copy(id = existingSwitchStructure.id)
            if (!existingSwitchStructure.isSame(switchStructureWithExistingId)) {
                switchStructureDao.updateSwitchStructure(switchStructureWithExistingId)
            }
        }
    }

    @Transactional
    fun replaceExistingSwitchStructures(newSwitchStructures: List<SwitchStructure>) {
        val existingSwitchStructures = switchStructureDao.fetchSwitchStructures()
        existingSwitchStructures.forEach { existingSwitchStructure ->
            val existsInNewSet =
                newSwitchStructures.any { newSwitchStructure ->
                    newSwitchStructure.type == existingSwitchStructure.type
                }
            if (!existsInNewSet) {
                switchStructureDao.delete(existingSwitchStructure.id as IntId)
            }
        }

        newSwitchStructures.forEach { newSwitchStructure ->
            val existingSwitchStructure = existingSwitchStructures.find { it.type == newSwitchStructure.type }
            upsertSwitchStructure(newSwitchStructure, existingSwitchStructure)
        }
    }
}
