package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geography.GeometryPoint
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.localization.LocalizationKey
import fi.fta.geoviite.infra.localization.Translation
import fi.fta.geoviite.infra.localization.localizationParams
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.roundTo1Decimal
import fi.fta.geoviite.infra.math.roundTo3Decimals
import fi.fta.geoviite.infra.switchLibrary.SwitchBaseType
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutAssetDao
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.SegmentPoint
import fi.fta.geoviite.infra.tracklayout.TopologyLocationTrackSwitch
import fi.fta.geoviite.infra.util.CsvEntry
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.SortOrder
import fi.fta.geoviite.infra.util.nullsLastComparator
import fi.fta.geoviite.infra.util.printCsv
import fi.fta.geoviite.infra.util.rangesOfConsecutiveIndicesOf
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class GeometryChangeRanges(val added: List<Range<Double>>, val removed: List<Range<Double>>)

fun getDateStringForFileName(instant1: Instant?, instant2: Instant?, timeZone: ZoneId): String? {
    val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(timeZone)

    val instant1Date = instant1?.let { dateFormatter.format(it) }
    val instant2Date = instant2?.let { dateFormatter.format(it) }

    return if (instant1Date == instant2Date) instant1Date
    else if (instant1Date == null) "-$instant2Date"
    else if (instant2Date == null) "$instant1Date" else "$instant1Date-$instant2Date"
}

fun getCsvResponseEntity(content: String, fileName: FileName): ResponseEntity<ByteArray> {
    val headers = HttpHeaders()
    headers.contentType = MediaType.APPLICATION_OCTET_STREAM
    headers.set(
        HttpHeaders.CONTENT_DISPOSITION,
        ContentDisposition.attachment().filename(fileName.toString()).build().toString(),
    )

    return ResponseEntity.ok().headers(headers).body(content.toByteArray())
}

fun asCsvFile(items: List<PublicationTableItem>, timeZone: ZoneId, translation: Translation): String {
    val columns =
        mapOf<String, (item: PublicationTableItem) -> Any?>(
                "publication-table.name" to { it.name },
                "publication-table.track-number" to { it.trackNumbers.sorted().joinToString(", ") },
                "publication-table.km-number" to
                    {
                        it.changedKmNumbers.joinToString(", ") { range ->
                            "${range.min}${if (range.min != range.max) "-${range.max}" else ""}"
                        }
                    },
                "publication-table.operation" to { translation.enum(it.operation) },
                "publication-table.publication-time" to { formatInstant(it.publicationTime, timeZone) },
                "publication-table.publication-user" to { "${it.publicationUser}" },
                "publication-table.message" to { it.message.escapeNewLines() },
                "publication-table.pushed-to-ratko" to
                    {
                        it.ratkoPushTime?.let { pushTime -> formatInstant(pushTime, timeZone) } ?: translation.t("no")
                    },
                "publication-table.changes" to
                    {
                        it.propChanges.map { change ->
                            "${
                    translation.t("publication-details-table.prop.${change.propKey.key}", change.propKey.params)
                }: ${formatChangeValue(translation, change.value)}${
                    if (change.remark != null) " (${change.remark})" else ""
                }"
                        }
                    },
            )
            .map { (column, fn) -> CsvEntry(translation.t(column), fn) }

    return printCsv(columns, items)
}

private fun enumTranslationKey(enumName: LocalizationKey, value: String) = "enum.${enumName}.${value}"

private fun <T> formatChangeValue(translation: Translation, value: ChangeValue<T>): String {
    return "${
        (if (value.localizationKey != null && value.oldValue != null) translation.t(
            enumTranslationKey(
                value.localizationKey, value.oldValue.toString(),
            )
        ) else if (value.oldValue == null) null else value.oldValue.toString()) ?: ""
    } -> ${
        (if (value.localizationKey != null && value.newValue != null) translation.t(
            enumTranslationKey(
                value.localizationKey, value.newValue.toString(),
            )
        ) else if (value.newValue == null) null else value.newValue.toString()) ?: ""
    }"
}

private fun formatInstant(time: Instant, timeZone: ZoneId) =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(timeZone).format(time)

fun groupChangedKmNumbers(kmNumbers: List<KmNumber>) =
    kmNumbers
        .sorted()
        .fold(mutableListOf<List<KmNumber>>()) { acc, kmNumber ->
            if (acc.isEmpty()) acc.add(listOf(kmNumber))
            else {
                val previousKmNumbers = acc.last()
                val previousKmNumber = previousKmNumbers.last().number

                if (kmNumber.number == previousKmNumber || kmNumber.number == previousKmNumber + 1) {
                    acc[acc.lastIndex] = listOf(previousKmNumbers.first(), kmNumber)
                } else acc.add(listOf(kmNumber))
            }
            acc
        }
        .map { Range(it.first(), it.last()) }

fun formatChangedKmNumbers(kmNumbers: List<KmNumber>) =
    groupChangedKmNumbers(kmNumbers).joinToString(", ") { if (it.min == it.max) "${it.min}" else "${it.min}-${it.max}" }

fun formatDistance(dist: Double) = if (dist >= 0.1) "${roundTo1Decimal(dist)}" else "<${roundTo1Decimal(0.1)}"

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

fun formatLocation(location: IPoint) =
    "${roundTo3Decimals(location.x)} E, ${
    roundTo3Decimals(
        location.y
    )
} N"

fun formatGkLocation(location: GeometryPoint, crsNameGetter: (srid: Srid) -> String) =
    "${roundTo3Decimals(location.y)} N, ${
    roundTo3Decimals(
        location.x
    )
} E (${crsNameGetter(location.srid)})"

const val DISTANCE_CHANGE_THRESHOLD = 0.0005

fun lengthDifference(len1: Double, len2: Double) = abs(abs(len1) - abs(len2))

fun lengthDifference(len1: BigDecimal, len2: BigDecimal) = abs(abs(len1.toDouble()) - abs(len2.toDouble()))

fun pointsAreSame(point1: IPoint?, point2: IPoint?) =
    point1 == point2 || point1 != null && point2 != null && point1.isSame(point2, DISTANCE_CHANGE_THRESHOLD)

fun getLengthChangedRemarkOrNull(translation: Translation, length1: Double?, length2: Double?) =
    if (length1 == null || length2 == null) {
        null
    } else {
        (length2 - length1).let { directionalLengthDifference ->
            when {
                abs(directionalLengthDifference) <= DISTANCE_CHANGE_THRESHOLD -> null

                (directionalLengthDifference < 0) ->
                    publicationChangeRemark(
                        translation,
                        "shortened-x-meters",
                        formatDistance(abs(directionalLengthDifference)),
                    )

                (directionalLengthDifference > 0) ->
                    publicationChangeRemark(
                        translation,
                        "lengthened-x-meters",
                        formatDistance(abs(directionalLengthDifference)),
                    )

                else -> null
            }
        }
    }

fun getPointMovedRemarkOrNull(
    translation: Translation,
    oldPoint: Point?,
    newPoint: Point?,
    translationKey: String = "moved-x-meters",
) =
    oldPoint?.let { p1 ->
        newPoint?.let { p2 ->
            if (!pointsAreSame(p1, p2)) {
                val distance = calculateDistance(listOf(p1, p2), LAYOUT_SRID)
                if (distance > DISTANCE_CHANGE_THRESHOLD)
                    publicationChangeRemark(translation, translationKey, formatDistance(distance))
                else null
            } else null
        }
    }

fun getAddressMovedRemarkOrNull(translation: Translation, oldAddress: TrackMeter?, newAddress: TrackMeter?) =
    if (newAddress == null || oldAddress == null) {
        null
    } else if (newAddress.kmNumber != oldAddress.kmNumber) {
        publicationChangeRemark(translation = translation, key = "km-number-changed", value = "${newAddress.kmNumber}")
    } else if (lengthDifference(newAddress.meters, oldAddress.meters) > DISTANCE_CHANGE_THRESHOLD) {
        publicationChangeRemark(
            translation = translation,
            key = "moved-x-meters",
            value = formatDistance(lengthDifference(newAddress.meters, oldAddress.meters)),
        )
    } else {
        null
    }

fun getSwitchLinksChangedRemark(
    translation: Translation,
    switchLinkChanges: LocationTrackPublicationSwitchLinkChanges,
): String {
    val removed = switchLinkChanges.old.minus(switchLinkChanges.new.keys)
    val added = switchLinkChanges.new.minus(switchLinkChanges.old.keys)
    val commonNames = removed.values.map { it.name }.intersect(added.values.map { it.name }.toSet())

    fun remarkOnIds(ids: SwitchChangeIds) =
        if (commonNames.contains(ids.name) && ids.externalId != null) "${ids.name} (${ids.externalId})" else ids.name

    val remarkRemoved =
        publicationChangeRemark(
            translation,
            if (removed.size > 1) "switch-link-removed-plural" else "switch-link-removed-singular",
            removed.values.map(::remarkOnIds).sorted().joinToString(),
        )
    val remarkAdded =
        publicationChangeRemark(
            translation,
            if (added.size > 1) "switch-link-added-plural" else "switch-link-added-singular",
            added.values.map(::remarkOnIds).sorted().joinToString(),
        )
    return if (removed.isNotEmpty() && added.isNotEmpty()) "${remarkRemoved}. ${remarkAdded}."
    else if (removed.isNotEmpty()) remarkRemoved else remarkAdded
}

fun <T, U> compareChangeValues(
    change: Change<T>,
    valueTransform: (T) -> U,
    propKey: PropKey,
    remark: String? = null,
    enumLocalizationKey: String? = null,
) =
    compareChange(
        predicate = { change.new != change.old },
        oldValue = change.old,
        newValue = change.new,
        valueTransform = valueTransform,
        propKey = propKey,
        remark = remark,
        enumLocalizationKey = enumLocalizationKey,
    )

fun <U> compareLength(
    oldValue: Double?,
    newValue: Double?,
    threshold: Double,
    valueTransform: (Double) -> U,
    propKey: PropKey,
    remark: String? = null,
    enumLocalizationKey: String? = null,
) =
    compareChange(
        predicate = {
            if (oldValue != null && newValue != null) lengthDifference(oldValue, newValue) > threshold
            else if (oldValue != null || newValue != null) true else false
        },
        oldValue = oldValue,
        newValue = newValue,
        valueTransform = valueTransform,
        propKey = propKey,
        remark = remark,
        enumLocalizationKey = enumLocalizationKey,
    )

fun <T, U> compareChange(
    predicate: () -> Boolean,
    oldValue: T?,
    newValue: T?,
    valueTransform: (T) -> U,
    propKey: PropKey,
    remark: String? = null,
    enumLocalizationKey: String? = null,
) =
    if (predicate()) {
        PublicationChange(
            propKey,
            value =
                ChangeValue(
                    oldValue = oldValue?.let(valueTransform),
                    newValue = newValue?.let(valueTransform),
                    localizationKey = enumLocalizationKey,
                ),
            remark,
        )
    } else null

fun switchBaseTypeToProp(translation: Translation, switchBaseType: SwitchBaseType) =
    when (switchBaseType) {
        SwitchBaseType.KRV,
        SwitchBaseType.YRV,
        SwitchBaseType.SRR,
        SwitchBaseType.RR -> translation.t("publication-details-table.joint.forward-joint")

        SwitchBaseType.KV,
        SwitchBaseType.SKV,
        SwitchBaseType.TYV,
        SwitchBaseType.UKV,
        SwitchBaseType.EV,
        SwitchBaseType.YV -> translation.t("publication-details-table.joint.math-point")
    }

data class GeometryChangeSummary(
    val changedLengthM: Double,
    val maxDistance: Double,
    val startAddress: TrackMeter,
    val endAddress: TrackMeter,
)

fun getKmNumbersChangedRemarkOrNull(
    translation: Translation,
    changedKmNumbers: Set<KmNumber>,
    summaries: List<GeometryChangeSummary>?,
): String =
    if (summaries.isNullOrEmpty()) {
        publicationChangeRemark(
            translation,
            if (changedKmNumbers.size > 1) "changed-km-numbers" else "changed-km-number",
            formatChangedKmNumbers(changedKmNumbers.toList()),
        )
    } else
        summaries.joinToString(". ") { summary ->
            translation.t(
                "publication-details-table.remark.geometry-changed",
                localizationParams(
                    "changedLengthM" to roundTo1Decimal(summary.changedLengthM).toString(),
                    "maxDistance" to roundTo1Decimal(summary.maxDistance).toString(),
                    "addressRange" to "${summary.startAddress.round(0)}-${summary.endAddress.round(0)}",
                ),
            )
        }

private data class ComparisonPoints(
    val mOnReferenceLine: Double,
    val oldPointIndex: Int,
    val oldPoint: IPoint,
    val newPoint: IPoint,
) {
    val roughDistance = hypot(oldPoint.x - newPoint.x, oldPoint.y - newPoint.y)

    fun distance() = calculateDistance(LAYOUT_SRID, oldPoint, newPoint)
}

const val MINIMUM_M_DISTANCE_SEPARATING_ALIGNMENT_CHANGE_SUMMARIES = 10.0

fun summarizeAlignmentChanges(
    geocodingContext: GeocodingContext,
    oldAlignment: LayoutAlignment,
    newAlignment: LayoutAlignment,
    changeThreshold: Double = 1.0,
): List<GeometryChangeSummary> {
    val changedRanges = getChangedAlignmentRanges(oldAlignment, newAlignment)
    return changedRanges
        .mapNotNull { oldSegments ->
            val oldPoints = oldSegments.flatMap { segment -> segment.segmentPoints }
            val changedPoints =
                oldPoints
                    .mapIndexed { index, oldPoint -> index to oldPoint }
                    .parallelStream()
                    .map { (index, oldPoint) ->
                        geocodingContext.getAddressAndM(oldPoint)?.let { (address, mOnReferenceLine) ->
                            geocodingContext
                                .getTrackLocation(newAlignment, address)
                                ?.let { newAddressPoint ->
                                    ComparisonPoints(mOnReferenceLine, index, oldPoint, newAddressPoint.point)
                                }
                                ?.let { comparison ->
                                    if (comparison.roughDistance < changeThreshold) null else comparison
                                }
                        }
                    }
                    .toList()
                    .filterNotNull()
            val changedPointsRangesFirstIndices =
                changedPoints
                    .zipWithNext { a, b ->
                        b.mOnReferenceLine - a.mOnReferenceLine >
                            MINIMUM_M_DISTANCE_SEPARATING_ALIGNMENT_CHANGE_SUMMARIES
                    }
                    .mapIndexedNotNull { index, jump -> (index + 1).takeIf { jump } }
            val changedPointsRanges =
                (listOf(0) + changedPointsRangesFirstIndices + changedPoints.size).zipWithNext { a, b -> a to b }

            if (changedPoints.isEmpty()) null
            else
                changedPointsRanges.mapNotNull { (from, to) ->
                    val start = changedPoints[from]
                    val end = changedPoints[to - 1]
                    val startAddress = geocodingContext.getAddress(oldPoints[start.oldPointIndex])?.first
                    val endAddress = geocodingContext.getAddress(oldPoints[end.oldPointIndex])?.first

                    if (startAddress == null || endAddress == null) null
                    else
                        GeometryChangeSummary(
                            end.mOnReferenceLine - start.mOnReferenceLine,
                            changedPoints.subList(from, to).maxByOrNull { it.roughDistance }?.distance() ?: 0.0,
                            startAddress,
                            endAddress,
                        )
                }
        }
        .flatten()
}

private fun getChangedAlignmentRanges(old: LayoutAlignment, new: LayoutAlignment): List<List<LayoutSegment>> {
    val newIndexByGeometryId =
        new.segments.mapIndexed { i, s -> i to s }.associate { (index, segment) -> segment.geometry.id to index }
    val changedOldSegmentIndexRanges =
        rangesOfConsecutiveIndicesOf(
            false,
            old.segments.map { segment -> newIndexByGeometryId.containsKey(segment.geometry.id) },
        )
    return changedOldSegmentIndexRanges.map { oldSegmentIndexRange ->
        old.segments.subList(oldSegmentIndexRange.start, oldSegmentIndexRange.endInclusive + 1)
    }
}

fun publicationChangeRemark(translation: Translation, key: String, value: String? = null) =
    translation.t("publication-details-table.remark.$key", localizationParams("value" to value))

fun addOperationClarificationsToPublicationTableItem(
    translation: Translation,
    publicationTableItem: PublicationTableItem,
): PublicationTableItem {
    return when (publicationTableItem.operation) {
        Operation.CALCULATED ->
            publicationTableItem.copy(
                propChanges =
                    publicationTableItem.propChanges.map { publicationChange ->
                        addChangeClarification(
                            publicationChange,
                            translation.t("publication-table.calculated-change"),
                            translation.t("publication-table.calculated-change-lowercase"),
                        )
                    }
            )

        else -> publicationTableItem
    }
}

fun addChangeClarification(
    publicationChange: PublicationChange<*>,
    clarification: String,
    clarificationInSentenceBody: String? = null,
): PublicationChange<*> {
    return when (publicationChange.remark) {
        null -> publicationChange.copy(remark = clarification)

        else -> {
            val displayedClarification = clarificationInSentenceBody ?: clarification

            publicationChange.copy(remark = "${publicationChange.remark}, $displayedClarification")
        }
    }
}

fun findJointPoint(
    locationTrack: LocationTrack,
    alignment: LayoutAlignment,
    switchId: IntId<LayoutSwitch>,
    jointNumber: JointNumber,
): SegmentPoint? {
    val asTopoSwitch = TopologyLocationTrackSwitch(switchId, jointNumber)
    return if (locationTrack.topologyStartSwitch == asTopoSwitch) alignment.firstSegmentStart
    else if (locationTrack.topologyEndSwitch == asTopoSwitch) alignment.lastSegmentEnd
    else {
        val segment =
            alignment.segments.find { segment ->
                segment.switchId == switchId &&
                    (segment.startJointNumber == jointNumber || segment.endJointNumber == jointNumber)
            }
        if (segment == null) null
        else {
            if (segment.startJointNumber == jointNumber) segment.segmentStart else segment.segmentEnd
        }
    }
}

fun getChangedGeometryRanges(newSegments: List<LayoutSegment>, oldSegments: List<LayoutSegment>) =
    GeometryChangeRanges(
        added = getAddedSegmentMRanges(newSegments, oldSegments),
        removed = getAddedSegmentMRanges(oldSegments, newSegments),
    )

fun getAddedSegmentMRanges(newSegments: List<LayoutSegment>, oldSegments: List<LayoutSegment>): List<Range<Double>> {
    val addedSegmentIndices = getAddedIndexRangesExclusive(newSegments, oldSegments) { s -> s.geometry.id }
    return addedSegmentIndices.flatMap { (newRange, oldRange) ->
        val newPoints = getPointsWithMExclusive(newSegments, newRange)
        val oldPoints = getPointsWithMExclusive(oldSegments, oldRange)
        getAddedIndexRangesExclusive(newPoints, oldPoints) { it.first }
            .map { (newPointRange, _) ->
                // The exclusive range over-indexes if the last point is inside the interval
                // However, we can then just use the last point itself for m-value as it will be a segment end
                val start = newPoints[max(0, newPointRange.min)]
                val end = newPoints[min(newPoints.lastIndex, newPointRange.max)]
                Range(start.second, end.second)
            }
    }
}

fun getPointsWithMExclusive(segments: List<LayoutSegment>, indexRange: Range<Int>): List<Pair<Point, Double>> =
    indexRange
        .takeIf { r -> r.max - r.min >= 2 }
        ?.let { exclusive -> Range(exclusive.min + 1, exclusive.max - 1) }
        ?.let { inclusive ->
            (inclusive.min..inclusive.max).flatMap { i ->
                segments.getOrNull(i)?.let { segment ->
                    segment.segmentPoints
                        .let { if (i == inclusive.min) it else it.subList(0, it.lastIndex) }
                        .map { p -> p.toPoint() to (p.m + segment.startM) }
                } ?: emptyList()
            }
        } ?: emptyList()

fun <T, S> getAddedIndexRangesExclusive(
    newObjects: List<T>,
    oldObjects: List<T>,
    compareBy: (T) -> S,
): List<Pair<Range<Int>, Range<Int>>> {
    val oldCompareObjects = oldObjects.mapIndexed { i, o -> compareBy(o) to i }.toMap()
    val addedIndexRanges = mutableListOf<Pair<Range<Int>, Range<Int>>>()
    var prevMatchedIndex: Pair<Int, Int>? = null
    newObjects.forEachIndexed { i, o ->
        val match = oldCompareObjects[compareBy(o)]
        if (match != null || i == newObjects.lastIndex) {
            val startIndex = prevMatchedIndex?.first ?: -1
            val endIndex = if (match != null) i else i + 1
            // Something was only added if the exclusive range has at least one point between the ends
            if (endIndex - startIndex > 1) {
                val oldStartIndex = prevMatchedIndex?.second ?: -1
                val oldEndIndex = match ?: (oldObjects.lastIndex + 1)
                addedIndexRanges.add(Range(startIndex, endIndex) to Range(oldStartIndex, oldEndIndex))
            }
        }
        if (match != null) prevMatchedIndex = i to match
    }
    return addedIndexRanges
}

fun <T : LayoutAsset<T>> getObjectFromValidationVersions(
    versions: List<LayoutRowVersion<T>>,
    dao: LayoutAssetDao<T>,
    target: ValidationTarget,
    id: IntId<T>,
): T? = (versions.find { it.id == id } ?: dao.fetchVersion(target.baseContext, id))?.let(dao::fetch)
