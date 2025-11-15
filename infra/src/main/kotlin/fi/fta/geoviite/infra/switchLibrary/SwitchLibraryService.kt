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
        structures.associateBy { structure -> structure.id }
    }

    private val owners: List<SwitchOwner> by lazy { switchOwnerDao.fetchSwitchOwners() }

    private val ownersById: Map<IntId<SwitchOwner>, SwitchOwner> by lazy { owners.associateBy { owner -> owner.id } }

    fun getDefaultSwitchOwner(): SwitchOwner {
        return requireNotNull(getSwitchOwner(IntId(1))) { "Default switch owner not found" }
    }

    fun getSwitchStructures(): List<SwitchStructure> = structures

    fun getSwitchStructuresById(): Map<IntId<SwitchStructure>, SwitchStructure> = structuresById

    fun getSwitchStructure(id: IntId<SwitchStructure>): SwitchStructure =
        structuresById[id] ?: throw NoSuchEntityException(SwitchStructure::class, id)

    fun getPresentationJointNumber(id: IntId<SwitchStructure>): JointNumber =
        getSwitchStructure(id).presentationJointNumber

    fun getSwitchOwners(): List<SwitchOwner> = owners

    fun getSwitchOwnersById(): Map<IntId<SwitchOwner>, SwitchOwner> = ownersById

    fun getSwitchOwner(id: IntId<SwitchOwner>): SwitchOwner =
        ownersById[id] ?: throw NoSuchEntityException(SwitchOwner::class, id)

    fun getInframodelAliases(): Map<String, String> {
        return switchStructureDao.getInfraModelAliases()
    }

    @Transactional
    fun replaceExistingSwitchStructures(newSwitchStructures: List<SwitchStructureData>) {
        val existingSwitchStructures = switchStructureDao.fetchSwitchStructures()
        existingSwitchStructures.forEach { existingSwitchStructure ->
            val existsInNewSet =
                newSwitchStructures.any { newSwitchStructure ->
                    newSwitchStructure.type == existingSwitchStructure.type
                }
            if (!existsInNewSet) {
                switchStructureDao.delete(existingSwitchStructure.id)
            }
        }

        newSwitchStructures.forEach { newSwitchStructure ->
            val existingSwitchStructure = existingSwitchStructures.find { it.type == newSwitchStructure.type }
            upsertSwitchStructure(newSwitchStructure, existingSwitchStructure)
        }
    }

    @Transactional
    fun upsertSwitchStructure(modifiedSwitchStructure: SwitchStructureData, existingSwitchStructure: SwitchStructure?) {
        if (existingSwitchStructure?.data != modifiedSwitchStructure) {
            switchStructureDao.upsertSwitchStructure(modifiedSwitchStructure)
        }
    }

    @Transactional
    fun replaceExistingInfraModelAliases(infraModelAliases: Map<String, String>) {
        val oldAliases = switchStructureDao.getInfraModelAliases()
        infraModelAliases.forEach { (key, value) ->
            if (oldAliases[key] != value) {
                switchStructureDao.upsertInfraModelAlias(key, value)
            }
        }
        oldAliases
            .filterNot { (key, _) -> infraModelAliases.containsKey(key) }
            .forEach { (key, _) -> switchStructureDao.deleteInfraModelAlias(key) }
    }
}
