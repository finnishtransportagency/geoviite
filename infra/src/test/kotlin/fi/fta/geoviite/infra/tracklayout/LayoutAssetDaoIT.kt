package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.util.getInstant
import fi.fta.geoviite.infra.util.getIntId
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutAssetDaoIT
@Autowired
constructor(
    private val trackNumberService: LayoutTrackNumberService,
    private val trackNumberDao: LayoutTrackNumberDao,
) : DBTestBase() {

    @BeforeEach
    fun cleanup() {
        testDBService.clearLayoutTables()
    }

    @Test
    fun `an object's state in a design goes back to its main state upon cancellation`() {
        val designBranch = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)
        val trackNumberA = mainOfficialContext.save(trackNumber(number = TrackNumber("created in main"))).id
        designDraftContext.save(
            mainOfficialContext.fetch(trackNumberA)!!.copy(number = TrackNumber("edited in design"))
        )
        val trackNumberB = designDraftContext.save(trackNumber(number = TrackNumber("created in design"))).id

        trackNumberService.publish(designBranch, designDraftContext.fetchVersion(trackNumberA)!!)
        trackNumberService.publish(designBranch, designDraftContext.fetchVersion(trackNumberB)!!)
        val cancellationVersionA = trackNumberService.cancel(designBranch, trackNumberA)!!
        val cancellationVersionB = trackNumberService.cancel(designBranch, trackNumberB)!!
        trackNumberService.publish(designBranch, cancellationVersionA)
        trackNumberService.publish(designBranch, cancellationVersionB)

        val (officialVersionTimesA, officialVersionTimesB) =
            jdbc
                .query("select id, change_time from layout.track_number_version where not draft") { rs, _ ->
                    rs.getIntId<LayoutTrackNumber>("id") to rs.getInstant("change_time")
                }
                .sortedBy { it.second }
                .partition { (id) -> id == trackNumberA }

        assertEquals(
            3,
            officialVersionTimesA.size,
            "original publication version, edited in design version, cancellation",
        )
        assertEquals(2, officialVersionTimesB.size, "edited in design version, cancellation")

        fun a(branch: LayoutBranch, version: Int) = LayoutRowVersion(trackNumberA, branch.official, version)
        fun b(branch: LayoutBranch, version: Int) = LayoutRowVersion(trackNumberB, branch.official, version)

        // after a's creation: Only version 1 of a exists, in both main and design
        expectAllVersions(listOf(a(LayoutBranch.main, 1)), LayoutBranch.main, officialVersionTimesA[0])
        expectAllVersions(listOf(a(LayoutBranch.main, 1)), designBranch, officialVersionTimesA[0])

        // after b's creation: a has now been published in the design, and also still has a version in main as well
        expectAllVersions(listOf(a(LayoutBranch.main, 1)), LayoutBranch.main, officialVersionTimesB[0])
        expectAllVersions(listOf(a(designBranch, 1), b(designBranch, 1)), designBranch, officialVersionTimesB[0])

        // after a's cancellation: The version of a in the design goes back to main
        expectAllVersions(listOf(a(LayoutBranch.main, 1)), LayoutBranch.main, officialVersionTimesA[2])
        expectAllVersions(listOf(a(LayoutBranch.main, 1), b(designBranch, 1)), designBranch, officialVersionTimesA[2])

        // after b's cancellation: b disappears
        expectAllVersions(listOf(a(LayoutBranch.main, 1)), LayoutBranch.main, officialVersionTimesB[1])
        expectAllVersions(listOf(a(LayoutBranch.main, 1)), designBranch, officialVersionTimesB[1])
    }

    @Test
    fun `change time is scoped to effective layout contexts`() {
        val designBranch = testDBService.createDesignBranch()
        val otherDesignBranch = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)
        val designOfficialContext = testDBService.testContext(designBranch, PublicationState.OFFICIAL)
        val otherDesignDraftContext = testDBService.testContext(otherDesignBranch, PublicationState.DRAFT)

        mainOfficialContext.save(trackNumber(number = TrackNumber("main official initial")))
        val initialDesignDraftChangeTime = trackNumberService.getChangeTime(designDraftContext.context)
        val initialDesignOfficialChangeTime = trackNumberService.getChangeTime(designOfficialContext.context)
        val initialMainDraftChangeTime = trackNumberService.getChangeTime(mainDraftContext.context)

        designDraftContext.save(trackNumber(number = TrackNumber("design draft update")))
        val designDraftChangeTime = trackNumberService.getChangeTime(designDraftContext.context)
        assertNotEquals(initialDesignDraftChangeTime, designDraftChangeTime)
        assertEquals(initialDesignOfficialChangeTime, trackNumberService.getChangeTime(designOfficialContext.context))

        otherDesignDraftContext.save(trackNumber(number = TrackNumber("other design draft update")))
        mainDraftContext.save(trackNumber(number = TrackNumber("main draft update")))
        assertNotEquals(initialMainDraftChangeTime, trackNumberService.getChangeTime(mainDraftContext.context))
        assertEquals(designDraftChangeTime, trackNumberService.getChangeTime(designDraftContext.context))

        mainOfficialContext.save(trackNumber(number = TrackNumber("main official update")))
        assertNotEquals(designDraftChangeTime, trackNumberService.getChangeTime(designDraftContext.context))
    }

    @Test
    fun `design-internal fetch returns the design's own rows in any design asset state and no main rows`() {
        val designBranch = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)

        val mainOnly = mainOfficialContext.save(trackNumber(unusedNumber())).id
        val toComplete = mainOfficialContext.save(trackNumber(unusedNumber())).id
        // A design draft that was never published is not part of the design's official state
        designDraftContext.save(trackNumber(unusedNumber()))

        val open = publishInDesign(designBranch, designDraftContext.save(trackNumber(unusedNumber())))
        val cancelled =
            publishInDesign(designBranch, designDraftContext.save(trackNumber(unusedNumber()))).let { version ->
                publishInDesign(designBranch, trackNumberService.cancel(designBranch, version.id)!!)
            }
        publishInDesign(
            designBranch,
            designDraftContext.save(mainOfficialContext.fetch(toComplete)!!.copy(number = unusedNumber())),
        )
        val mainDraft = trackNumberService.mergeToMainBranch(designBranch, toComplete)
        val completed =
            publishInDesign(designBranch, trackNumberService.publish(LayoutBranch.main, mainDraft).completed!!.second)

        val now = testDBService.getDbTime()
        val expected = listOf(open, cancelled, completed)
        assertEquals(
            expected.sortedBy { it.id.intValue },
            trackNumberDao.fetchDesignInternalVersionsAtMoment(designBranch, now).sortedBy { it.id.intValue },
        )
        expected.forEach { version ->
            assertEquals(version, trackNumberDao.fetchDesignInternalVersionAtMoment(designBranch, version.id, now))
        }
        assertNull(trackNumberDao.fetchDesignInternalVersionAtMoment(designBranch, mainOnly, now))
        assertEquals(
            listOf(open, completed).sortedBy { it.id.intValue },
            trackNumberDao
                .fetchManyDesignInternalVersionsAtMoment(designBranch, listOf(open.id, completed.id, mainOnly), now)
                .sortedBy { it.id.intValue },
        )
        assertEquals(
            emptyList(),
            trackNumberDao.fetchManyDesignInternalVersionsAtMoment(designBranch, emptyList(), now),
        )
    }

    @Test
    fun `design-internal fetch at an earlier moment returns the row as it was at that moment`() {
        val designBranch = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)

        val draft = designDraftContext.save(trackNumber(unusedNumber()))
        val beforePublication = testDBService.getDbTime()
        val open = publishInDesign(designBranch, draft)
        val afterPublication = testDBService.getDbTime()
        val cancelled = publishInDesign(designBranch, trackNumberService.cancel(designBranch, draft.id)!!)
        val afterCancellation = testDBService.getDbTime()

        assertNull(trackNumberDao.fetchDesignInternalVersionAtMoment(designBranch, draft.id, beforePublication))
        assertEquals(open, trackNumberDao.fetchDesignInternalVersionAtMoment(designBranch, draft.id, afterPublication))
        assertEquals(
            cancelled,
            trackNumberDao.fetchDesignInternalVersionAtMoment(designBranch, draft.id, afterCancellation),
        )
        assertEquals(listOf(open), trackNumberDao.fetchDesignInternalVersionsAtMoment(designBranch, afterPublication))
    }

    @Test
    fun `design-internal fetch includes design rows in layout state DELETED`() {
        val designBranch = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)

        val deletedInDesign =
            publishInDesign(
                designBranch,
                designDraftContext.save(trackNumber(unusedNumber(), state = LayoutState.DELETED)),
            )

        assertEquals(
            listOf(deletedInDesign),
            trackNumberDao.fetchDesignInternalVersionsAtMoment(designBranch, testDBService.getDbTime()),
        )
    }

    @Test
    fun `design-internal fetch skips rows that were removed from the design`() {
        val designBranch = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)

        val published = publishInDesign(designBranch, designDraftContext.save(trackNumber(unusedNumber())))
        val beforeRemoval = testDBService.getDbTime()
        testDBService.delete(published)
        val afterRemoval = testDBService.getDbTime()

        assertEquals(listOf(published), trackNumberDao.fetchDesignInternalVersionsAtMoment(designBranch, beforeRemoval))
        assertEquals(emptyList(), trackNumberDao.fetchDesignInternalVersionsAtMoment(designBranch, afterRemoval))
        assertNull(trackNumberDao.fetchDesignInternalVersionAtMoment(designBranch, published.id, afterRemoval))
    }

    private fun unusedNumber() = testDBService.getUnusedTrackNumber()

    private fun publishInDesign(
        designBranch: DesignBranch,
        draft: LayoutRowVersion<LayoutTrackNumber>,
    ): LayoutRowVersion<LayoutTrackNumber> = trackNumberService.publish(designBranch, draft).published

    private fun expectAllVersions(
        expectedVersions: List<LayoutRowVersion<LayoutTrackNumber>>,
        branch: LayoutBranch,
        timePair: Pair<*, Instant>,
    ) {
        val time = timePair.second
        checkOfficialVersionsInHistory(expectedVersions, branch, time)
        checkOfficialVersionAtMoment(expectedVersions, branch, time)
        checkAllVersionsAtMoment(expectedVersions, branch, time)
    }

    private fun checkOfficialVersionsInHistory(
        expectedVersions: List<LayoutRowVersion<LayoutTrackNumber>>,
        branch: LayoutBranch,
        time: Instant,
    ) {
        val expected = expectedVersions.associateBy { v -> LayoutAssetIdInHistory(v.id, branch, time) }
        val actual =
            trackNumberDao.fetchOfficialVersionsInHistory(
                expectedVersions.map { v -> LayoutAssetIdInHistory(v.id, branch, time) }
            )
        assertEquals(expected, actual)
    }

    private fun checkOfficialVersionAtMoment(
        expectedVersions: List<LayoutRowVersion<LayoutTrackNumber>>,
        branch: LayoutBranch,
        time: Instant,
    ) {
        expectedVersions.forEach { expected ->
            val actual = trackNumberDao.fetchOfficialVersionAtMoment(branch, expected.id, time)
            assertEquals(expected, actual)
        }
    }

    private fun checkAllVersionsAtMoment(
        expectedVersions: List<LayoutRowVersion<LayoutTrackNumber>>,
        branch: LayoutBranch,
        time: Instant,
    ) {
        val expected = expectedVersions
        val actual = trackNumberDao.fetchAllOfficialVersionsAtMoment(branch, time)

        assertEquals(expected.sortedBy { it.id.intValue }, actual.sortedBy { it.id.intValue })
    }
}
