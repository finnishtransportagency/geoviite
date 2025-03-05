package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.util.SortOrder
import fi.fta.geoviite.infra.util.nullsLastComparator

fun getComparator(sortBy: PublicationTableColumn, order: SortOrder? = null): Comparator<PublicationTableItem> =
    if (order == SortOrder.DESCENDING) getComparator(sortBy).reversed() else getComparator(sortBy)

private fun getComparator(sortBy: PublicationTableColumn): Comparator<PublicationTableItem> {
    return when (sortBy) {
        PublicationTableColumn.NAME -> Comparator.comparing { p -> p.name }
        PublicationTableColumn.TRACK_NUMBERS ->
            Comparator { a, b -> nullsLastComparator(a.trackNumbers.minOrNull(), b.trackNumbers.minOrNull()) }

        PublicationTableColumn.CHANGED_KM_NUMBERS ->
            Comparator { a, b ->
                nullsLastComparator(a.changedKmNumbers.firstOrNull()?.min, b.changedKmNumbers.firstOrNull()?.min)
            }

        PublicationTableColumn.OPERATION -> Comparator.comparing { p -> p.operation.priority }
        PublicationTableColumn.PUBLICATION_TIME -> Comparator.comparing { p -> p.publicationTime }
        PublicationTableColumn.PUBLICATION_USER -> Comparator.comparing { p -> p.publicationUser }
        PublicationTableColumn.MESSAGE -> Comparator.comparing { p -> p.message }
        PublicationTableColumn.RATKO_PUSH_TIME ->
            Comparator { a, b -> nullsLastComparator(a.ratkoPushTime, b.ratkoPushTime) }

        PublicationTableColumn.CHANGES ->
            Comparator { a, b ->
                nullsLastComparator(
                    a.propChanges.firstOrNull()?.propKey?.key,
                    b.propChanges.firstOrNull()?.propKey?.key,
                )
            }
    }
}
