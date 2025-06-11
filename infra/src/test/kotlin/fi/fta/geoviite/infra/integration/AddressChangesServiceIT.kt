package fi.fta.geoviite.infra.integration

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.geocoding.GeocodingContextCacheKey
import fi.fta.geoviite.infra.geocoding.GeocodingDao
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geography.GeometryPoint
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.IntersectType
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.tracklayout.AlignmentPoint
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineService
import fi.fta.geoviite.infra.tracklayout.SegmentPoint
import fi.fta.geoviite.infra.tracklayout.TmpLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.fixMValues
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.locationTrackDbName
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.splitSegment
import fi.fta.geoviite.infra.tracklayout.toAlignmentPoints
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import fi.fta.geoviite.infra.tracklayout.trackNumber
import java.time.Instant
import kotlin.math.ceil
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class AddressChangesServiceIT
@Autowired
constructor(
    val geocodingService: GeocodingService,
    val geocodingDao: GeocodingDao,
    val locationTrackDao: LocationTrackDao,
    val locationTrackService: LocationTrackService,
    val referenceLineDao: ReferenceLineDao,
    val referenceLineService: ReferenceLineService,
    val layoutAlignmentDao: LayoutAlignmentDao,
    val layoutTrackNumberDao: LayoutTrackNumberDao,
    val layoutKmPostDao: LayoutKmPostDao,
    val addressChangesService: AddressChangesService,
) : DBTestBase() {

    @Test
    fun addressChangesAreEmptyIfNothingCanBeGeocoded() {
        val setupData1 = createAndInsertTrackNumberAndLocationTrack()
        val setupData2 = createAndInsertTrackNumberAndLocationTrack()
        val changes =
            addressChangesService.getAddressChanges(
                beforeTrack = setupData1.locationTrack,
                afterTrack = setupData2.locationTrack,
                beforeContextKey = null,
                afterContextKey = null,
            )
        assertFalse(changes.isChanged())
    }

    @Test
    fun addressChangesAreEmptyIfNothingIsChanged() {
        val setupData = createAndInsertTrackNumberAndLocationTrack()
        val contextKey =
            geocodingDao.getLayoutGeocodingContextCacheKey(
                MainLayoutContext.official,
                setupData.locationTrack.trackNumberId,
            )!!
        val changes =
            addressChangesService.getAddressChanges(
                beforeTrack = setupData.locationTrack,
                afterTrack = setupData.locationTrack,
                beforeContextKey = contextKey,
                afterContextKey = contextKey,
            )
        assertFalse(changes.isChanged())
    }

    @Test
    fun addressChangesContainAllAddressesIfThereIsNoBeforeVersion() {
        val setupData = createAndInsertTrackNumberAndLocationTrack()
        val contextKey =
            geocodingDao.getLayoutGeocodingContextCacheKey(
                MainLayoutContext.official,
                setupData.locationTrack.trackNumberId,
            )!!
        val changes =
            addressChangesService.getAddressChanges(
                beforeTrack = null,
                afterTrack = setupData.locationTrack,
                beforeContextKey = null,
                afterContextKey = contextKey,
            )
        assertTrue(changes.isChanged())
        assertTrue(changes.startPointChanged)
        assertTrue(changes.endPointChanged)
        val allKms =
            getAllKms(contextKey, setupData.locationTrackGeometry.start!!, setupData.locationTrackGeometry.end!!)
        assertEquals(allKms, changes.changedKmNumbers)
    }

    @Test
    fun addressChangesContainAllAddressesIfTrackIsBeingRestoredFromBeingDeleted() {
        val setupData = createAndInsertTrackNumberAndLocationTrack()
        val contextKey =
            geocodingDao.getLayoutGeocodingContextCacheKey(
                MainLayoutContext.official,
                setupData.locationTrack.trackNumberId,
            )!!
        val changes =
            addressChangesService.getAddressChanges(
                beforeTrack = setupData.locationTrack.copy(state = LocationTrackState.DELETED),
                afterTrack = setupData.locationTrack,
                beforeContextKey = contextKey,
                afterContextKey = contextKey,
            )
        assertTrue(changes.isChanged())
        assertTrue(changes.startPointChanged)
        assertTrue(changes.endPointChanged)
        val allKms =
            getAllKms(contextKey, setupData.locationTrackGeometry.start!!, setupData.locationTrackGeometry.end!!)
        assertEquals(allKms, changes.changedKmNumbers)
    }

    @Test
    fun addressChangesContainAllAddressesIfAfterVersionHasNoGeometry() {
        val setupData = createAndInsertTrackNumberAndLocationTrack()
        val initialLocationTrack = setupData.locationTrack
        val locationTrackId = initialLocationTrack.id as IntId
        val initialChangeMoment = locationTrackDao.fetchChangeTime()
        val contextKey =
            geocodingDao.getLayoutGeocodingContextCacheKey(
                MainLayoutContext.official,
                setupData.locationTrack.trackNumberId,
            )!!

        removeLocationTrackGeometryAndUpdate(initialLocationTrack)
        val updateMoment = locationTrackDao.fetchChangeTime()

        val changes =
            addressChangesService.getAddressChanges(
                getTrackAtMoment(locationTrackId, initialChangeMoment),
                getTrackAtMoment(locationTrackId, updateMoment)!!,
                getContextKeyAtMoment(initialLocationTrack.trackNumberId, initialChangeMoment),
                getContextKeyAtMoment(initialLocationTrack.trackNumberId, updateMoment),
            )
        assertTrue(changes.isChanged())
        assertTrue(changes.startPointChanged)
        assertTrue(changes.endPointChanged)
        val allKms =
            getAllKms(contextKey, setupData.locationTrackGeometry.start!!, setupData.locationTrackGeometry.end!!)
        assertEquals(allKms, changes.changedKmNumbers)
    }

    @Test
    fun addressChangesFoundWhenAlignmentChanges() {
        val setupData = createAndInsertTrackNumberAndLocationTrack()
        val initialLocationTrack = setupData.locationTrack
        val locationTrackId = initialLocationTrack.id as IntId
        val trackNumberId = initialLocationTrack.trackNumberId
        val initialChangeMoment = locationTrackDao.fetchChangeTime()

        // Move start-point a bit
        updateAndPublish(
            initialLocationTrack,
            trackGeometryOfSegments(
                setupData.locationTrackGeometry.segments.mapIndexed { index, segment ->
                    if (index == 0)
                        segment.copy(
                            geometry =
                                segment.geometry.withPoints(
                                    fixMValues(
                                        listOf(movePoint(segment.segmentPoints.first(), -1.0)) +
                                            segment.segmentPoints.drop(1)
                                    )
                                )
                        )
                    else segment
                }
            ),
        )
        val updateMoment = locationTrackDao.fetchChangeTime()

        val changes =
            addressChangesService.getAddressChanges(
                getTrackAtMoment(locationTrackId, initialChangeMoment),
                getTrackAtMoment(locationTrackId, updateMoment)!!,
                getContextKeyAtMoment(trackNumberId, initialChangeMoment),
                getContextKeyAtMoment(trackNumberId, updateMoment),
            )
        assertTrue(changes.isChanged())
        assertTrue(changes.startPointChanged)
        assertFalse(changes.endPointChanged)
        val startAddress =
            geocodingService
                .getAddress(MainLayoutContext.official, trackNumberId, setupData.locationTrackGeometry.start!!)!!
                .first
        assertEquals(setOf(startAddress.kmNumber), changes.changedKmNumbers)
    }

    @Test
    fun addressChangesFoundWhenReferenceLineChanges() {
        val setupData = createAndInsertTrackNumberAndLocationTrack()
        val initialLocationTrack = setupData.locationTrack
        val locationTrackId = initialLocationTrack.id as IntId
        val trackNumberId = initialLocationTrack.trackNumberId
        val initialChangeMoment = locationTrackDao.fetchChangeTime()

        moveReferenceLineGeometryPointsAndUpdate(setupData.referenceLine, setupData.referenceLineGeometry) {
            index,
            point ->
            // The reference-line is parallel to track, so alter the shape a bit to force all
            // addresses changing
            point + if (index % 2 == 0) Point(0.5, 0.5) else Point(0.5, 0.4)
        }
        val updateMoment = referenceLineDao.fetchChangeTime()

        val changes =
            addressChangesService.getAddressChanges(
                getTrackAtMoment(locationTrackId, initialChangeMoment),
                getTrackAtMoment(locationTrackId, updateMoment)!!,
                getContextKeyAtMoment(trackNumberId, initialChangeMoment),
                getContextKeyAtMoment(trackNumberId, updateMoment),
            )
        assertTrue(changes.isChanged())
        assertTrue(changes.startPointChanged, "Start should change: changes=$changes")
        assertTrue(changes.endPointChanged, "End should change: changes=$changes")
        val allKms =
            getAllKms(
                geocodingDao.getLayoutGeocodingContextCacheKey(
                    MainLayoutContext.official,
                    setupData.locationTrack.trackNumberId,
                )!!,
                setupData.locationTrackGeometry.start!!,
                setupData.locationTrackGeometry.end!!,
            )
        assertEquals(allKms, changes.changedKmNumbers)
    }

    @Test
    fun addressChangesFoundWhenKmPostChanges() {
        val setupData = createAndInsertTrackNumberAndLocationTrack()
        val initialLocationTrack = setupData.locationTrack
        val locationTrackId = initialLocationTrack.id as IntId
        val trackNumberId = initialLocationTrack.trackNumberId
        val initialChangeMoment = locationTrackDao.fetchChangeTime()

        moveKmPostGkLocationAndUpdate(setupData.kmPosts[0]) { point -> point + 0.5 }
        val updateMoment = layoutKmPostDao.fetchChangeTime()

        val changes =
            addressChangesService.getAddressChanges(
                getTrackAtMoment(locationTrackId, initialChangeMoment),
                getTrackAtMoment(locationTrackId, updateMoment)!!,
                getContextKeyAtMoment(trackNumberId, initialChangeMoment),
                getContextKeyAtMoment(trackNumberId, updateMoment),
            )
        assertTrue(changes.isChanged())
        assertFalse(changes.startPointChanged)
        assertFalse(changes.endPointChanged)
        assertEquals(setOf(setupData.kmPosts[0].kmNumber), changes.changedKmNumbers)
    }

    @Test
    fun shouldFindDifferencesWhenEitherHasNoMidPoints() {
        assertEquals(
            setOf(),
            resolveChangedGeometryKilometers(
                createEmptyAddresses(
                    Point(100.0, 100.0) to TrackMeter(2, 1),
                    Point(100.0, 200.0) to TrackMeter(2, 101),
                ),
                createEmptyAddresses(Point(100.0, 100.0) to TrackMeter(2, 1), Point(100.0, 200.0) to TrackMeter(2, 101)),
            ),
        )
        assertEquals(
            setOf(KmNumber(3)),
            resolveChangedGeometryKilometers(
                createAddresses(Point(100.0, 100.0) to TrackMeter(3, 1), Point(100.0, 200.0) to TrackMeter(3, 101)),
                createEmptyAddresses(Point(100.0, 100.0) to TrackMeter(3, 1), Point(100.0, 200.0) to TrackMeter(3, 101)),
            ),
        )
        assertEquals(
            setOf(KmNumber(2)),
            resolveChangedGeometryKilometers(
                createEmptyAddresses(
                    Point(100.0, 100.0) to TrackMeter(2, 1),
                    Point(100.0, 200.0) to TrackMeter(2, 101),
                ),
                createAddresses(Point(100.0, 100.0) to TrackMeter(2, 1), Point(100.0, 200.0) to TrackMeter(2, 101)),
            ),
        )
    }

    @Test
    fun shouldFindDifferencesInAddressesNewIsLongerAtEnds() {
        //
        //      -------------
        // 0    1     2     3    4
        val origAddresses =
            createAddresses(
                Point(1000.0, 0.0) to TrackMeter(1, 0),
                Point(2000.0, 0.0) to TrackMeter(2, 0),
                Point(3000.0, 0.0) to TrackMeter(3, 0),
            )

        //
        // -----------------------
        // 0    1     2     3    4
        val newAddresses =
            createAddresses(
                Point(0.0, 0.0) to TrackMeter(0, 0),
                Point(1000.0, 0.0) to TrackMeter(1, 0),
                Point(2000.0, 0.0) to TrackMeter(2, 0),
                Point(3000.0, 0.0) to TrackMeter(3, 0),
                Point(4000.0, 0.0) to TrackMeter(4, 0),
            )

        val differences = resolveChangedGeometryKilometers(origAddresses, newAddresses)

        assertEquals(setOf(KmNumber(0), KmNumber(3)), differences, "Contains wrong km numbers")
    }

    @Test
    fun shouldFindDifferencesInAddressesNewIsShorterAtEnds() {
        //
        // -----------------------
        // 0    1     2     3    4
        val origAddresses =
            createAddresses(
                Point(0.0, 0.0) to TrackMeter(0, 0),
                Point(1000.0, 0.0) to TrackMeter(1, 0),
                Point(2000.0, 0.0) to TrackMeter(2, 0),
                Point(3000.0, 0.0) to TrackMeter(3, 0),
                Point(4000.0, 0.0) to TrackMeter(4, 0),
            )

        //
        //      -------------
        // 0    1     2     3    4
        val newAddresses =
            createAddresses(
                Point(1000.0, 0.0) to TrackMeter(1, 0),
                Point(2000.0, 0.0) to TrackMeter(2, 0),
                Point(3000.0, 0.0) to TrackMeter(3, 0),
            )

        val differences = resolveChangedGeometryKilometers(origAddresses, newAddresses)

        assertEquals(setOf(KmNumber(0), KmNumber(3)), differences, "Contains wrong km numbers")
    }

    @Test
    fun shouldFindDifferencesInAddressesNewAfterOriginal() {
        //
        // ------------
        // 0    1     2     3    4
        val origAddresses =
            createAddresses(
                Point(0.0, 0.0) to TrackMeter(0, 0),
                Point(1000.0, 0.0) to TrackMeter(1, 0),
                Point(2000.0, 0.0) to TrackMeter(2, 0),
            )

        //
        //            ------------
        // 0    1     2     3    4
        val newAddresses =
            createAddresses(
                Point(2000.0, 0.0) to TrackMeter(2, 0),
                Point(3000.0, 0.0) to TrackMeter(3, 0),
                Point(4000.0, 0.0) to TrackMeter(4, 0),
            )

        val differences = resolveChangedGeometryKilometers(origAddresses, newAddresses)

        assertEquals(
            setOf(KmNumber(0), KmNumber(1), KmNumber(2), KmNumber(3)),
            differences,
            "Contains wrong km numbers",
        )
    }

    @Test
    fun shouldFindDifferencesInAddressesNewBeforeOriginal() {
        //
        //            ------------
        // 0    1     2     3    4
        val origAddresses =
            createAddresses(
                Point(2000.0, 0.0) to TrackMeter(2, 0),
                Point(3000.0, 0.0) to TrackMeter(3, 0),
                Point(4000.0, 0.0) to TrackMeter(4, 0),
            )

        //
        // ------------
        // 0    1     2     3    4
        val newAddresses =
            createAddresses(
                Point(0.0, 0.0) to TrackMeter(0, 0),
                Point(1000.0, 0.0) to TrackMeter(1, 0),
                Point(2000.0, 0.0) to TrackMeter(2, 0),
            )

        val differences = resolveChangedGeometryKilometers(origAddresses, newAddresses)

        assertEquals(
            setOf(KmNumber(0), KmNumber(1), KmNumber(2), KmNumber(3)),
            differences,
            "Contains wrong km numbers",
        )
    }

    @Test
    fun shouldFindDifferencesInAddressesNewIsLongerInMiddle() {
        //
        // -----------------------
        // 0    1     2     3    4
        val origAddresses =
            createAddresses(
                Point(0.0, 0.0) to TrackMeter(0, 0),
                Point(1000.0, 0.0) to TrackMeter(1, 0),
                Point(2000.0, 0.0) to TrackMeter(2, 0),
                Point(3000.0, 0.0) to TrackMeter(3, 0),
                Point(4000.0, 0.0) to TrackMeter(4, 0),
            )

        //         /-----\
        // -------/       \-------
        // 0    1     2     3    4
        val newAddresses =
            createAddresses(
                Point(0.0, 0.0) to TrackMeter(0, 0),
                Point(1000.0, 0.0) to TrackMeter(1, 0),
                Point(1200.0, 0.0) to TrackMeter(1, 200),
                Point(1500.0, 100.0) to TrackMeter(1, 500),
                Point(2000.0, 100.0) to TrackMeter(2, 0),
                Point(2500.0, 100.0) to TrackMeter(2, 500),
                Point(2800.0, 0.0) to TrackMeter(2, 800),
                Point(3000.0, 0.0) to TrackMeter(3, 0),
                Point(4000.0, 0.0) to TrackMeter(4, 0),
            )

        val differences = resolveChangedGeometryKilometers(origAddresses, newAddresses)

        assertEquals(setOf(KmNumber(1), KmNumber(2)), differences, "Contains wrong km numbers")
    }

    @Test
    fun shouldFindDifferencesInAddressesNewIsShorterInMiddle() {
        //         /-----\
        // -------/       \-------
        // 0    1     2     3    4
        val origAddresses =
            createAddresses(
                Point(0.0, 0.0) to TrackMeter(0, 0),
                Point(1000.0, 0.0) to TrackMeter(1, 0),
                Point(1200.0, 0.0) to TrackMeter(1, 200),
                Point(1500.0, 100.0) to TrackMeter(1, 500),
                Point(2000.0, 100.0) to TrackMeter(2, 0),
                Point(2500.0, 100.0) to TrackMeter(2, 500),
                Point(2800.0, 0.0) to TrackMeter(2, 800),
                Point(3000.0, 0.0) to TrackMeter(3, 0),
                Point(4000.0, 0.0) to TrackMeter(4, 0),
            )

        //
        // -----------------------
        // 0    1           3    4
        val newAddresses =
            createAddresses(
                Point(0.0, 0.0) to TrackMeter(0, 0),
                Point(1000.0, 0.0) to TrackMeter(1, 0),
                Point(3000.0, 0.0) to TrackMeter(3, 0),
                Point(4000.0, 0.0) to TrackMeter(4, 0),
            )

        val differences = resolveChangedGeometryKilometers(origAddresses, newAddresses)

        assertEquals(setOf(KmNumber(1), KmNumber(2)), differences, "Contains wrong km numbers")
    }

    @Test
    fun shouldFindDifferencesInAddressesNewStartsWithDifferentKmNumber() {
        //
        //      ------------------
        //      1     2     3    4
        val origAddresses =
            createAddresses(
                Point(1000.0, 0.0) to TrackMeter(1, 0),
                Point(2000.0, 0.0) to TrackMeter(2, 0),
                Point(3000.0, 0.0) to TrackMeter(3, 0),
                Point(4000.0, 0.0) to TrackMeter(4, 0),
            )

        //
        // -----------------------
        // 0          2     3    4
        val newAddresses =
            createAddresses(
                Point(1000.0, 0.0) to TrackMeter(0, 0),
                Point(2000.0, 0.0) to TrackMeter(2, 0),
                Point(3000.0, 0.0) to TrackMeter(3, 0),
                Point(4000.0, 0.0) to TrackMeter(4, 0),
            )

        val differences = resolveChangedGeometryKilometers(origAddresses, newAddresses)

        assertEquals(setOf(KmNumber(0), KmNumber(1)), differences, "Contains wrong km numbers")
    }

    @Test
    fun shouldFindDifferencesInAddressesNewIsLongerInMiddleTwoSections() {
        //
        // -------------------------------
        // 0    1         2     3        4
        val origAddresses =
            createAddresses(
                Point(0.0, 0.0) to TrackMeter(0, 0),
                Point(1000.0, 0.0) to TrackMeter(1, 0),
                Point(2000.0, 0.0) to TrackMeter(2, 0),
                Point(3000.0, 0.0) to TrackMeter(3, 0),
                Point(4000.0, 0.0) to TrackMeter(4, 0),
            )

        //         /--\            /--\
        // -------/    \----------/    \--
        // 0    1         2     3        4
        val newAddresses =
            createAddresses(
                Point(0.0, 0.0) to TrackMeter(0, 0),
                Point(1000.0, 0.0) to TrackMeter(1, 0),
                Point(1300.0, 100.0) to TrackMeter(1, 300),
                Point(1600.0, 0.0) to TrackMeter(1, 600),
                Point(2000.0, 0.0) to TrackMeter(2, 0),
                Point(3000.0, 0.0) to TrackMeter(3, 0),
                Point(3300.0, 100.0) to TrackMeter(3, 300),
                Point(3600.0, 0.0) to TrackMeter(3, 600),
                Point(4000.0, 0.0) to TrackMeter(4, 0),
            )

        val differences = resolveChangedGeometryKilometers(origAddresses, newAddresses)

        assertEquals(setOf(KmNumber(1), KmNumber(3)), differences, "Contains wrong km numbers")
    }

    @Test
    fun shouldNotFindDifferencesInSameAddresses() {
        //
        //      -------------
        // 0    1     2     3    4
        val origAddresses =
            createAddresses(
                Point(1000.0, 0.0) to TrackMeter(1, 0),
                Point(2000.0, 0.0) to TrackMeter(2, 0),
                Point(3000.0, 0.0) to TrackMeter(3, 0),
            )

        //
        //      -------------
        // 0    1     2     3    4
        val newAddresses =
            createAddresses(
                Point(1000.0, 0.0) to TrackMeter(1, 0),
                Point(2000.0, 0.0) to TrackMeter(2, 0),
                Point(3000.0, 0.0) to TrackMeter(3, 0),
            )

        val differences = resolveChangedGeometryKilometers(origAddresses, newAddresses)

        assertEquals(differences, emptySet(), "Should not contain differences")
    }

    @Test
    fun whenOldAddressesDoesNotExistsCompareShouldReturnAllNewAddresses() {
        //
        //      -------------
        // 0    1     2     3    4
        val newAddresses =
            createAddresses(
                Point(1000.0, 0.0) to TrackMeter(1, 0),
                Point(2000.0, 0.0) to TrackMeter(2, 0),
                Point(3000.0, 0.0) to TrackMeter(3, 0),
            )

        val addressChanges = getAddressChanges(null, newAddresses)

        assertNotNull(addressChanges)
        assertEquals(setOf(KmNumber(1), KmNumber(2)), addressChanges.changedKmNumbers)
        assertTrue(addressChanges.startPointChanged)
        assertTrue(addressChanges.endPointChanged)
    }

    data class SetupData(
        val locationTrack: LocationTrack,
        val locationTrackGeometry: LocationTrackGeometry,
        val referenceLine: ReferenceLine,
        val referenceLineGeometry: LayoutAlignment,
        val kmPosts: List<LayoutKmPost>,
    )

    fun createAndInsertTrackNumberAndLocationTrack(): SetupData {
        val sequence = System.currentTimeMillis().toString().takeLast(8)
        val refPoint = Point(370000.0, 7100000.0) // any point in Finland
        val trackNumber =
            layoutTrackNumberDao.fetch(
                layoutTrackNumberDao.save(trackNumber(TrackNumber("TEST TN $sequence"), draft = false))
            )
        val kmPost1 =
            layoutKmPostDao.fetch(
                layoutKmPostDao.save(
                    kmPost(
                        trackNumberId = trackNumber.id as IntId,
                        km = KmNumber(1),
                        roughLayoutLocation = refPoint + 5.0,
                        draft = false,
                    )
                )
            )
        val kmPost2 =
            layoutKmPostDao.fetch(
                layoutKmPostDao.save(
                    kmPost(
                        trackNumberId = trackNumber.id as IntId,
                        km = KmNumber(2),
                        roughLayoutLocation = refPoint + 10.0,
                        draft = false,
                    )
                )
            )
        val referenceLinePoints = (0..15).map { i -> refPoint + i.toDouble() }
        val referenceLineGeometryVersion =
            layoutAlignmentDao.insert(alignment(splitSegment(segment(*referenceLinePoints.toTypedArray()), 4)))
        val referenceLineGeometry = layoutAlignmentDao.fetch(referenceLineGeometryVersion)
        val referenceLine =
            referenceLineDao.fetch(
                referenceLineDao.save(
                    referenceLine(
                        trackNumber.id as IntId<LayoutTrackNumber>,
                        alignment = referenceLineGeometry,
                        alignmentVersion = referenceLineGeometryVersion,
                        draft = false,
                    )
                )
            )
        val alignmentPoints = referenceLinePoints.subList(2, referenceLinePoints.count() - 2)

        val (locationTrack, geometry) =
            mainOfficialContext.saveAndFetch(
                locationTrack(
                    trackNumberId = trackNumber.id as IntId,
                    name = locationTrackDbName("TEST LocTr $sequence"),
                    draft = false,
                ),
                trackGeometryOfSegments(splitSegment(segment(*alignmentPoints.toTypedArray()), 3)),
            )

        return SetupData(locationTrack, geometry, referenceLine, referenceLineGeometry, listOf(kmPost1, kmPost2))
    }

    fun removeLocationTrackGeometryAndUpdate(locationTrack: LocationTrack) =
        updateAndPublish(locationTrack, TmpLocationTrackGeometry.empty)

    fun updateAndPublish(locationTrack: LocationTrack, geometry: LocationTrackGeometry) {
        val version = locationTrackService.saveDraft(LayoutBranch.main, locationTrack, geometry)
        locationTrackService.publish(LayoutBranch.main, version)
    }

    fun moveReferenceLineGeometryPointsAndUpdate(
        referenceLine: ReferenceLine,
        alignment: LayoutAlignment,
        moveFunc: (index: Int, point: IPoint) -> IPoint,
    ) {
        var index = 0
        val version =
            referenceLineService.saveDraft(
                LayoutBranch.main,
                referenceLine,
                alignment.copy(
                    segments =
                        alignment.segments.map { segment ->
                            segment.copy(
                                geometry =
                                    segment.geometry.withPoints(
                                        fixMValues(
                                            segment.segmentPoints.mapIndexed { inSegmentIndex, point ->
                                                val newPoint = moveFunc(index, point)
                                                if (inSegmentIndex < segment.segmentPoints.lastIndex) index++
                                                point.copy(x = newPoint.x, y = newPoint.y)
                                            }
                                        )
                                    )
                            )
                        }
                ),
            )
        referenceLineService.publish(LayoutBranch.main, version)
    }

    fun moveKmPostGkLocationAndUpdate(kmPost: LayoutKmPost, moveFunc: (point: IPoint) -> Point): LayoutKmPost {
        return layoutKmPostDao.fetch(
            layoutKmPostDao.save(
                kmPost.copy(
                    gkLocation =
                        kmPost.gkLocation?.copy(
                            location =
                                GeometryPoint(moveFunc(kmPost.gkLocation!!.location), kmPost.gkLocation!!.location.srid)
                        )
                )
            )
        )
    }

    fun createLineString(vararg transitPoints: Point): List<Point> {
        return transitPoints
            .flatMapIndexed { index, from ->
                val to = transitPoints.getOrNull(index + 1)
                if (to != null) {
                    val length = ceil(lineLength(from, to)).toInt()
                    (0..length).map { i ->
                        val ratio = i / length.toDouble()
                        (from * (1 - ratio) + to * ratio) / 2.0
                    }
                } else {
                    emptyList()
                }
            }
            .distinct()
    }

    fun createEmptyAddresses(start: Pair<Point, TrackMeter>, end: Pair<Point, TrackMeter>): AlignmentAddresses =
        AlignmentAddresses(
            startPoint = AddressPoint(AlignmentPoint(start.first.x, start.first.y, null, 0.0, null), start.second),
            endPoint =
                AddressPoint(
                    AlignmentPoint(end.first.x, end.first.y, null, lineLength(start.first, end.first), null),
                    start.second,
                ),
            startIntersect = IntersectType.WITHIN,
            endIntersect = IntersectType.WITHIN,
            midPoints = listOf(),
            alignmentWalkFinished = true,
        )

    fun createAddresses(vararg transitionPoints: Pair<Point, TrackMeter>): AlignmentAddresses {
        val addressPoints =
            transitionPoints.flatMapIndexed { index, transitionPoint ->
                val from = transitionPoint.first
                val fromAddress = transitionPoint.second
                val nextTransitionPoint = transitionPoints.getOrNull(index + 1)
                if (nextTransitionPoint != null) {
                    val to = nextTransitionPoint.first
                    val points = createLineString(from, to)
                    val alignmentPoints = toAlignmentPoints(points = points.toTypedArray())
                    val addressPoints =
                        alignmentPoints.map { alignmentPoint: AlignmentPoint ->
                            AddressPoint(point = alignmentPoint, address = fromAddress + alignmentPoint.m)
                        }
                    addressPoints
                } else {
                    emptyList()
                }
            }
        return AlignmentAddresses(
            startPoint = addressPoints.firstOrNull() ?: someAddressPoint(),
            endPoint = addressPoints.lastOrNull() ?: someAddressPoint(),
            startIntersect = IntersectType.WITHIN,
            endIntersect = IntersectType.WITHIN,
            midPoints = addressPoints.slice(1..addressPoints.size - 2),
            alignmentWalkFinished = true,
        )
    }

    fun someAddressPoint() = AddressPoint(AlignmentPoint(0.0, 0.0, null, 0.0, null), TrackMeter(0, 0))

    fun getAllKms(geocodingContextCacheKey: GeocodingContextCacheKey, start: IPoint, end: IPoint): Set<KmNumber> {
        val context = geocodingService.getGeocodingContext(geocodingContextCacheKey)!!
        val startKm = context.getAddress(start)!!.first.kmNumber
        val endKm = context.getAddress(end)!!.first.kmNumber
        return context.referencePoints.map { r -> r.kmNumber }.filter { km -> km in startKm..endKm }.toSet()
    }

    fun movePoint(point: SegmentPoint, delta: Double) =
        SegmentPoint(x = point.x + delta, y = point.y + delta, z = point.z, m = point.m, cant = point.cant)

    fun getTrackAtMoment(locationTrackId: IntId<LocationTrack>, moment: Instant) =
        locationTrackService.getOfficialAtMoment(LayoutBranch.main, locationTrackId, moment)

    fun getContextKeyAtMoment(trackNumberId: IntId<LayoutTrackNumber>, moment: Instant) =
        geocodingService.getGeocodingContextCacheKey(LayoutBranch.main, trackNumberId, moment)
}
