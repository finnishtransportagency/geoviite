package fi.fta.geoviite.infra.dataImport

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geometry.line
import fi.fta.geoviite.infra.getSomeOid
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Point3DM
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.FileName
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.test.assertEquals

class CsvParsingTest {
    @Test
    fun lastSwitchJointWithSameNumberWillOverwriteEarlierJoint() {
        val oid = Oid<TrackLayoutSwitch>("1.2.246.578.3.10002.192148")
        val joint1 = TrackLayoutSwitchJoint(
            JointNumber(1),
            Point(0.0, 1.0),
            null
        )
        val joint2 = TrackLayoutSwitchJoint(
            JointNumber(2),
            Point(0.0, 2.0),
            null
        )
        val joint3 = TrackLayoutSwitchJoint(
            JointNumber(1),
            Point(0.0, 3.0),
            null
        )
        val switchJointMap = mapOf(
            oid to listOf(
                joint1, joint2, joint3
            )
        )
        val filteredList = filterOutDuplicateJointNumber(switchJointMap)[oid]!!
        assertEquals(2, filteredList.size)
        assertEquals(joint2, filteredList[0])
        assertEquals(joint3, filteredList[1])
    }


    @Test
    fun segmentingRangesWorksWithoutCsvMetadata() {
        val start = AddressPoint(point(1), TrackMeter("0002+123.123"))
        val end = AddressPoint(point(2), TrackMeter("0007+321.345"))

        val segments = segmentCsvMetadata<LocationTrack>(listOf(start, end), listOf(), listOf())
        val expected = listOf<SegmentCsvMetaDataRange<LocationTrack>>(
            emptyCsvMetaData(TrackMeter("0002+123.123")..TrackMeter("0003+0")),
            emptyCsvMetaData(TrackMeter("0003+0")..TrackMeter("0004+0")),
            emptyCsvMetaData(TrackMeter("0004+0")..TrackMeter("0005+0")),
            emptyCsvMetaData(TrackMeter("0005+0")..TrackMeter("0006+0")),
            emptyCsvMetaData(TrackMeter("0006+0")..TrackMeter("0007+0")),
            emptyCsvMetaData(TrackMeter("0007+0")..TrackMeter("0007+321.345")),
        )
        assertEquals(expected, segments)
    }

    @Test
    fun segmentingRangesWorksWithFullAlignmentCsvMetadata() {
        val start = AddressPoint(point(1), TrackMeter("0002+456.654"))
        val end = AddressPoint(point(2), TrackMeter("0006+111.222"))

        val md1 = elementCsvMetaData(1, TrackMeter("0002+456.654"), TrackMeter("0002+963.9"))
        val md2 = elementCsvMetaData(2, TrackMeter("0002+963.9"), TrackMeter("0004+111.1"))
        val md3 = elementCsvMetaData(3, TrackMeter("0004+111.1"), TrackMeter("0006+111.222"))

        val segments = segmentCsvMetadata(listOf(start, end), listOf(md1, md2, md3), listOf())
        val expected = listOf(
            SegmentCsvMetaDataRange(TrackMeter("0002+456.654")..TrackMeter("0002+963.9"), md1, null),
            SegmentCsvMetaDataRange(TrackMeter("0002+963.9")..TrackMeter("0003+0"), md2, null),
            SegmentCsvMetaDataRange(TrackMeter("0003+0")..TrackMeter("0004+0"), md2, null),
            SegmentCsvMetaDataRange(TrackMeter("0004+0")..TrackMeter("0004+111.1"), md2, null),
            SegmentCsvMetaDataRange(TrackMeter("0004+111.1")..TrackMeter("0005+0"), md3, null),
            SegmentCsvMetaDataRange(TrackMeter("0005+0")..TrackMeter("0006+0"), md3, null),
            SegmentCsvMetaDataRange(TrackMeter("0006+0")..TrackMeter("0006+111.222"), md3, null),
        )
        assertEquals(expected, segments)
    }

    @Test
    fun segmentingRangesWorksWithPartialAlignmentCsvMetadata() {
        val start = AddressPoint(point(1), TrackMeter("0002+456.654"))
        val end = AddressPoint(point(2), TrackMeter("0007+999.999"))

        val md1 = elementCsvMetaData(1, TrackMeter("0002+963.9"), TrackMeter("0003+333.3"))
        val md2 = elementCsvMetaData(2, TrackMeter("0004+444.4"), TrackMeter("0004+555.5"))
        val md3 = elementCsvMetaData(3, TrackMeter("0005+555.5"), TrackMeter("0006+666.6"))

        val segments = segmentCsvMetadata(listOf(start, end), listOf(md1, md2, md3), listOf())
        val expected = listOf(
            emptyCsvMetaData(TrackMeter("0002+456.654")..TrackMeter("0002+963.9")),
            SegmentCsvMetaDataRange(TrackMeter("0002+963.9")..TrackMeter("0003+0"), md1, null),
            SegmentCsvMetaDataRange(TrackMeter("0003+0")..TrackMeter("0003+333.3"), md1, null),
            emptyCsvMetaData(TrackMeter("0003+333.3")..TrackMeter("0004+0")),
            emptyCsvMetaData(TrackMeter("0004+0")..TrackMeter("0004+444.4")),
            SegmentCsvMetaDataRange(TrackMeter("0004+444.4")..TrackMeter("0004+555.5"), md2, null),
            emptyCsvMetaData(TrackMeter("0004+555.5")..TrackMeter("0005+0")),
            emptyCsvMetaData(TrackMeter("0005+0")..TrackMeter("0005+555.5")),
            SegmentCsvMetaDataRange(TrackMeter("0005+555.5")..TrackMeter("0006+0"), md3, null),
            SegmentCsvMetaDataRange(TrackMeter("0006+0")..TrackMeter("0006+666.6"), md3, null),
            emptyCsvMetaData(TrackMeter("0006+666.6")..TrackMeter("0007+0")),
            emptyCsvMetaData(TrackMeter("0007+0")..TrackMeter("0007+999.999")),
        )
        assertEquals(expected, segments)
    }

    @Test
    fun segmentingRangesWorksWithAlignmentSwitchLinks() {
        val start = AddressPoint(point(1), TrackMeter("0002+000.0"))
        val end = AddressPoint(point(2), TrackMeter("0006+999.999"))

        val sl1 = switchLink(1, TrackMeter("0002+123.0"), TrackMeter("0002+223.0"))
        val sl2 = switchLink(
            2,
            TrackMeter("0003+999.9"),
            TrackMeter("0004+111.1"),
            TrackMeter("0004+555.5"),
        )
        val sl3 = switchLink(3, TrackMeter("0006+111.1"), TrackMeter("0006+222.2"))

        val segments = segmentCsvMetadata<LocationTrack>(listOf(start, end), listOf(), listOf(sl1, sl2, sl3))
        val expected = listOf<SegmentCsvMetaDataRange<LocationTrack>>(
            emptyCsvMetaData(TrackMeter("0002+000.0")..TrackMeter("0002+123.0")),
            SegmentCsvMetaDataRange(TrackMeter("0002+123.0")..TrackMeter("0002+223.0"), null, sl1),
            emptyCsvMetaData(TrackMeter("0002+223.0")..TrackMeter("0003+000")),
            emptyCsvMetaData(TrackMeter("0003+000")..TrackMeter("0003+999.9")),
            SegmentCsvMetaDataRange(TrackMeter("0003+999.9")..TrackMeter("0004+000"), null, sl2),
            SegmentCsvMetaDataRange(TrackMeter("0004+000")..TrackMeter("0004+111.1"), null, sl2),
            SegmentCsvMetaDataRange(TrackMeter("0004+111.1")..TrackMeter("0004+555.5"), null, sl2),
            emptyCsvMetaData(TrackMeter("0004+555.5")..TrackMeter("0005+000")),
            emptyCsvMetaData(TrackMeter("0005+000")..TrackMeter("0006+000")),
            emptyCsvMetaData(TrackMeter("0006+000")..TrackMeter("0006+111.1")),
            SegmentCsvMetaDataRange(TrackMeter("0006+111.1")..TrackMeter("0006+222.2"), null, sl3),
            emptyCsvMetaData(TrackMeter("0006+222.2")..TrackMeter("0006+999.999")),
        )
        assertEquals(expected, segments)
    }

    @Test
    fun segmentingRangesWorksWithFullCsvMetadata() {
        val start = AddressPoint(point(1), TrackMeter("0001+000.000"))
        val end = AddressPoint(point(2), TrackMeter("0003+000.000"))

        val md1 = elementCsvMetaData(1, TrackMeter("0001+100.0"), TrackMeter("0002+300.0"))
        val md2 = elementCsvMetaData(2, TrackMeter("0002+950.0"), TrackMeter("0003+000.0"))

        val sl1 = switchLink(3, TrackMeter("0001+200.0"), TrackMeter("0001+300.0"))
        val sl2 = switchLink(
            4,
            TrackMeter("0002+100.0"),
            TrackMeter("0002+200.0"),
            TrackMeter("0002+400.0"),
            TrackMeter("0002+500.0"),
        )
        val sl3 = switchLink(4, TrackMeter("0002+800.0"), TrackMeter("0002+900.0"))

        val segments = segmentCsvMetadata(listOf(start, end), listOf(md1, md2), listOf(sl1, sl2, sl3))
        val expected = listOf(
            emptyCsvMetaData(TrackMeter("0001+000.000")..TrackMeter("0001+100.0")),
            SegmentCsvMetaDataRange(TrackMeter("0001+100.0")..TrackMeter("0001+200.0"), md1, null),
            SegmentCsvMetaDataRange(TrackMeter("0001+200.0")..TrackMeter("0001+300.0"), md1, sl1),
            SegmentCsvMetaDataRange(TrackMeter("0001+300.0")..TrackMeter("0002+000"), md1, null),
            SegmentCsvMetaDataRange(TrackMeter("0002+000")..TrackMeter("0002+100.0"), md1, null),
            SegmentCsvMetaDataRange(TrackMeter("0002+100.0")..TrackMeter("0002+200.0"), md1, sl2),
            SegmentCsvMetaDataRange(TrackMeter("0002+200.0")..TrackMeter("0002+300.0"), md1, sl2),
            SegmentCsvMetaDataRange(TrackMeter("0002+300.0")..TrackMeter("0002+400.0"), null, sl2),
            SegmentCsvMetaDataRange(TrackMeter("0002+400.0")..TrackMeter("0002+500.0"), null, sl2),
            emptyCsvMetaData(TrackMeter("0002+500.0")..TrackMeter("0002+800.0")),
            SegmentCsvMetaDataRange(TrackMeter("0002+800.0")..TrackMeter("0002+900.0"), null, sl3),
            emptyCsvMetaData(TrackMeter("0002+900.0")..TrackMeter("0002+950.0")),
            SegmentCsvMetaDataRange(TrackMeter("0002+950.0")..TrackMeter("0003+000.0"), md2, null),
        )

        assertEquals(expected, segments)
    }

    @Test
    fun segmentingRangesExpandsMetadataRangeStartBySwitchLink() {
        // points    |-------|
        // meta M1    |------| (start of this meta moves to the switch start)
        // switch S1 |---|
        //
        // expected ranges:
        // M1+S1     |---|
        // M1            |---|
        val points = listOf(
            AddressPoint(point(1), TrackMeter("0001+000.5")),
            AddressPoint(point(2), TrackMeter("0001+001.0")),
            AddressPoint(point(3), TrackMeter("0001+002.0")),
            AddressPoint(point(4), TrackMeter("0001+003.0")),
            AddressPoint(point(6), TrackMeter("0001+004.0")),
        )

        val md1 = alignmentCsvMetaData(123, points[1], points[4])
        val expandedMd1 = fullElementCsvMetadata(md1).copy(
            startMeter = TrackMeter("0001+000.5")
        )

        val sl1 = switchLink(3, TrackMeter("0001+000.5"), TrackMeter("0001+002.0"))

        val segments = combineMetadataToSegments(
            listOf(sl1),
            listOf(md1),
            points,
            listOf(),
        )
        val expected = listOf(
            SegmentCsvMetaDataRange(TrackMeter("0001+000.5")..TrackMeter("0001+002.0"), expandedMd1, sl1),
            SegmentCsvMetaDataRange(
                TrackMeter("0001+002.0")..TrackMeter("0001+004.0"),
                expandedMd1,
                null
            ),
        )

        assertEquals(expected, segments)
    }

    @Test
    fun alignmentMetadataIsAdjustedForConsistency() {
        val start = TrackMeter(1, 0.2, 1)
        val end = TrackMeter(1, 8.6, 1)

        // Not from start -> will be adjusted to start
        val md1 = alignmentCsvMetaData(1, TrackMeter(1, 1), TrackMeter(1, 3))
        // Overlaps previous -> will be adjusted to match previous end
        val md2 = alignmentCsvMetaData(2, TrackMeter(1, 2), TrackMeter(1, 5))
        // Redundant after adjustments -> will be removed
        val md3 = alignmentCsvMetaData(3, TrackMeter(1, 4), TrackMeter(1, 5))
        // Not until end -> will be adjusted to end
        val md4 = alignmentCsvMetaData(4, TrackMeter(1, 5), TrackMeter(1, 8))

        val adjusted = validateAndAdjustAlignmentCsvMetaData(start, end, listOf(md1, md2, md3, md4))
        val expected = listOf(
            md1.copy(startMeter = start),
            md2.copy(startMeter = md1.endMeter),
            md4.copy(endMeter = end),
        )
        assertEquals(expected, adjusted)
    }

    @Test
    fun alignmentMetadataIsNotAdjustedOver1m() {
        val start = TrackMeter(1, 1.2, 1)
        val end = TrackMeter(1, 8.6, 1)

        // Not a rounding error compared to start -> won't adjust
        val md1 = alignmentCsvMetaData(1, TrackMeter(1, 3), TrackMeter(1, 4))
        // Not a rounding error compared to end -> won't adjust
        val md2 = alignmentCsvMetaData(1, TrackMeter(1, 4), TrackMeter(1, 7))

        val adjusted = validateAndAdjustAlignmentCsvMetaData(start, end, listOf(md1, md2))
        assertEquals(listOf(md1, md2), adjusted)
    }

    @Test
    fun elementMetadataRangesExpandsBySwitchLink() {
        // points    |-------|
        // meta M1   |------| (end of this meta moves to the S1 end)
        // meta M2          |------| (start of this meta moves to the S1 end and end to S2 start)
        // switch S1     |---|
        // switch S2                |---|

        // expected metadata:
        // M1        |-------|
        // M2                |------|

        // Should result in segments:
        // M1        |---|
        // M1+S1         |---|
        // M2                |------|
        // S2                       |---|

        val points = listOf(
            AddressPoint(point(2), TrackMeter("0001+001.0")),
            AddressPoint(point(3), TrackMeter("0001+002.0")),
            AddressPoint(point(4), TrackMeter("0001+003.0")),
            AddressPoint(point(6), TrackMeter("0001+004.0")),
            AddressPoint(point(1), TrackMeter("0001+004.5")),
            AddressPoint(point(7), TrackMeter("0001+005.0")),
            AddressPoint(point(8), TrackMeter("0001+006.0")),
            AddressPoint(point(9), TrackMeter("0001+007.0")),
            AddressPoint(point(10), TrackMeter("0001+007.5")),
            AddressPoint(point(11), TrackMeter("0001+008.0")),
            AddressPoint(point(12), TrackMeter("0001+009.0")),
            AddressPoint(point(13), TrackMeter("0001+009.5")),
        )

        val md1 = elementCsvMetaData(1, TrackMeter("0001+001.0"), TrackMeter("0001+004.0"))
        val md2 = elementCsvMetaData(1, TrackMeter("0001+004.0"), TrackMeter("0001+007.0"))

        val sl1 = switchLink(3, TrackMeter("0001+003.0"), TrackMeter("0001+004.5"))
        val sl2 = switchLink(4, TrackMeter("0001+007.5"), TrackMeter("0001+009.5"))

        val expandedMd1 = md1.copy(endMeter = sl1.endMeter)
        val expandedMd2 = md2.copy(startMeter = sl1.endMeter, endMeter = sl2.startMeter)

        val adjustedMetadata = adjustMetadataToSwitchLinks(listOf(md1, md2), listOf(sl1, sl2))
        assertEquals(listOf(expandedMd1, expandedMd2), adjustedMetadata)

        val segments = segmentCsvMetadata(
            points,
            listOf(expandedMd1, expandedMd2),
            listOf(sl1, sl2)
        )
        val expected = listOf(
            SegmentCsvMetaDataRange(md1.startMeter..sl1.startMeter, expandedMd1, null),
            SegmentCsvMetaDataRange(sl1.startMeter..sl1.endMeter, expandedMd1, sl1),
            SegmentCsvMetaDataRange(sl1.endMeter..sl2.startMeter, expandedMd2, null),
            SegmentCsvMetaDataRange(sl2.startMeter..sl2.endMeter, null, sl2),
        )

        assertEquals(expected, segments)
    }

    @Test
    fun segmentingRangesExpandsMetadataRangeEndsBySwitchLinks() {
        // points    |----------|
        // meta M1    |------|
        // switch S1 |---|
        // switch S2        |---|
        //
        // expected ranges:
        // M1+S1     |---|
        // M1            |--|
        // M1+S2            |---|
        val points = listOf(
            AddressPoint(point(1), TrackMeter("0001+000.5")),
            AddressPoint(point(2), TrackMeter("0001+001.0")),
            AddressPoint(point(3), TrackMeter("0001+002.0")),
            AddressPoint(point(4), TrackMeter("0001+003.0")),
            AddressPoint(point(6), TrackMeter("0001+004.0")),
            AddressPoint(point(7), TrackMeter("0001+005.0")),
            AddressPoint(point(8), TrackMeter("0001+006.0")),
            AddressPoint(point(9), TrackMeter("0001+007.0")),
            AddressPoint(point(10), TrackMeter("0001+007.5")),
        )

        val md1 = alignmentCsvMetaData(123, points[1], points[7])
        val expandedMd1 = fullElementCsvMetadata(md1).copy(
            startMeter = TrackMeter("0001+000.5"),
            endMeter = TrackMeter("0001+007.5")
        )

        val sl1 = switchLink(1, TrackMeter("0001+000.5"), TrackMeter("0001+002.0"))
        val sl2 = switchLink(2, TrackMeter("0001+005.0"), TrackMeter("0001+007.5"))

        val segments = combineMetadataToSegments(
            listOf(sl1, sl2),
            listOf(md1),
            points,
            listOf(),
        )
        val expected = listOf(
            SegmentCsvMetaDataRange(TrackMeter("0001+000.5")..TrackMeter("0001+002.0"), expandedMd1, sl1),
            SegmentCsvMetaDataRange(
                TrackMeter("0001+002.0")..TrackMeter("0001+005.0"),
                expandedMd1,
                null
            ),
            SegmentCsvMetaDataRange(TrackMeter("0001+005.0")..TrackMeter("0001+007.5"), expandedMd1, sl2),
        )

        assertEquals(expected, segments)
    }

    @Test
    fun segmentingRangesWorksWithSinglePointSwitchAtStart() {
        // points    |-------|
        // meta M1     |-----|
        // switch S1 |
        //
        // expected ranges:
        // S1        ||
        // -          ||
        // M1          |-----|

        val points = listOf(
            AddressPoint(point(1), TrackMeter("0001+000.0")),
            AddressPoint(point(2), TrackMeter("0001+001.0")),
            AddressPoint(point(3), TrackMeter("0001+002.0")),
            AddressPoint(point(4), TrackMeter("0001+003.0")),
            AddressPoint(point(6), TrackMeter("0001+004.0")),
            AddressPoint(point(7), TrackMeter("0001+005.0")),
        )

        val md1 = elementCsvMetaData(1, TrackMeter("0001+002.0"), TrackMeter("0001+005.0"))

        val sl1 = switchLink(1, TrackMeter("0001+000.0"))

        val segments = segmentCsvMetadata(
            points,
            listOf(md1),
            listOf(sl1)
        )
        val expected = listOf(
            SegmentCsvMetaDataRange(TrackMeter("0001+000.0")..TrackMeter("0001+001.0"), null, sl1),
            SegmentCsvMetaDataRange(TrackMeter("0001+001.0")..TrackMeter("0001+002.0"), null, null),
            SegmentCsvMetaDataRange(TrackMeter("0001+002.0")..TrackMeter("0001+005.0"), md1, null),
        )

        assertEquals(expected, segments)
    }

    @Test
    fun segmentingRangesWorksWithSinglePointSwitchAtEnd() {
        // points    |--------|
        // meta M1   |------|
        // switch S1          |
        //
        // expected ranges:
        // M1        |------|
        // -                ||
        // S1                ||
        val points = listOf(
            AddressPoint(point(1), TrackMeter("0001+000.0")),
            AddressPoint(point(2), TrackMeter("0001+001.0")),
            AddressPoint(point(3), TrackMeter("0001+002.0")),
            AddressPoint(point(4), TrackMeter("0001+003.0")),
            AddressPoint(point(6), TrackMeter("0001+004.0")),
            AddressPoint(point(7), TrackMeter("0001+005.0")),
        )

        val md1 = elementCsvMetaData(1, TrackMeter("0001+000.0"), TrackMeter("0001+003.0"))

        val sl1 = switchLink(1, TrackMeter("0001+005.0"))

        val segments = segmentCsvMetadata(
            points,
            listOf(md1),
            listOf(sl1)
        )
        val expected = listOf(
            SegmentCsvMetaDataRange(TrackMeter("0001+000.0")..TrackMeter("0001+003.0"), md1, null),
            SegmentCsvMetaDataRange(TrackMeter("0001+003.0")..TrackMeter("0001+004.0"), null, null),
            SegmentCsvMetaDataRange(TrackMeter("0001+004.0")..TrackMeter("0001+005.0"), null, sl1),
        )

        assertEquals(expected, segments)
    }

    @Test
    fun segmentingRangesWorksWithSinglePointSwitchAtMiddle() {
        // points    |---------|
        // meta M1   |------|
        // switch S1     |
        //
        // expected ranges:
        // M1        |---|
        // M1+S1         ||
        // M1             |-|
        // empty            |--|
        val points = listOf(
            AddressPoint(point(1), TrackMeter("0001+000.0")),
            AddressPoint(point(2), TrackMeter("0001+001.0")),
            AddressPoint(point(3), TrackMeter("0001+002.0")),
            AddressPoint(point(4), TrackMeter("0001+003.0")),
            AddressPoint(point(6), TrackMeter("0001+004.0")),
            AddressPoint(point(7), TrackMeter("0001+005.0")),
            AddressPoint(point(8), TrackMeter("0001+006.0")),
            AddressPoint(point(9), TrackMeter("0001+007.0")),
        )

        val md1 = elementCsvMetaData(1, TrackMeter("0001+000.0"), TrackMeter("0001+005.0"))

        val sl1 = switchLink(1, TrackMeter("0001+003.0"))

        val segments = segmentCsvMetadata(
            points,
            listOf(md1),
            listOf(sl1)
        )
        val expected = listOf(
            SegmentCsvMetaDataRange(TrackMeter("0001+000.0")..TrackMeter("0001+003.0"), md1, null),
            SegmentCsvMetaDataRange(TrackMeter("0001+003.0")..TrackMeter("0001+004.0"), md1, sl1),
            SegmentCsvMetaDataRange(TrackMeter("0001+004.0")..TrackMeter("0001+005.0"), md1, null),
            SegmentCsvMetaDataRange(TrackMeter("0001+005.0")..TrackMeter("0001+007.0"), null, null),
        )

        assertEquals(expected, segments)
    }


    @Test
    fun dividePointsToSegmentsWorks() {
        val points = listOf(
            AddressPoint(point(1), TrackMeter("0000+0.0")),
            AddressPoint(point(2), TrackMeter("0000+4.0")),
            AddressPoint(point(3), TrackMeter("0000+9.0")),
            AddressPoint(point(4), TrackMeter("0000+11.0")),
            AddressPoint(point(5), TrackMeter("0000+15.0")),
            AddressPoint(point(6), TrackMeter("0000+20.0")),
            AddressPoint(point(7), TrackMeter("0000+25.0")),
            AddressPoint(point(8), TrackMeter("0000+30.0")),
            AddressPoint(point(9), TrackMeter("0000+31.0")),
        )
        val md0 = emptyCsvMetaData<LocationTrack>(TrackMeter("0000+0.0")..TrackMeter("0000+1.0"))
        val md1 = someSegmentCsvMetaData(TrackMeter("0000+1.0")..TrackMeter("0000+10.0"), 1)
        val md2 = someSegmentCsvMetaData(TrackMeter("0000+10.0")..TrackMeter("0000+20.0"), 2)
        val md3 = someSegmentCsvMetaData(TrackMeter("0000+20.0")..TrackMeter("0000+30.0"), 3)
        val md4 = emptyCsvMetaData<LocationTrack>(TrackMeter("0000+30.0")..TrackMeter("0000+31.0"))
        val divided = dividePointsToSegments(points, listOf(md0, md1, md2, md3, md4), setOf())
        assertEquals(
            listOf(
                points.slice(0..1).map(AddressPoint::point) to SegmentFullMetaDataRange(md0, false),
                points.slice(1..3).map(AddressPoint::point) to SegmentFullMetaDataRange(md1, false),
                points.slice(3..5).map(AddressPoint::point) to SegmentFullMetaDataRange(md2, false),
                points.slice(5..7).map(AddressPoint::point) to SegmentFullMetaDataRange(md3, false),
                points.slice(7..8).map(AddressPoint::point) to SegmentFullMetaDataRange(md4, false),
            ), divided
        )
    }

    @Test
    fun dividePointSegmentsSkipsOverEmptyRanges() {
        val points = listOf(
            AddressPoint(point(1), TrackMeter("0000+0.0")),
            AddressPoint(point(2), TrackMeter("0000+1.0")),
            AddressPoint(point(3), TrackMeter("0000+2.0")),
            AddressPoint(point(4), TrackMeter("0000+3.0")),
            AddressPoint(point(5), TrackMeter("0000+11.0")),
            AddressPoint(point(6), TrackMeter("0000+12.0")),
            AddressPoint(point(7), TrackMeter("0000+13.0")),
        )

        val md1 = someSegmentCsvMetaData(TrackMeter("0000+0.0")..TrackMeter("0000+3.0"), 1)
        val mdPartial = someSegmentCsvMetaData(TrackMeter("0000+3.0")..TrackMeter("0000+5.0"), 2)
        val mdEmpty1 = someSegmentCsvMetaData(TrackMeter("0000+5.0")..TrackMeter("0000+8.0"), 3)
        val mdEmpty2 = someSegmentCsvMetaData(TrackMeter("0000+8.0")..TrackMeter("0000+10.0"), 4)
        val md2 = someSegmentCsvMetaData(TrackMeter("0000+10.0")..TrackMeter("0000+13.0"), 9)

        val divided = dividePointsToSegments(points, listOf(md1, mdPartial, mdEmpty1, mdEmpty2, md2), setOf())
        assertEquals(
            listOf(
                points.slice(0..3).map(AddressPoint::point) to SegmentFullMetaDataRange(md1, false),
                points.slice(3..4).map(AddressPoint::point) to SegmentFullMetaDataRange(mdPartial, false),
                points.slice(4..6).map(AddressPoint::point) to SegmentFullMetaDataRange(md2, false),
            ), divided
        )
    }

    @Test
    fun getFilteredIndicesFavorsForwardFilteringWhenEitherWayWorks() {
        /*
        Filtering either 1 or 2 is equally good -> favor forward filtering (remove index 2)
        0   1
           2   3
        */
        assertEquals(
            listOf(2..2),
            calculateFilteredIndices(
                Point(0.0, 0.0),
                Point(1.0, 0.0),
                Point(0.75, 0.25),
                Point(1.75, 0.25),
            ),
        )
    }

    @Test
    fun getFilteredIndicesAllowsAngles0to45() {
        /*
        Filtering either 1 or 2 is equally good -> favor forward filtering (remove index 2)
        0 1      4 5
              2 3  67
        */
        assertEquals(
            listOf(6..6),
            calculateFilteredIndices(
                Point(0.0, 0.0),
                Point(2.0, 0.0),
                Point(6.0, 1.0),
                Point(8.0, 1.0),
                Point(9.0, 0.0),
                Point(11.0, 0.0),
                Point(11.5, 1.0),
                Point(12.5, 1.0),
            ),
        )
    }

    @Test
    fun getFilteredIndicesSolvesAnglesThatContinueInOffDirection() {
        /*
        0 1 2 3 4 5
                  6
                  7
                  8
        */
        assertEquals(
            listOf(6..8),
            calculateFilteredIndices(
                Point(0.0, 0.0),
                Point(1.0, 0.0),
                Point(2.0, 0.0),
                Point(3.0, 0.0),
                Point(4.0, 0.0),
                Point(5.0, 0.0),
                Point(5.0, 1.0),
                Point(5.0, 2.0),
                Point(5.0, 3.0),
            ),
        )

        /*
        0 1 2
            3
            4
            5
            6
        */
        assertEquals(
            listOf(0..1),
            calculateFilteredIndices(
                Point(0.0, 0.0),
                Point(1.0, 0.0),
                Point(2.0, 0.0),
                Point(2.0, 1.0),
                Point(2.0, 2.0),
                Point(2.0, 3.0),
                Point(2.0, 4.0),
            ),
        )
    }

    @Test
    fun getFilteredIndicesSolvesDoubleCrossings() {
        /*
        0 1 2 3 4

             5

              6

              7

              8

              9
        */
        assertEquals(
            listOf(2..4),
            calculateFilteredIndices(
                Point(0.0, 0.0), // 0
                Point(1.0, 0.0), // 1
                Point(2.0, 0.0), // 2
                Point(3.0, 0.0), // 3
                Point(4.0, 0.0), // 4
                Point(2.5, 1.0), // 5
                Point(3.0, 2.0), // 6
                Point(3.0, 3.0), // 7
                Point(3.0, 4.0), // 8
                Point(3.0, 5.0), // 9
            ),
        )

        /*
        0 1 7 8 9

             2

              3

              4

              5

              6
        */
        assertEquals(
            listOf(7..9),
            calculateFilteredIndices(
                Point(0.0, 0.0), // 0
                Point(1.0, 0.0), // 1
                Point(2.5, 1.0), // 2
                Point(3.0, 2.0), // 3
                Point(3.0, 3.0), // 4
                Point(3.0, 4.0), // 5
                Point(3.0, 5.0), // 6
                Point(2.0, 0.0), // 7
                Point(3.0, 0.0), // 8
                Point(4.0, 0.0), // 9
            ),
        )

        /*
             3

        0 1 2

             4

               5

                 6
        */
        assertEquals(
            listOf(0..2),
            calculateFilteredIndices(
                Point(0.0, 1.0), // 0
                Point(1.0, 1.0), // 1
                Point(2.0, 1.0), // 2
                Point(2.5, 0.0), // 3
                Point(2.5, 2.0), // 4
                Point(3.5, 3.0), // 5
                Point(3.5, 4.0), // 6
            ),
        )

        /*
               4

        0 1 2 3

               5

                 6
        */
        assertEquals(
            listOf(4..6),
            calculateFilteredIndices(
                Point(0.0, 1.0), // 0
                Point(1.0, 1.0), // 1
                Point(2.0, 1.0), // 2
                Point(3.0, 1.0), // 3
                Point(3.5, 0.0), // 4
                Point(3.5, 2.0), // 5
                Point(4.5, 3.0), // 6
            ),
        )
    }

    @Test
    fun getFilteredIndicesFavorsFilteringLessPoints() {
        /*
        Could be solved by removing [1] or [2,3] -> favor lesser filtering
        0     1
           2 3  4
         */
        assertEquals(
            listOf(1..1),
            calculateFilteredIndices(
                Point(0.0, 0.0),
                Point(2.0, 0.0),
                Point(0.75, 0.25),
                Point(1.75, 1.25),
                Point(2.75, 2.25),
            ),
        )
        /*
        Could be solved by removing [1,2] or [3,4,5] -> favor lesser filtering
        0   1   2
           3 4 5  6
         */
        assertEquals(
            listOf(1..2),
            calculateFilteredIndices(
                Point(0.0, 0.0),
                Point(1.0, 0.0),
                Point(2.0, 0.0),
                Point(0.75, 0.25),
                Point(1.25, 0.25),
                Point(1.75, 0.25),
                Point(2.25, 0.25),
            ),
        )
        /*
        Could be solved by removing [1,2] or [3] -> favor lesser filtering
        0   1 2
             3  4
         */
        assertEquals(
            listOf(3..3),
            calculateFilteredIndices(
                Point(0.0, 0.0),
                Point(1.0, 0.0),
                Point(1.5, 0.0),
                Point(1.25, 0.25),
                Point(2.0, 0.25),
            ),
        )
    }

    @Test
    fun getFilteredIndicesFindsMultipleIssuesInSameLine() {
        /*
        0    1  2    6  7    8
               3  4   5   9 A
                               B
         */
        assertEquals(
            listOf(3..3, 6..6, 8..8),
            calculateFilteredIndices(
                Point(0.0, 0.0), // 0
                Point(1.0, 0.0), // 1
                Point(1.5, 0.0), // 2
                Point(1.25, 0.25), // 3
                Point(2.0, 0.25), // 4
                Point(3.0, 0.25), // 5
                Point(2.75, 0.0), // 6
                Point(3.5, 0.0), // 7
                Point(4.75, 0.0), // 8
                Point(4.00, 0.25), // 9
                Point(4.5, 0.25), // A
                Point(5.25, 0.5), // B
            ),
        )
    }

    @Test
    fun getFilteredIndicesCanFilterEndPoints() {
        /*
         0
        1 2 3
         */
        assertEquals(
            listOf(0..0),
            calculateFilteredIndices(
                Point(0.25, 0.0),
                Point(0.0, 0.1),
                Point(0.5, 0.1),
                Point(1.0, 0.1),
            ),
        )
        /*
           0
        1 2 3
         */
        assertEquals(
            listOf(0..0),
            calculateFilteredIndices(
                Point(0.75, 0.0),
                Point(0.0, 0.1),
                Point(0.5, 0.1),
                Point(1.0, 0.1),
            ),
        )
        /*
        0 1 2
           3
         */
        assertEquals(
            listOf(3..3),
            calculateFilteredIndices(
                Point(0.0, 0.0),
                Point(0.5, 0.0),
                Point(1.0, 0.0),
                Point(0.75, 0.1),
            ),
        )
    }

    @Test
    fun getFilteredIndicesCanFilterMultipleEndPoints() {
        /*
         0   1
        2 3 4
        */
        assertEquals(
            listOf(0..1),
            calculateFilteredIndices(
                Point(0.25, 0.0),
                Point(1.25, 0.0),
                Point(0.0, 0.1),
                Point(0.5, 0.1),
                Point(1.0, 0.1),
            ),
        )

        /*
         1 0
        2 3 4 5
        */
        assertEquals(
            listOf(0..1),
            calculateFilteredIndices(
                Point(0.75, 0.0),
                Point(0.25, 0.0),
                Point(0.0, 0.1),
                Point(0.5, 0.1),
                Point(1.0, 0.1),
                Point(1.5, 0.1),
            ),
        )

        /*
         0 1 2
          3 4
         */
        assertEquals(
            listOf(3..4),
            calculateFilteredIndices(
                Point(0.0, 0.0),
                Point(0.5, 0.0),
                Point(1.0, 0.0),
                Point(0.25, 0.1),
                Point(0.75, 0.1),
            ),
        )

        /*
         0 1 2
          4 3
         */
        assertEquals(
            listOf(3..4),
            calculateFilteredIndices(
                Point(0.0, 0.0),
                Point(0.5, 0.0),
                Point(1.0, 0.0),
                Point(0.75, 0.1),
                Point(0.25, 0.1),
            ),
        )
    }

    @Test
    fun getFilteredIndicesCanFilterDistantPoints() {
        /*
        0 1   3 4
        <long distance>
            2
         */
        assertEquals(
            listOf(2..2),
            calculateFilteredIndices(
                Point(0.0, 0.0),
                Point(0.25, 0.0),
                Point(0.5, 50.0),
                Point(0.75, 0.0),
                Point(1.0, 0.0),
            ),
        )
    }

    @Test
    fun getFilteredIndicesCanFilterWrongDirectionSegments() {
        /*
        0 1 2 3 7 8
        6 5 4
         */
        assertEquals(
            listOf(4..6),
            calculateFilteredIndices(
                Point(0.0, 0.0),
                Point(0.5, 0.0),
                Point(1.0, 0.0),
                Point(1.5, 0.0),
                Point(1.0, 0.1),
                Point(0.5, 0.1),
                Point(0.0, 0.1),
                Point(2.0, 0.0),
                Point(2.5, 0.0),
            ),
        )

    }

    @Test
    fun getFilteredIndicesGivesSaneResultsForHardCases() {
        // The algorithm works poorly if the convoluted segment is longer than one side of the good parts
        // It's only meant to resolve simple cases, though, so it suffices if the result is sane

        /*
        Best would be to filter 2..4, but it resolves as 2 issues and ends keeping line 4->9
        0    1 5 6 7 8 9
        4 3 2
         */
        assertEquals(
            listOf(0..0, 0..3),
            calculateFilteredIndices(
                Point(0.0, 0.0),
                Point(1.25, 0.0),
                Point(1.0, 0.1),
                Point(0.5, 0.1),
                Point(0.0, 0.1),
                Point(1.75, 0.0),
                Point(2.25, 0.0),
                Point(2.75, 0.0),
                Point(3.25, 0.0),
                Point(3.75, 0.0),
            ),
        )

        /*
        Best would be to filter 2..4, but it resolves as 2 issues and ends keeping the backwards line 1-4
        0    1 5 6
        4 3 2
         */
        assertEquals(
            listOf(0..0, 5..6),
            calculateFilteredIndices(
                Point(0.0, 0.0),
                Point(1.25, 0.0),
                Point(1.0, 0.1),
                Point(0.5, 0.1),
                Point(0.0, 0.1),
                Point(1.75, 0.0),
                Point(2.25, 0.0),
            ),
        )
    }

    @Test
    fun discoverConnectionSegment() {
        val basePoint = Point(25.0, 60.0)
        val (points, connectionSegmentIndices) = toAddressPoints(
            "foo", 1, listOf(
                /*
                track chicane like:
                     2-3
                    /
                 0-1
                */
                basePoint + Point(0.0, 0.0),
                basePoint + Point(1.0, 0.0),
                basePoint + Point(1.6, 0.5),
                basePoint + Point(2.6, 0.5)
            ),
            (0..3).map { s -> TrackMeter("0000+$s.0") }
        )
        assertEquals(4, points.size)
        assertEquals(listOf(2), connectionSegmentIndices)

        val (segments, _) = createSegments(
            listOf(emptySegmentMetadata(points)),
            points,
            100,
            connectionSegmentIndices,
        )
        assertEquals(3, segments.size)
        assertEquals(GeometrySource.IMPORTED, segments[0].source)
        assertEquals(2, segments[0].points.size)
        assertEquals(GeometrySource.GENERATED, segments[1].source)
        assertEquals(2, segments[1].points.size)
        assertEquals(GeometrySource.IMPORTED, segments[2].source)
        assertEquals(2, segments[2].points.size)
    }

    @Test
    fun discoverMultipleConnectionSegments() {
        val basePoint = Point(25.0, 60.0)
        val (points, connectionSegmentIndices) = toAddressPoints(
            "foo", 1, listOf(
                /*
                complex wonky multi-turn like:
                                9
                               /
                              8
                              |
                              7
                             /
                        4-5-6
                       /
                    2-3
                   /
                0-1

                1-2, 2-3, 3-4 and 7-8 are the point pairs that fulfill the conditions for detecting connection segments

                 */
                basePoint + Point(0.0, 0.0), // 0
                // duplicate points should get dropped first
                basePoint + Point(0.0000000001, 0.0),
                basePoint + Point(1.0, 0.0), // 1
                basePoint + Point(1.5, 0.4), // 2
                basePoint + Point(3.0, 0.4), // 3
                basePoint + Point(3.5, 0.8), // 4
                basePoint + Point(4.2, 0.8), // 5
                basePoint + Point(5.0, 0.8), // 6
                basePoint + Point(5.5, 1.2), // 7
                basePoint + Point(5.6, 1.8), // 8
                basePoint + Point(6.1, 2.2), // 9
            ),
            (0..10).map { s -> TrackMeter("0000+$s.0") }
        )
        assertEquals(10, points.size)
        assertEquals(listOf(2, 3, 4, 8), connectionSegmentIndices)

        val (segments, _) = createSegments(listOf(emptySegmentMetadata(points)), points, 100, connectionSegmentIndices)
        assertEquals(7, segments.size)
        assertEquals(listOf(
            GeometrySource.IMPORTED,
            GeometrySource.GENERATED,
            GeometrySource.GENERATED,
            GeometrySource.GENERATED,
            GeometrySource.IMPORTED,
            GeometrySource.GENERATED,
            GeometrySource.IMPORTED
        ), segments.map { segment -> segment.source })
        assertEquals(listOf(2, 2, 2, 2, 4, 2, 2), segments.map { segment -> segment.points.size })
    }

    @Test
    fun switchLinkTrackMeterRangesMakesShortSegmentsForSinglePointLinks() {
        val singlePointLinks = listOf(
            switchLink(1, TrackMeter(1, 0)),
            switchLink(2, TrackMeter(1, 999))
        )
        val bigMetadataRange = TrackMeter(0, 0)..TrackMeter(1, 999)
        val allTrackMeters = (0..1).flatMap { km -> (0..999).map { m -> TrackMeter(km, m) } }
        val ranges = getSwitchLinkTrackMeterRanges(singlePointLinks, listOf(bigMetadataRange), allTrackMeters)
            .keys.toList()
        assertEquals(TrackMeter(1, 0)..TrackMeter(1, 1), ranges[0])
        assertEquals(TrackMeter(1, 998)..TrackMeter(1, 999), ranges[1])
    }

    @Test
    fun dividePointsToSegmentsSplitsMetadataRanges() {
        val basePoint = Point(25.0, 60.0)
        val (points, _) = toAddressPoints(
            "foo", 1, (0..5).map { num -> basePoint + Point(num.toDouble(), 0.0) },
            (0..5).map { s -> TrackMeter(0, s) }
        )
        val metadata = someSegmentCsvMetaData(points[0].trackMeter..points[5].trackMeter, 10)
        val segmentRanges: List<SegmentCsvMetaDataRange<LocationTrack>> = listOf(metadata)
        // a single connection segment that *ends* at index 2
        val connectionSegmentIndices = setOf(2)

        val r = dividePointsToSegments(points, segmentRanges, connectionSegmentIndices)
        assertEquals(TrackMeter(0, 0)..TrackMeter(0, 1), r[0].second.metadata.meters)
        assertEquals(TrackMeter(0, 1)..TrackMeter(0, 2), r[1].second.metadata.meters)
        assertEquals(TrackMeter(0, 2)..TrackMeter(0, 5), r[2].second.metadata.meters)
    }

    private fun calculateFilteredIndices(vararg points: IPoint): List<ClosedRange<Int>>? =
        getFilteredIndices(
            logId = "test",
            resolution = 1,
            points = points.toList(),
            addresses = (1..points.size).map { i -> TrackMeter(KmNumber(1), i) },
        )

    private fun point(seed: Int) = Point3DM(seed.toDouble(), seed.toDouble(), seed.toDouble())

    private fun someSegmentCsvMetaData(meters: ClosedRange<TrackMeter>, seed: Int) = SegmentCsvMetaDataRange(
        meters,
        noElementsCsvMetadata(alignmentCsvMetaData(seed, meters.start, meters.endInclusive)),
        switchLink(seed, meters.start, meters.endInclusive),
    )

    private fun emptySegmentMetadata(points: List<AddressPoint>) =
        SegmentCsvMetaDataRange<LocationTrack>(
            points.first().trackMeter..points.last().trackMeter,
            null,
            null,
        )

    private fun elementCsvMetaData(seed: Int, start: TrackMeter, end: TrackMeter) =
        noElementsCsvMetadata(alignmentCsvMetaData(seed, start, end))

    private fun <T> fullElementCsvMetadata(alignmentMetaData: AlignmentCsvMetaData<T>): ElementCsvMetadata<T> =
        if (alignmentMetaData.geometry!!.elements.size != 1) throw IllegalStateException("Bad test data")
        else ElementCsvMetadata(
            metadataId = alignmentMetaData.id!!,
            startMeter = alignmentMetaData.startMeter,
            endMeter = alignmentMetaData.endMeter,
            createdYear = alignmentMetaData.createdYear,
            geometryElement = alignmentMetaData.geometry!!.elements.first(),
            geometrySrid = alignmentMetaData.geometry!!.coordinateSystemSrid,
        )

    private fun alignmentCsvMetaData(
        seed: Int,
        start: AddressPoint,
        end: AddressPoint,
    ) = alignmentCsvMetaData(seed, start.trackMeter, end.trackMeter, alignmentImportGeometry(start.point, end.point))

    private fun alignmentImportGeometry(start: IPoint, end: IPoint) = AlignmentImportGeometry(
        IntId(1),
        LAYOUT_SRID,
        listOf(line(start.toPoint(), end.toPoint())),
    )

    private fun alignmentCsvMetaData(
        seed: Int,
        start: TrackMeter,
        end: TrackMeter,
        geometry: AlignmentImportGeometry? = null,
    ): AlignmentCsvMetaData<LocationTrack> {
        val rand = Random(seed)
        return AlignmentCsvMetaData(
            id = IntId(seed),
            alignmentOid = getSomeOid(rand.nextInt()),
            metadataOid = getSomeOid(rand.nextInt()),
            startMeter = start,
            endMeter = end,
            createdYear = rand.nextInt(1950, 2020),
            geometry = geometry,
            originalCrs = "OLD_CRS",
            planAlignmentName = AlignmentName("001"),
            fileName = FileName("test_file.xml"),
            measurementMethod = "TEST DATA GENERATION",
        )
    }

    private fun switchLink(seed: Int, vararg points: TrackMeter): AlignmentSwitchLink {
        val rand = Random(seed)
        var jointIdx = 0
        val linkPoints = points.map { p ->
            AlignmentSwitchLinkPoint(
                jointNumber = JointNumber(1 + (seed + jointIdx++ % 10)),
                trackMeter = p,
                location = null
            )
        }
        return AlignmentSwitchLink(
            alignmentOid = getSomeOid(rand.nextInt()),
            switchOid = getSomeOid(rand.nextInt()),
            switchId = IntId(rand.nextInt(1..1000)),
            linkPoints = linkPoints,
        )
    }
}
