package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.main
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.boundingBoxAroundPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class ReferenceLineDaoIT
@Autowired
constructor(private val alignmentDao: LayoutAlignmentDao, private val referenceLineDao: ReferenceLineDao) :
    DBTestBase() {

    @Test
    fun referenceLineSaveAndLoadWorks() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val alignment = alignment()
        val alignmentVersion = alignmentDao.insert(alignment)
        val referenceLine =
            referenceLine(trackNumberId, alignment, draft = false)
                .copy(startAddress = TrackMeter(KmNumber(10), 125.5, 3), alignmentVersion = alignmentVersion)

        assertEquals(DataType.TEMP, referenceLine.dataType)
        val (id, version) = referenceLineDao.insert(referenceLine)
        assertEquals(version, referenceLineDao.fetchVersion(MainLayoutContext.official, id))
        assertEquals(version, referenceLineDao.fetchVersion(MainLayoutContext.draft, id))
        val fromDb = referenceLineDao.fetch(version)
        assertEquals(DataType.STORED, fromDb.dataType)
        assertMatches(referenceLine, fromDb, contextMatch = false)

        val updatedLine = fromDb.copy(startAddress = TrackMeter(KmNumber(12), 321))
        val (updatedId, updatedVersion) = referenceLineDao.update(updatedLine)
        assertEquals(id, updatedId)
        assertEquals(updatedVersion.rowId, version.rowId)
        assertEquals(updatedVersion, referenceLineDao.fetchVersion(MainLayoutContext.official, id))
        assertEquals(updatedVersion, referenceLineDao.fetchVersion(MainLayoutContext.draft, id))
        val updatedFromDb = referenceLineDao.fetch(updatedVersion)
        assertEquals(DataType.STORED, updatedFromDb.dataType)
        assertMatches(updatedLine, updatedFromDb, contextMatch = false)
    }

    @Test
    fun referenceLineVersioningWorks() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val tempAlignment = alignment(segment(Point(1.0, 1.0), Point(2.0, 2.0)))
        val alignmentVersion = alignmentDao.insert(tempAlignment)
        val tempTrack =
            referenceLine(
                trackNumberId = trackNumberId,
                startAddress = TrackMeter(12, 13),
                alignment = tempAlignment,
                alignmentVersion = alignmentVersion,
                draft = false,
            )
        val (id, insertVersion) = referenceLineDao.insert(tempTrack)
        val inserted = referenceLineDao.fetch(insertVersion)
        assertMatches(tempTrack, inserted, contextMatch = false)
        assertEquals(insertVersion, referenceLineDao.fetchVersion(MainLayoutContext.official, id))
        assertEquals(insertVersion, referenceLineDao.fetchVersion(MainLayoutContext.draft, id))

        val tempDraft1 = asMainDraft(inserted).copy(startAddress = TrackMeter(2, 4))
        val draftVersion1 = referenceLineDao.insert(tempDraft1).rowVersion
        val draft1 = referenceLineDao.fetch(draftVersion1)
        assertMatches(tempDraft1, draft1, contextMatch = false)
        assertEquals(insertVersion, referenceLineDao.fetchVersion(MainLayoutContext.official, id))
        assertEquals(draftVersion1, referenceLineDao.fetchVersion(MainLayoutContext.draft, id))

        val newTempAlignment = alignment(segment(Point(2.0, 2.0), Point(4.0, 4.0)))
        val newAlignmentVersion = alignmentDao.insert(newTempAlignment)
        val tempDraft2 = draft1.copy(alignmentVersion = newAlignmentVersion, length = newTempAlignment.length)
        val draftVersion2 = referenceLineDao.update(tempDraft2).rowVersion
        val draft2 = referenceLineDao.fetch(draftVersion2)
        assertMatches(tempDraft2, draft2, contextMatch = false)
        assertEquals(insertVersion, referenceLineDao.fetchVersion(MainLayoutContext.official, id))
        assertEquals(draftVersion2, referenceLineDao.fetchVersion(MainLayoutContext.draft, id))

        referenceLineDao.deleteDraft(LayoutBranch.main, id)
        alignmentDao.deleteOrphanedAlignments()
        assertEquals(insertVersion, referenceLineDao.fetchVersion(MainLayoutContext.official, id))
        assertEquals(insertVersion, referenceLineDao.fetchVersion(MainLayoutContext.draft, id))

        assertEquals(inserted, referenceLineDao.fetch(insertVersion))
        assertEquals(draft1, referenceLineDao.fetch(draftVersion1))
        assertEquals(draft2, referenceLineDao.fetch(draftVersion2))
        assertThrows<NoSuchEntityException> { referenceLineDao.fetch(draftVersion2.next()) }
    }

    @Test
    fun `fetchVersionsNear() filters by context, bbox and includeDeleted`() {
        testDBService.clearLayoutTables()

        val design = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(design, PublicationState.OFFICIAL)
        val existingMainAndNearby =
            mainOfficialContext
                .insert(
                    referenceLineAndAlignment(
                        mainOfficialContext.createLayoutTrackNumber().id,
                        segment(Point(0.0, 0.0), Point(1.0, 0.0)),
                    )
                )
                .rowVersion
        val existingMainButDistant =
            mainOfficialContext
                .insert(
                    referenceLineAndAlignment(
                        mainOfficialContext.createLayoutTrackNumber().id,
                        segment(Point(0.0, 100.0), Point(1.0, 100.0)),
                    )
                )
                .rowVersion
        val nearbyAndMainButDeleted =
            mainOfficialContext
                .insert(
                    referenceLineAndAlignment(
                        mainOfficialContext.insert(trackNumber(TrackNumber("123"), state = LayoutState.DELETED)).id,
                        segment(Point(0.0, 0.0), Point(1.0, 0.0)),
                    )
                )
                .rowVersion
        val existingAndNearbyInDesign =
            mainOfficialContext
                .insert(
                    referenceLineAndAlignment(
                        designOfficialContext.createLayoutTrackNumber().id,
                        segment(Point(0.0, 0.0), Point(1.0, 0.0)),
                    )
                )
                .rowVersion

        assertEquals(
            listOf(existingMainAndNearby),
            referenceLineDao.fetchVersionsNear(
                mainOfficialContext.context,
                boundingBoxAroundPoint(Point(0.0, 0.0), 1.0),
            ),
        )
        assertEquals(
            listOf(existingMainButDistant),
            referenceLineDao.fetchVersionsNear(
                mainOfficialContext.context,
                boundingBoxAroundPoint(Point(0.0, 100.0), 1.0),
            ),
        )
        assertEquals(
            setOf(existingMainAndNearby, nearbyAndMainButDeleted),
            referenceLineDao
                .fetchVersionsNear(
                    mainOfficialContext.context,
                    boundingBoxAroundPoint(Point(0.0, 0.0), 1.0),
                    includeDeleted = true,
                )
                .toSet(),
        )
        assertEquals(
            setOf(existingMainAndNearby, existingAndNearbyInDesign),
            referenceLineDao
                .fetchVersionsNear(designOfficialContext.context, boundingBoxAroundPoint(Point(0.0, 0.0), 1.0))
                .toSet(),
        )
    }
}
