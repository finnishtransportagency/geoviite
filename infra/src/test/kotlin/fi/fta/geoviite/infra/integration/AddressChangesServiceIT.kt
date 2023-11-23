package fi.fta.geoviite.infra.integration

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geocoding.*
import fi.fta.geoviite.infra.linking.fixSegmentStarts
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.IntersectType
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.publication.ValidationVersion
import fi.fta.geoviite.infra.tracklayout.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import kotlin.math.ceil
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ActiveProfiles("dev", "test")
@SpringBootTest
class AddressChangesServiceIT @Autowired constructor(
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
): DBTestBase() {

    @Test
    fun addressChangesAreEmptyIfNothingCanBeGeocoded() {
        val setupData1 = createAndInsertTrackNumberAndLocationTrack()
        val setupData2 = createAndInsertTrackNumberAndLocationTrack()
        val changes = addressChangesService.getAddressChanges(
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
        val contextKey = geocodingDao.getLayoutGeocodingContextCacheKey(OFFICIAL, setupData.locationTrack.trackNumberId)!!
        val changes = addressChangesService.getAddressChanges(
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
        val contextKey = geocodingDao.getLayoutGeocodingContextCacheKey(OFFICIAL, setupData.locationTrack.trackNumberId)!!
        val changes = addressChangesService.getAddressChanges(
            beforeTrack = null,
            afterTrack = setupData.locationTrack,
            beforeContextKey = null,
            afterContextKey = contextKey,
        )
        assertTrue(changes.isChanged())
        assertTrue(changes.startPointChanged)
        assertTrue(changes.endPointChanged)
        val allKms = getAllKms(
            contextKey,
            setupData.locationTrackGeometry.start!!,
            setupData.locationTrackGeometry.end!!,
        )
        assertEquals(allKms, changes.changedKmNumbers)
    }

    @Test
    fun addressChangesContainAllAddressesIfTrackIsBeingRestoredFromBeingDeleted() {
        val setupData = createAndInsertTrackNumberAndLocationTrack()
        val contextKey = geocodingDao.getLayoutGeocodingContextCacheKey(OFFICIAL, setupData.locationTrack.trackNumberId)!!
        val changes = addressChangesService.getAddressChanges(
            beforeTrack = setupData.locationTrack.copy(state = LayoutState.DELETED),
            afterTrack = setupData.locationTrack,
            beforeContextKey = contextKey,
            afterContextKey = contextKey,
        )
        assertTrue(changes.isChanged())
        assertTrue(changes.startPointChanged)
        assertTrue(changes.endPointChanged)
        val allKms = getAllKms(
            contextKey,
            setupData.locationTrackGeometry.start!!,
            setupData.locationTrackGeometry.end!!,
        )
        assertEquals(allKms, changes.changedKmNumbers)
    }

    @Test
    fun addressChangesContainAllAddressesIfAfterVersionHasNoGeometry() {
        val setupData = createAndInsertTrackNumberAndLocationTrack()
        val initialLocationTrack = setupData.locationTrack
        val locationTrackId = initialLocationTrack.id as IntId
        val initialChangeMoment = locationTrackDao.fetchChangeTime()
        val contextKey = geocodingDao.getLayoutGeocodingContextCacheKey(OFFICIAL, setupData.locationTrack.trackNumberId)!!

        removeLocationTrackGeometryAndUpdate(initialLocationTrack, setupData.locationTrackGeometry)
        val updateMoment = locationTrackDao.fetchChangeTime()

        val changes = addressChangesService.getAddressChanges(
            getTrackAtMoment(locationTrackId, initialChangeMoment),
            getTrackAtMoment(locationTrackId, updateMoment)!!,
            getContextKeyAtMoment(initialLocationTrack.trackNumberId, initialChangeMoment),
            getContextKeyAtMoment(initialLocationTrack.trackNumberId, updateMoment),
        )
        assertTrue(changes.isChanged())
        assertTrue(changes.startPointChanged)
        assertTrue(changes.endPointChanged)
        val allKms = getAllKms(
            contextKey,
            setupData.locationTrackGeometry.start!!,
            setupData.locationTrackGeometry.end!!,
        )
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
            setupData.locationTrackGeometry.copy(
                segments = fixSegmentStarts(setupData.locationTrackGeometry.segments.mapIndexed { index, segment ->
                    if (index == 0) segment.copy(
                        geometry = segment.geometry.withPoints(
                            fixMValues(listOf(movePoint(segment.segmentPoints.first(), -1.0)) + segment.segmentPoints.drop(1)),
                        )
                    )
                    else segment
                }),
            ),
        )
        val updateMoment = locationTrackDao.fetchChangeTime()

        val changes = addressChangesService.getAddressChanges(
            getTrackAtMoment(locationTrackId, initialChangeMoment),
            getTrackAtMoment(locationTrackId, updateMoment)!!,
            getContextKeyAtMoment(trackNumberId, initialChangeMoment),
            getContextKeyAtMoment(trackNumberId, updateMoment),
        )
        assertTrue(changes.isChanged())
        assertTrue(changes.startPointChanged)
        assertFalse(changes.endPointChanged)
        val startAddress = geocodingService.getAddress(OFFICIAL, trackNumberId, setupData.locationTrackGeometry.start!!)!!.first
        assertEquals(setOf(startAddress.kmNumber), changes.changedKmNumbers)
    }

    @Test
    fun addressChangesFoundWhenReferenceLineChanges() {
        val setupData = createAndInsertTrackNumberAndLocationTrack()
        val initialLocationTrack = setupData.locationTrack
        val locationTrackId = initialLocationTrack.id as IntId
        val trackNumberId = initialLocationTrack.trackNumberId
        val initialChangeMoment = locationTrackDao.fetchChangeTime()

        moveReferenceLineGeometryPointsAndUpdate(
            setupData.referenceLine,
            setupData.referenceLineGeometry
        ) { index, point ->
            // The reference-line is parallel to track, so alter the shape a bit to force all addresses changing
            point + if (index % 2 == 0) Point(0.5, 0.5) else Point(0.5, 0.4)
        }
        val updateMoment = referenceLineDao.fetchChangeTime()

        val changes = addressChangesService.getAddressChanges(
            getTrackAtMoment(locationTrackId, initialChangeMoment),
            getTrackAtMoment(locationTrackId, updateMoment)!!,
            getContextKeyAtMoment(trackNumberId, initialChangeMoment),
            getContextKeyAtMoment(trackNumberId, updateMoment),
        )
        assertTrue(changes.isChanged())
        assertTrue(changes.startPointChanged, "Start should change: changes=$changes")
        assertTrue(changes.endPointChanged, "End should change: changes=$changes")
        val allKms = getAllKms(
            geocodingDao.getLayoutGeocodingContextCacheKey(OFFICIAL, setupData.locationTrack.trackNumberId)!!,
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

        moveKmPostAndUpdate(setupData.kmPosts[0]) { point -> point + 0.5 }
        val updateMoment = layoutKmPostDao.fetchChangeTime()

        val changes = addressChangesService.getAddressChanges(
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
        assertEquals(setOf(), resolveChangedGeometryKilometers(
            createEmptyAddresses(
                Point(100.0, 100.0) to TrackMeter(2, 1),
                Point(100.0, 200.0) to TrackMeter(2, 101),
            ),
            createEmptyAddresses(
                Point(100.0, 100.0) to TrackMeter(2, 1),
                Point(100.0, 200.0) to TrackMeter(2, 101),
            ),
        ))
        assertEquals(setOf(KmNumber(3)), resolveChangedGeometryKilometers(
            createAddresses(
                Point(100.0, 100.0) to TrackMeter(3, 1),
                Point(100.0, 200.0) to TrackMeter(3, 101),
            ),
            createEmptyAddresses(
                Point(100.0, 100.0) to TrackMeter(3, 1),
                Point(100.0, 200.0) to TrackMeter(3, 101),
            ),
        ))
        assertEquals(setOf(KmNumber(2)), resolveChangedGeometryKilometers(
            createEmptyAddresses(
                Point(100.0, 100.0) to TrackMeter(2, 1),
                Point(100.0, 200.0) to TrackMeter(2, 101),
            ),
            createAddresses(
                Point(100.0, 100.0) to TrackMeter(2, 1),
                Point(100.0, 200.0) to TrackMeter(2, 101),
            ),
        ))
    }

    @Test
    fun shouldFindDifferencesInAddressesNewIsLongerAtEnds() {
        //
        //      -------------
        // 0    1     2     3    4
        val origAddresses = createAddresses(
            Point(1000.0, 0.0) to TrackMeter(1, 0),
            Point(2000.0, 0.0) to TrackMeter(2, 0),
            Point(3000.0, 0.0) to TrackMeter(3, 0),
        )

        //
        // -----------------------
        // 0    1     2     3    4
        val newAddresses = createAddresses(
            Point(0.0, 0.0) to TrackMeter(0, 0),
            Point(1000.0, 0.0) to TrackMeter(1, 0),
            Point(2000.0, 0.0) to TrackMeter(2, 0),
            Point(3000.0, 0.0) to TrackMeter(3, 0),
            Point(4000.0, 0.0) to TrackMeter(4, 0),
        )

        val differences = resolveChangedGeometryKilometers(origAddresses, newAddresses)

        assertEquals(
            setOf(
                KmNumber(0),
                KmNumber(3)
            ),
            differences,
            "Contains wrong km numbers"
        )
    }

    @Test
    fun shouldFindDifferencesInAddressesNewIsShorterAtEnds() {
        //
        // -----------------------
        // 0    1     2     3    4
        val origAddresses = createAddresses(
            Point(0.0, 0.0) to TrackMeter(0, 0),
            Point(1000.0, 0.0) to TrackMeter(1, 0),
            Point(2000.0, 0.0) to TrackMeter(2, 0),
            Point(3000.0, 0.0) to TrackMeter(3, 0),
            Point(4000.0, 0.0) to TrackMeter(4, 0),
        )

        //
        //      -------------
        // 0    1     2     3    4
        val newAddresses = createAddresses(
            Point(1000.0, 0.0) to TrackMeter(1, 0),
            Point(2000.0, 0.0) to TrackMeter(2, 0),
            Point(3000.0, 0.0) to TrackMeter(3, 0),
        )

        val differences = resolveChangedGeometryKilometers(origAddresses, newAddresses)

        assertEquals(
            setOf(
                KmNumber(0),
                KmNumber(3),
            ),
            differences,
            "Contains wrong km numbers"
        )
    }

    @Test
    fun shouldFindDifferencesInAddressesNewAfterOriginal() {
        //
        // ------------
        // 0    1     2     3    4
        val origAddresses = createAddresses(
            Point(0.0, 0.0) to TrackMeter(0, 0),
            Point(1000.0, 0.0) to TrackMeter(1, 0),
            Point(2000.0, 0.0) to TrackMeter(2, 0),
        )

        //
        //            ------------
        // 0    1     2     3    4
        val newAddresses = createAddresses(
            Point(2000.0, 0.0) to TrackMeter(2, 0),
            Point(3000.0, 0.0) to TrackMeter(3, 0),
            Point(4000.0, 0.0) to TrackMeter(4, 0),
        )

        val differences = resolveChangedGeometryKilometers(origAddresses, newAddresses)

        assertEquals(
            setOf(
                KmNumber(0),
                KmNumber(1),
                KmNumber(2),
                KmNumber(3),
            ),
            differences,
            "Contains wrong km numbers"
        )
    }

    @Test
    fun shouldFindDifferencesInAddressesNewBeforeOriginal() {
        //
        //            ------------
        // 0    1     2     3    4
        val origAddresses = createAddresses(
            Point(2000.0, 0.0) to TrackMeter(2, 0),
            Point(3000.0, 0.0) to TrackMeter(3, 0),
            Point(4000.0, 0.0) to TrackMeter(4, 0),
        )

        //
        // ------------
        // 0    1     2     3    4
        val newAddresses = createAddresses(
            Point(0.0, 0.0) to TrackMeter(0, 0),
            Point(1000.0, 0.0) to TrackMeter(1, 0),
            Point(2000.0, 0.0) to TrackMeter(2, 0),
        )

        val differences = resolveChangedGeometryKilometers(origAddresses, newAddresses)

        assertEquals(
            setOf(
                KmNumber(0),
                KmNumber(1),
                KmNumber(2),
                KmNumber(3),
            ),
            differences,
            "Contains wrong km numbers"
        )
    }


    @Test
    fun shouldFindDifferencesInAddressesNewIsLongerInMiddle() {
        //
        // -----------------------
        // 0    1     2     3    4
        val origAddresses = createAddresses(
            Point(0.0, 0.0) to TrackMeter(0, 0),
            Point(1000.0, 0.0) to TrackMeter(1, 0),
            Point(2000.0, 0.0) to TrackMeter(2, 0),
            Point(3000.0, 0.0) to TrackMeter(3, 0),
            Point(4000.0, 0.0) to TrackMeter(4, 0),
        )

        //         /-----\
        // -------/       \-------
        // 0    1     2     3    4
        val newAddresses = createAddresses(
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

        assertEquals(
            setOf(
                KmNumber(1),
                KmNumber(2)
            ),
            differences,
            "Contains wrong km numbers"
        )
    }

    @Test
    fun shouldFindDifferencesInAddressesNewIsShorterInMiddle() {
        //         /-----\
        // -------/       \-------
        // 0    1     2     3    4
        val origAddresses = createAddresses(
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
        val newAddresses = createAddresses(
            Point(0.0, 0.0) to TrackMeter(0, 0),
            Point(1000.0, 0.0) to TrackMeter(1, 0),
            Point(3000.0, 0.0) to TrackMeter(3, 0),
            Point(4000.0, 0.0) to TrackMeter(4, 0),
        )

        val differences = resolveChangedGeometryKilometers(origAddresses, newAddresses)

        assertEquals(
            setOf(
                KmNumber(1),
                KmNumber(2),
            ),
            differences,
            "Contains wrong km numbers"
        )
    }

    @Test
    fun shouldFindDifferencesInAddressesNewStartsWithDifferentKmNumber() {
        //
        //      ------------------
        //      1     2     3    4
        val origAddresses = createAddresses(
            Point(1000.0, 0.0) to TrackMeter(1, 0),
            Point(2000.0, 0.0) to TrackMeter(2, 0),
            Point(3000.0, 0.0) to TrackMeter(3, 0),
            Point(4000.0, 0.0) to TrackMeter(4, 0),
        )

        //
        // -----------------------
        // 0          2     3    4
        val newAddresses = createAddresses(
            Point(1000.0, 0.0) to TrackMeter(0, 0),
            Point(2000.0, 0.0) to TrackMeter(2, 0),
            Point(3000.0, 0.0) to TrackMeter(3, 0),
            Point(4000.0, 0.0) to TrackMeter(4, 0),
        )

        val differences = resolveChangedGeometryKilometers(origAddresses, newAddresses)

        assertEquals(
            setOf(
                KmNumber(0),
                KmNumber(1),
            ),
            differences,
            "Contains wrong km numbers"
        )
    }

    @Test
    fun shouldFindDifferencesInAddressesNewIsLongerInMiddleTwoSections() {
        //
        // -------------------------------
        // 0    1         2     3        4
        val origAddresses = createAddresses(
            Point(0.0, 0.0) to TrackMeter(0, 0),
            Point(1000.0, 0.0) to TrackMeter(1, 0),
            Point(2000.0, 0.0) to TrackMeter(2, 0),
            Point(3000.0, 0.0) to TrackMeter(3, 0),
            Point(4000.0, 0.0) to TrackMeter(4, 0),
        )

        //         /--\            /--\
        // -------/    \----------/    \--
        // 0    1         2     3        4
        val newAddresses = createAddresses(
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

        assertEquals(
            setOf(
                KmNumber(1),
                KmNumber(3)
            ),
            differences,
            "Contains wrong km numbers"
        )
    }

    @Test
    fun shouldNotFindDifferencesInSameAddresses() {
        //
        //      -------------
        // 0    1     2     3    4
        val origAddresses = createAddresses(
            Point(1000.0, 0.0) to TrackMeter(1, 0),
            Point(2000.0, 0.0) to TrackMeter(2, 0),
            Point(3000.0, 0.0) to TrackMeter(3, 0),
        )

        //
        //      -------------
        // 0    1     2     3    4
        val newAddresses = createAddresses(
            Point(1000.0, 0.0) to TrackMeter(1, 0),
            Point(2000.0, 0.0) to TrackMeter(2, 0),
            Point(3000.0, 0.0) to TrackMeter(3, 0),
        )

        val differences = resolveChangedGeometryKilometers(origAddresses, newAddresses)

        assertEquals(
            differences, emptySet(),
            "Should not contain differences"
        )
    }

    @Test
    fun whenOldAddressesDoesNotExistsCompareShouldReturnAllNewAddresses() {
        //
        //      -------------
        // 0    1     2     3    4
        val newAddresses = createAddresses(
            Point(1000.0, 0.0) to TrackMeter(1, 0),
            Point(2000.0, 0.0) to TrackMeter(2, 0),
            Point(3000.0, 0.0) to TrackMeter(3, 0),
        )

        val addressChanges = getAddressChanges(null, newAddresses)

        assertNotNull(addressChanges)
        assertEquals(
            setOf(KmNumber(1), KmNumber(2)),
            addressChanges.changedKmNumbers
        )
        assertTrue(addressChanges.startPointChanged)
        assertTrue(addressChanges.endPointChanged)
    }

    data class SetupData(
        val locationTrack: LocationTrack,
        val locationTrackGeometry: LayoutAlignment,
        val referenceLine: ReferenceLine,
        val referenceLineGeometry: LayoutAlignment,
        val kmPosts: List<TrackLayoutKmPost>,
    )


    fun createAndInsertTrackNumberAndLocationTrack(): SetupData {
        val sequence = System.currentTimeMillis().toString().takeLast(8)
        val refPoint = Point(370000.0, 7100000.0) // any point in Finland
        val trackNumber = layoutTrackNumberDao.fetch(
            layoutTrackNumberDao.insert(trackNumber(TrackNumber("TEST TN $sequence"))).rowVersion
        )
        val kmPost1 = layoutKmPostDao.fetch(
            layoutKmPostDao.insert(
                kmPost(
                    trackNumberId = trackNumber.id as IntId,
                    km = KmNumber(1),
                    location = refPoint + 5.0
                )
            ).rowVersion
        )
        val kmPost2 = layoutKmPostDao.fetch(
            layoutKmPostDao.insert(
                kmPost(
                    trackNumberId = trackNumber.id as IntId,
                    km = KmNumber(2),
                    location = refPoint + 10.0
                )
            ).rowVersion
        )
        val referenceLinePoints = (0..15).map { i -> refPoint + i.toDouble() }
        val referenceLineGeometryVersion = layoutAlignmentDao.insert(
            alignment(
                splitSegment(segment(*referenceLinePoints.toTypedArray()), 4)
            )
        )
        val referenceLineGeometry = layoutAlignmentDao.fetch(referenceLineGeometryVersion)
        val referenceLine = referenceLineDao.fetch(
            referenceLineDao.insert(
                referenceLine(
                    trackNumber.id as IntId<TrackLayoutTrackNumber>,
                    alignment = referenceLineGeometry
                ).copy(
                    alignmentVersion = referenceLineGeometryVersion
                )
            ).rowVersion
        )
        val alignmentPoints = referenceLinePoints.subList(2, referenceLinePoints.count() - 2)
        val locationTrackGeometryVersion = layoutAlignmentDao.insert(
            alignment(
                splitSegment(segment(*alignmentPoints.toTypedArray()), 3)
            )
        )
        val locationTrackGeometry = layoutAlignmentDao.fetch(locationTrackGeometryVersion)
        val locationTrack = locationTrackDao.fetch(
            locationTrackDao.insert(
                locationTrack(
                    trackNumberId = trackNumber.id as IntId,
                    alignment = locationTrackGeometry,
                    name = "TEST LocTr $sequence"
                ).copy(
                    alignmentVersion = locationTrackGeometryVersion
                )
            ).rowVersion
        )

        return SetupData(
            locationTrack,
            locationTrackGeometry,
            referenceLine,
            referenceLineGeometry,
            listOf(kmPost1, kmPost2),
        )
    }

    fun removeLocationTrackGeometryAndUpdate(locationTrack: LocationTrack, alignment: LayoutAlignment) =
        updateAndPublish(locationTrack, alignment.copy(segments = listOf()))

    fun updateAndPublish(locationTrack: LocationTrack, alignment: LayoutAlignment) {
        val version = locationTrackService.saveDraft(locationTrack, alignment)
        locationTrackService.publish(ValidationVersion(version.id, version.rowVersion))
    }

    fun moveReferenceLineGeometryPointsAndUpdate(
        referenceLine: ReferenceLine,
        alignment: LayoutAlignment,
        moveFunc: (index: Int, point: IPoint) -> IPoint,
    ) {
        var index = 0
        val version = referenceLineService.saveDraft(
            referenceLine,
            alignment.copy(
                segments = fixSegmentStarts(alignment.segments.map { segment ->
                    segment.copy(
                        geometry = segment.geometry.withPoints(
                            fixMValues(segment.segmentPoints.mapIndexed { inSegmentIndex, point ->
                                val newPoint = moveFunc(index, point)
                                if (inSegmentIndex < segment.alignmentPoints.lastIndex) index++
                                point.copy(x = newPoint.x, y = newPoint.y)
                            }),
                        ),
                    )
                }),
            ),
        )
        referenceLineService.publish(ValidationVersion(version.id, version.rowVersion))
    }

    fun moveKmPostAndUpdate(
        kmPost: TrackLayoutKmPost,
        moveFunc: (point: IPoint) -> Point,
    ): TrackLayoutKmPost {
        return layoutKmPostDao.fetch(
            layoutKmPostDao.update(
                kmPost.copy(location = moveFunc(kmPost.location!!))
            ).rowVersion
        )
    }

    fun createLineString(vararg transitPoints: Point): List<Point> {
        return transitPoints.flatMapIndexed { index, from ->
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
        }.distinct()
    }

    fun createEmptyAddresses(start: Pair<Point, TrackMeter>, end: Pair<Point, TrackMeter>): AlignmentAddresses =
        AlignmentAddresses(
            startPoint = AddressPoint(
                AlignmentPoint(start.first.x, start.first.y, null, 0.0, null),
                start.second,
            ),
            endPoint = AddressPoint(
                AlignmentPoint(end.first.x, end.first.y, null, lineLength(start.first, end.first), null),
                start.second,
            ),
            startIntersect = IntersectType.WITHIN,
            endIntersect = IntersectType.WITHIN,
            midPoints = listOf(),
        )

    fun createAddresses(
        vararg transitionPoints: Pair<Point, TrackMeter>,
    ): AlignmentAddresses {
        val addressPoints = transitionPoints.flatMapIndexed { index, transitionPoint ->
            val from = transitionPoint.first
            val fromAddress = transitionPoint.second
            val nextTransitionPoint = transitionPoints.getOrNull(index + 1)
            if (nextTransitionPoint != null) {
                val to = nextTransitionPoint.first
                val points = createLineString(from, to)
                val alignmentPoints = toAlignmentPoints(points = points.toTypedArray())
                val addressPoints = alignmentPoints.map { alignmentPoint: AlignmentPoint ->
                    AddressPoint(
                        point = alignmentPoint,
                        address = fromAddress + alignmentPoint.m,
                    )
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
            midPoints = addressPoints.slice(1..addressPoints.size-2),
        )
    }

    fun someAddressPoint() = AddressPoint(
        AlignmentPoint(0.0, 0.0, null, 0.0, null),
        TrackMeter(0, 0),
    )

    fun getAllKms(geocodingContextCacheKey: GeocodingContextCacheKey, start: IPoint, end: IPoint): Set<KmNumber> {
        val context = geocodingService.getGeocodingContext(geocodingContextCacheKey)!!
        val startKm = context.getAddress(start)!!.first.kmNumber
        val endKm = context.getAddress(end)!!.first.kmNumber
        return context.referencePoints.map { r -> r.kmNumber }.filter { km -> km in startKm..endKm }.toSet()
    }

    fun movePoint(point: SegmentPoint, delta: Double) = SegmentPoint(
        x = point.x + delta,
        y = point.y + delta,
        z = point.z,
        m = point.m,
        cant = point.cant,
    )

    fun getTrackAtMoment(locationTrackId: IntId<LocationTrack>, moment: Instant) =
        locationTrackService.getOfficialAtMoment(locationTrackId, moment)

    fun getContextKeyAtMoment(trackNumberId: IntId<TrackLayoutTrackNumber>, moment: Instant) =
        geocodingService.getGeocodingContextCacheKey(trackNumberId, moment)
}
