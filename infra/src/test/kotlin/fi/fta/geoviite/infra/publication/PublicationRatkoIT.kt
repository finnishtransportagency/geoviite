package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.error.PublicationFailureException
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.ratko.FakeRatko
import fi.fta.geoviite.infra.ratko.FakeRatkoService
import fi.fta.geoviite.infra.ratko.ratkoSwitch
import fi.fta.geoviite.infra.split.SplitDao
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
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
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.edge
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.kmPostGkLocation
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.moveKmPostLocation
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.switchLinkYV
import fi.fta.geoviite.infra.tracklayout.trackGeometry
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import fi.fta.geoviite.infra.tracklayout.trackNumber
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getLayoutRowVersion
import fi.fta.geoviite.infra.util.queryOne
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ActiveProfiles("dev", "test")
@SpringBootTest
class PublicationRatkoIT
@Autowired
constructor(
    val publicationService: PublicationService,
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
        val mainTrackNumberOid = "1.2.3.4.5"
        trackNumberDao.insertExternalId(trackNumber, LayoutBranch.main, Oid(mainTrackNumberOid))

        mainOfficialContext.save(referenceLine(trackNumber), alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))))
        // split track with two km posts, so that moving the latter one even further doesn't affect
        // the first track km at all
        mainOfficialContext.save(kmPost(trackNumber, KmNumber(1), kmPostGkLocation(3.0, 0.0)))
        val kmPost = mainOfficialContext.save(kmPost(trackNumber, KmNumber(2), kmPostGkLocation(4.0, 0.0))).id

        val switchAtStart =
            mainOfficialContext.save(
                switch(joints = listOf(LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(0.0, 0.0), null)))
            )
        val switchAtEnd =
            mainOfficialContext.save(
                switch(joints = listOf(LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(10.0, 0.0), null)))
            )
        val mainSwitchAtStartOid = "2.2.3.4.5"
        val mainSwitchAtEndOid = "2.2.3.4.6"
        switchDao.insertExternalId(switchAtStart.id, LayoutBranch.main, Oid(mainSwitchAtStartOid))
        switchDao.insertExternalId(switchAtEnd.id, LayoutBranch.main, Oid(mainSwitchAtEndOid))

        val locationTrack1 =
            mainOfficialContext.save(
                locationTrack(trackNumber),
                trackGeometry(
                    edge(
                        startOuterSwitch = switchLinkYV(switchAtStart.id, 1),
                        endOuterSwitch = switchLinkYV(switchAtEnd.id, 1),
                        segments = listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
                    )
                ),
            )
        val locationTrack2 =
            mainOfficialContext.save(
                locationTrack(trackNumber),
                trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
            )
        val mainLocationTrack1Oid = "3.2.3.4.5"
        val mainLocationTrack2Oid = "3.2.3.4.6"
        locationTrackDao.insertExternalId(locationTrack1.id, LayoutBranch.main, Oid(mainLocationTrack1Oid))
        locationTrackDao.insertExternalId(locationTrack2.id, LayoutBranch.main, Oid(mainLocationTrack2Oid))

        val someDesign = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(someDesign, PublicationState.DRAFT)
        designDraftContext.save(mainOfficialContext.fetch(kmPost)!!)
        moveKmPostLocation(designDraftContext.fetch(kmPost)!!, Point(5.0, 0.0), kmPostService)

        fakeRatko.acceptsNewDesignGivingItId(1)
        val designTrackNumberOid = "1.3.3.4.6"
        fakeRatko.acceptsNewRouteNumbersGivingThemOids(listOf(designTrackNumberOid))
        val designLocationTrackOids = listOf("3.3.3.4.5", "3.3.3.4.6")
        designLocationTrackOids.forEach(fakeRatko::acceptsNewLocationTrackGivingItOid)
        val designSwitchOid = "2.3.3.4.6"
        fakeRatko.acceptsNewSwitchGivingItOid(designSwitchOid)
        publicationService.publishManualPublication(
            someDesign,
            PublicationRequest(publicationRequestIds(kmPosts = listOf(kmPost)), PublicationMessage.of("aoeu")),
        )

        // GVT-2798 will implement properly querying Ratko for the inherited ext IDs; for now, we
        // just get fresh ext IDs
        assertEquals(
            designLocationTrackOids.toSet(),
            locationTrackDao.fetchExternalIds(someDesign).values.map { it.oid.toString() }.toSet(),
        )
        // switch at end was touched by the km post change, switch at start wasn't
        assertEquals(null, switchDao.fetchExternalId(someDesign, switchAtStart.id))
        assertEquals(designSwitchOid, switchDao.fetchExternalId(someDesign, switchAtEnd.id)?.oid.toString())

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
                      join publication.switch ps using (id, layout_context_id, version)
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
                assertEquals(locationTrack1.id, rs.getIntId("location_track_id"))
                assertEquals(trackNumber, rs.getIntId("track_number_id"))
                assertTrue(designLocationTrackOids.contains(rs.getString("location_track_external_id")))
                assertEquals(designTrackNumberOid, rs.getString("track_number_external_id"))
            }
        assertEquals(1, publishedSwitchJointListForSizeCount.size)

        val publishedLocationTracks =
            jdbc.query(
                """
                    select id, design_id, draft, version
                    from layout.location_track_version ltv
                      join publication.location_track plt using (id, layout_context_id, version)
                    where plt.publication_id = :publication_id
                """
                    .trimIndent(),
                mapOf("publication_id" to designPublicationId.intValue),
            ) { rs, _ ->
                rs.getLayoutRowVersion<LocationTrack>("id", "design_id", "draft", "version")
            }
        assertEquals(setOf(locationTrack2, locationTrack1), publishedLocationTracks.toSet())
    }

    @Test
    fun `switch draft oid existence is checked upon publication`() {
        val trackNumber = mainOfficialContext.save(trackNumber()).id
        mainOfficialContext.save(referenceLine(trackNumber), alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))))
        val switch =
            mainDraftContext
                .save(
                    switch(
                        draftOid = Oid("1.2.3.4.5"),
                        joints =
                            listOf(
                                LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(0.0, 1.0), null),
                                LayoutSwitchJoint(JointNumber(3), SwitchJointRole.MAIN, Point(0.0, 10.0), null),
                            ),
                    )
                )
                .id
        val locationTrack =
            mainDraftContext
                .save(
                    locationTrack(trackNumber),
                    trackGeometry(
                        edge(
                            endOuterSwitch = switchLinkYV(switch, 1),
                            segments = listOf(segment(Point(0.0, 0.0), Point(1.0, 0.0))),
                        ),
                        edge(
                            startInnerSwitch = switchLinkYV(switch, 1),
                            endInnerSwitch = switchLinkYV(switch, 3),
                            segments = listOf(segment(Point(1.0, 0.0), Point(10.0, 0.0))),
                        ),
                    ),
                )
                .id
        fakeRatko.acceptsNewLocationTrackGivingItOid("2.3.4.5.6")
        fakeRatko.acceptsNewSwitchGivingItOid("3.4.5.6.7")

        // Ratko was ready to give a new switch OID, but we wanted a specific one that doesn't exist
        // -> should fail
        assertThrows<PublicationFailureException> {
            publicationService.publishManualPublication(
                LayoutBranch.main,
                PublicationRequest(
                    publicationRequestIds(switches = listOf(switch), locationTracks = listOf(locationTrack)),
                    PublicationMessage.of(""),
                ),
            )
        }
        // ... and the OID we tried to use didn't get recorded as being a proper OID
        assertNull(switchDao.fetchExternalId(LayoutBranch.main, switch))
        // ... however, if Ratko does have a switch by the draft OID we used...
        fakeRatko.hasSwitch(ratkoSwitch("1.2.3.4.5"))
        // ... then publication does succeed
        publicationService.publishManualPublication(
            LayoutBranch.main,
            PublicationRequest(
                publicationRequestIds(switches = listOf(switch), locationTracks = listOf(locationTrack)),
                PublicationMessage.of(""),
            ),
        )
        assertEquals(Oid("1.2.3.4.5"), switchDao.fetchExternalId(LayoutBranch.main, switch)?.oid)
    }

    @Test
    fun `editing several objects and finishing merging all to main leaves design empty`() {
        val trackNumber = mainOfficialContext.save(trackNumber()).id
        val referenceLine =
            mainOfficialContext
                .save(referenceLine(trackNumber), alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))))
                .id
        val switch =
            mainOfficialContext
                .save(
                    switch(
                        joints =
                            listOf(
                                LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(4.0, 0.0), null),
                                LayoutSwitchJoint(JointNumber(3), SwitchJointRole.MAIN, Point(8.0, 0.0), null),
                            )
                    )
                )
                .id
        val locationTrack =
            mainOfficialContext
                .save(
                    locationTrack(trackNumber),
                    trackGeometry(
                        edge(
                            endOuterSwitch = switchLinkYV(switch, 1),
                            segments = listOf(segment(Point(0.0, 0.0), Point(4.0, 0.0))),
                        ),
                        edge(
                            startInnerSwitch = switchLinkYV(switch, 1),
                            endInnerSwitch = switchLinkYV(switch, 3),
                            segments = listOf(segment(Point(4.0, 0.0), Point(8.0, 0.0))),
                        ),
                    ),
                )
                .id
        val kmPost = mainOfficialContext.save(kmPost(trackNumber, KmNumber(1), kmPostGkLocation(2.0, 0.0))).id

        val designBranch = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)

        designDraftContext.save(mainOfficialContext.fetch(trackNumber)!!)
        designDraftContext.save(mainOfficialContext.fetch(referenceLine)!!)
        designDraftContext.saveLocationTrack(mainOfficialContext.fetchWithGeometry(locationTrack)!!)
        designDraftContext.save(mainOfficialContext.fetch(switch)!!)
        designDraftContext.save(mainOfficialContext.fetch(kmPost)!!)

        val requestPublishAll =
            PublicationRequest(
                publicationRequestIds(
                    trackNumbers = listOf(trackNumber),
                    referenceLines = listOf(referenceLine),
                    locationTracks = listOf(locationTrack),
                    switches = listOf(switch),
                    kmPosts = listOf(kmPost),
                ),
                PublicationMessage.of(""),
            )

        fakeRatko.acceptsNewRouteNumbersGivingThemOids(listOf("1.1.1.1.1"))
        fakeRatko.acceptsNewLocationTrackGivingItOid("1.1.1.1.2")
        fakeRatko.acceptsNewSwitchGivingItOid("1.1.1.1.3")
        fakeRatko.acceptsNewDesignGivingItId(1)
        fakeRatko.providesPlanItemIdsInDesign(1)
        publicationService.publishManualPublication(designBranch, requestPublishAll)
        publicationService.mergeChangesToMain(designBranch, requestPublishAll.content)

        fakeRatko.acceptsNewRouteNumbersGivingThemOids(listOf("1.1.1.2.1"))
        fakeRatko.acceptsNewLocationTrackGivingItOid("1.1.1.2.2")
        fakeRatko.acceptsNewSwitchGivingItOid("1.1.1.2.3")
        val mergePublicationResult = publicationService.publishManualPublication(LayoutBranch.main, requestPublishAll)

        // currently calculated change publications don't record their cause (GVT-2979), but they do
        // simply just occur right next after their cause
        val mergeCompletionPublicationId = mergePublicationResult.publicationId.intValue + 1

        assertEquals(MainLayoutContext.official, designDraftContext.fetchVersion(trackNumber)!!.context)
        assertEquals(MainLayoutContext.official, designDraftContext.fetchVersion(referenceLine)!!.context)
        assertEquals(MainLayoutContext.official, designDraftContext.fetchVersion(locationTrack)!!.context)
        assertEquals(MainLayoutContext.official, designDraftContext.fetchVersion(switch)!!.context)
        assertEquals(MainLayoutContext.official, designDraftContext.fetchVersion(kmPost)!!.context)

        // check both that the correct versions got stored in the publication table, and that
        // they're the ones that the publication result claim were the merger versions, *and* that
        // those versions are in fact in completed state
        fun <T : LayoutAsset<T>> checkMergerCompletion(table: String, id: IntId<T>) =
            checkMergerCompletionOnPublication(mergeCompletionPublicationId, designBranch, table, id)

        checkMergerCompletion("track_number", trackNumber)
        checkMergerCompletion("reference_line", referenceLine)
        checkMergerCompletion("location_track", locationTrack)
        checkMergerCompletion("switch", switch)
        checkMergerCompletion("km_post", kmPost)
    }

    private data class MergerCompletionVersion(val draft: Boolean, val version: Int, val deleted: Boolean)

    private fun <T : LayoutAsset<T>> checkMergerCompletionOnPublication(
        mergeCompletionPublicationId: Int,
        designBranch: DesignBranch,
        table: String,
        id: IntId<T>,
    ) {
        val completionVersions =
            jdbc.query(
                """
            select draft, version, deleted
            from layout.${table}_version
            where id = :id and design_id = :design_id and design_asset_state = 'COMPLETED'  
        """
                    .trimIndent(),
                mapOf("id" to id.intValue, "design_id" to designBranch.designId.intValue),
            ) { rs, _ ->
                MergerCompletionVersion(rs.getBoolean("draft"), rs.getInt("version"), rs.getBoolean("deleted"))
            }

        // The main publication initially creates the actual completion marker (draft), then that
        // gets published (draft to official, draft is deleted) and immediately cleaned up (deleted)
        // in the triggered design publication.
        assertEquals(
            setOf(true to false, false to false, false to true, true to true),
            completionVersions.map { it.draft to it.deleted }.toSet(),
        )
        // ... and the non-deleted official one is the one that actually gets recorded in the
        // publication table.
        val completionVersionInVersionTable = completionVersions.find { !it.draft && !it.deleted }!!.version

        val completionVersionInPublicationTable =
            jdbc.queryOne(
                """
                select version from publication.$table
                where publication_id = :publication_id and id = :id and layout_context_id = :layout_context_id
            """
                    .trimIndent(),
                mapOf(
                    "publication_id" to mergeCompletionPublicationId,
                    "id" to id.intValue,
                    "layout_context_id" to designBranch.official.toSqlString(),
                ),
            ) { rs, _ ->
                rs.getInt("version")
            }
        assertEquals(completionVersionInVersionTable, completionVersionInPublicationTable)
    }
}
