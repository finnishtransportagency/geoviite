package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.logging.serviceCall
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LayoutDesignService(
    private val dao: LayoutDesignDao,
) {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun list(): List<LayoutDesign> {
        logger.serviceCall("list")
        return dao.list()
    }

    fun getOrThrow(id: IntId<LayoutDesign>): LayoutDesign {
        logger.serviceCall("getOrThrow", "id" to id)
        return dao.fetch(id)
    }

    @Transactional
    fun update(id: IntId<LayoutDesign>, request: LayoutDesignSaveRequest): IntId<LayoutDesign> {
        logger.serviceCall("update", "design" to request)
        return dao.update(LayoutDesign(id, request.name, request.estimatedCompletion, request.designState))
    }

    @Transactional
    fun insert(request: LayoutDesignSaveRequest): IntId<LayoutDesign> {
        logger.serviceCall("insert", "request" to request)
        return dao.insert(request)
    }
}
