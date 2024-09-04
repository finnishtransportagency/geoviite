package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional

@GeoviiteService
class LayoutDesignService(private val dao: LayoutDesignDao) {
    fun list(): List<LayoutDesign> {
        return dao.list()
    }

    fun getOrThrow(id: IntId<LayoutDesign>): LayoutDesign {
        return dao.fetch(id)
    }

    @Transactional
    fun update(id: IntId<LayoutDesign>, request: LayoutDesignSaveRequest): IntId<LayoutDesign> =
        try {
            dao.update(id, request)
        } catch (e: DataIntegrityViolationException) {
            throw asDuplicateNameException(e) ?: e
        }

    @Transactional
    fun insert(request: LayoutDesignSaveRequest): IntId<LayoutDesign> =
        try {
            dao.insert(request)
        } catch (e: DataIntegrityViolationException) {
            throw asDuplicateNameException(e) ?: e
        }
}
