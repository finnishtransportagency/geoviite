package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.ratko.FakeRatko
import fi.fta.geoviite.infra.ratko.FakeRatkoService
import fi.fta.geoviite.infra.split.SplitDao
import fi.fta.geoviite.infra.split.SplitService
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutDesignDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostService
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchJoint
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineService
import fi.fta.geoviite.infra.tracklayout.TopologyLocationTrackSwitch
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.moveKmPostLocation
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.util.FreeTextWithNewLines
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getLayoutRowVersion
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class PublicationRatkoIT
@Autowired
constructor(
    val publicationService: PublicationService,
    val publicationValidationService: PublicationValidationService,
    val publicationLogService: PublicationLogService,
    val publicationTestSupportService: PublicationTestSupportService,
    val publicationDao: PublicationDao,
    val alignmentDao: LayoutAlignmentDao,
    val trackNumberDao: LayoutTrackNumberDao,
    val trackNumberService: LayoutTrackNumberService,
    val referenceLineDao: ReferenceLineDao,
    val referenceLineService: ReferenceLineService,
    val kmPostDao: LayoutKmPostDao,
    val kmPostService: LayoutKmPostService,
    val locationTrackDao: LocationTrackDao,
    val locationTrackService: LocationTrackService,
    val switchDao: LayoutSwitchDao,
    val switchService: LayoutSwitchService,
    val switchStructureDao: SwitchStructureDao,
    val splitDao: SplitDao,
    val fakeRatkoService: FakeRatkoService,
    val splitService: SplitService,
    val layoutDesignDao: LayoutDesignDao,
) : DBTestBase() {
    @BeforeEach
    fun cleanup() {
        val sql =
            """
                truncate publication.publication,
                         integrations.lock,
                         layout.track_number_id,
                         layout.location_track_id,
                         layout.switch_id,
                         layout.operating_point,
                         layout.operating_point_version
                  cascade;
            """
                .trimIndent()
        jdbc.execute(sql) { it.execute() }
        jdbc.execute("update integrations.ratko_push set status = 'SUCCESSFUL' where status != 'SUCCESSFUL'") {
            it.execute()
        }
    }

    lateinit var fakeRatko: FakeRatko

    @BeforeEach
    fun startServer() {
        fakeRatko = fakeRatkoService.start()
        fakeRatko.isOnline()
    }

    @AfterEach
    fun stopServer() {
        fakeRatko.stop()
    }

    @Test
    fun `external ids are fetched for calculated changes affecting assets not published in design`() {
        val trackNumber = mainOfficialContext.createLayoutTrackNumber().id
        trackNumberDao.insertExternalId(trackNumber, LayoutBranch.main, Oid("1.2.3.4.5"))

        mainOfficialContext.insert(referenceLine(trackNumber), alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))))
        val kmPost =
            mainOfficialContext.insert(kmPost(trackNumber, KmNumber(1), roughLayoutLocation = Point(3.0, 0.0))).id

        val switchAtStart =
            mainOfficialContext.insert(
                switch(joints = listOf(LayoutSwitchJoint(JointNumber(1), Point(0.0, 0.0), null)))
            )
        val switchAtEnd =
            mainOfficialContext.insert(
                switch(joints = listOf(LayoutSwitchJoint(JointNumber(1), Point(10.0, 0.0), null)))
            )
        switchDao.insertExternalId(switchAtStart.id, LayoutBranch.main, Oid("2.2.3.4.5"))
        switchDao.insertExternalId(switchAtEnd.id, LayoutBranch.main, Oid("2.2.3.4.6"))

        val longLocationTrack =
            mainOfficialContext.insert(
                locationTrack(
                    trackNumber,
                    topologyStartSwitch = TopologyLocationTrackSwitch(switchAtStart.id, JointNumber(1)),
                    topologyEndSwitch = TopologyLocationTrackSwitch(switchAtEnd.id, JointNumber(1)),
                ),
                alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
            )
        val shortLocationTrack =
            mainOfficialContext.insert(locationTrack(trackNumber), alignment(segment(Point(0.0, 0.0), Point(2.0, 0.0))))
        locationTrackDao.insertExternalId(longLocationTrack.id, LayoutBranch.main, Oid("3.2.3.4.5"))
        locationTrackDao.insertExternalId(shortLocationTrack.id, LayoutBranch.main, Oid("3.2.3.4.6"))

        val someDesign = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(someDesign, PublicationState.DRAFT)
        designDraftContext.insert(mainOfficialContext.fetch(kmPost)!!)
        moveKmPostLocation(designDraftContext.fetch(kmPost)!!, Point(5.0, 0.0), kmPostService)

        fakeRatko.acceptsNewRouteNumbersGivingThemOids(listOf("1.3.3.4.6"))
        fakeRatko.acceptsNewSwitchGivingItOid("2.3.3.4.6")
        fakeRatko.acceptsNewLocationTrackGivingItOid("3.3.3.4.5")
        fakeRatko.acceptsNewLocationTrackGivingItOid("3.3.3.4.6")
        publicationService.publishManualPublication(
            someDesign,
            PublicationRequest(publicationRequestIds(kmPosts = listOf(kmPost)), FreeTextWithNewLines.of("aoeu")),
        )

        // GVT-2798 will implement properly querying Ratko for the inherited ext IDs; for now, we
        // just get fresh ext IDs
        assertEquals(
            setOf("3.3.3.4.5", "3.3.3.4.6"),
            locationTrackDao.fetchExternalIds(someDesign).values.map { it.toString() }.toSet(),
        )
        // switch at end was touched by the km post change, switch at start wasn't
        assertEquals(null, switchDao.fetchExternalId(someDesign, switchAtStart.id))
        assertEquals("2.3.3.4.6", switchDao.fetchExternalId(someDesign, switchAtEnd.id).toString())

        val latestDesignPubs = publicationDao.fetchLatestPublications(LayoutBranchType.DESIGN, 1)
        assertEquals(someDesign, latestDesignPubs[0].layoutBranch.branch)

        val designPublicationId = latestDesignPubs[0].id

        // testing publication table contents manually at this time, since the publication log fetch
        // functions make various assumptions about only handling the main branch
        val publishedSwitches =
            jdbc.query(
                """
                    select id, design_id, draft, version
                    from layout.switch_version sv
                      join publication.switch ps
                        on sv.id = ps.switch_id
                          and sv.version = ps.switch_version
                          and sv.layout_context_id = ps.layout_context_id
                    where ps.publication_id = :publication_id
                """
                    .trimIndent(),
                mapOf("publication_id" to designPublicationId.intValue),
            ) { rs, _ ->
                rs.getLayoutRowVersion<LayoutSwitch>("id", "design_id", "draft", "version")
            }
        assertEquals(listOf(switchAtEnd), publishedSwitches)

        val publishedSwitchJointListForSizeCount =
            jdbc.query(
                "select * from publication.switch_joint where publication_id = :publication_id",
                mapOf("publication_id" to designPublicationId.intValue),
            ) { rs, _ ->
                assertEquals(longLocationTrack.id, rs.getIntId("location_track_id"))
                assertEquals(trackNumber, rs.getIntId("track_number_id"))
                assertTrue(setOf("3.3.3.4.5", "3.3.3.4.6").contains(rs.getString("location_track_external_id")))
                assertEquals("1.3.3.4.6", rs.getString("track_number_external_id"))
            }
        assertEquals(1, publishedSwitchJointListForSizeCount.size)

        val publishedLocationTracks =
            jdbc.query(
                """
                    select id, design_id, draft, version
                    from layout.location_track_version ltv
                      join publication.location_track plt
                        on ltv.id = plt.location_track_id
                          and ltv.version = plt.location_track_version
                          and ltv.layout_context_id = plt.layout_context_id
                    where plt.publication_id = :publication_id
                """
                    .trimIndent(),
                mapOf("publication_id" to designPublicationId.intValue),
            ) { rs, _ ->
                rs.getLayoutRowVersion<LocationTrack>("id", "design_id", "draft", "version")
            }
        assertEquals(setOf(shortLocationTrack, longLocationTrack), publishedLocationTracks.toSet())
    }
}
