package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.ratko.FakeRatko
import fi.fta.geoviite.infra.ratko.FakeRatkoService
import fi.fta.geoviite.infra.tracklayout.LayoutDesignDao
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.kmPostGkLocation
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.referenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import fi.fta.geoviite.infra.tracklayout.trackNumber
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class PublicationDesignInheritanceIT
@Autowired
constructor(
    val publicationService: PublicationService,
    val publicationLogService: PublicationLogService,
    val layoutDesignDao: LayoutDesignDao,
    val trackNumberDao: LayoutTrackNumberDao,
    val locationTrackDao: LocationTrackDao,
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
                         integrations.ratko_operational_point,
                         integrations.ratko_operational_point_version
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
    fun `change inherited to reference-line-only change in design goes to main branch track number version`() {
        // case: track number is only in main-official, but reference line is also edited in design, and km post on
        // the track number is changed -> inherited change is recorded on the track number version in main
        val trackNumber = mainOfficialContext.save(trackNumber()).id
        trackNumberDao.insertExternalId(trackNumber, LayoutBranch.main, Oid("1.1.1.1.1"))
        val referenceLine =
            mainOfficialContext
                .save(referenceLine(trackNumber), referenceLineGeometry(segment(Point(0.0, 0.0), Point(10.0, 0.0))))
                .id
        val kmPost =
            mainOfficialContext
                .save(kmPost(trackNumber, gkLocation = kmPostGkLocation(Point(5.0, 0.0)), km = KmNumber(1)))
                .id
        val design = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(design, DRAFT)

        designDraftContext.copyFrom(mainOfficialContext.fetchVersion(referenceLine)!!)
        fakeRatko.acceptsNewRouteNumbersGivingThemOids(listOf("2.2.2.2.2"))
        publicationService.publishManualPublication(
            design,
            PublicationRequest(
                publicationRequestIds(referenceLines = listOf(referenceLine)),
                PublicationMessage.of("ref line to design"),
            ),
        )

        mainDraftContext.save(mainDraftContext.fetch(kmPost)!!.copy(kmNumber = KmNumber(2)))
        publicationService.publishManualPublication(
            LayoutBranch.main,
            PublicationRequest(publicationRequestIds(kmPosts = listOf(kmPost)), PublicationMessage.of("km post")),
        )

        val designPublications = publicationLogService.fetchPublications(design)
        assertEquals(
            listOf(PublicationCause.MANUAL, PublicationCause.CALCULATED_CHANGE),
            designPublications.map { it.cause },
        )
        val inheritedPublicationId = designPublications[1].id
        assertPublicationHasInheritedTrackNumberChange(
            mainOfficialContext.fetchVersion(trackNumber)!!,
            setOf(KmNumber(1), KmNumber(2)),
            inheritedPublicationId,
        )
    }

    @Test
    fun `change inherited to track number change in design is recorded`() {
        // case: track number is edited in design, and km post on the track number is changed -> inherited change is
        // recorded on the track number version in the design
        val trackNumber = mainOfficialContext.save(trackNumber()).id
        trackNumberDao.insertExternalId(trackNumber, LayoutBranch.main, Oid("1.1.1.1.1"))
        mainOfficialContext
            .save(referenceLine(trackNumber), referenceLineGeometry(segment(Point(0.0, 0.0), Point(10.0, 0.0))))
            .id
        val kmPost =
            mainOfficialContext
                .save(kmPost(trackNumber, gkLocation = kmPostGkLocation(Point(5.0, 0.0)), km = KmNumber(1)))
                .id
        val design = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(design, OFFICIAL)

        val designDraftContext = testDBService.testContext(design, DRAFT)

        designDraftContext.copyFrom(mainOfficialContext.fetchVersion(trackNumber)!!)
        fakeRatko.acceptsNewRouteNumbersGivingThemOids(listOf("2.2.2.2.2"))
        publicationService.publishManualPublication(
            design,
            PublicationRequest(
                publicationRequestIds(trackNumbers = listOf(trackNumber)),
                PublicationMessage.of("track number to design"),
            ),
        )

        mainDraftContext.save(mainDraftContext.fetch(kmPost)!!.copy(kmNumber = KmNumber(2)))
        publicationService.publishManualPublication(
            LayoutBranch.main,
            PublicationRequest(publicationRequestIds(kmPosts = listOf(kmPost)), PublicationMessage.of("km post")),
        )

        val designPublications = publicationLogService.fetchPublications(design)
        assertEquals(
            listOf(PublicationCause.MANUAL, PublicationCause.CALCULATED_CHANGE),
            designPublications.map { it.cause },
        )
        val inheritedPublicationId = designPublications[1].id
        assertPublicationHasInheritedTrackNumberChange(
            designOfficialContext.fetchVersion(trackNumber)!!,
            setOf(KmNumber(1), KmNumber(2)),
            inheritedPublicationId,
        )
    }

    @Test
    fun `changes inherited to location track edited in design is recorded`() {
        // case: location track is create in main and edited in design, then a km post change is edited in main,
        // causing an inherited change
        val trackNumber = mainOfficialContext.save(trackNumber()).id
        trackNumberDao.insertExternalId(trackNumber, LayoutBranch.main, Oid("1.1.1.1.1"))
        mainOfficialContext
            .save(referenceLine(trackNumber), referenceLineGeometry(segment(Point(0.0, 0.0), Point(10.0, 0.0))))
            .id
        val kmPost =
            mainOfficialContext
                .save(kmPost(trackNumber, gkLocation = kmPostGkLocation(Point(5.0, 0.0)), km = KmNumber(1)))
                .id
        val locationTrack =
            mainOfficialContext
                .save(locationTrack(trackNumber), trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 0.0))))
                .id
        val design = testDBService.createDesignBranch()
        val designOfficialContext = testDBService.testContext(design, OFFICIAL)
        val designDraftContext = testDBService.testContext(design, DRAFT)

        designDraftContext.save(mainDraftContext.fetch(locationTrack)!!)
        fakeRatko.acceptsNewLocationTrackGivingItOid("2.2.2.2.2")
        publicationService.publishManualPublication(
            design,
            PublicationRequest(
                publicationRequestIds(locationTracks = listOf(locationTrack)),
                PublicationMessage.of("location track to design"),
            ),
        )

        mainDraftContext.save(mainDraftContext.fetch(kmPost)!!.copy(kmNumber = KmNumber(2)))
        publicationService.publishManualPublication(
            LayoutBranch.main,
            PublicationRequest(publicationRequestIds(kmPosts = listOf(kmPost)), PublicationMessage.of("km post")),
        )

        val designPublications = publicationLogService.fetchPublications(design)
        assertEquals(
            listOf(PublicationCause.MANUAL, PublicationCause.CALCULATED_CHANGE),
            designPublications.map { it.cause },
        )
        val inheritedPublicationId = designPublications[1].id
        assertPublicationHasInheritedLocationTrackChange(
            designOfficialContext.fetchVersion(locationTrack)!!,
            inheritedPublicationId,
        )
    }

    @Test
    fun `changes inherited to location track created in design is recorded`() {
        // case: location track is created in design, then a km post change is edited in main, causing an inherited
        // change
        val trackNumber = mainOfficialContext.save(trackNumber()).id
        trackNumberDao.insertExternalId(trackNumber, LayoutBranch.main, Oid("1.1.1.1.1"))
        mainOfficialContext
            .save(referenceLine(trackNumber), referenceLineGeometry(segment(Point(0.0, 0.0), Point(10.0, 0.0))))
            .id
        val kmPost =
            mainOfficialContext
                .save(kmPost(trackNumber, gkLocation = kmPostGkLocation(Point(5.0, 0.0)), km = KmNumber(1)))
                .id
        val design = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(design, DRAFT)
        val designOfficialContext = testDBService.testContext(design, OFFICIAL)

        val locationTrack =
            designDraftContext
                .save(locationTrack(trackNumber), trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 0.0))))
                .id
        fakeRatko.acceptsNewLocationTrackGivingItOid("2.2.2.2.2")

        publicationService.publishManualPublication(
            design,
            PublicationRequest(
                publicationRequestIds(locationTracks = listOf(locationTrack)),
                PublicationMessage.of("location track in design"),
            ),
        )

        mainDraftContext.save(mainDraftContext.fetch(kmPost)!!.copy(kmNumber = KmNumber(2)))
        publicationService.publishManualPublication(
            LayoutBranch.main,
            PublicationRequest(publicationRequestIds(kmPosts = listOf(kmPost)), PublicationMessage.of("km post")),
        )

        val designPublications = publicationLogService.fetchPublications(design)
        assertEquals(
            listOf(PublicationCause.MANUAL, PublicationCause.CALCULATED_CHANGE),
            designPublications.map { it.cause },
        )
        val inheritedPublicationId = designPublications[1].id
        assertPublicationHasInheritedLocationTrackChange(
            designOfficialContext.fetchVersion(locationTrack)!!,
            inheritedPublicationId,
        )
    }

    @Test
    fun `self-inherited changes both assign oids and record indirect changes for the inheritance targets`() {
        val trackNumber = mainOfficialContext.save(trackNumber()).id
        trackNumberDao.insertExternalId(trackNumber, LayoutBranch.main, Oid("1.1.1.1.1"))
        mainOfficialContext
            .save(referenceLine(trackNumber), referenceLineGeometry(segment(Point(0.0, 0.0), Point(10.0, 0.0))))
            .id
        val kmPost =
            mainOfficialContext
                .save(kmPost(trackNumber, gkLocation = kmPostGkLocation(Point(5.0, 0.0)), km = KmNumber(1)))
                .id

        val locationTrack =
            mainOfficialContext
                .save(locationTrack(trackNumber), trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 0.0))))
                .id
        val design = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(design, DRAFT)
        val designOfficialContext = testDBService.testContext(design, OFFICIAL)

        designDraftContext.save(mainOfficialContext.fetch(kmPost)!!.copy(kmNumber = KmNumber(2)))

        fakeRatko.acceptsNewLocationTrackGivingItOid("2.2.2.2.2")
        fakeRatko.acceptsNewRouteNumbersGivingThemOids(listOf("3.3.3.3.3"))
        publicationService.publishManualPublication(
            design,
            PublicationRequest(
                publicationRequestIds(kmPosts = listOf(kmPost)),
                PublicationMessage.of("edit km post in design"),
            ),
        )
        assertEquals("2.2.2.2.2", locationTrackDao.fetchExternalId(design, locationTrack)?.oid?.toString())
        assertEquals("3.3.3.3.3", trackNumberDao.fetchExternalId(design, trackNumber)?.oid?.toString())

        val designPublications = publicationLogService.fetchPublications(design)
        assertEquals(listOf(PublicationCause.MANUAL), designPublications.map { it.cause })
        val designPublicationId = designPublications[0].id
        assertPublicationHasInheritedTrackNumberChange(
            designOfficialContext.fetchVersion(trackNumber)!!,
            setOf(KmNumber(1), KmNumber(2)),
            designPublicationId,
        )
        assertPublicationHasInheritedLocationTrackChange(
            designOfficialContext.fetchVersion(locationTrack)!!,
            designPublicationId,
        )
    }

    private fun assertPublicationHasInheritedTrackNumberChange(
        version: LayoutRowVersion<LayoutTrackNumber>,
        changedKmNumbers: Set<KmNumber>,
        publicationId: IntId<Publication>,
    ) {
        val details = publicationLogService.getPublicationDetails(publicationId)
        val trackNumberChange = details.indirectChanges.trackNumbers.find { it.id == version.id }
        assertNotNull(trackNumberChange)
        assertEquals(version, trackNumberChange!!.version)
        assertEquals(changedKmNumbers, trackNumberChange.changedKmNumbers)
    }

    private fun assertPublicationHasInheritedLocationTrackChange(
        version: LayoutRowVersion<LocationTrack>,
        publicationId: IntId<Publication>,
    ) {
        val details = publicationLogService.getPublicationDetails(publicationId)
        val locationTrackChange = details.indirectChanges.locationTracks.find { it.id == version.id }
        assertNotNull(locationTrackChange)
        assertEquals(version, locationTrackChange!!.version)
    }
}
