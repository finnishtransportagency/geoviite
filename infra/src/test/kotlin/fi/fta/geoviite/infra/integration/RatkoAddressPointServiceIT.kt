package fi.fta.geoviite.infra.integration

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.IntersectType
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.tracklayout.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.math.ceil
import kotlin.test.*

@ActiveProfiles("dev", "test")
@SpringBootTest
class RatkoAddressPointServiceIT @Autowired constructor(
    val locationTrackDao: LocationTrackDao,
    val locationTrackService: LocationTrackService,
    val referenceLineDao: ReferenceLineDao,
    val referenceLineService: ReferenceLineService,
    val layoutAlignmentDao: LayoutAlignmentDao,
    val layoutTrackNumberDao: LayoutTrackNumberDao,
    val layoutKmPostDao: LayoutKmPostDao,
    val addressChangesService: AddressChangesService,
): ITTestBase() {
    @Test
    fun getAddressesAtMomentReturnsNullIfThereIsNoGeometry() {
        val setupData = createAndInsertTrackNumberAndLocationTrack()
        val initialLocationTrack = setupData.locationTrack
        val locationTrackId = initialLocationTrack.id as IntId
        val initialChangeMoment = locationTrackDao.fetchChangeTime()

        val addressesAfterInsert = addressChangesService.getAlignmentAddressesAtMoment(
            locationTrackId,
            initialChangeMoment
        )

        removeLocationTrackGeometryAndUpdate(
            initialLocationTrack,
            setupData.locationTrackGeometry
        )
        val updateMoment = locationTrackDao.fetchChangeTime()

        val addressesAfterUpdateAtInitialMoment = addressChangesService.getAlignmentAddressesAtMoment(
            locationTrackId,
            initialChangeMoment
        )
        val addressesAfterUpdateAtUpdateMoment = addressChangesService.getAlignmentAddressesAtMoment(
            locationTrackId,
            updateMoment
        )

        assertNotNull(addressesAfterInsert)
        assertAreEqual(addressesAfterInsert, addressesAfterUpdateAtInitialMoment)
        assertNull(addressesAfterUpdateAtUpdateMoment)
    }


    @Test
    fun canGetAddressesAtSpecificMomentWhenAlignmentGeometryIsChanged() {
        val setupData = createAndInsertTrackNumberAndLocationTrack()
        val initialLocationTrack = setupData.locationTrack
        val locationTrackId = initialLocationTrack.id as IntId
        val initialChangeMoment = locationTrackDao.fetchChangeTime()

        val addressesAfterInsert = addressChangesService.getAlignmentAddressesAtMoment(
            locationTrackId,
            initialChangeMoment
        )

        moveLocationTrackGeometryPointsAndUpdate(
            initialLocationTrack,
            setupData.locationTrackGeometry
        ) { point -> point + 1.1 }
        val updateMoment = locationTrackDao.fetchChangeTime()

        val addressesAfterUpdateAtInitialMoment = addressChangesService.getAlignmentAddressesAtMoment(
            locationTrackId,
            initialChangeMoment
        )
        val addressesAfterUpdateAtUpdateMoment = addressChangesService.getAlignmentAddressesAtMoment(
            locationTrackId,
            updateMoment
        )

        assertNotNull(addressesAfterInsert)
        assertAreEqual(addressesAfterInsert, addressesAfterUpdateAtInitialMoment)
        assertAreNotEqual(addressesAfterInsert, addressesAfterUpdateAtUpdateMoment)
    }

    @Test
    fun canGetAddressesAtSpecificMomentWhenReferenceGeometryIsChanged() {
        val setupData = createAndInsertTrackNumberAndLocationTrack()
        val initialAlignment = setupData.locationTrack
        val locationTrackId = initialAlignment.id as IntId
        val initialChangeMoment = locationTrackDao.fetchChangeTime()

        val addressesAfterInsert = addressChangesService.getAlignmentAddressesAtMoment(
            locationTrackId,
            initialChangeMoment
        )

        moveReferenceLineGeometryPointsAndUpdate(
            setupData.referenceLine,
            setupData.referenceLineGeometry
        ) { point -> point + 1.1 }
        val updateMoment = referenceLineDao.fetchChangeTime()

        val addressesAfterUpdateAtInitialMoment = addressChangesService.getAlignmentAddressesAtMoment(
            locationTrackId,
            initialChangeMoment
        )
        val addressesAfterUpdateAtUpdateMoment = addressChangesService.getAlignmentAddressesAtMoment(
            locationTrackId,
            updateMoment
        )

        assertAreEqual(addressesAfterInsert, addressesAfterUpdateAtInitialMoment)
        assertAreNotEqual(addressesAfterInsert, addressesAfterUpdateAtUpdateMoment)
    }

    @Test
    fun canGetAddressesAtSpecificMomentWhenKmPostIsChanged() {
        val setupData = createAndInsertTrackNumberAndLocationTrack()
        val initialAlignment = setupData.locationTrack
        val locationTrackId = initialAlignment.id as IntId
        val initialChangeMoment = locationTrackDao.fetchChangeTime()

        val addressesAfterInsert = addressChangesService.getAlignmentAddressesAtMoment(
            locationTrackId,
            initialChangeMoment
        )

        moveKmPostAndUpdate(setupData.kmPost) { point -> point + 1.1 }
        val updateMoment = layoutKmPostDao.fetchChangeTime()

        val addressesAfterUpdateAtInitialMoment = addressChangesService.getAlignmentAddressesAtMoment(
            locationTrackId,
            initialChangeMoment
        )
        val addressesAfterUpdateAtUpdateMoment = addressChangesService.getAlignmentAddressesAtMoment(
            locationTrackId,
            updateMoment
        )

        assertAreEqual(addressesAfterInsert, addressesAfterUpdateAtInitialMoment)
        assertAreNotEqual(addressesAfterInsert, addressesAfterUpdateAtUpdateMoment)
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
        val kmPost: TrackLayoutKmPost,
    )


    fun createAndInsertTrackNumberAndLocationTrack(): SetupData {
        val sequence = System.currentTimeMillis().toString().takeLast(8)
        val refPoint = Point(370000.0, 7100000.0) // any point in Finland
        val trackNumber = layoutTrackNumberDao.fetch(
            layoutTrackNumberDao.insert(trackNumber(TrackNumber("TEST TN $sequence")))
        )
        val kmPost = layoutKmPostDao.fetch(
            layoutKmPostDao.insert(
                kmPost(
                    trackNumberId = trackNumber.id,
                    km = KmNumber(1),
                    location = refPoint + 1.0
                )
            )
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
                    trackNumber.id as IntId<LayoutTrackNumber>,
                    alignment = referenceLineGeometry
                ).copy(
                    alignmentVersion = referenceLineGeometryVersion
                )
            )
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
            )
        )

        return SetupData(
            locationTrack,
            locationTrackGeometry,
            referenceLine,
            referenceLineGeometry,
            kmPost
        )
    }

    fun removeLocationTrackGeometryAndUpdate(
        locationTrack: LocationTrack,
        alignment: LayoutAlignment
    ) {
        val version = locationTrackService.saveDraft(
            locationTrack,
            alignment.copy(
                segments = listOf()
            )
        )
        locationTrackService.publish(version.id)
    }


    fun moveLocationTrackGeometryPointsAndUpdate(
        locationTrack: LocationTrack,
        alignment: LayoutAlignment,
        moveFunc: (point: IPoint) -> IPoint,
    ) {
        val version = locationTrackService.saveDraft(
            locationTrack,
            alignment.copy(
                segments = alignment.segments.map { segment ->
                    segment.copy(
                        points = segment.points.map { point ->
                            val newPoint = moveFunc(point)
                            point.copy(
                                x = newPoint.x,
                                y = newPoint.y
                            )
                        }
                    )
                }
            )
        )
        locationTrackService.publish(version.id)
    }

    fun moveReferenceLineGeometryPointsAndUpdate(
        referenceLine: ReferenceLine,
        alignment: LayoutAlignment,
        moveFunc: (point: IPoint) -> IPoint,
    ) {
        val version = referenceLineService.saveDraft(
            referenceLine,
            alignment.copy(
                segments = alignment.segments.map { segment ->
                    segment.copy(
                        points = segment.points.map { point ->
                            val newPoint = moveFunc(point)
                            point.copy(
                                x = newPoint.x,
                                y = newPoint.y
                            )
                        }
                    )
                }
            )
        )
        referenceLineService.publish(version.id)
    }

    fun moveKmPostAndUpdate(
        kmPost: TrackLayoutKmPost,
        moveFunc: (point: IPoint) -> Point,
    ): TrackLayoutKmPost {
        return layoutKmPostDao.fetch(
            layoutKmPostDao.update(
                kmPost.copy(
                    location = moveFunc(kmPost.location!!)
                )
            )
        )
    }

    fun assertAreEqual(addresses1: AlignmentAddresses?, addresses2: AlignmentAddresses?) {
        assertEquals(addresses1, addresses2)
    }

    fun assertAreNotEqual(addresses1: AlignmentAddresses?, addresses2: AlignmentAddresses?) {
        assertNotEquals(addresses1, addresses2)
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
                LayoutPoint(start.first.x, start.first.y, null, 0.0, null),
                start.second,
                0.0,
            ),
            endPoint = AddressPoint(
                LayoutPoint(end.first.x, end.first.y, null, lineLength(start.first, end.first), null),
                start.second,
                0.0,
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
                val layoutPoints = toTrackLayoutPoints(points = points.toTypedArray())
                val addressPoints = layoutPoints.map { layoutPoint: LayoutPoint ->
                    AddressPoint(
                        point = layoutPoint,
                        address = fromAddress + layoutPoint.m,
                        distance = layoutPoint.m
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
        LayoutPoint(0.0, 0.0, null, 0.0, null),
        TrackMeter(0, 0),
        0.0,
    )
}
