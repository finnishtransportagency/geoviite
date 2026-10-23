package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.util.getInstant
import fi.fta.geoviite.infra.util.getIntId
import java.time.Instant
import kotlin.test.assertEquals
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
    fun `fetchAllDesignVersionsAtMoment returns OPEN items in the design`() {
        val designBranch = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)

        mainOfficialContext.save(trackNumber(number = TrackNumber("main only")))
        val designDraft = designDraftContext.save(trackNumber(number = TrackNumber("in design")))
        val designOfficial = trackNumberService.publish(designBranch, designDraft).published

        val result = trackNumberDao.fetchAllDesignVersionsAtMoment(designBranch.designId, Instant.now())

        assertEquals(listOf(designOfficial.id), result.map { it.id })
    }

    @Test
    fun `fetchAllDesignVersionsAtMoment returns CANCELLED items`() {
        val designBranch = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)

        val designDraft = designDraftContext.save(trackNumber(number = TrackNumber("to cancel")))
        val tnId = designDraft.id
        trackNumberService.publish(designBranch, designDraft)
        val cancelDraft = trackNumberService.cancel(designBranch, tnId)!!
        trackNumberService.publish(designBranch, cancelDraft)

        val result = trackNumberDao.fetchAllDesignVersionsAtMoment(designBranch.designId, Instant.now())

        assertEquals(listOf(tnId), result.map { it.id })
    }

    @Test
    fun `fetchAllDesignVersionsAtMoment returns COMPLETED items`() {
        val designBranch = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)

        val designDraft = designDraftContext.save(trackNumber(number = TrackNumber("to complete")))
        val tnId = designDraft.id
        trackNumberService.publish(designBranch, designDraft)
        val mainDraft = trackNumberService.mergeToMainBranch(designBranch, tnId)
        val completedDesignDraft = trackNumberService.publish(LayoutBranch.main, mainDraft).completed!!.second
        trackNumberService.publish(designBranch, completedDesignDraft)

        val result = trackNumberDao.fetchAllDesignVersionsAtMoment(designBranch.designId, Instant.now())

        assertEquals(listOf(tnId), result.map { it.id })
    }

    @Test
    fun `fetchAllDesignVersionsAtMoment does not return items not in the design`() {
        val designBranch = testDBService.createDesignBranch()

        mainOfficialContext.save(trackNumber(number = TrackNumber("main only")))

        val result = trackNumberDao.fetchAllDesignVersionsAtMoment(designBranch.designId, Instant.now())

        assertEquals(emptyList(), result)
    }

    @Test
    fun `fetchDesignVersionAtMoment returns version for OPEN item in design`() {
        val designBranch = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)

        val draft = designDraftContext.save(trackNumber(number = TrackNumber("in design")))
        val designOfficial = trackNumberService.publish(designBranch, draft).published

        val result = trackNumberDao.fetchDesignVersionAtMoment(designBranch.designId, designOfficial.id, Instant.now())

        assertEquals(designOfficial, result)
    }

    @Test
    fun `fetchDesignVersionAtMoment returns null for item not in design`() {
        val designBranch = testDBService.createDesignBranch()
        val mainOnly = mainOfficialContext.save(trackNumber(number = TrackNumber("main only")))

        val result = trackNumberDao.fetchDesignVersionAtMoment(designBranch.designId, mainOnly.id, Instant.now())

        assertEquals(null, result)
    }

    @Test
    fun `fetchDesignVersionAtMoment returns CANCELLED item`() {
        val designBranch = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)

        val draft = designDraftContext.save(trackNumber(number = TrackNumber("to cancel")))
        val tnId = draft.id
        trackNumberService.publish(designBranch, draft)
        val cancelDraft = trackNumberService.cancel(designBranch, tnId)!!
        trackNumberService.publish(designBranch, cancelDraft)

        val result = trackNumberDao.fetchDesignVersionAtMoment(designBranch.designId, tnId, Instant.now())

        assertEquals(tnId, result?.id)
    }

    @Test
    fun `fetchDesignVersionAtMoment returns COMPLETED item`() {
        val designBranch = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)

        val draft = designDraftContext.save(trackNumber(number = TrackNumber("to complete")))
        val tnId = draft.id
        trackNumberService.publish(designBranch, draft)
        val mainDraft = trackNumberService.mergeToMainBranch(designBranch, tnId)
        val completedDesignDraft = trackNumberService.publish(LayoutBranch.main, mainDraft).completed!!.second
        trackNumberService.publish(designBranch, completedDesignDraft)

        val result = trackNumberDao.fetchDesignVersionAtMoment(designBranch.designId, tnId, Instant.now())

        assertEquals(tnId, result?.id)
    }

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
