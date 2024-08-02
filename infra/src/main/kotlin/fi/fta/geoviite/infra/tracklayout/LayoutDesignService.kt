package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import org.springframework.transaction.annotation.Transactional

@GeoviiteService
class LayoutDesignService(
    private val dao: LayoutDesignDao,
) {

    fun list(): List<LayoutDesign> {
        return dao.list()
    }

    fun getOrThrow(id: IntId<LayoutDesign>): LayoutDesign {
        return dao.fetch(id)
    }

    @Transactional
    fun update(id: IntId<LayoutDesign>, request: LayoutDesignSaveRequest): IntId<LayoutDesign> {
        return dao.update(id, request)
    }

    @Transactional
    fun insert(request: LayoutDesignSaveRequest): IntId<LayoutDesign> {
        return dao.insert(request)
    }
}
