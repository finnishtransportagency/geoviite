package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LocationAccuracy
import fi.fta.geoviite.infra.common.LocationTrackDescriptionBase
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.MeasurementMethod
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumberDescription
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.geometryAlignment
import fi.fta.geoviite.infra.geometry.lineFromOrigin
import fi.fta.geoviite.infra.geometry.plan
import fi.fta.geoviite.infra.inframodel.InfraModelFile
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.boundingBoxAroundPoint
import fi.fta.geoviite.infra.publication.PublicationCause
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationRequest
import fi.fta.geoviite.infra.publication.PublicationRequestIds
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.publication.PublicationTestSupportService
import fi.fta.geoviite.infra.publication.publicationRequestIds
import fi.fta.geoviite.infra.ratko.model.OperationalPointType
import fi.fta.geoviite.infra.ratko.model.RatkoAssetLocation
import fi.fta.geoviite.infra.ratko.model.RatkoAssetState
import fi.fta.geoviite.infra.ratko.model.RatkoLocationTrackState
import fi.fta.geoviite.infra.ratko.model.RatkoLocationTrackType
import fi.fta.geoviite.infra.ratko.model.RatkoMeasurementMethod
import fi.fta.geoviite.infra.ratko.model.RatkoMetadataAsset
import fi.fta.geoviite.infra.ratko.model.RatkoNodeType
import fi.fta.geoviite.infra.ratko.model.RatkoOperatingPointParse
import fi.fta.geoviite.infra.ratko.model.RatkoPlan
import fi.fta.geoviite.infra.ratko.model.RatkoPlanId
import fi.fta.geoviite.infra.ratko.model.RatkoPlanItem
import fi.fta.geoviite.infra.ratko.model.RatkoPlanPhase
import fi.fta.geoviite.infra.ratko.model.RatkoPlanState
import fi.fta.geoviite.infra.ratko.model.RatkoPoint
import fi.fta.geoviite.infra.ratko.model.RatkoPointStates
import fi.fta.geoviite.infra.ratko.model.RatkoRouteNumber
import fi.fta.geoviite.infra.ratko.model.RatkoRouteNumberStateType
import fi.fta.geoviite.infra.split.BulkTransferState
import fi.fta.geoviite.infra.split.SplitDao
import fi.fta.geoviite.infra.split.SplitTestDataService
import fi.fta.geoviite.infra.tracklayout.DesignState
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutDesignDao
import fi.fta.geoviite.infra.tracklayout.LayoutDesignName
import fi.fta.geoviite.infra.tracklayout.LayoutDesignSaveRequest
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostService
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchJoint
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackDescriptionStructure
import fi.fta.geoviite.infra.tracklayout.LocationTrackDescriptionSuffix
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineService
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole
import fi.fta.geoviite.infra.tracklayout.TmpLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.asMainDraft
import fi.fta.geoviite.infra.tracklayout.edge
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.someOid
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.switchJoint
import fi.fta.geoviite.infra.tracklayout.switchLinkYV
import fi.fta.geoviite.infra.tracklayout.trackGeometry
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import fi.fta.geoviite.infra.tracklayout.trackNameStructure
import fi.fta.geoviite.infra.tracklayout.trackNumber
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.FreeTextWithNewLines
import fi.fta.geoviite.infra.util.queryOne
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

@ActiveProfiles("dev", "test")
@SpringBootTest
class RatkoServiceIT
@Autowired
constructor(
    val trackNumberService: LayoutTrackNumberService,
    val locationTrackService: LocationTrackService,
    val locationTrackDao: LocationTrackDao,
    val referenceLineService: ReferenceLineService,
    val referenceLineDao: ReferenceLineDao,
    val alignmentDao: LayoutAlignmentDao,
    val ratkoService: RatkoService,
    val ratkoLocalService: RatkoLocalService,
    val layoutTrackNumberDao: LayoutTrackNumberDao,
    val publicationService: PublicationService,
    val switchService: LayoutSwitchService,
    val switchDao: LayoutSwitchDao,
    val kmPostService: LayoutKmPostService,
    val kmPostDao: LayoutKmPostDao,
    val fakeRatkoService: FakeRatkoService,
    val geometryDao: GeometryDao,
    val publicationDao: PublicationDao,
    val splitDao: SplitDao,
    val splitTestDataService: SplitTestDataService,
    val layoutDesignDao: LayoutDesignDao,
    val publicationTestSupportService: PublicationTestSupportService,
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
                         layout.operating_point_version,
                         layout.design
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
    fun startRatkoPublish() {
        ratkoService.pushChangesToRatko(LayoutBranch.main)
    }

    @Test
    fun testChangeSet() {
        val referenceLineAlignmentVersion = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(10.0, 10.0))))
        val locationTrackGeometry = trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 10.0)))
        val trackNumber = trackNumber(testDBService.getUnusedTrackNumber(), draft = false)
        val trackNumberId = layoutTrackNumberDao.save(trackNumber).id
        trackNumberService.insertExternalId(LayoutBranch.main, trackNumberId, someOid())
        referenceLineDao.save(
            referenceLine(
                trackNumberId = trackNumberId,
                alignmentVersion = referenceLineAlignmentVersion,
                draft = false,
            )
        )
        val official =
            locationTrackDao.save(locationTrack(trackNumberId = trackNumberId, draft = false), locationTrackGeometry)
        val oid = Oid<LocationTrack>("1.2.3.4.5")
        locationTrackDao.insertExternalId(official.id, LayoutBranch.main, oid)
        val draft =
            locationTrackService.getOrThrow(MainLayoutContext.draft, official.id).let { orig ->
                orig.copy(name = AlignmentName("${orig.name}-draft"))
            }
        locationTrackService.saveDraft(LayoutBranch.main, draft, locationTrackGeometry)
        fakeRatko.hasLocationTrack(ratkoLocationTrack(id = oid.toString()))
        publishAndPush(locationTracks = listOf(official.id))
    }

    @Test
    fun pushNewTrackNumber() {
        val trackNumber = testDBService.getUnusedTrackNumber()
        val originalTrackNumber =
            layoutTrackNumberDao.save(trackNumber(trackNumber, description = "augh", draft = true))
        val referenceLine = insertReferenceLineFor(originalTrackNumber.id, draft = true).id

        fakeRatko.acceptsNewRouteNumbersGivingThemOids(listOf("1.2.3.4.5"))
        publishAndPush(trackNumbers = listOf(originalTrackNumber.id), referenceLines = listOf(referenceLine))

        val pushedRouteNumber = fakeRatko.getPushedRouteNumber(Oid("1.2.3.4.5"))
        assertEquals(trackNumber.value, pushedRouteNumber[0].name)
        assertEquals("augh", pushedRouteNumber[0].description)
        assertEquals(RatkoRouteNumberStateType.VALID, pushedRouteNumber[0].state.name)
    }

    @Test
    fun updateTrackNumber() {
        val originalTrackNumber = establishedTrackNumber()
        val newTrackNumber = testDBService.getUnusedTrackNumber()
        trackNumberService.update(
            LayoutBranch.main,
            originalTrackNumber.id,
            TrackNumberSaveRequest(
                newTrackNumber,
                TrackNumberDescription("aoeu"),
                LayoutState.IN_USE,
                TrackMeter(KmNumber("0123"), 0),
            ),
        )
        publishAndPush(trackNumbers = listOf(originalTrackNumber.id))
        val pushedRouteNumber = fakeRatko.getPushedRouteNumber(originalTrackNumber.externalId)
        assertEquals(newTrackNumber.value, pushedRouteNumber[0].name)
        assertEquals("aoeu", pushedRouteNumber[0].description)
        assertEquals(RatkoRouteNumberStateType.VALID, pushedRouteNumber[0].state.name)
    }

    @Test
    fun deleteTrackNumber() {
        val trackNumber = establishedTrackNumber()
        trackNumberService.update(
            LayoutBranch.main,
            trackNumber.id,
            TrackNumberSaveRequest(
                trackNumber.trackNumberObject.number,
                TrackNumberDescription("augh"),
                LayoutState.DELETED,
                TrackMeter(KmNumber("0123"), 0),
            ),
        )
        publishAndPush(trackNumbers = listOf(trackNumber.id))
        val pushedRouteNumber = fakeRatko.getPushedRouteNumber(trackNumber.externalId)
        assertEquals(RatkoRouteNumberStateType.NOT_VALID, pushedRouteNumber[0].state.name)
    }

    @Test
    fun pushAndDeleteLocationTrack() {
        val trackNumber = testDBService.getUnusedTrackNumber()
        val trackNumberId = layoutTrackNumberDao.save(trackNumber(trackNumber, description = "augh", draft = false)).id
        insertReferenceLineFor(trackNumberId, draft = false)
        layoutTrackNumberDao.insertExternalId(trackNumberId, LayoutBranch.main, Oid("1.2.3.4.5"))

        val locationTrackOriginal =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(
                    trackNumberId,
                    name = "abcde",
                    description = "cdefg",
                    type = LocationTrackType.CHORD,
                    state = LocationTrackState.BUILT,
                    draft = true,
                ),
                trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
            )
        fakeRatko.acceptsNewLocationTrackGivingItOid("2.3.4.5.6")
        publishAndPush(locationTracks = listOf(locationTrackOriginal.id))
        fakeRatko.hostPushedLocationTrack("2.3.4.5.6")
        val officialVersion = locationTrackDao.fetchVersionOrThrow(MainLayoutContext.official, locationTrackOriginal.id)
        val createdPush = fakeRatko.getLastPushedLocationTrack("2.3.4.5.6")!!
        assertEquals("abcde", createdPush.name)
        assertEquals("cdefg", createdPush.description)
        assertEquals(RatkoAssetState.BUILT.name, createdPush.state.name)
        assertEquals(RatkoLocationTrackType.CHORD.name, createdPush.type.name)

        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackDao.fetch(officialVersion).copy(state = LocationTrackState.DELETED),
            alignmentDao.fetch(officialVersion),
        )
        publishAndPush(locationTracks = listOf(locationTrackOriginal.id))

        assertEquals(listOf(""), fakeRatko.getLocationTrackPointDeletions("2.3.4.5.6"))
        val deletedPush = fakeRatko.getLastPushedLocationTrack("2.3.4.5.6")!!
        assertEquals(RatkoLocationTrackState.DELETED.name, deletedPush.state.name)
    }

    @Test
    fun modifyLocationTrackProperties() {
        val trackNumber = testDBService.getUnusedTrackNumber()
        val trackNumberId = layoutTrackNumberDao.save(trackNumber(trackNumber, description = "augh", draft = false)).id
        insertReferenceLineFor(trackNumberId, draft = false)
        layoutTrackNumberDao.insertExternalId(trackNumberId, LayoutBranch.main, Oid("1.2.3.4.5"))

        val locationTrackOriginal =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(
                    trackNumberId,
                    name = "abcde",
                    description = "cdefg",
                    type = LocationTrackType.CHORD,
                    draft = true,
                ),
                trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
            )
        fakeRatko.acceptsNewLocationTrackGivingItOid("2.3.4.5.6")
        publishAndPush(locationTracks = listOf(locationTrackOriginal.id))
        fakeRatko.hostPushedLocationTrack("2.3.4.5.6")
        val officialVersion = locationTrackDao.fetchVersionOrThrow(MainLayoutContext.official, locationTrackOriginal.id)
        locationTrackService.saveDraft(
            LayoutBranch.main,
            asMainDraft(
                locationTrackDao
                    .fetch(officialVersion)
                    .copy(
                        descriptionStructure =
                            LocationTrackDescriptionStructure(
                                LocationTrackDescriptionBase("aoeu"),
                                LocationTrackDescriptionSuffix.NONE,
                            ),
                        nameStructure = trackNameStructure("uuba aaba"),
                        type = LocationTrackType.MAIN,
                    )
            ),
            alignmentDao.fetch(officialVersion),
        )
        publishAndPush(locationTracks = listOf(locationTrackOriginal.id))
        val pushed = fakeRatko.getLastPushedLocationTrack("2.3.4.5.6")!!
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
        val trackNumber = testDBService.getUnusedTrackNumber()
        val trackNumberId = layoutTrackNumberDao.save(trackNumber(trackNumber, description = "augh", draft = false)).id
        insertReferenceLineFor(trackNumberId, draft = false)
        layoutTrackNumberDao.insertExternalId(trackNumberId, LayoutBranch.main, Oid("1.2.3.4.5"))

        val locationTrackOriginal =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(
                    trackNumberId,
                    name = "abcde",
                    description = "cdefg",
                    type = LocationTrackType.CHORD,
                    draft = true,
                ),
                trackGeometryOfSegments(segment(Point(4.0, 0.0), Point(6.0, 0.0))),
            )
        fakeRatko.acceptsNewLocationTrackGivingItOid("2.3.4.5.6")
        publishAndPush(locationTracks = listOf(locationTrackOriginal.id))
        fakeRatko.hostPushedLocationTrack("2.3.4.5.6")
        val officialVersion = locationTrackDao.fetchVersionOrThrow(MainLayoutContext.official, locationTrackOriginal.id)
        locationTrackService.saveDraft(
            LayoutBranch.main,
            asMainDraft(locationTrackDao.fetch(officialVersion)),
            trackGeometryOfSegments(segment(Point(2.0, 0.0), Point(8.0, 0.0))),
        )
        publishAndPush(locationTracks = listOf(locationTrackOriginal.id))
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
        val trackNumber = testDBService.getUnusedTrackNumber()
        val trackNumberId = layoutTrackNumberDao.save(trackNumber(trackNumber, description = "augh", draft = false)).id
        insertReferenceLineFor(trackNumberId, draft = false)
        layoutTrackNumberDao.insertExternalId(trackNumberId, LayoutBranch.main, Oid("1.2.3.4.5"))

        val locationTrackOriginal =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(
                    trackNumberId,
                    name = "abcde",
                    description = "cdefg",
                    type = LocationTrackType.CHORD,
                    draft = true,
                ),
                trackGeometryOfSegments(segment(Point(2.0, 0.0), Point(8.0, 0.0))),
            )
        fakeRatko.acceptsNewLocationTrackGivingItOid("2.3.4.5.6")
        publishAndPush(locationTracks = listOf(locationTrackOriginal.id))
        fakeRatko.hostPushedLocationTrack("2.3.4.5.6")
        val officialVersion = locationTrackDao.fetchVersionOrThrow(MainLayoutContext.official, locationTrackOriginal.id)
        locationTrackService.saveDraft(
            LayoutBranch.main,
            asMainDraft(locationTrackDao.fetch(officialVersion)),
            trackGeometryOfSegments(segment(Point(4.0, 0.0), Point(6.0, 0.0))),
        )
        publishAndPush(locationTracks = listOf(locationTrackOriginal.id))
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
        val trackNumber = testDBService.getUnusedTrackNumber()
        val trackNumberId = layoutTrackNumberDao.save(trackNumber(trackNumber, description = "augh", draft = false)).id
        insertReferenceLineFor(trackNumberId, draft = false)
        layoutTrackNumberDao.insertExternalId(trackNumberId, LayoutBranch.main, Oid("1.2.3.4.5"))

        val locationTrackOriginal =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(
                    trackNumberId,
                    name = "abcde",
                    description = "cdefg",
                    type = LocationTrackType.CHORD,
                    draft = true,
                ),
                trackGeometryOfSegments(
                    segment(Point(0.0, 0.0), Point(1.0, 0.0), Point(5.0, 3.0), Point(9.0, 0.0), Point(10.0, 0.0))
                ),
            )
        fakeRatko.acceptsNewLocationTrackGivingItOid("2.3.4.5.6")
        publishAndPush(locationTracks = listOf(locationTrackOriginal.id))
        fakeRatko.hostPushedLocationTrack("2.3.4.5.6")
        val officialVersion = locationTrackDao.fetchVersionOrThrow(MainLayoutContext.official, locationTrackOriginal.id)
        locationTrackService.saveDraft(
            LayoutBranch.main,
            asMainDraft(locationTrackDao.fetch(officialVersion)),
            trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
        )
        publishAndPush(locationTracks = listOf(locationTrackOriginal.id))
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
                0.00001,
            )
        }
        assertTrue(createdPoints[0].subList(1, 8).all { p -> p.geometry!!.coordinates[1] > 0.0 })
        assertEquals(List(9) { 0.0 }, updatedPoints[0].map { p -> p.geometry!!.coordinates[1] })
        assertEquals(listOf("0000"), deletedPoints)
    }

    @Test
    fun `push new deleted location track without points`() {
        val trackNumber = testDBService.getUnusedTrackNumber()
        val trackNumberId = layoutTrackNumberDao.save(trackNumber(trackNumber, description = "augh", draft = false)).id
        insertReferenceLineFor(trackNumberId, draft = false)
        layoutTrackNumberDao.insertExternalId(trackNumberId, LayoutBranch.main, Oid("1.2.3.4.5"))

        val locationTrackOriginal =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(
                    trackNumberId,
                    name = "abcde",
                    description = "cdefg",
                    type = LocationTrackType.CHORD,
                    draft = true,
                    state = LocationTrackState.DELETED,
                ),
                trackGeometryOfSegments(
                    segment(Point(0.0, 0.0), Point(1.0, 0.0), Point(5.0, 3.0), Point(9.0, 0.0), Point(10.0, 0.0))
                ),
            )
        fakeRatko.acceptsNewLocationTrackWithoutPointsGivingItOid("2.3.4.5.6")
        publishAndPush(locationTracks = listOf(locationTrackOriginal.id))
        fakeRatko.hostLocationTrackOid("2.3.4.5.6")
        val officialVersion = locationTrackDao.fetchVersionOrThrow(MainLayoutContext.official, locationTrackOriginal.id)
        locationTrackService.saveDraft(
            LayoutBranch.main,
            asMainDraft(locationTrackDao.fetch(officialVersion)),
            trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
        )
        publishAndPush(locationTracks = listOf(locationTrackOriginal.id))
    }

    @Test
    fun pushLocationTrackMetadata() {
        val trackNumber = testDBService.getUnusedTrackNumber()
        val trackNumberId = layoutTrackNumberDao.save(trackNumber(trackNumber, description = "augh", draft = false)).id
        insertReferenceLineFor(trackNumberId, draft = false)
        layoutTrackNumberDao.insertExternalId(trackNumberId, LayoutBranch.main, Oid("1.2.3.4.5"))

        val planVersion =
            geometryDao.insertPlan(
                plan(
                    trackNumber,
                    srid = Srid(3879),
                    measurementMethod = MeasurementMethod.OFFICIALLY_MEASURED_GEODETICALLY,
                    planTime = Instant.parse("2018-11-30T18:35:24.00Z"),
                    alignments = listOf(geometryAlignment(name = "geo name", elements = listOf(lineFromOrigin(1.0)))),
                ),
                InfraModelFile(FileName("foobar"), "<a></a>"),
                null,
            )
        val plan = geometryDao.fetchPlan(planVersion)

        val locationTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(
                    trackNumberId,
                    name = "abcde",
                    description = "cdefg",
                    type = LocationTrackType.CHORD,
                    draft = true,
                ),
                trackGeometryOfSegments(
                    segment(Point(2.0, 0.0), Point(8.0, 0.0), sourceId = plan.alignments[0].elements[0].id)
                ),
            )
        fakeRatko.acceptsNewLocationTrackGivingItOid("2.3.4.5.6")
        publishAndPush(locationTracks = listOf(locationTrack.id))
        val metadata = fakeRatko.getPushedMetadata(locationTrackOid = "2.3.4.5.6")

        val props = metadata[0].properties
        fun prop(name: String) = props.find { p -> p.name == name }!!
        assertEquals("foobar", prop("filename").stringValue)
        assertEquals(
            RatkoMeasurementMethod.OFFICIALLY_MEASURED_GEODETICALLY.value,
            prop("measurement_method").enumValue,
        )
        assertEquals(2018, prop("created_year").integerValue)
        assertEquals("EPSG:3879", prop("original_crs").stringValue)
        assertEquals("geo name", prop("alignment").stringValue)
    }

    @Test
    fun pushTwoTrackNumbers() {
        val trackNumber1 = testDBService.getUnusedTrackNumber()
        val trackNumber1Version =
            trackNumberService.saveDraft(LayoutBranch.main, trackNumber(trackNumber1, draft = true))
        val referenceLine1 = insertReferenceLineFor(trackNumber1Version.id, draft = true).id
        val trackNumber2 = testDBService.getUnusedTrackNumber()
        val trackNumber2Version =
            trackNumberService.saveDraft(LayoutBranch.main, trackNumber(trackNumber2, draft = true))
        val referenceLine2 = insertReferenceLineFor(trackNumber2Version.id, draft = true).id

        fakeRatko.acceptsNewRouteNumbersGivingThemOids(listOf("2.3.4.5.6", "3.4.5.6.7"))
        publishAndPush(
            trackNumbers = listOf(trackNumber1Version.id, trackNumber2Version.id),
            referenceLines = listOf(referenceLine1, referenceLine2),
        )
        val pushedRouteNumber1 = fakeRatko.getPushedRouteNumber(Oid("2.3.4.5.6"))
        val pushedRouteNumber2 = fakeRatko.getPushedRouteNumber(Oid("3.4.5.6.7"))
        assertEquals(trackNumber1.value, pushedRouteNumber1[0].name)
        assertEquals(trackNumber2.value, pushedRouteNumber2[0].name)
    }

    @Test
    fun lengthenReferenceLine() {
        val trackNumber = testDBService.getUnusedTrackNumber()
        val originalTrackNumber =
            layoutTrackNumberDao.save(trackNumber(trackNumber, description = "augh", draft = true))
        val originalReferenceLineVersion = insertReferenceLineFor(originalTrackNumber.id, draft = true)
        val originalReferenceLine = referenceLineDao.fetch(originalReferenceLineVersion)

        fakeRatko.acceptsNewRouteNumbersGivingThemOids(listOf("1.2.3.4.5"))
        publishAndPush(
            trackNumbers = listOf(originalTrackNumber.id),
            referenceLines = listOf(originalReferenceLineVersion.id),
        )
        mainDraftContext.save(originalReferenceLine, alignment(segment(Point(0.0, 0.0), Point(20.0, 0.0))))
        publishAndPush(referenceLines = listOf(originalReferenceLineVersion.id))
        val pushedPoints = fakeRatko.getCreatedRouteNumberPoints("1.2.3.4.5")
        assertEquals(9, pushedPoints[0].size)
        assertEquals(19, pushedPoints[1].size)
        assertEquals(
            pushedPoints[0].map { p -> p.geometry?.coordinates },
            pushedPoints[1].take(9).map { p -> p.geometry?.coordinates },
        )
        assertEquals((1..19).toList(), pushedPoints[1].map { p -> p.kmM.meters.toInt() })
    }

    @Test
    fun shortenReferenceLine() {
        val trackNumber = testDBService.getUnusedTrackNumber()
        val originalTrackNumber =
            trackNumberService.saveDraft(
                LayoutBranch.main,
                trackNumber(trackNumber, description = "augh", draft = true),
            )
        val originalReferenceLineDaoResponse =
            referenceLineService.saveDraft(
                LayoutBranch.main,
                referenceLine(originalTrackNumber.id, draft = true),
                alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
            )

        fakeRatko.acceptsNewRouteNumbersGivingThemOids(listOf("1.2.3.4.5"))
        publishAndPush(
            trackNumbers = listOf(originalTrackNumber.id),
            referenceLines = listOf(originalReferenceLineDaoResponse.id),
        )
        fakeRatko.hasRouteNumber(ratkoRouteNumber("1.2.3.4.5"))
        referenceLineService.saveDraft(
            LayoutBranch.main,
            asMainDraft(
                referenceLineDao.fetch(
                    referenceLineDao.fetchVersionByTrackNumberId(MainLayoutContext.official, originalTrackNumber.id)!!
                )
            ),
            alignment(segment(Point(0.0, 0.0), Point(5.0, 0.0))),
        )
        publishAndPush(referenceLines = listOf(originalReferenceLineDaoResponse.id))
        val pushedPoints = fakeRatko.getCreatedRouteNumberPoints("1.2.3.4.5")
        val updatedPoints = fakeRatko.getUpdatedRouteNumberPoints("1.2.3.4.5")
        val deletions = fakeRatko.getRouteNumberPointDeletions("1.2.3.4.5")
        assertEquals(9, pushedPoints[0].size)
        assertEquals(4, updatedPoints[0].size)
        assertEquals(
            pushedPoints[0].take(4).map { p -> p.geometry?.coordinates },
            updatedPoints[0].map { p -> p.geometry?.coordinates },
        )
        assertEquals(RatkoPointStates.VALID, pushedPoints[0].map { p -> p.state?.name }.distinct().single())
        assertEquals(RatkoPointStates.VALID, updatedPoints[0].map { p -> p.state?.name }.distinct().single())
        assertEquals(listOf("0000"), deletions)
    }

    @Test
    fun changeReferenceLineGeometry() {
        val trackNumber = testDBService.getUnusedTrackNumber()
        val originalTrackNumber =
            trackNumberService.saveDraft(
                LayoutBranch.main,
                trackNumber(trackNumber, description = "augh", draft = true),
            )
        val originalReferenceLineDaoResponse =
            referenceLineService.saveDraft(
                LayoutBranch.main,
                referenceLine(originalTrackNumber.id, draft = true),
                alignment(
                    segment(
                        Point(0.0, 0.0),
                        Point(10.0, 0.0),
                        Point(20.0, 1.0), // reference line has bump
                        Point(30.0, 0.0),
                        Point(40.0, 0.0),
                    )
                ),
            )
        val locationTrackDaoResponse =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(originalTrackNumber.id, draft = true),
                trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(40.0, 0.0))),
            )

        fakeRatko.acceptsNewRouteNumbersGivingThemOids(listOf("1.2.3.4.5"))
        fakeRatko.acceptsNewLocationTrackGivingItOid("2.3.4.5.6")
        publishAndPush(
            trackNumbers = listOf(originalTrackNumber.id),
            referenceLines = listOf(originalReferenceLineDaoResponse.id),
            locationTracks = listOf(locationTrackDaoResponse.id),
        )
        fakeRatko.hasRouteNumber(ratkoRouteNumber("1.2.3.4.5"))
        fakeRatko.hostPushedLocationTrack("2.3.4.5.6")
        referenceLineService.saveDraft(
            LayoutBranch.main,
            asMainDraft(
                referenceLineDao.fetch(
                    referenceLineDao.fetchVersionByTrackNumberId(MainLayoutContext.official, originalTrackNumber.id)!!
                )
            ),
            alignment(segment(Point(0.0, 0.0), Point(40.0, 0.0))),
        )
        publishAndPush(
            // not publishing a change to location track, but we want to update its points anyway
            // due to reference line
            // geometry change
            referenceLines = listOf(originalReferenceLineDaoResponse.id)
        )
        val deletedOnTrack = fakeRatko.getLocationTrackPointDeletions("2.3.4.5.6")
        val updatedOnTrack = fakeRatko.getUpdatedLocationTrackPoints("2.3.4.5.6")
        val createdOnTrack = fakeRatko.getCreatedLocationTrackPoints("2.3.4.5.6")
        // bump in reference line was after 10 m, so the addresses of the first 9 points (separate
        // of start point) on
        // the track don't move, but the rest do
        assertEquals(40, createdOnTrack[0].size)
        assertEquals(39, updatedOnTrack[0].size)
        assertEquals(
            createdOnTrack[0].take(9).map { p -> p.geometry?.coordinates?.get(0) },
            updatedOnTrack[0].take(9).map { p -> p.geometry?.coordinates?.get(0) },
        )
        (10..38).forEach { i ->
            assertNotEquals(
                createdOnTrack[0][i].geometry?.coordinates?.get(0),
                updatedOnTrack[0][i].geometry?.coordinates?.get(0),
            )
        }
        assertEquals(listOf("0000"), deletedOnTrack)
    }

    @Test
    fun `push new deleted track number and reference line without points`() {
        val trackNumber = testDBService.getUnusedTrackNumber()
        val originalTrackNumber =
            trackNumberService.saveDraft(
                LayoutBranch.main,
                trackNumber(trackNumber, description = "augh", draft = true, state = LayoutState.DELETED),
            )
        val originalReferenceLineDaoResponse =
            referenceLineService.saveDraft(
                LayoutBranch.main,
                referenceLine(originalTrackNumber.id, draft = true),
                alignment(
                    segment(
                        Point(0.0, 0.0),
                        Point(10.0, 0.0),
                        Point(20.0, 1.0), // reference line has bump
                        Point(30.0, 0.0),
                        Point(40.0, 0.0),
                    )
                ),
            )

        fakeRatko.acceptsNewRouteNumbersWithoutPointsGivingThemOids(listOf("1.2.3.4.5"))
        publishAndPush(
            trackNumbers = listOf(originalTrackNumber.id),
            referenceLines = listOf(originalReferenceLineDaoResponse.id),
        )
        fakeRatko.hasRouteNumber(ratkoRouteNumber("1.2.3.4.5"))
    }

    @Test
    fun removeKmPostBeforeSwitch() {
        val trackNumber = testDBService.getUnusedTrackNumber()
        val trackNumberId = layoutTrackNumberDao.save(trackNumber(trackNumber, description = "augh", draft = false)).id
        insertReferenceLineFor(trackNumberId, draft = false)
        layoutTrackNumberDao.insertExternalId(trackNumberId, LayoutBranch.main, Oid("1.1.1.1.1"))

        val kmPost1 =
            kmPostService.saveDraft(
                LayoutBranch.main,
                kmPost(trackNumberId, KmNumber(1), Point(2.0, 0.0), draft = true),
            )
        val kmPost2 =
            kmPostService.saveDraft(
                LayoutBranch.main,
                kmPost(trackNumberId, KmNumber(2), Point(4.0, 0.0), draft = true),
            )

        val (switch, throughTrack, branchingTrack) = setupDraftSwitchAndLocationTracks(trackNumberId)
        listOf("1.2.3.4.5", "2.3.4.5.6").forEach(fakeRatko::acceptsNewLocationTrackGivingItOid)
        fakeRatko.acceptsNewSwitchGivingItOid("3.4.5.6.7")
        fakeRatko.hasRouteNumber(ratkoRouteNumber("1.1.1.1.1"))

        publishAndPush(
            locationTracks = listOf(throughTrack.id, branchingTrack.id),
            switches = listOf(switch.id),
            kmPosts = listOf(kmPost1.id, kmPost2.id),
        )

        kmPostService.saveDraft(
            LayoutBranch.main,
            asMainDraft(kmPostDao.getOrThrow(MainLayoutContext.official, kmPost2.id)).copy(state = LayoutState.DELETED),
        )
        listOf("1.2.3.4.5", "2.3.4.5.6").forEach(fakeRatko::hostPushedLocationTrack)
        fakeRatko.hostPushedSwitch("3.4.5.6.7")

        publishAndPush(kmPosts = listOf(kmPost2.id))

        val switchLocations = fakeRatko.getPushedSwitchLocations("3.4.5.6.7")
        val createPush = switchLocations.first().let(::sortRatkoSwitchLocationsByTrack)
        val updatePush = switchLocations.last().let(::sortRatkoSwitchLocationsByTrack)

        // switch was originally after kmPost2, but it got removed and then we pushed again. kmPost2 was at m=4, while
        // throughTrack's non-math joints are at 0 and 9.5, branchingTrack's at 0 and 7.5.
        assertEquals(
            listOf(listOf("0000+0000", "0002+0005.5"), listOf("0000+0000", "0002+0003.5")),
            createPush.map { track -> track.nodecollection.nodes.map { joint -> joint.point.kmM.toString() } },
        )
        assertEquals(
            listOf(listOf("0001+0007.5"), listOf("0001+0005.5")),
            updatePush.map { track -> track.nodecollection.nodes.map { joint -> joint.point.kmM.toString() } },
        )
    }

    private fun sortRatkoSwitchLocationsByTrack(locations: List<RatkoAssetLocation>) =
        locations.sortedBy { it.nodecollection.nodes.first().point.locationtrack.toString() }

    @Test
    fun removeLocationTrackWithSwitch() {
        val trackNumber = establishedTrackNumber()
        val kmPost1 =
            kmPostService.saveDraft(
                LayoutBranch.main,
                kmPost(trackNumber.id, KmNumber(1), Point(2.0, 0.0), draft = true),
            )
        val kmPost2 =
            kmPostService.saveDraft(
                LayoutBranch.main,
                kmPost(trackNumber.id, KmNumber(2), Point(4.0, 0.0), draft = true),
            )

        val (switch, throughTrack, branchingTrack) = setupDraftSwitchAndLocationTracks(trackNumber.id)
        listOf("1.2.3.4.5", "2.3.4.5.6").forEach(fakeRatko::acceptsNewLocationTrackGivingItOid)
        fakeRatko.acceptsNewSwitchGivingItOid("3.4.5.6.7")

        publishAndPush(
            locationTracks = listOf(throughTrack.id, branchingTrack.id),
            switches = listOf(switch.id),
            kmPosts = listOf(kmPost1.id, kmPost2.id),
        )
        listOf("1.2.3.4.5", "2.3.4.5.6").forEach(fakeRatko::hostPushedLocationTrack)
        val officialThroughTrackVersion =
            locationTrackDao.fetchVersionOrThrow(MainLayoutContext.official, throughTrack.id)
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackDao.fetch(officialThroughTrackVersion).copy(state = LocationTrackState.DELETED),
            alignmentDao.fetch(officialThroughTrackVersion),
        )
        publishAndPush(locationTracks = listOf(throughTrack.id))
        val pushedSwitchLocations = fakeRatko.getPushedSwitchLocations("3.4.5.6.7")

        val createPush = pushedSwitchLocations.first().let(::sortRatkoSwitchLocationsByTrack)
        assertEquals(2, createPush.size)
        assertEquals(
            listOf(listOf("JOINT_A", "JOINT_B"), listOf("JOINT_A", "JOINT_C")),
            createPush.map { track -> track.nodecollection.nodes.map { node -> node.nodeType.name } },
        )
        val updatePush = pushedSwitchLocations.last()
        assertEquals(1, updatePush.size)
        assertEquals(
            listOf("JOINT_A", "JOINT_B"),
            updatePush[0].nodecollection.nodes.map { node -> node.nodeType.name },
        )
    }

    @Test
    fun modifySwitchJointPositionAccuracy() {
        val trackNumber = establishedTrackNumber()
        val kmPost1 =
            kmPostService.saveDraft(
                LayoutBranch.main,
                kmPost(trackNumber.id, KmNumber(1), Point(2.0, 0.0), draft = true),
            )
        val kmPost2 =
            kmPostService.saveDraft(
                LayoutBranch.main,
                kmPost(trackNumber.id, KmNumber(2), Point(4.0, 0.0), draft = true),
            )

        val (switch, throughTrack, branchingTrack) = setupDraftSwitchAndLocationTracks(trackNumber.id)
        listOf("1.2.3.4.5", "2.3.4.5.6").forEach(fakeRatko::acceptsNewLocationTrackGivingItOid)
        fakeRatko.acceptsNewSwitchGivingItOid("3.4.5.6.7")

        publishAndPush(
            locationTracks = listOf(throughTrack.id, branchingTrack.id),
            switches = listOf(switch.id),
            kmPosts = listOf(kmPost1.id, kmPost2.id),
        )
        listOf("1.2.3.4.5", "2.3.4.5.6").forEach(fakeRatko::hostPushedLocationTrack)
        fakeRatko.hostPushedSwitch("3.4.5.6.7")

        val officialSwitchVersion = switchDao.fetchVersionOrThrow(MainLayoutContext.official, switch.id)
        val officialSwitch = switchDao.fetch(officialSwitchVersion)
        switchService.saveDraft(
            LayoutBranch.main,
            officialSwitch.copy(
                joints =
                    officialSwitch.joints.map { j -> j.copy(locationAccuracy = LocationAccuracy.GEOMETRY_CALCULATED) }
            ),
        )
        publishAndPush(switches = listOf(switch.id))
        val pushedSwitchGeoms = fakeRatko.getPushedSwitchGeometries("3.4.5.6.7")
        assertEquals(
            (0 until 4).toList().map { "OFFICIALLY MEASURED GEODETICALLY" },
            pushedSwitchGeoms[0].map { geom -> geom.assetGeomAccuracyType.value },
        )
        assertEquals(
            (0 until 4).toList().map { "GEOMETRY CALCULATED" },
            pushedSwitchGeoms[1].map { geom -> geom.assetGeomAccuracyType.value },
        )
    }

    @Test
    fun pushSwitch() {
        val trackNumber = establishedTrackNumber()

        val (switch, throughTrack, branchingTrack) = setupDraftSwitchAndLocationTracks(trackNumber.id, "TV123")

        listOf("1.2.3.4.5", "2.3.4.5.6").forEach(fakeRatko::acceptsNewLocationTrackGivingItOid)
        fakeRatko.acceptsNewSwitchGivingItOid("3.4.5.6.7")

        publishAndPush(locationTracks = listOf(throughTrack.id, branchingTrack.id), switches = listOf(switch.id))
        val switchLocations = fakeRatko.getPushedSwitchLocations("3.4.5.6.7")[0].let(::sortRatkoSwitchLocationsByTrack)

        assertEquals(
            listOf(listOf(0.0, 9.5), listOf(0.0, 7.5)),
            switchLocations.map { l -> l.nodecollection.nodes.map { p -> p.point.kmM.meters.toDouble() } },
        )
        assertEquals(
            listOf(
                listOf(RatkoNodeType.JOINT_A, RatkoNodeType.JOINT_B),
                listOf(RatkoNodeType.JOINT_A, RatkoNodeType.JOINT_C),
            ),
            switchLocations.map { l -> l.nodecollection.nodes.map { n -> n.nodeType } },
        )
        assertEquals(
            listOf(listOf("1.2.3.4.5", "1.2.3.4.5"), listOf("2.3.4.5.6", "2.3.4.5.6")),
            switchLocations.map { push -> push.nodecollection.nodes.map { n -> n.point.locationtrack!!.toString() } },
        )
        assertEquals(
            trackNumber.externalId.toString(),
            switchLocations
                .map { s -> s.nodecollection.nodes.first().point.routenumber!!.toString() }
                .distinct()
                .single(),
        )
        assertEquals(listOf(1, 2), switchLocations.map { s -> s.priority })
        val pushedSwitch = fakeRatko.getLastPushedSwitch("3.4.5.6.7")!!
        fun prop(name: String) = pushedSwitch.properties!!.find { p -> p.name == name }!!
        assertEquals("TV123", prop("name").stringValue)
        assertEquals("TV123", prop("turnout_id").stringValue)
    }

    @Test
    fun `push new deleted switch without data`() {
        val trackNumber = establishedTrackNumber()

        val (switch, throughTrack, branchingTrack) = setupDraftSwitchAndLocationTracks(trackNumber.id, "TV123")

        // establish switch as existing with one publication, then delete it in another, so both
        // publications validate but the integration has to contend with pushing an already-deleted
        // switch
        publicationTestSupportService.publish(
            LayoutBranch.main,
            publicationRequestIds(
                locationTracks = listOf(throughTrack.id, branchingTrack.id),
                switches = listOf(switch.id),
            ),
        )
        mainDraftContext.save(switchDao.fetch(switch).copy(stateCategory = LayoutStateCategory.NOT_EXISTING))
        detachSwitchesFromTrack(throughTrack.id)
        detachSwitchesFromTrack(branchingTrack.id)

        listOf("1.2.3.4.5", "2.3.4.5.6").forEach(fakeRatko::acceptsNewLocationTrackGivingItOid)
        fakeRatko.acceptsNewSwitchWithoutDataGivingItOid("3.4.5.6.7")

        publishAndPush(locationTracks = listOf(throughTrack.id, branchingTrack.id), switches = listOf(switch.id))
    }

    @Test
    fun moveSwitch() {
        val trackNumber = establishedTrackNumber()

        val (switch, throughTrack, branchingTrack) = setupDraftSwitchAndLocationTracks(trackNumber.id)

        listOf("1.2.3.4.5", "2.3.4.5.6").forEach(fakeRatko::acceptsNewLocationTrackGivingItOid)
        fakeRatko.acceptsNewSwitchGivingItOid("3.4.5.6.7")

        publishAndPush(locationTracks = listOf(throughTrack.id, branchingTrack.id), switches = listOf(switch.id))
        fakeRatko.hasSwitch(fakeRatko.getLastPushedSwitch("3.4.5.6.7")!!)
        listOf("1.2.3.4.5", "2.3.4.5.6").forEach(fakeRatko::hostPushedLocationTrack)

        detachSwitchesFromTrack(throughTrack.id)
        detachSwitchesFromTrack(branchingTrack.id)

        val differentThroughTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumber.id, draft = true),
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(switch.id, 1),
                        endInnerSwitch = switchLinkYV(switch.id, 5),
                        segments = listOf(segment(Point(0.0, 10.0), Point(5.0, 10.0))),
                    ),
                    edge(
                        startInnerSwitch = switchLinkYV(switch.id, 5),
                        endInnerSwitch = switchLinkYV(switch.id, 2),
                        segments = listOf(segment(Point(5.0, 10.0), Point(10.0, 10.0))),
                    ),
                ),
            )
        val differentBranchingTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumber.id, draft = true),
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(switch.id, 1),
                        endInnerSwitch = switchLinkYV(switch.id, 3),
                        segments =
                            listOf(
                                segment(Point(5.0, 10.0), Point(7.5, 10.5)),
                                segment(Point(7.5, 10.5), Point(10.0, 11.0)),
                            ),
                    )
                ),
            )
        listOf("4.4.4.4.4", "5.5.5.5.5").forEach(fakeRatko::acceptsNewLocationTrackGivingItOid)
        publishAndPush(locationTracks = listOf(differentThroughTrack.id, differentBranchingTrack.id))
        val switchLocations = fakeRatko.getPushedSwitchLocations("3.4.5.6.7")
        val initialPush = switchLocations.first().let(::sortRatkoSwitchLocationsByTrack)
        val updatePush = switchLocations.last().let(::sortRatkoSwitchLocationsByTrack)

        assertEquals(
            listOf(
                listOf(RatkoNodeType.JOINT_A, RatkoNodeType.JOINT_B),
                listOf(RatkoNodeType.JOINT_A, RatkoNodeType.JOINT_C),
            ),
            initialPush.map { location -> location.nodecollection.nodes.map { n -> n.nodeType } },
        )

        assertEquals(
            listOf(
                listOf(RatkoNodeType.JOINT_A, RatkoNodeType.JOINT_B),
                listOf(RatkoNodeType.JOINT_A, RatkoNodeType.JOINT_C),
            ),
            updatePush.map { location -> location.nodecollection.nodes.map { n -> n.nodeType } },
        )

        assertEquals(
            listOf(listOf("1.2.3.4.5", "1.2.3.4.5"), listOf("2.3.4.5.6", "2.3.4.5.6")),
            initialPush.map { location -> location.nodecollection.nodes.map { n -> n.point.locationtrack.toString() } },
        )
        assertEquals(
            listOf(listOf("4.4.4.4.4", "4.4.4.4.4"), listOf("5.5.5.5.5", "5.5.5.5.5")),
            updatePush.map { location -> location.nodecollection.nodes.map { n -> n.point.locationtrack.toString() } },
        )
    }

    /**
     * Setup a switch and tracks such that:
     * - throughTrack has joint sequence 1,5,2 at positions (0, 0), (5, 0), (9.5, 0)
     * - branchingTrack has joint sequence 1,3 at positions (0, 0), (7.5, 0.5)
     */
    private fun setupDraftSwitchAndLocationTracks(
        trackNumberId: IntId<LayoutTrackNumber>,
        switchName: String = "TV123",
        switchStateCategory: LayoutStateCategory = LayoutStateCategory.EXISTING,
    ): Triple<LayoutRowVersion<LayoutSwitch>, LayoutRowVersion<LocationTrack>, LayoutRowVersion<LocationTrack>> {
        val switch =
            switchService.saveDraft(
                LayoutBranch.main,
                switch(
                    name = switchName,
                    joints =
                        listOf(
                            switchJoint(124)
                                .copy(
                                    number = JointNumber(1),
                                    locationAccuracy = LocationAccuracy.OFFICIALLY_MEASURED_GEODETICALLY,
                                ),
                            switchJoint(125)
                                .copy(
                                    number = JointNumber(3),
                                    locationAccuracy = LocationAccuracy.OFFICIALLY_MEASURED_GEODETICALLY,
                                ),
                            switchJoint(126)
                                .copy(
                                    number = JointNumber(5),
                                    locationAccuracy = LocationAccuracy.OFFICIALLY_MEASURED_GEODETICALLY,
                                ),
                            switchJoint(127)
                                .copy(
                                    number = JointNumber(2),
                                    locationAccuracy = LocationAccuracy.OFFICIALLY_MEASURED_GEODETICALLY,
                                ),
                        ),
                    draft = true,
                    stateCategory = switchStateCategory,
                ),
            )
        val throughTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, draft = true),
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(switch.id, 1),
                        endInnerSwitch = switchLinkYV(switch.id, 5),
                        segments = listOf(segment(Point(0.0, 0.0), Point(5.0, 0.0))),
                    ),
                    edge(
                        startInnerSwitch = switchLinkYV(switch.id, 5),
                        endInnerSwitch = switchLinkYV(switch.id, 2),
                        segments = listOf(segment(Point(5.0, 0.0), Point(9.5, 0.0))),
                    ),
                ),
            )
        val branchingTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, draft = true),
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(switch.id, 1),
                        endInnerSwitch = switchLinkYV(switch.id, 3),
                        segments = listOf(segment(Point(0.0, 0.0), Point(7.5, 0.5))),
                    )
                ),
            )

        return Triple(switch, throughTrack, branchingTrack)
    }

    @Test
    fun linkKmPost() {
        val trackNumber = layoutTrackNumberDao.save(trackNumber(testDBService.getUnusedTrackNumber(), draft = true))
        val referenceLine = insertReferenceLineFor(trackNumber.id, draft = true).id
        fakeRatko.acceptsNewRouteNumbersGivingThemOids(listOf("1.2.3.4.5"))
        publishAndPush(trackNumbers = listOf(trackNumber.id), referenceLines = listOf(referenceLine))
        fakeRatko.hasRouteNumber(ratkoRouteNumber(id = "1.2.3.4.5"))
        val pushedPoints = fakeRatko.getCreatedRouteNumberPoints("1.2.3.4.5")

        val kmPost =
            kmPostService.saveDraft(
                LayoutBranch.main,
                kmPost(trackNumber.id, KmNumber(1), roughLayoutLocation = Point(5.0, 0.0), draft = true),
            )
        publishAndPush(kmPosts = listOf(kmPost.id))
        val deletions = fakeRatko.getRouteNumberPointDeletions("1.2.3.4.5")
        val updatedPoints = fakeRatko.getUpdatedRouteNumberPoints("1.2.3.4.5")
        assertEquals(listOf("0000", "0001"), deletions)
        // km post was placed roughly at meter point, causing updated points' positions to move
        // slightly due to
        // conversion from GK to layout coordinates
        pushedPoints.flatten().zip(updatedPoints.flatten()) { expected, actual ->
            assertEquals(expected.geometry!!.coordinates[0], actual.geometry!!.coordinates[0], 0.000001)
            assertEquals(expected.geometry.coordinates[1], actual.geometry.coordinates[1], 0.000001)
        }
        assertEquals("0000", updatedPoints[0].map { p -> p.kmM.kmNumber.toString() }.distinct().single())
        assertEquals("0001", updatedPoints[1].map { p -> p.kmM.kmNumber.toString() }.distinct().single())
    }

    @Test
    fun pushTrackNumberDeletion() {
        val trackNumber = establishedTrackNumber()
        trackNumberService.saveDraft(LayoutBranch.main, trackNumber.trackNumberObject.copy(state = LayoutState.DELETED))

        val originalVersion =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumber.id, draft = true),
                trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
            )
        val locationTrack = locationTrackDao.fetch(originalVersion)
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrack.copy(state = LocationTrackState.DELETED),
            alignmentDao.fetch(originalVersion),
        )
        val oid: Oid<LocationTrack> = Oid("1.2.3.4.5")
        locationTrackDao.insertExternalId(originalVersion.id, LayoutBranch.main, oid)

        fakeRatko.hasLocationTrack(ratkoLocationTrack(id = oid.toString()))

        publishAndPush(trackNumbers = listOf(trackNumber.id), locationTracks = listOf(originalVersion.id))
        assertEquals(listOf(""), fakeRatko.getRouteNumberPointDeletions(trackNumber.externalId.toString()))
        assertEquals(listOf(""), fakeRatko.getLocationTrackPointDeletions(oid.toString()))
    }

    @Test
    fun avoidPushingShortMetadata() {
        val trackNumber = establishedTrackNumber()
        val plan =
            plan(
                trackNumber.number,
                LAYOUT_SRID,
                // elements don't matter, only the names being different matters since that makes
                // the metadatas distinct
                geometryAlignment(elements = listOf(lineFromOrigin(1.0)), name = "foo"),
                geometryAlignment(elements = listOf(lineFromOrigin(1.0)), name = "bar"),
            )
        val fileContent = "<a></a>"
        val planVersion = geometryDao.insertPlan(plan, InfraModelFile(plan.fileName, fileContent), null)
        val planAlignments = geometryDao.fetchPlan(planVersion).alignments

        val locationTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumber.id, draft = true),
                trackGeometryOfSegments(
                    segment(Point(0.0, 0.0), Point(5.0, 0.0), sourceId = planAlignments[0].elements[0].id),
                    segment(Point(5.0, 0.0), Point(5.6, 0.0), sourceId = planAlignments[1].elements[0].id),
                ),
            )

        fakeRatko.acceptsNewLocationTrackGivingItOid("1.2.3.4.5")
        publishAndPush(locationTracks = listOf(locationTrack.id))
        val pushedMetadata = fakeRatko.getPushedMetadata(locationTrackOid = "1.2.3.4.5")
        assertEquals(1, pushedMetadata.size)
        val pushedNodes = pushedMetadata[0].locations[0].nodecollection.nodes.toList()
        assertEquals("0000+0000", pushedNodes[0].point.kmM.toString())
        assertEquals("0000+0005", pushedNodes[1].point.kmM.toString())
    }

    private fun operatingPoint(
        oid: String,
        name: String,
        trackNumberOid: Oid<RatkoRouteNumber>,
        location: Point = Point(10.0, 10.0),
    ) = RatkoOperatingPointParse(Oid(oid), name, name, name, OperationalPointType.LP, location, trackNumberOid)

    @Test
    fun fetchAndFindOperatingPoints() {
        val trackNumberId =
            trackNumberService
                .saveDraft(LayoutBranch.main, trackNumber(testDBService.getUnusedTrackNumber(), draft = true))
                .id
        trackNumberService.insertExternalId(LayoutBranch.main, trackNumberId, Oid("5.5.5.5.5"))

        val kannustamoOperatingPoint =
            RatkoOperatingPointParse(
                Oid("1.2.3.4.5"),
                "Kannustamo",
                "KST",
                "KST-123",
                OperationalPointType.LPO,
                Point(100.0, 100.0),
                Oid("5.5.5.5.5"),
            )
        fakeRatko.hasOperatingPoints(
            listOf(
                operatingPoint("1.2.3.4.6", "Turpeela", Oid("5.5.5.5.5"), location = Point(10.0, 10.0)),
                kannustamoOperatingPoint,
            )
        )
        ratkoService.updateOperatingPointsFromRatko()
        val pointsFromDatabase = ratkoLocalService.getOperatingPoints(boundingBoxAroundPoint(Point(95.0, 95.0), 10.0))
        assertEquals(1, pointsFromDatabase.size)
        val point = pointsFromDatabase[0]
        assertEquals("1.2.3.4.5", point.externalId.toString())
        assertEquals("Kannustamo", point.name)
        assertEquals("KST", point.abbreviation)
        assertEquals("KST-123", point.uicCode)
        assertEquals(OperationalPointType.LPO, point.type)
        assertEquals(Point(100.0, 100.0), point.location)
        assertEquals(trackNumberId, point.trackNumberId)
    }

    @Test
    fun updateOperatingPointsVersioning() {
        val trackNumberId =
            trackNumberService
                .saveDraft(LayoutBranch.main, trackNumber(testDBService.getUnusedTrackNumber(), draft = true))
                .id
        trackNumberService.insertExternalId(LayoutBranch.main, trackNumberId, Oid("5.5.5.5.5"))
        val turpeela = operatingPoint("1.2.3.4.6", "Turpeela", Oid("5.5.5.5.5"))
        val kannustamo = operatingPoint("1.2.3.4.5", "Kannustamo", Oid("5.5.5.5.5"))
        val liukuainen = operatingPoint("1.2.3.4.7", "Liukuainen", Oid("5.5.5.5.5"))

        fakeRatko.hasOperatingPoints(listOf(turpeela, kannustamo, liukuainen))
        ratkoService.updateOperatingPointsFromRatko()
        fakeRatko.hasOperatingPoints(listOf(turpeela.copy(name = "Turpasauna"), liukuainen))
        ratkoService.updateOperatingPointsFromRatko()
        assertTrue(
            jdbc.queryOne(
                """select deleted from layout.operating_point_version where name = 'Kannustamo' and version = 2"""
            ) { rs, _ ->
                rs.getBoolean("deleted")
            }
        )
        val pointsFromDatabase = ratkoLocalService.getOperatingPoints(boundingBoxAroundPoint(Point(10.0, 10.0), 10.0))
        assertEquals(2, pointsFromDatabase.size)
        assertEquals(listOf("Liukuainen", "Turpasauna"), pointsFromDatabase.map { it.name }.sorted())
        assertEquals(
            5,
            jdbc.queryOne("""select count(*) as c from layout.operating_point_version""") { rs, _ -> rs.getInt("c") },
        )
    }

    @Test
    fun `Bulk transfers should not be started for any unpublished splits`() {
        splitTestDataService.forcefullyFinishAllCurrentlyUnfinishedSplits(LayoutBranch.main)

        val splitId = splitTestDataService.insertSplit()
        ratkoService.manageRatkoBulkTransfers(LayoutBranch.main)
        val splitAfterManagerRun = splitDao.getOrThrow(splitId)

        assertEquals(null, splitAfterManagerRun.publicationId)
        assertEquals(null, splitAfterManagerRun.bulkTransferId)
        assertEquals(BulkTransferState.PENDING, splitAfterManagerRun.bulkTransferState)
    }

    @Test
    fun `Bulk transfer manager should start and poll bulk transfers for pending & published splits`() {
        splitTestDataService.forcefullyFinishAllCurrentlyUnfinishedSplits(LayoutBranch.main)

        val splitId = splitTestDataService.insertSplit()
        val publicationId =
            publicationDao.createPublication(
                LayoutBranch.main,
                FreeTextWithNewLines.of("test: bulk transfer to in progress"),
                PublicationCause.MANUAL,
                parentId = null,
            )
        splitDao.updateSplit(splitId = splitId, publicationId = publicationId)

        val someBulkTransferId = testDBService.getUnusedBulkTransferId()
        fakeRatko.acceptsNewBulkTransferGivingItId(someBulkTransferId)

        ratkoService.manageRatkoBulkTransfers(LayoutBranch.main)

        val splitAfterBulkTransferStart = splitDao.getOrThrow(splitId)
        assertEquals(BulkTransferState.IN_PROGRESS, splitAfterBulkTransferStart.bulkTransferState)
        assertEquals(someBulkTransferId, splitAfterBulkTransferStart.bulkTransferId)

        ratkoService.manageRatkoBulkTransfers(LayoutBranch.main)

        val splitAfterPoll = splitDao.getOrThrow(splitId)
        assertEquals(BulkTransferState.DONE, splitAfterPoll.bulkTransferState)
        assertEquals(someBulkTransferId, splitAfterPoll.bulkTransferId)
    }

    @Test
    fun `Bulk transfer should not be started when another bulk transfer is in progress`() {
        val someBulkTransferId = testDBService.getUnusedBulkTransferId()

        splitTestDataService.insertSplit().let { splitId ->
            splitDao
                .updateSplit(
                    splitId = splitId,
                    publicationId =
                        publicationDao.createPublication(
                            LayoutBranch.main,
                            FreeTextWithNewLines.of("some in progress bulk transfer"),
                            PublicationCause.MANUAL,
                            parentId = null,
                        ),
                    bulkTransferState = BulkTransferState.IN_PROGRESS,
                    bulkTransferId = someBulkTransferId,
                )
                .id
        }

        val pendingSplitId =
            splitTestDataService.insertSplit().let { splitId ->
                splitDao
                    .updateSplit(
                        splitId = splitId,
                        publicationId =
                            publicationDao.createPublication(
                                LayoutBranch.main,
                                FreeTextWithNewLines.of("pending bulk transfer"),
                                PublicationCause.MANUAL,
                                parentId = null,
                            ),
                    )
                    .id
            }

        fakeRatko.allowsBulkTransferStatePollingAndAnswersWithState(someBulkTransferId, BulkTransferState.IN_PROGRESS)
        ratkoService.manageRatkoBulkTransfers(LayoutBranch.main)

        val pendingSplitAfterBulkTransferProcessing = splitDao.getOrThrow(pendingSplitId)
        assertEquals(null, pendingSplitAfterBulkTransferProcessing.bulkTransferId)
        assertEquals(BulkTransferState.PENDING, pendingSplitAfterBulkTransferProcessing.bulkTransferState)
    }

    @Test
    fun `Temporary failed bulk transfer should be retried`() {
        splitTestDataService.forcefullyFinishAllCurrentlyUnfinishedSplits(LayoutBranch.main)

        val splitId =
            splitTestDataService.insertSplit().let { splitId ->
                splitDao
                    .updateSplit(
                        splitId = splitId,
                        publicationId =
                            publicationDao.createPublication(
                                LayoutBranch.main,
                                FreeTextWithNewLines.of("pending bulk transfer"),
                                PublicationCause.MANUAL,
                                parentId = null,
                            ),
                    )
                    .id
            }

        val bulkTransferIdThatWillFail = testDBService.getUnusedBulkTransferId()
        fakeRatko.acceptsNewBulkTransferGivingItId(bulkTransferIdThatWillFail)
        ratkoService.manageRatkoBulkTransfers(LayoutBranch.main)

        val splitAfterFirstBulkTransferStart = splitDao.getOrThrow(splitId)
        assertEquals(bulkTransferIdThatWillFail, splitAfterFirstBulkTransferStart.bulkTransferId)
        assertEquals(BulkTransferState.IN_PROGRESS, splitAfterFirstBulkTransferStart.bulkTransferState)

        splitDao.updateSplit(splitId = splitId, bulkTransferState = BulkTransferState.TEMPORARY_FAILURE)

        val retriedBulkTransferId = testDBService.getUnusedBulkTransferId()
        fakeRatko.acceptsNewBulkTransferGivingItId(retriedBulkTransferId)
        ratkoService.manageRatkoBulkTransfers(LayoutBranch.main)

        val splitAfterSecondExpectedBulkTransferStart = splitDao.getOrThrow(splitId)
        assertEquals(retriedBulkTransferId, splitAfterSecondExpectedBulkTransferStart.bulkTransferId)
        assertEquals(BulkTransferState.IN_PROGRESS, splitAfterSecondExpectedBulkTransferStart.bulkTransferState)
    }

    @Test
    fun `Bulk transfer should be started on the earliest unfinished split`() {
        splitTestDataService.forcefullyFinishAllCurrentlyUnfinishedSplits(LayoutBranch.main)

        val splitIds =
            (0..2).map { index ->
                splitTestDataService.insertSplit().let { splitId ->
                    splitDao
                        .updateSplit(
                            splitId = splitId,
                            publicationId =
                                publicationDao.createPublication(
                                    LayoutBranch.main,
                                    FreeTextWithNewLines.of("pending bulk transfer $index"),
                                    PublicationCause.MANUAL,
                                    parentId = null,
                                ),
                        )
                        .id
                }
            }

        val someBulkTransferId = testDBService.getUnusedBulkTransferId()
        fakeRatko.acceptsNewBulkTransferGivingItId(someBulkTransferId)
        ratkoService.manageRatkoBulkTransfers(LayoutBranch.main)

        val splitAfterSecondExpectedBulkTransferStart = splitDao.getOrThrow(splitIds[0])
        assertEquals(someBulkTransferId, splitAfterSecondExpectedBulkTransferStart.bulkTransferId)
        assertEquals(BulkTransferState.IN_PROGRESS, splitAfterSecondExpectedBulkTransferStart.bulkTransferState)
    }

    @Test
    fun `Polling bulk transfer state updates should not change the state of any splits that are not in progress`() {
        val splitsAndExpectedBulkTransferStates =
            BulkTransferState.entries
                .filter { state -> state != BulkTransferState.IN_PROGRESS }
                .map { bulkTransferState ->
                    val splitId = splitTestDataService.insertSplit()

                    when (bulkTransferState) {
                        BulkTransferState.PENDING ->
                            splitDao.updateSplit(splitId = splitId, bulkTransferState = bulkTransferState)

                        else ->
                            splitDao.updateSplit(
                                splitId = splitId,
                                publicationId =
                                    publicationDao.createPublication(
                                        LayoutBranch.main,
                                        FreeTextWithNewLines.of("testing $bulkTransferState"),
                                        PublicationCause.MANUAL,
                                        parentId = null,
                                    ),
                                bulkTransferId = testDBService.getUnusedBulkTransferId(),
                                bulkTransferState = bulkTransferState,
                            )
                    }

                    splitId to bulkTransferState
                }

        splitsAndExpectedBulkTransferStates.forEach { (splitId, _) ->
            val split = splitDao.getOrThrow(splitId)
            ratkoService.pollBulkTransferStateUpdate(LayoutBranch.main, split)
        }

        splitsAndExpectedBulkTransferStates.forEach { (splitId, expectedBulkTransferState) ->
            assertEquals(expectedBulkTransferState, splitDao.getOrThrow(splitId).bulkTransferState)
        }
    }

    @Test
    fun `design publication push fetches plan ID for design`() {
        val design = testDBService.createDesignBranch()
        fakeRatko.acceptsNewDesignGivingItId(123)
        publicationService.publishManualPublication(
            design,
            PublicationRequest(publicationRequestIds(), message = FreeTextWithNewLines.of("aoeu")),
        )
        ratkoService.pushChangesToRatko(design)
        assertEquals(123, layoutDesignDao.fetchRatkoId(design.designId)?.intValue)
    }

    @Test
    fun `design updates get sent with design publication push`() {
        val design = testDBService.createDesignBranch()
        fakeRatko.acceptsNewDesignGivingItId(123)
        publicationService.publishManualPublication(
            design,
            PublicationRequest(publicationRequestIds(), message = FreeTextWithNewLines.of("aoeu")),
        )
        layoutDesignDao.update(
            design.designId,
            LayoutDesignSaveRequest(
                name = LayoutDesignName("diipa daapa"),
                estimatedCompletion = LocalDate.parse("2022-02-02"),
                designState = DesignState.ACTIVE,
            ),
        )
        publicationService.publishManualPublication(
            design,
            PublicationRequest(publicationRequestIds(), message = FreeTextWithNewLines.of("aoeu")),
        )
        ratkoService.pushChangesToRatko(design)
        layoutDesignDao.update(
            design.designId,
            LayoutDesignSaveRequest(
                name = LayoutDesignName("uuba aaba"),
                estimatedCompletion = LocalDate.parse("2023-02-02"),
                designState = DesignState.COMPLETED,
            ),
        )
        publicationService.publishManualPublication(
            design,
            PublicationRequest(publicationRequestIds(), message = FreeTextWithNewLines.of("aoeu")),
        )
        ratkoService.pushChangesToRatko(design)
        assertEquals(
            listOf(
                RatkoPlan(
                    id = RatkoPlanId(123),
                    name = "uuba aaba",
                    estimatedCompletion = "2023-02-02T12:00Z",
                    phase = RatkoPlanPhase.RAILWAY_PLAN,
                    state = RatkoPlanState.COMPLETED,
                )
            ),
            fakeRatko.getUpdatesToDesign(123),
        )
    }

    @Test
    fun `multiple pushes don't unnecessarily send updates to design`() {
        val design = testDBService.createDesignBranch()
        fakeRatko.acceptsNewDesignGivingItId(123)
        publishAndPush()
        publicationService.publishManualPublication(
            design,
            PublicationRequest(publicationRequestIds(), message = FreeTextWithNewLines.of("aoeu")),
        )
        publicationService.publishManualPublication(
            design,
            PublicationRequest(publicationRequestIds(), message = FreeTextWithNewLines.of("uuba aaba")),
        )
        ratkoService.pushChangesToRatko(design)
        assertEquals(listOf<RatkoPlan>(), fakeRatko.getUpdatesToDesign(123))
    }

    @Test
    fun `pushing location tracks and switches created in design works`() {
        val design = testDBService.createDesignBranch()
        val designDraft = testDBService.testContext(design, PublicationState.DRAFT)

        val trackNumber = establishedTrackNumber("1.1.1.1.1")
        val switch = designDraft.save(switch(joints = listOf(switchJoint(1, Point(1.0, 0.0))))).id
        val locationTrack =
            designDraft
                .save(
                    locationTrack(trackNumber.id),
                    trackGeometry(
                        edge(
                            endOuterSwitch = switchLinkYV(switch, 1),
                            segments = listOf(segment(Point(0.0, 0.0), Point(1.0, 0.0))),
                        ),
                        edge(
                            startInnerSwitch = switchLinkYV(switch, 1),
                            segments = listOf(segment(Point(1.0, 0.0), Point(10.0, 0.0))),
                        ),
                    ),
                )
                .id
        fakeRatko.acceptsNewLocationTrackGivingItOid("2.2.2.2.2")
        fakeRatko.acceptsNewSwitchGivingItOid("3.3.3.3.3")
        publicationService.publishManualPublication(
            design,
            PublicationRequest(
                publicationRequestIds(locationTracks = listOf(locationTrack), switches = listOf(switch)),
                message = FreeTextWithNewLines.of("aoeu"),
            ),
        )

        fakeRatko.acceptsNewDesignGivingItId(123)
        fakeRatko.providesPlanItemIdsInDesign(123)
        ratkoService.pushChangesToRatko(design)
        val pushedLocationTrack = fakeRatko.getLastPushedLocationTrack("2.2.2.2.2")!!
        val pushedSwitch = fakeRatko.getLastPushedSwitch("3.3.3.3.3")!!
        assertTrue(pushedSwitch.isPlanContext)
        assertEquals(pushedSwitch.planItemIds!![0], switchDao.fetchExternalId(design, switch)?.planItemId?.intValue)
        assertTrue(pushedLocationTrack.isPlanContext)
        assertEquals(
            pushedLocationTrack.planItemIds!![0],
            locationTrackDao.fetchExternalId(design, locationTrack)?.planItemId?.intValue,
        )
        assertEquals(listOf<RatkoMetadataAsset>(), fakeRatko.getPushedMetadata(locationTrackOid = "2.2.2.2.2"))
    }

    @Test
    fun `objects that are cancelled before being ever pushed don't get pushed post-cancellation`() {
        val designBranch = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)

        val trackNumber = designDraftContext.save(trackNumber())
        val referenceLine =
            designDraftContext.save(
                referenceLine(trackNumber.id),
                alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
            )
        val switch =
            designDraftContext.save(
                switch(
                    joints =
                        listOf(
                            LayoutSwitchJoint(
                                number = JointNumber(1),
                                role = SwitchJointRole.MAIN,
                                location = Point(4.0, 0.0),
                                locationAccuracy = null,
                            )
                        )
                )
            )
        val locationTrack =
            designDraftContext.save(
                locationTrack(trackNumber.id),
                trackGeometry(
                    edge(
                        endOuterSwitch = switchLinkYV(switch.id, 1),
                        segments = listOf(segment(Point(0.0, 0.0), Point(4.0, 0.0))),
                    ),
                    edge(
                        startInnerSwitch = switchLinkYV(switch.id, 1),
                        segments = listOf(segment(Point(4.0, 0.0), Point(8.0, 0.0))),
                    ),
                ),
            )

        val publishAll =
            publicationRequestIds(
                trackNumbers = listOf(trackNumber.id),
                referenceLines = listOf(referenceLine.id),
                locationTracks = listOf(locationTrack.id),
                switches = listOf(switch.id),
            )
        fakeRatko.acceptsNewRouteNumbersGivingThemOids(listOf("1.1.1.1.2"))
        fakeRatko.acceptsNewSwitchGivingItOid("1.1.1.2.2")
        fakeRatko.acceptsNewLocationTrackGivingItOid("1.1.1.3.2")
        publicationService.publishManualPublication(
            designBranch,
            PublicationRequest(publishAll, FreeTextWithNewLines.of("")),
        )
        trackNumberService.cancel(designBranch, trackNumber.id)
        referenceLineService.cancel(designBranch, referenceLine.id)
        locationTrackService.cancel(designBranch, locationTrack.id)
        switchService.cancel(designBranch, switch.id)
        publicationService.publishManualPublication(
            designBranch,
            PublicationRequest(publishAll, FreeTextWithNewLines.of("")),
        )

        fakeRatko.acceptsNewDesignGivingItId(123)
        fakeRatko.providesPlanItemIdsInDesign(123)

        ratkoService.pushChangesToRatko(designBranch)
        // no location track pushed, no points attempted to be either pushed or deleted
        assertEquals(listOf<List<RatkoPoint>>(), fakeRatko.getCreatedLocationTrackPoints("1.1.1.3.2"))
        assertEquals(listOf<List<RatkoPoint>>(), fakeRatko.getUpdatedLocationTrackPoints("1.1.1.3.2"))
        assertEquals(listOf<String>(), fakeRatko.getLocationTrackPointDeletions("1.1.1.3.2"))
        assertNull(fakeRatko.getLastPushedLocationTrack("1.1.1.3.2"))

        // no switch pushed either
        assertNull(fakeRatko.getLastPushedSwitch("1.1.1.2.2"))

        // and no route number
        assertEquals(listOf<List<RatkoPoint>>(), fakeRatko.getCreatedRouteNumberPoints("1.1.1.1.2"))
        assertEquals(listOf<List<RatkoPoint>>(), fakeRatko.getUpdatedRouteNumberPoints("1.1.1.1.2"))
        assertEquals(listOf<String>(), fakeRatko.getRouteNumberPointDeletions("1.1.1.1.2"))
        assertNull(fakeRatko.getLastPushedRouteNumber("1.1.1.1.2"))
    }

    @Test
    fun `cancellations get pushed as plan item states`() {
        val designBranch = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)

        val trackNumber = designDraftContext.save(trackNumber())
        val referenceLine =
            designDraftContext.save(
                referenceLine(trackNumber.id),
                alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
            )
        val switch =
            designDraftContext.save(
                switch(
                    joints =
                        listOf(
                            LayoutSwitchJoint(
                                number = JointNumber(1),
                                role = SwitchJointRole.MAIN,
                                location = Point(4.0, 0.0),
                                locationAccuracy = null,
                            )
                        )
                )
            )
        val locationTrack =
            designDraftContext.save(
                locationTrack(trackNumber.id),
                trackGeometry(
                    edge(
                        endOuterSwitch = switchLinkYV(switch.id, 1),
                        segments = listOf(segment(Point(0.0, 0.0), Point(4.0, 0.0))),
                    ),
                    edge(
                        startInnerSwitch = switchLinkYV(switch.id, 1),
                        segments = listOf(segment(Point(4.0, 0.0), Point(8.0, 0.0))),
                    ),
                ),
            )

        val publishAll =
            publicationRequestIds(
                trackNumbers = listOf(trackNumber.id),
                referenceLines = listOf(referenceLine.id),
                locationTracks = listOf(locationTrack.id),
                switches = listOf(switch.id),
            )
        fakeRatko.acceptsNewRouteNumbersGivingThemOids(listOf("1.1.1.1.2"))
        fakeRatko.acceptsNewSwitchGivingItOid("1.1.1.2.2")
        fakeRatko.acceptsNewLocationTrackGivingItOid("1.1.1.3.2")
        fakeRatko.acceptsNewDesignGivingItId(123)
        fakeRatko.providesPlanItemIdsInDesign(123)
        publicationService.publishManualPublication(
            designBranch,
            PublicationRequest(publishAll, FreeTextWithNewLines.of("")),
        )
        ratkoService.pushChangesToRatko(designBranch)

        fakeRatko.hostPushedLocationTrack("1.1.1.3.2")
        fakeRatko.hostPushedSwitch("1.1.1.2.2")
        fakeRatko.hostPushedRouteNumber("1.1.1.1.2")

        trackNumberService.cancel(designBranch, trackNumber.id)
        referenceLineService.cancel(designBranch, referenceLine.id)
        locationTrackService.cancel(designBranch, locationTrack.id)
        switchService.cancel(designBranch, switch.id)

        publicationService.publishManualPublication(
            designBranch,
            PublicationRequest(publishAll, FreeTextWithNewLines.of("")),
        )
        ratkoService.pushChangesToRatko(designBranch)
        val trackNumberPlanItem = layoutTrackNumberDao.fetchExternalId(designBranch, trackNumber.id)!!.planItemId!!
        assertEquals(
            listOf(RatkoPlanItem(trackNumberPlanItem, RatkoPlanId(123), null, RatkoPlanState.CANCELLED)),
            fakeRatko.getUpdatesToPlanItem(trackNumberPlanItem.intValue),
        )
        val locationTrackPlanItem = locationTrackDao.fetchExternalId(designBranch, locationTrack.id)!!.planItemId!!
        assertEquals(
            listOf(RatkoPlanItem(locationTrackPlanItem, RatkoPlanId(123), null, RatkoPlanState.CANCELLED)),
            fakeRatko.getUpdatesToPlanItem(locationTrackPlanItem.intValue),
        )
        val switchPlanItem = switchDao.fetchExternalId(designBranch, switch.id)!!.planItemId!!
        assertEquals(
            listOf(RatkoPlanItem(switchPlanItem, RatkoPlanId(123), null, RatkoPlanState.CANCELLED)),
            fakeRatko.getUpdatesToPlanItem(switchPlanItem.intValue),
        )
    }

    @Test
    fun `completed merges get pushed as plan item states`() {
        val designBranch = testDBService.createDesignBranch()
        val designDraftContext = testDBService.testContext(designBranch, PublicationState.DRAFT)

        val trackNumber = establishedTrackNumber("1.1.1.1.1")
        val switch =
            mainOfficialContext.save(
                switch(
                    joints =
                        listOf(
                            LayoutSwitchJoint(
                                number = JointNumber(1),
                                role = SwitchJointRole.MAIN,
                                location = Point(4.0, 0.0),
                                locationAccuracy = null,
                            )
                        )
                )
            )
        switchDao.insertExternalId(switch.id, LayoutBranch.main, Oid("1.1.1.2.1"))
        fakeRatko.hasSwitch(ratkoSwitch("1.1.1.2.1"))
        val locationTrack =
            mainOfficialContext.save(
                locationTrack(trackNumber.id),
                trackGeometry(
                    edge(
                        endOuterSwitch = switchLinkYV(switch.id, 1),
                        segments = listOf(segment(Point(0.0, 0.0), Point(4.0, 0.0))),
                    ),
                    edge(
                        startInnerSwitch = switchLinkYV(switch.id, 1),
                        segments = listOf(segment(Point(4.0, 0.0), Point(8.0, 0.0))),
                    ),
                ),
            )
        locationTrackDao.insertExternalId(locationTrack.id, LayoutBranch.main, Oid("1.1.1.3.1"))
        fakeRatko.hasLocationTrack(ratkoLocationTrack("1.1.1.3.1"))

        fakeRatko.acceptsNewDesignGivingItId(123)
        fakeRatko.providesPlanItemIdsInDesign(123)
        designDraftContext.copyFrom(trackNumber.trackNumberVersion)
        designDraftContext.copyFrom(switch)
        designDraftContext.copyFrom(locationTrack)

        val publishAll =
            publicationRequestIds(
                trackNumbers = listOf(trackNumber.id),
                locationTracks = listOf(locationTrack.id),
                switches = listOf(switch.id),
            )
        fakeRatko.acceptsNewRouteNumbersGivingThemOids(listOf("1.1.1.1.2"))
        fakeRatko.acceptsNewSwitchGivingItOid("1.1.1.2.2")
        fakeRatko.acceptsNewLocationTrackGivingItOid("1.1.1.3.2")
        publicationService.publishManualPublication(
            designBranch,
            PublicationRequest(publishAll, FreeTextWithNewLines.of("")),
        )
        ratkoService.pushChangesToRatko(designBranch)
        fakeRatko.hostPushedLocationTrack("1.1.1.3.2")
        fakeRatko.hostPushedSwitch("1.1.1.2.2")

        trackNumberService.mergeToMainBranch(designBranch, trackNumber.id)
        locationTrackService.mergeToMainBranch(designBranch, locationTrack.id)
        switchService.mergeToMainBranch(designBranch, switch.id)
        publicationService.publishManualPublication(
            LayoutBranch.main,
            PublicationRequest(publishAll, FreeTextWithNewLines.of("")),
        )
        ratkoService.pushChangesToRatko(designBranch)
        val trackNumberPlanItem = layoutTrackNumberDao.fetchExternalId(designBranch, trackNumber.id)!!.planItemId!!
        val locationTrackPlanItem = locationTrackDao.fetchExternalId(designBranch, locationTrack.id)!!.planItemId!!
        val switchPlanItem = switchDao.fetchExternalId(designBranch, switch.id)!!.planItemId!!
        assertEquals(
            listOf(
                RatkoPlanItem(
                    trackNumberPlanItem,
                    RatkoPlanId(123),
                    Oid<LayoutTrackNumber>("1.1.1.1.1"),
                    RatkoPlanState.COMPLETED,
                )
            ),
            fakeRatko.getUpdatesToPlanItem(trackNumberPlanItem.intValue),
        )
        assertEquals(
            listOf(
                RatkoPlanItem(
                    locationTrackPlanItem,
                    RatkoPlanId(123),
                    Oid<LayoutTrackNumber>("1.1.1.3.1"),
                    RatkoPlanState.COMPLETED,
                )
            ),
            fakeRatko.getUpdatesToPlanItem(locationTrackPlanItem.intValue),
        )
        assertEquals(
            listOf(
                RatkoPlanItem(
                    switchPlanItem,
                    RatkoPlanId(123),
                    Oid<LayoutTrackNumber>("1.1.1.2.1"),
                    RatkoPlanState.COMPLETED,
                )
            ),
            fakeRatko.getUpdatesToPlanItem(switchPlanItem.intValue),
        )
    }

    private fun insertReferenceLineFor(
        trackNumberId: IntId<LayoutTrackNumber>,
        draft: Boolean,
    ): LayoutRowVersion<ReferenceLine> =
        (if (draft) mainDraftContext else mainOfficialContext).save(
            referenceLine(trackNumberId),
            alignment(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
        )

    private fun detachSwitchesFromTrack(locationTrackId: IntId<LocationTrack>) {
        val locationTrackVersion = locationTrackDao.fetchVersionOrThrow(MainLayoutContext.draft, locationTrackId)
        val locationTrack = locationTrackDao.fetch(locationTrackVersion)
        val geometry = alignmentDao.fetch(locationTrackVersion)
        val deSwitchedGeometry = TmpLocationTrackGeometry.ofSegments(geometry.segments, locationTrackId)
        locationTrackService.saveDraft(LayoutBranch.main, locationTrack, deSwitchedGeometry)
    }

    private fun publishAndPush(
        branch: LayoutBranch = LayoutBranch.main,
        trackNumbers: List<IntId<LayoutTrackNumber>> = listOf(),
        referenceLines: List<IntId<ReferenceLine>> = listOf(),
        locationTracks: List<IntId<LocationTrack>> = listOf(),
        switches: List<IntId<LayoutSwitch>> = listOf(),
        kmPosts: List<IntId<LayoutKmPost>> = listOf(),
    ) {
        val ids =
            PublicationRequestIds(
                trackNumbers = trackNumbers,
                referenceLines = referenceLines,
                locationTracks = locationTracks,
                switches = switches,
                kmPosts = kmPosts,
            )
        publicationService.publishManualPublication(branch, PublicationRequest(ids, FreeTextWithNewLines.of("")))
        ratkoService.pushChangesToRatko(branch)
    }

    private data class EstablishedTrackNumber(
        val trackNumberVersion: LayoutRowVersion<LayoutTrackNumber>,
        val referenceLineVersion: LayoutRowVersion<ReferenceLine>,
        val externalId: Oid<LayoutTrackNumber>,
        val trackNumberObject: LayoutTrackNumber,
    ) {
        val number = trackNumberObject.number
        val id = trackNumberVersion.id
    }

    private fun establishedTrackNumber(oidString: String = "1.1.1.1.1"): EstablishedTrackNumber {
        val oid = Oid<LayoutTrackNumber>(oidString)
        val trackNumber = testDBService.getUnusedTrackNumber()
        val trackNumberVersion = layoutTrackNumberDao.save(trackNumber(trackNumber, draft = false))
        trackNumberService.insertExternalId(LayoutBranch.main, trackNumberVersion.id, Oid(oidString))
        val referenceLineVersion = insertReferenceLineFor(trackNumberVersion.id, draft = false)
        fakeRatko.hasRouteNumber(ratkoRouteNumber(oidString))
        return EstablishedTrackNumber(
            trackNumberVersion = trackNumberVersion,
            referenceLineVersion = referenceLineVersion,
            externalId = oid,
            trackNumberObject = layoutTrackNumberDao.fetch(trackNumberVersion),
        )
    }
}
