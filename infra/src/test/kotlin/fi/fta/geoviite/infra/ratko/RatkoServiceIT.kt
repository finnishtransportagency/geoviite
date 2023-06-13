package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.geometryAlignment
import fi.fta.geoviite.infra.geometry.lineFromOrigin
import fi.fta.geoviite.infra.geometry.plan
import fi.fta.geoviite.infra.inframodel.InfraModelFile
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.publication.PublishRequestIds
import fi.fta.geoviite.infra.ratko.model.*
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.FreeText
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import kotlin.test.assertNotEquals

@ActiveProfiles("dev", "test")
@SpringBootTest
class RatkoServiceIT @Autowired constructor(
    val trackNumberService: LayoutTrackNumberService,
    val locationTrackService: LocationTrackService,
    val locationTrackDao: LocationTrackDao,
    val referenceLineService: ReferenceLineService,
    val referenceLineDao: ReferenceLineDao,
    val alignmentDao: LayoutAlignmentDao,
    val ratkoService: RatkoService,
    val layoutTrackNumberDao: LayoutTrackNumberDao,
    val publicationService: PublicationService,
    val switchService: LayoutSwitchService,
    val switchDao: LayoutSwitchDao,
    val kmPostService: LayoutKmPostService,
    val kmPostDao: LayoutKmPostDao,
    val fakeRatkoService: FakeRatkoService,
    val geometryDao: GeometryDao,

    ) : DBTestBase() {
    @BeforeEach
    fun cleanup() {
        val sql = """
            truncate publication.publication cascade;
            truncate integrations.lock cascade;
            truncate layout.track_number cascade;
        """.trimIndent()
        jdbc.execute(sql) { it.execute() }
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
    fun startRatkoPublish() {
        ratkoService.pushChangesToRatko()
    }

    @Test
    fun testChangeSet() {
        val referenceLineAlignmentVersion = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(10.0, 10.0))))
        val locationTrackAlignmentVersion = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(10.0, 10.0))))
        val trackNumber = trackNumber(getUnusedTrackNumber())
        val trackNumberId = layoutTrackNumberDao.insert(trackNumber).id
        referenceLineDao.insert(referenceLine(trackNumberId).copy(alignmentVersion = referenceLineAlignmentVersion))
        val officialVersion = locationTrackDao.insert(
            locationTrack(trackNumberId = trackNumberId).copy(alignmentVersion = locationTrackAlignmentVersion)
        )
        val draft = locationTrackService.getDraft(officialVersion.id).let { orig ->
            orig.copy(name = AlignmentName("${orig.name}-draft"))
        }
        locationTrackService.saveDraft(draft, alignmentDao.fetch(locationTrackAlignmentVersion)).rowVersion
        fakeRatko.hasLocationTrack(ratkoLocationTrack(id = draft.externalId.toString()))
        publishAndPush(locationTracks = listOf(officialVersion.rowVersion))
    }

    @Test
    fun pushNewTrackNumber() {
        val trackNumber = getUnusedTrackNumber()
        val originalTrackNumberVersion =
            layoutTrackNumberDao.insert(trackNumber(trackNumber, description = "augh", externalId = null)).rowVersion
        insertSomeOfficialReferenceLineFor(originalTrackNumberVersion.id)

        fakeRatko.acceptsNewRouteNumbersGivingThemOids(listOf("1.2.3.4.5"))
        publishAndPush(trackNumbers = listOf(originalTrackNumberVersion))

        val pushedRouteNumber = fakeRatko.getPushedRouteNumber(Oid("1.2.3.4.5"))
        assertEquals(trackNumber.value, pushedRouteNumber[0].name)
        assertEquals("augh", pushedRouteNumber[0].description)
        assertEquals(RatkoRouteNumberStateType.VALID, pushedRouteNumber[0].state.name)
    }

    @Test
    fun updateTrackNumber() {
        val trackNumber = getUnusedTrackNumber()
        val originalTrackNumberVersion =
            layoutTrackNumberDao.insert(trackNumber(trackNumber, description = "augh", externalId = Oid("1.2.3.4.5"))).rowVersion
        insertSomeOfficialReferenceLineFor(originalTrackNumberVersion.id)
        fakeRatko.hasRouteNumber(ratkoRouteNumber("1.2.3.4.5"))
        val newTrackNumber = getUnusedTrackNumber()
        trackNumberService.update(
            originalTrackNumberVersion.id,
            TrackNumberSaveRequest(
                newTrackNumber,
                FreeText("aoeu"),
                LayoutState.IN_USE,
                TrackMeter(KmNumber("0123"), 0)
            )
        )
        publishAndPush(trackNumbers = listOf(originalTrackNumberVersion))
        val pushedRouteNumber = fakeRatko.getPushedRouteNumber(Oid("1.2.3.4.5"))
        assertEquals(newTrackNumber.value, pushedRouteNumber[0].name)
        assertEquals("aoeu", pushedRouteNumber[0].description)
        assertEquals(RatkoRouteNumberStateType.VALID, pushedRouteNumber[0].state.name)
    }

    @Test
    fun deleteTrackNumber() {
        val trackNumber = getUnusedTrackNumber()
        val originalTrackNumberVersion =
            layoutTrackNumberDao.insert(trackNumber(trackNumber, description = "augh", externalId = Oid("1.2.3.4.5"))).rowVersion
        insertSomeOfficialReferenceLineFor(originalTrackNumberVersion.id)
        fakeRatko.hasRouteNumber(ratkoRouteNumber("1.2.3.4.5"))
        trackNumberService.update(
            originalTrackNumberVersion.id,
            TrackNumberSaveRequest(
                trackNumber,
                FreeText("augh"),
                LayoutState.DELETED,
                TrackMeter(KmNumber("0123"), 0)
            )
        )
        publishAndPush(trackNumbers = listOf(originalTrackNumberVersion))
        val pushedRouteNumber = fakeRatko.getPushedRouteNumber(Oid("1.2.3.4.5"))
        assertEquals(RatkoRouteNumberStateType.NOT_VALID, pushedRouteNumber[0].state.name)
    }

    @Test
    fun pushAndDeleteLocationTrack() {
        val trackNumber = getUnusedTrackNumber()
        val trackNumberId =
            layoutTrackNumberDao.insert(trackNumber(trackNumber, description = "augh", externalId = Oid("1.2.3.4.5"))).id
        insertSomeOfficialReferenceLineFor(trackNumberId)

        val locationTrackOriginalVersion =
            locationTrackService.saveDraft(
                locationTrack(trackNumberId, name = "abcde", description = "cdefg", type = LocationTrackType.CHORD, externalId = null),
                alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
            ).rowVersion
        fakeRatko.acceptsNewLocationTrackGivingItOid("2.3.4.5.6")
        publishAndPush(locationTracks = listOf(locationTrackOriginalVersion))
        fakeRatko.hostPushedLocationTrack("2.3.4.5.6")
        val officialVersion = locationTrackDao.fetchVersion(locationTrackOriginalVersion.id, PublishType.OFFICIAL)!!
        val createdPush = fakeRatko.getLastPushedLocationTrack("2.3.4.5.6")
        assertEquals("abcde", createdPush.name)
        assertEquals("cdefg", createdPush.description)
        assertEquals(RatkoAssetState.IN_USE.name, createdPush.state.name)
        assertEquals(RatkoLocationTrackType.CHORD.name, createdPush.type.name)

        locationTrackService.saveDraft(locationTrackDao.fetch(officialVersion).copy(state = LayoutState.DELETED))
        publishAndPush(locationTracks = listOf(officialVersion))

        assertEquals(listOf(""), fakeRatko.getLocationTrackPointDeletions("2.3.4.5.6"))
        val deletedPush = fakeRatko.getLastPushedLocationTrack("2.3.4.5.6")
        assertEquals(RatkoLocationTrackState.DELETED.name, deletedPush.state.name)
    }

    @Test
    fun modifyLocationTrackProperties() {
        val trackNumber = getUnusedTrackNumber()
        val trackNumberId =
            layoutTrackNumberDao.insert(trackNumber(trackNumber, description = "augh", externalId = Oid("1.2.3.4.5"))).id
        insertSomeOfficialReferenceLineFor(trackNumberId)

        val locationTrackOriginalVersion =
            locationTrackService.saveDraft(
                locationTrack(trackNumberId, name = "abcde", description = "cdefg", type = LocationTrackType.CHORD, externalId = null),
                alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
            ).rowVersion
        fakeRatko.acceptsNewLocationTrackGivingItOid("2.3.4.5.6")
        publishAndPush(locationTracks = listOf(locationTrackOriginalVersion))
        fakeRatko.hostPushedLocationTrack("2.3.4.5.6")
        val officialVersion = locationTrackDao.fetchVersion(locationTrackOriginalVersion.id, PublishType.OFFICIAL)!!
        locationTrackService.saveDraft(
            draft(locationTrackDao.fetch(officialVersion).copy(
                description = FreeText("aoeu"),
                name = AlignmentName("uuba aaba"),
                type = LocationTrackType.MAIN,
            ))
        )
        publishAndPush(locationTracks = listOf(officialVersion))
        val pushed = fakeRatko.getLastPushedLocationTrack("2.3.4.5.6")
        val createdPoints = fakeRatko.getCreatedLocationTrackPoints("2.3.4.5.6")
        val updatedPoints = fakeRatko.getUpdatedLocationTrackPoints("2.3.4.5.6")
        val deletedPoints = fakeRatko.getLocationTrackPointDeletions("2.3.4.5.6")
        assertEquals("aoeu", pushed.description)
        assertEquals("uuba aaba", pushed.name)
        assertEquals(RatkoLocationTrackType.MAIN, pushed.type)
        assertEquals(1, createdPoints.size)
        assertEquals(9, createdPoints[0].size)
        assertEquals(listOf<List<RatkoPoint>>(), updatedPoints)
        assertEquals(listOf<String>(), deletedPoints)
    }

    @Test
    fun lengthenLocationTrack() {
        val trackNumber = getUnusedTrackNumber()
        val trackNumberId =
            layoutTrackNumberDao.insert(trackNumber(trackNumber, description = "augh", externalId = Oid("1.2.3.4.5"))).id
        insertSomeOfficialReferenceLineFor(trackNumberId)

        val locationTrackOriginalVersion =
            locationTrackService.saveDraft(
                locationTrack(trackNumberId, name = "abcde", description = "cdefg", type = LocationTrackType.CHORD, externalId = null),
                alignment(segment(Point(4.0, 0.0), Point(6.0, 0.0)))
            ).rowVersion
        fakeRatko.acceptsNewLocationTrackGivingItOid("2.3.4.5.6")
        publishAndPush(locationTracks = listOf(locationTrackOriginalVersion))
        fakeRatko.hostPushedLocationTrack("2.3.4.5.6")
        val officialVersion = locationTrackDao.fetchVersion(locationTrackOriginalVersion.id, PublishType.OFFICIAL)!!
        locationTrackService.saveDraft(
            draft(locationTrackDao.fetch(officialVersion)),
            alignment(segment(Point(2.0, 0.0), Point(8.0, 0.0)))
        )
        publishAndPush(locationTracks = listOf(officialVersion))
        val createdPoints = fakeRatko.getCreatedLocationTrackPoints("2.3.4.5.6")
        val updatedPoints = fakeRatko.getUpdatedLocationTrackPoints("2.3.4.5.6")
        val deletedPoints = fakeRatko.getLocationTrackPointDeletions("2.3.4.5.6")
        assertEquals(1, createdPoints.size)
        assertEquals(1, createdPoints[0].size)
        assertEquals(5, createdPoints[0][0].kmM.meters.intValueExact())
        assertEquals((3..7).toList(), updatedPoints[0].map { p -> p.kmM.meters.intValueExact() })
        assertEquals(listOf("0000"), deletedPoints)
    }

    @Test
    fun shortenLocationTrack() {
        val trackNumber = getUnusedTrackNumber()
        val trackNumberId =
            layoutTrackNumberDao.insert(trackNumber(trackNumber, description = "augh", externalId = Oid("1.2.3.4.5"))).id
        insertSomeOfficialReferenceLineFor(trackNumberId)

        val locationTrackOriginalVersion =
            locationTrackService.saveDraft(
                locationTrack(trackNumberId, name = "abcde", description = "cdefg", type = LocationTrackType.CHORD, externalId = null),
                alignment(segment(Point(2.0, 0.0), Point(8.0, 0.0)))
            ).rowVersion
        fakeRatko.acceptsNewLocationTrackGivingItOid("2.3.4.5.6")
        publishAndPush(locationTracks = listOf(locationTrackOriginalVersion))
        fakeRatko.hostPushedLocationTrack("2.3.4.5.6")
        val officialVersion = locationTrackDao.fetchVersion(locationTrackOriginalVersion.id, PublishType.OFFICIAL)!!
        locationTrackService.saveDraft(
            draft(locationTrackDao.fetch(officialVersion)),
            alignment(segment(Point(4.0, 0.0), Point(6.0, 0.0)))
        )
        publishAndPush(locationTracks = listOf(officialVersion))
        val createdPoints = fakeRatko.getCreatedLocationTrackPoints("2.3.4.5.6")
        val updatedPoints = fakeRatko.getUpdatedLocationTrackPoints("2.3.4.5.6")
        val deletedPoints = fakeRatko.getLocationTrackPointDeletions("2.3.4.5.6")
        assertEquals(1, updatedPoints.size)
        assertEquals(1, updatedPoints[0].size)
        assertEquals(5, updatedPoints[0][0].kmM.meters.intValueExact())
        assertEquals((3..7).toList(), createdPoints[0].map { p -> p.kmM.meters.intValueExact() })
        assertEquals(listOf("0000"), deletedPoints)
    }

    @Test
    fun alterLocationTrackGeometry() {
        val trackNumber = getUnusedTrackNumber()
        val trackNumberId =
            layoutTrackNumberDao.insert(trackNumber(trackNumber, description = "augh", externalId = Oid("1.2.3.4.5"))).id
        insertSomeOfficialReferenceLineFor(trackNumberId)

        val locationTrackOriginalVersion =
            locationTrackService.saveDraft(
                locationTrack(trackNumberId, name = "abcde", description = "cdefg", type = LocationTrackType.CHORD, externalId = null),
                alignment(segment(Point(0.0, 0.0), Point(1.0, 0.0), Point(5.0, 3.0), Point(9.0, 0.0), Point(10.0, 0.0)))
            ).rowVersion
        fakeRatko.acceptsNewLocationTrackGivingItOid("2.3.4.5.6")
        publishAndPush(locationTracks = listOf(locationTrackOriginalVersion))
        fakeRatko.hostPushedLocationTrack("2.3.4.5.6")
        val officialVersion = locationTrackDao.fetchVersion(locationTrackOriginalVersion.id, PublishType.OFFICIAL)!!
        locationTrackService.saveDraft(
            draft(locationTrackDao.fetch(officialVersion)),
            alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
        )
        publishAndPush(locationTracks = listOf(officialVersion))
        val createdPoints = fakeRatko.getCreatedLocationTrackPoints("2.3.4.5.6")
        val updatedPoints = fakeRatko.getUpdatedLocationTrackPoints("2.3.4.5.6")
        val deletedPoints = fakeRatko.getLocationTrackPointDeletions("2.3.4.5.6")
        assertEquals(1, updatedPoints.size)
        assertEquals(9, createdPoints[0].size)
        assertEquals(9, updatedPoints[0].size)
        (0..8).forEach { i ->
            assertEquals(
                createdPoints[0][i].geometry!!.coordinates[0],
                updatedPoints[0][i].geometry!!.coordinates[0],
                0.00001
            )
        }
        assertTrue(createdPoints[0].subList(1, 8).all { p -> p.geometry!!.coordinates[1] > 0.0 })
        assertEquals(List(9) { 0.0 }, updatedPoints[0].map { p -> p.geometry!!.coordinates[1] })
        assertEquals(listOf("0000"), deletedPoints)
    }

    @Test
    fun pushLocationTrackMetadata() {
        val trackNumber = getUnusedTrackNumber()
        val trackNumberId =
            layoutTrackNumberDao.insert(trackNumber(trackNumber, description = "augh", externalId = Oid("1.2.3.4.5"))).id
        insertSomeOfficialReferenceLineFor(trackNumberId)

        val planVersion = geometryDao.insertPlan(
            plan(
                trackNumberId,
                srid = Srid(4009),
                measurementMethod = MeasurementMethod.OFFICIALLY_MEASURED_GEODETICALLY,
                planTime = Instant.parse("2018-11-30T18:35:24.00Z"),
                alignments = listOf(
                    geometryAlignment(
                        trackNumberId,
                        name = "geo name",
                        elements = listOf(lineFromOrigin(1.0))
                    )
                ),
            ),
            InfraModelFile(FileName("foobar"), "<a></a>"),
            null
        )
        val plan = geometryDao.fetchPlan(planVersion)

        val locationTrack =
            locationTrackService.saveDraft(
                locationTrack(trackNumberId, name = "abcde", description = "cdefg", type = LocationTrackType.CHORD, externalId = null),
                alignment(segment(Point(2.0, 0.0), Point(8.0, 0.0)).copy(sourceId = plan.alignments[0].elements[0].id))
            ).rowVersion
        fakeRatko.acceptsNewLocationTrackGivingItOid("2.3.4.5.6")
        publishAndPush(locationTracks = listOf(locationTrack))
        val metadata = fakeRatko.getPushedMetadata(locationTrackOid = "2.3.4.5.6")

        val props = metadata[0].properties
        fun prop(name: String) = props.find { p -> p.name == name }!!
        assertEquals("foobar", prop("filename").stringValue)
        assertEquals(RatkoMeasurementMethod.OFFICIALLY_MEASURED_GEODETICALLY.value, prop("measurement_method").enumValue)
        assertEquals(2018, prop("created_year").integerValue)
        assertEquals("EPSG:4009", prop("original_crs").stringValue)
        assertEquals("geo name", prop("alignment").stringValue)
    }

    @Test
    fun pushTwoTrackNumbers() {
        val trackNumber1 = getUnusedTrackNumber()
        val trackNumber1Version = trackNumberService.saveDraft(trackNumber(trackNumber1, externalId = null))
        insertSomeOfficialReferenceLineFor(trackNumber1Version.id)
        val trackNumber2 = getUnusedTrackNumber()
        val trackNumber2Version = trackNumberService.saveDraft(trackNumber(trackNumber2, externalId = null))
        insertSomeOfficialReferenceLineFor(trackNumber2Version.id)

        fakeRatko.acceptsNewRouteNumbersGivingThemOids(listOf("2.3.4.5.6", "3.4.5.6.7"))
        publishAndPush(trackNumbers = listOf(trackNumber1Version.rowVersion, trackNumber2Version.rowVersion))
        val pushedRouteNumber1 = fakeRatko.getPushedRouteNumber(Oid("2.3.4.5.6"))
        val pushedRouteNumber2 = fakeRatko.getPushedRouteNumber(Oid("3.4.5.6.7"))
        assertEquals(trackNumber1.value, pushedRouteNumber1[0].name)
        assertEquals(trackNumber2.value, pushedRouteNumber2[0].name)
    }

    @Test
    fun lengthenReferenceLine() {
        val trackNumber = getUnusedTrackNumber()
        val originalTrackNumberVersion =
            layoutTrackNumberDao.insert(trackNumber(trackNumber, description = "augh", externalId = null)).rowVersion
        val originalReferenceLineDaoResponse = insertSomeOfficialReferenceLineFor(originalTrackNumberVersion.id)
        val originalReferenceLine = referenceLineDao.fetch(originalReferenceLineDaoResponse.rowVersion)

        fakeRatko.acceptsNewRouteNumbersGivingThemOids(listOf("1.2.3.4.5"))
        publishAndPush(trackNumbers = listOf(originalTrackNumberVersion))
        referenceLineService.saveDraft(
            originalReferenceLine,
            alignment(segment(Point(0.0, 0.0), Point(20.0, 0.0)))
        )
        publishAndPush(referenceLines = listOf(originalReferenceLineDaoResponse.rowVersion))
        val pushedPoints = fakeRatko.getCreatedRouteNumberPoints("1.2.3.4.5")
        assertEquals(9, pushedPoints[0].size)
        assertEquals(19, pushedPoints[1].size)
        assertEquals(
            pushedPoints[0].map { p -> p.geometry?.coordinates },
            pushedPoints[1].take(9).map { p -> p.geometry?.coordinates })
        assertEquals((1..19).toList(), pushedPoints[1].map { p -> p.kmM.meters.toInt() })
    }

    @Test
    fun shortenReferenceLine() {
        val trackNumber = getUnusedTrackNumber()
        val originalTrackNumberVersion =
            trackNumberService.saveDraft(trackNumber(trackNumber, description = "augh", externalId = null)).rowVersion
        val originalReferenceLineDaoResponse = referenceLineService.saveDraft(
            referenceLine(originalTrackNumberVersion.id),
            alignment(
                segment(
                    Point(0.0, 0.0),
                    Point(10.0, 0.0)
                )
            )
        )

        fakeRatko.acceptsNewRouteNumbersGivingThemOids(listOf("1.2.3.4.5"))
        publishAndPush(trackNumbers = listOf(originalTrackNumberVersion), referenceLines = listOf(originalReferenceLineDaoResponse.rowVersion))
        fakeRatko.hasRouteNumber(ratkoRouteNumber("1.2.3.4.5"))
        referenceLineService.saveDraft(
            draft(referenceLineDao.fetch(referenceLineDao.fetchVersion(PublishType.OFFICIAL, originalTrackNumberVersion.id)!!)),
            alignment(segment(Point(0.0, 0.0), Point(5.0, 0.0)))
        )
        publishAndPush(referenceLines = listOf(originalReferenceLineDaoResponse.rowVersion))
        val pushedPoints = fakeRatko.getCreatedRouteNumberPoints("1.2.3.4.5")
        val updatedPoints = fakeRatko.getUpdatedRouteNumberPoints("1.2.3.4.5")
        val deletions = fakeRatko.getRouteNumberPointDeletions("1.2.3.4.5")
        assertEquals(9, pushedPoints[0].size)
        assertEquals(4, updatedPoints[0].size)
        assertEquals(
            pushedPoints[0].take(4).map { p -> p.geometry?.coordinates },
            updatedPoints[0].map { p -> p.geometry?.coordinates })
        assertEquals(RatkoPointStates.VALID, pushedPoints[0].map { p -> p.state?.name }.distinct().single())
        assertEquals(RatkoPointStates.VALID, updatedPoints[0].map { p -> p.state?.name }.distinct().single())
        assertEquals(listOf("0000"), deletions)
    }

    @Test
    fun changeReferenceLineGeometry() {
        val trackNumber = getUnusedTrackNumber()
        val originalTrackNumberVersion =
            trackNumberService.saveDraft(trackNumber(trackNumber, description = "augh", externalId = null)).rowVersion
        val originalReferenceLineDaoResponse = referenceLineService.saveDraft(
            referenceLine(originalTrackNumberVersion.id),
            alignment(
                segment(
                    Point(0.0, 0.0),
                    Point(10.0, 0.0),
                    Point(20.0, 1.0), // reference line has bump
                    Point(30.0, 0.0),
                    Point(40.0, 0.0),
                )
            )
        )
        val locationTrackDaoResponse = locationTrackService.saveDraft(locationTrack(originalTrackNumberVersion.id, externalId = null),
            alignment(segment(Point(0.0, 0.0), Point(40.0, 0.0)))
        )

        fakeRatko.acceptsNewRouteNumbersGivingThemOids(listOf("1.2.3.4.5"))
        fakeRatko.acceptsNewLocationTrackGivingItOid("2.3.4.5.6")
        publishAndPush(
            trackNumbers = listOf(originalTrackNumberVersion),
            referenceLines = listOf(originalReferenceLineDaoResponse.rowVersion),
            locationTracks = listOf(locationTrackDaoResponse.rowVersion)
        )
        fakeRatko.hasRouteNumber(ratkoRouteNumber("1.2.3.4.5"))
        fakeRatko.hostPushedLocationTrack("2.3.4.5.6")
        referenceLineService.saveDraft(
            draft(referenceLineDao.fetch(referenceLineDao.fetchVersion(PublishType.OFFICIAL, originalTrackNumberVersion.id)!!)),
            alignment(segment(Point(0.0, 0.0), Point(40.0, 0.0)))
        )
        publishAndPush(
            // not publishing a change to location track, but we want to update its points anyway due to reference line
            // geometry change
            referenceLines = listOf(originalReferenceLineDaoResponse.rowVersion),
        )
        val deletedOnTrack = fakeRatko.getLocationTrackPointDeletions("2.3.4.5.6")
        val updatedOnTrack = fakeRatko.getUpdatedLocationTrackPoints("2.3.4.5.6")
        val createdOnTrack = fakeRatko.getCreatedLocationTrackPoints("2.3.4.5.6")
        // bump in reference line was after 10 m, so the addresses of the first 9 points (separate of start point) on
        // the track don't move, but the rest do
        assertEquals(40, createdOnTrack[0].size)
        assertEquals(39, updatedOnTrack[0].size)
        assertEquals(
            createdOnTrack[0].take(9).map { p -> p.geometry?.coordinates?.get(0) },
            updatedOnTrack[0].take(9).map { p -> p.geometry?.coordinates?.get(0) }
        )
        (10..38).forEach { i ->
            assertNotEquals(
                createdOnTrack[0][i].geometry?.coordinates?.get(0),
                updatedOnTrack[0][i].geometry?.coordinates?.get(0)
            )
        }
        assertEquals(listOf("0000"), deletedOnTrack)
    }

    @Test
    fun removeKmPostBeforeSwitch() {
        val trackNumber = getUnusedTrackNumber()
        val trackNumberId = layoutTrackNumberDao.insert(
            trackNumber(trackNumber, description = "augh", externalId = Oid("1.1.1.1.1"))
        ).rowVersion.id
        insertSomeOfficialReferenceLineFor(trackNumberId)
        val kmPost1 = kmPostService.saveDraft(kmPost(trackNumberId, KmNumber(1), Point(2.0, 0.0)))
        val kmPost2 = kmPostService.saveDraft(kmPost(trackNumberId, KmNumber(2), Point(4.0, 0.0)))

        val (switch, throughTrack, branchingTrack) = setupDraftSwitchAndLocationTracks(trackNumberId)
        listOf("1.2.3.4.5", "2.3.4.5.6").forEach(fakeRatko::acceptsNewLocationTrackGivingItOid)
        fakeRatko.acceptsNewSwitchGivingItOid("3.4.5.6.7")
        fakeRatko.hasRouteNumber(ratkoRouteNumber("1.1.1.1.1"))

        publishAndPush(
            locationTracks = listOf(throughTrack.rowVersion, branchingTrack.rowVersion),
            switches = listOf(switch.rowVersion),
            kmPosts = listOf(kmPost1.rowVersion, kmPost2.rowVersion),
        )

        kmPostService.saveDraft(
            draft(kmPostDao.fetch(kmPostDao.fetchVersion(kmPost2.id, PublishType.OFFICIAL)!!)).copy(
                state = LayoutState.DELETED
            )
        )
        listOf("1.2.3.4.5", "2.3.4.5.6").forEach(fakeRatko::hostPushedLocationTrack)
        fakeRatko.hostPushedSwitch("3.4.5.6.7")

        publishAndPush(kmPosts = listOf(kmPost2.rowVersion))

        val switchLocations = fakeRatko.getPushedSwitchLocations("3.4.5.6.7")
        // switch was originally after kmPost2, but it got removed and then we pushed again
        assertEquals("0002+0001", switchLocations[0][0].nodecollection.nodes.first().point.kmM.toString())
        assertEquals("0002+0003.5", switchLocations[0][1].nodecollection.nodes.first().point.kmM.toString())
        assertEquals("0001+0003", switchLocations[1][0].nodecollection.nodes.first().point.kmM.toString())
        assertEquals("0001+0005.5", switchLocations[1][1].nodecollection.nodes.first().point.kmM.toString())
    }

    @Test
    fun removeLocationTrackWithSwitch() {
        val trackNumber = getUnusedTrackNumber()
        val trackNumberId = layoutTrackNumberDao.insert(
            trackNumber(trackNumber, description = "augh", externalId = Oid("1.1.1.1.1"))
        ).rowVersion.id
        insertSomeOfficialReferenceLineFor(trackNumberId)
        val kmPost1 = kmPostService.saveDraft(kmPost(trackNumberId, KmNumber(1), Point(2.0, 0.0)))
        val kmPost2 = kmPostService.saveDraft(kmPost(trackNumberId, KmNumber(2), Point(4.0, 0.0)))

        val (switch, throughTrack, branchingTrack) = setupDraftSwitchAndLocationTracks(trackNumberId)
        listOf("1.2.3.4.5", "2.3.4.5.6").forEach(fakeRatko::acceptsNewLocationTrackGivingItOid)
        fakeRatko.acceptsNewSwitchGivingItOid("3.4.5.6.7")
        fakeRatko.hasRouteNumber(ratkoRouteNumber("1.1.1.1.1"))

        publishAndPush(
            locationTracks = listOf(throughTrack.rowVersion, branchingTrack.rowVersion),
            switches = listOf(switch.rowVersion),
            kmPosts = listOf(kmPost1.rowVersion, kmPost2.rowVersion),
        )
        listOf("1.2.3.4.5", "2.3.4.5.6").forEach(fakeRatko::hostPushedLocationTrack)
        val officialThroughTrackVersion = locationTrackDao.fetchVersion(throughTrack.id, PublishType.OFFICIAL)!!
        locationTrackService.saveDraft(locationTrackDao.fetch(officialThroughTrackVersion).copy(state = LayoutState.DELETED))
        publishAndPush(locationTracks = listOf(officialThroughTrackVersion))
        val pushedSwitchLocations = fakeRatko.getPushedSwitchLocations("3.4.5.6.7")
        assertEquals(2, pushedSwitchLocations.size)
        val firstPush = pushedSwitchLocations[0]
        assertEquals(2, firstPush.size)
        assertEquals("JOINT_A", firstPush[0].nodecollection.nodes.toList()[0].nodeType.name)
        assertEquals("JOINT_C", firstPush[0].nodecollection.nodes.toList()[1].nodeType.name)
        assertEquals("JOINT_C", firstPush[1].nodecollection.nodes.toList()[0].nodeType.name)
        val secondPush = pushedSwitchLocations[1]
        assertEquals(1, secondPush.size)
        assertEquals("JOINT_A", secondPush[0].nodecollection.nodes.toList()[0].nodeType.name)
        assertEquals("JOINT_C", secondPush[0].nodecollection.nodes.toList()[1].nodeType.name)
    }

    @Test
    fun modifySwitchJointPositionAccuracy() {
        val trackNumber = getUnusedTrackNumber()
        val trackNumberId = layoutTrackNumberDao.insert(
            trackNumber(trackNumber, description = "augh", externalId = Oid("1.1.1.1.1"))
        ).rowVersion.id
        insertSomeOfficialReferenceLineFor(trackNumberId)
        val kmPost1 = kmPostService.saveDraft(kmPost(trackNumberId, KmNumber(1), Point(2.0, 0.0)))
        val kmPost2 = kmPostService.saveDraft(kmPost(trackNumberId, KmNumber(2), Point(4.0, 0.0)))

        val (switch, throughTrack, branchingTrack) = setupDraftSwitchAndLocationTracks(trackNumberId)
        listOf("1.2.3.4.5", "2.3.4.5.6").forEach(fakeRatko::acceptsNewLocationTrackGivingItOid)
        fakeRatko.acceptsNewSwitchGivingItOid("3.4.5.6.7")
        fakeRatko.hasRouteNumber(ratkoRouteNumber("1.1.1.1.1"))

        publishAndPush(
            locationTracks = listOf(throughTrack.rowVersion, branchingTrack.rowVersion),
            switches = listOf(switch.rowVersion),
            kmPosts = listOf(kmPost1.rowVersion, kmPost2.rowVersion),
        )
        listOf("1.2.3.4.5", "2.3.4.5.6").forEach(fakeRatko::hostPushedLocationTrack)
        fakeRatko.hostPushedSwitch("3.4.5.6.7")

        val officialSwitchVersion = switchDao.fetchVersion(switch.id, PublishType.OFFICIAL)!!
        val officialSwitch = switchDao.fetch(officialSwitchVersion)
        switchService.saveDraft(officialSwitch.copy(
            joints = officialSwitch.joints.map { j -> j.copy(locationAccuracy = LocationAccuracy.GEOMETRY_CALCULATED) }
        ))
        publishAndPush(switches = listOf(officialSwitchVersion))
        val pushedSwitchGeoms = fakeRatko.getPushedSwitchGeometries("3.4.5.6.7")
        assertEquals(
            listOf("OFFICIALLY MEASURED GEODETICALLY", "OFFICIALLY MEASURED GEODETICALLY"),
            pushedSwitchGeoms[0].map { geom -> geom.assetGeomAccuracyType.value }
        )
        assertEquals(
            listOf("GEOMETRY CALCULATED", "GEOMETRY CALCULATED"),
            pushedSwitchGeoms[1].map { geom -> geom.assetGeomAccuracyType.value }
        )
    }

    @Test
    fun pushSwitch() {
        val trackNumber = trackNumber(getUnusedTrackNumber())
        val trackNumberId = layoutTrackNumberDao.insert(trackNumber).id
        insertSomeOfficialReferenceLineFor(trackNumberId)

        val (switch, throughTrack, branchingTrack) = setupDraftSwitchAndLocationTracks(trackNumberId)

        listOf("1.2.3.4.5", "2.3.4.5.6").forEach(fakeRatko::acceptsNewLocationTrackGivingItOid)
        fakeRatko.acceptsNewSwitchGivingItOid("3.4.5.6.7")

        publishAndPush(
            locationTracks = listOf(throughTrack.rowVersion, branchingTrack.rowVersion),
            switches = listOf(switch.rowVersion)
        )
        val switchLocations = fakeRatko.getPushedSwitchLocations("3.4.5.6.7")[0]
        assertEquals(
            listOf(listOf(5f, 5f), listOf(7.5f)),
            switchLocations.map { l -> l.nodecollection.nodes.map { p -> p.point.kmM.meters.toFloat() } })
        assertEquals(listOf(listOf(RatkoNodeType.JOINT_A, RatkoNodeType.JOINT_C), listOf(RatkoNodeType.JOINT_C)),
            switchLocations.map { l -> l.nodecollection.nodes.map { n -> n.nodeType } })
        assertEquals(listOf(listOf("1.2.3.4.5", "1.2.3.4.5"), listOf("2.3.4.5.6")),
            switchLocations.map { push -> push.nodecollection.nodes.map { n -> n.point.locationtrack!!.toString() } })
        assertEquals(
            trackNumber.externalId!!.toString(),
            switchLocations.map { s -> s.nodecollection.nodes.first().point.routenumber!!.toString() }.distinct()
                .single()
        )
        assertEquals(listOf(1, 2), switchLocations.map { s -> s.priority })
        val pushedSwitch = fakeRatko.getLastPushedSwitch("3.4.5.6.7")
        fun prop(name: String) = pushedSwitch.properties!!.find { p -> p.name == name }!!
        assertEquals("TV123", prop("name").stringValue)
        assertEquals("TV123", prop("turnout_id").stringValue)
    }

    @Test
    fun moveSwitch() {
        val trackNumber = trackNumber(getUnusedTrackNumber())
        val trackNumberId = layoutTrackNumberDao.insert(trackNumber).id
        insertSomeOfficialReferenceLineFor(trackNumberId)

        val (switch, throughTrack, branchingTrack) = setupDraftSwitchAndLocationTracks(trackNumberId)

        listOf("1.2.3.4.5", "2.3.4.5.6").forEach(fakeRatko::acceptsNewLocationTrackGivingItOid)
        fakeRatko.acceptsNewSwitchGivingItOid("3.4.5.6.7")

        publishAndPush(
            locationTracks = listOf(throughTrack.rowVersion, branchingTrack.rowVersion),
            switches = listOf(switch.rowVersion)
        )
        fakeRatko.hasSwitch(fakeRatko.getLastPushedSwitch("3.4.5.6.7"))
        listOf("1.2.3.4.5", "2.3.4.5.6").forEach(fakeRatko::hostPushedLocationTrack)

        detachSwitchesFromTrack(throughTrack.id)
        detachSwitchesFromTrack(branchingTrack.id)

        val differentThroughTrack = locationTrackService.saveDraft(
            locationTrack(trackNumberId, externalId = null),
            alignment(
                segment(Point(0.0, 10.0), Point(5.0, 10.0), switchId = switch.id, endJointNumber = JointNumber(1)),
                segment(Point(5.0, 10.0), Point(10.0, 10.0), switchId = switch.id, startJointNumber = JointNumber(3)),
            )
        )
        val differentBranchingTrack = locationTrackService.saveDraft(
            locationTrack(trackNumberId, externalId = null),
            alignment(
                segment(Point(5.0, 10.0), Point(7.5, 10.5), switchId = switch.id, endJointNumber = JointNumber(3)),
                segment(Point(7.5, 10.5), Point(10.0, 11.0), switchId = switch.id, startJointNumber = JointNumber(3))
            )
        )
        listOf("4.4.4.4.4", "5.5.5.5.5").forEach(fakeRatko::acceptsNewLocationTrackGivingItOid)
        publishAndPush(locationTracks = listOf(differentThroughTrack.rowVersion, differentBranchingTrack.rowVersion))
        val switchLocations = fakeRatko.getPushedSwitchLocations("3.4.5.6.7")

        val expectedNodeTypes = listOf(
            listOf(RatkoNodeType.JOINT_A, RatkoNodeType.JOINT_C),
            listOf(RatkoNodeType.JOINT_C)
        )
        assertEquals(
            listOf(expectedNodeTypes, expectedNodeTypes),
            switchLocations.map { push -> push.map { location -> location.nodecollection.nodes.map { n -> n.nodeType } } }
        )

        assertEquals(
            listOf(listOf("1.2.3.4.5", "1.2.3.4.5"), listOf("2.3.4.5.6")),
            switchLocations[0].map { location -> location.nodecollection.nodes.map { n -> n.point.locationtrack.toString() } })
        assertEquals(
            listOf(listOf("4.4.4.4.4", "4.4.4.4.4"), listOf("5.5.5.5.5")),
            switchLocations[1].map { location -> location.nodecollection.nodes.map { n -> n.point.locationtrack.toString() } })

    }

    @Test
    fun changeSwitchStateCategory() {
        val trackNumber = getUnusedTrackNumber()
        val trackNumberId = layoutTrackNumberDao.insert(
            trackNumber(trackNumber, description = "augh", externalId = Oid("1.1.1.1.1"))
        ).rowVersion.id
        insertSomeOfficialReferenceLineFor(trackNumberId)
        val kmPost1 = kmPostService.saveDraft(kmPost(trackNumberId, KmNumber(1), Point(2.0, 0.0)))
        val kmPost2 = kmPostService.saveDraft(kmPost(trackNumberId, KmNumber(2), Point(4.0, 0.0)))

        val (switch, throughTrack, branchingTrack) = setupDraftSwitchAndLocationTracks(trackNumberId)
        listOf("1.2.3.4.5", "2.3.4.5.6").forEach(fakeRatko::acceptsNewLocationTrackGivingItOid)
        fakeRatko.acceptsNewSwitchGivingItOid("3.4.5.6.7")
        fakeRatko.hasRouteNumber(ratkoRouteNumber("1.1.1.1.1"))

        publishAndPush(
            locationTracks = listOf(throughTrack.rowVersion, branchingTrack.rowVersion),
            switches = listOf(switch.rowVersion),
            kmPosts = listOf(kmPost1.rowVersion, kmPost2.rowVersion),
        )
        fakeRatko.hostPushedSwitch("3.4.5.6.7")
        val officialSwitchVersion = switchDao.fetchVersion(switch.id, PublishType.OFFICIAL)!!
        switchService.saveDraft(
            switchDao.fetch(officialSwitchVersion).copy(stateCategory = LayoutStateCategory.FUTURE_EXISTING)
        )
        publishAndPush(switches = listOf(officialSwitchVersion))
        assertEquals(listOf<String>(), fakeRatko.getLocationTrackPointDeletions("1.2.3.4.5"))
        assertEquals(listOf<String>(), fakeRatko.getLocationTrackPointDeletions("2.3.4.5.6"))
        assertEquals(listOf<List<RatkoPoint>>(), fakeRatko.getUpdatedLocationTrackPoints("1.2.3.4.5"))
        assertEquals(listOf<List<RatkoPoint>>(), fakeRatko.getUpdatedLocationTrackPoints("2.3.4.5.6"))
        val pushedSwitch = fakeRatko.getLastPushedSwitch("3.4.5.6.7")
        assertEquals("FUTURE_EXISTING", pushedSwitch.state!!.category!!.name)
    }

    private fun setupDraftSwitchAndLocationTracks(trackNumberId: IntId<TrackLayoutTrackNumber>): Triple<DaoResponse<TrackLayoutSwitch>, DaoResponse<LocationTrack>, DaoResponse<LocationTrack>> {
        val switch = switchService.saveDraft(
            switch(
                123,
                joints = listOf(
                    switchJoint(124).copy(number = JointNumber(1), locationAccuracy = LocationAccuracy.OFFICIALLY_MEASURED_GEODETICALLY),
                    switchJoint(125).copy(number = JointNumber(3), locationAccuracy = LocationAccuracy.OFFICIALLY_MEASURED_GEODETICALLY),
                )
            )
        )
        val throughTrack = locationTrackService.saveDraft(
            locationTrack(trackNumberId, externalId = null),
            alignment(
                segment(Point(0.0, 0.0), Point(5.0, 0.0), switchId = switch.id, endJointNumber = JointNumber(1)),
                segment(Point(5.0, 0.0), Point(10.0, 0.0), switchId = switch.id, startJointNumber = JointNumber(3)),
            )
        )
        val branchingTrack = locationTrackService.saveDraft(
            locationTrack(trackNumberId, externalId = null),
            alignment(
                segment(Point(5.0, 0.0), Point(7.5, 0.5), switchId = switch.id, endJointNumber = JointNumber(3)),
                segment(Point(7.5, 0.5), Point(10.0, 1.0), switchId = switch.id, startJointNumber = JointNumber(3)),
            )
        )

        return Triple(switch, throughTrack, branchingTrack)
    }

    @Test
    fun linkKmPost() {
        val trackNumber = layoutTrackNumberDao.insert(trackNumber(getUnusedTrackNumber(), externalId = null))
        insertSomeOfficialReferenceLineFor(trackNumber.id)
        fakeRatko.acceptsNewRouteNumbersGivingThemOids(listOf("1.2.3.4.5"))
        publishAndPush(trackNumbers = listOf(trackNumber.rowVersion))
        fakeRatko.hasRouteNumber(ratkoRouteNumber(id = "1.2.3.4.5"))
        val pushedPoints = fakeRatko.getCreatedRouteNumberPoints("1.2.3.4.5")

        val kmPost = kmPostService.saveDraft(kmPost(trackNumber.id, KmNumber(1), Point(5.0, 0.0)))
        publishAndPush(kmPosts = listOf(kmPost.rowVersion))
        val deletions = fakeRatko.getRouteNumberPointDeletions("1.2.3.4.5")
        val updatedPoints = fakeRatko.getUpdatedRouteNumberPoints("1.2.3.4.5")
        assertEquals(listOf("0000", "0001"), deletions)
        // km post was placed precisely at meter point => coordinates shouldn't change
        assertEquals(
            pushedPoints.flatten().map { point -> point.geometry!!.coordinates },
            updatedPoints.flatten().map { point -> point.geometry!!.coordinates }
        )
        assertEquals("0000", updatedPoints[0].map { p -> p.kmM.kmNumber.toString() }.distinct().single())
        assertEquals("0001", updatedPoints[1].map { p -> p.kmM.kmNumber.toString() }.distinct().single())
    }

    @Test
    fun pushTrackNumberDeletion() {
        val originalTrackNumberVersion = layoutTrackNumberDao.insert(trackNumber(getUnusedTrackNumber())).rowVersion
        val trackNumberId = originalTrackNumberVersion.id
        val trackNumber = layoutTrackNumberDao.fetch(originalTrackNumberVersion)
        insertSomeOfficialReferenceLineFor(trackNumberId)
        trackNumberService.saveDraft(trackNumber.copy(state = LayoutState.DELETED))

        val originalLocationTrackVersion = locationTrackService.saveDraft(
            locationTrack(trackNumberId), alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0)))
        ).rowVersion
        val locationTrack = locationTrackDao.fetch(originalLocationTrackVersion)
        locationTrackService.saveDraft(locationTrack.copy(state = LayoutState.DELETED))

        fakeRatko.hasRouteNumber(ratkoRouteNumber(id = trackNumber.externalId.toString()))
        fakeRatko.hasLocationTrack(ratkoLocationTrack(id = locationTrack.externalId.toString()))

        publishAndPush(
            trackNumbers = listOf(originalTrackNumberVersion),
            locationTracks = listOf(originalLocationTrackVersion)
        )
        assertEquals(listOf(""), fakeRatko.getRouteNumberPointDeletions(trackNumber.externalId!!.toString()))
        assertEquals(listOf(""), fakeRatko.getLocationTrackPointDeletions(locationTrack.externalId!!.toString()))
    }

    @Test
    fun avoidPushingShortMetadata() {
        val trackNumber = trackNumber(getUnusedTrackNumber())
        val trackNumberId = layoutTrackNumberDao.insert(trackNumber).id
        insertSomeOfficialReferenceLineFor(trackNumberId)
        val plan = plan(
            trackNumberId,
            LAYOUT_SRID,
            // elements don't matter, only the names being different matters since that makes the metadatas distinct
            geometryAlignment(trackNumberId, elements = listOf(lineFromOrigin(1.0)), name = "foo"),
            geometryAlignment(trackNumberId, elements = listOf(lineFromOrigin(1.0)), name = "bar"),
        )
        val fileContent = "<a></a>"
        val planVersion = geometryDao.insertPlan(plan, InfraModelFile(plan.fileName, fileContent), null)
        val planAlignments = geometryDao.fetchPlan(planVersion).alignments

        val locationTrack = locationTrackService.saveDraft(
            locationTrack(trackNumberId, externalId = null),
            alignment(
                segment(Point(0.0, 0.0), Point(5.0, 0.0), sourceId = planAlignments[0].elements[0].id),
                segment(Point(5.0, 0.0), Point(5.6, 0.0), sourceId = planAlignments[1].elements[0].id),
            )
        )

        fakeRatko.acceptsNewLocationTrackGivingItOid("1.2.3.4.5")
        publishAndPush(locationTracks = listOf(locationTrack.rowVersion))
        val pushedMetadata = fakeRatko.getPushedMetadata(locationTrackOid = "1.2.3.4.5")
        assertEquals(1, pushedMetadata.size)
        val pushedNodes = pushedMetadata[0].locations[0].nodecollection.nodes.toList()
        assertEquals("0000+0000", pushedNodes[0].point.kmM.toString())
        assertEquals("0000+0005", pushedNodes[1].point.kmM.toString())
    }

    private fun insertSomeOfficialReferenceLineFor(trackNumberId: IntId<TrackLayoutTrackNumber>): DaoResponse<ReferenceLine> {
        return insertOfficialReferenceLineFromPair(
            referenceLineAndAlignment(
                trackNumberId,
                segment(
                    Point(0.0, 0.0),
                    Point(10.0, 0.0)
                )
            )
        )
    }

    private fun insertOfficialReferenceLineFromPair(pair: Pair<ReferenceLine, LayoutAlignment>): DaoResponse<ReferenceLine> {
        val alignmentVersion = alignmentDao.insert(pair.second)
        return referenceLineDao.insert(pair.first.copy(alignmentVersion = alignmentVersion))
    }

    private fun detachSwitchesFromTrack(locationTrackId: IntId<LocationTrack>) {
        val locationTrack = locationTrackDao.fetch(locationTrackDao.fetchDraftVersion(locationTrackId)!!)
        val alignment = alignmentDao.fetch(locationTrack.alignmentVersion!!)
        val deSwitchedAlignment = alignment.copy(
            segments = alignment.segments.map(LayoutSegment::withoutSwitch),
        )
        locationTrackService.saveDraft(locationTrack, deSwitchedAlignment)
    }

    private fun publishAndPush(
        trackNumbers: List<RowVersion<TrackLayoutTrackNumber>> = listOf(),
        referenceLines: List<RowVersion<ReferenceLine>> = listOf(),
        locationTracks: List<RowVersion<LocationTrack>> = listOf(),
        switches: List<RowVersion<TrackLayoutSwitch>> = listOf(),
        kmPosts: List<RowVersion<TrackLayoutKmPost>> = listOf(),
    ) {
        val ids = PublishRequestIds(
            trackNumbers = trackNumbers.map { it.id },
            referenceLines = referenceLines.map { it.id },
            locationTracks = locationTracks.map { it.id },
            switches = switches.map { it.id },
            kmPosts = kmPosts.map { it.id },
        )
        publicationService.updateExternalId(ids)
        val versions = publicationService.getValidationVersions(ids)
        val calculatedChanges = publicationService.getCalculatedChanges(versions)
        publicationService.publishChanges(
            versions,
            calculatedChanges,
            ""
        )
        ratkoService.pushChangesToRatko()
    }
}
