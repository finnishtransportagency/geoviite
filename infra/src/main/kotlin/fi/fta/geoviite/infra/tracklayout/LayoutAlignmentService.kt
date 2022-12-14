package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.DataType.TEMP
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.StringId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LayoutAlignmentService(
    private val dao: LayoutAlignmentDao,
) {
    fun update(alignment: LayoutAlignment) = dao.update(alignment)

    fun saveAsNew(alignment: LayoutAlignment): RowVersion<LayoutAlignment> = save(asNew(alignment))

    @Transactional
    fun duplicateOrNew(alignmentVersion: RowVersion<LayoutAlignment>?): RowVersion<LayoutAlignment> =
        alignmentVersion?.let(::duplicate) ?: newEmpty().second

    @Transactional
    fun duplicate(alignmentVersion: RowVersion<LayoutAlignment>): RowVersion<LayoutAlignment> =
        save(asNew(dao.fetch(alignmentVersion)))

    fun save(alignment: LayoutAlignment): RowVersion<LayoutAlignment> {
        return if (alignment.dataType == DataType.STORED) dao.update(alignment)
        else dao.insert(alignment)
    }

    fun newEmpty(): Pair<LayoutAlignment, RowVersion<LayoutAlignment>> {
        val alignment = emptyAlignment()
        return alignment to dao.insert(alignment)
    }
}

private fun asNew(alignment: LayoutAlignment) =
    if (alignment.dataType == TEMP) alignment
    else alignment.copy(id = StringId(), dataType = TEMP)
