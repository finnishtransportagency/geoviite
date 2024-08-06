package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.error.DuplicateDesignNameException
import fi.fta.geoviite.infra.error.getPSQLExceptionConstraintAndDetailOrRethrow
import org.postgresql.util.PSQLException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional

@GeoviiteService
class LayoutDesignService(
    private val dao: LayoutDesignDao,
) {
    private val duplicateSwitchErrorRegex = Regex("""Key \(lower\(name\)\)=\(([^,]+)\) conflicts with existing key""")

    fun list(): List<LayoutDesign> {
        return dao.list()
    }

    fun getOrThrow(id: IntId<LayoutDesign>): LayoutDesign {
        return dao.fetch(id)
    }

    @Transactional
    fun update(id: IntId<LayoutDesign>, request: LayoutDesignSaveRequest): IntId<LayoutDesign> = try {
        dao.update(id, request)
    } catch (e: DataIntegrityViolationException) {
        handleDuplicateNameOrRethrow(e)
    }

    @Transactional
    fun insert(request: LayoutDesignSaveRequest): IntId<LayoutDesign> = try {
        dao.insert(request)
    } catch (e: DataIntegrityViolationException) {
        handleDuplicateNameOrRethrow(e)
    }

    private fun handleDuplicateNameOrRethrow(e: DataIntegrityViolationException): Nothing {
        val cause = e.cause
        if (cause !is PSQLException) throw e

        val (constraint, detail) = getPSQLExceptionConstraintAndDetailOrRethrow(cause)

        duplicateSwitchErrorRegex.matchAt(detail, 0)?.let { match -> match.groups[1]?.value }?.let { name ->
            if (constraint == "layout_design_unique_name") {
                throw DuplicateDesignNameException(name, e)
            }
        }
        throw e
    }
}
