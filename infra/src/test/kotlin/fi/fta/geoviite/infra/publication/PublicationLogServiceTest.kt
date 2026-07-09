package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LocationTrackDescriptionBase
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geography.GeographyService
import fi.fta.geoviite.infra.geometry.MetaDataName
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.localization.Translation
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.ratko.RatkoPushDao
import fi.fta.geoviite.infra.split.SplitService
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackDescriptionSuffix
import fi.fta.geoviite.infra.tracklayout.LocationTrackNamingScheme
import fi.fta.geoviite.infra.tracklayout.LocationTrackOwner
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import fi.fta.geoviite.infra.tracklayout.OperationalPointDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import fi.fta.geoviite.infra.util.FreeText
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class PublicationLogServiceTest {

    private val service =
        PublicationLogService(
            publicationDao = mock(PublicationDao::class.java),
            geocodingService = mock(GeocodingService::class.java),
            locationTrackService = mock(LocationTrackService::class.java),
            locationTrackDao = mock(LocationTrackDao::class.java),
            trackNumberDao = mock(LayoutTrackNumberDao::class.java),
            ratkoPushDao = mock(RatkoPushDao::class.java),
            splitService = mock(SplitService::class.java),
            localizationService = mock(LocalizationService::class.java),
            geographyService = mock(GeographyService::class.java),
            switchDao = mock(LayoutSwitchDao::class.java),
            kmPostDao = mock(LayoutKmPostDao::class.java),
            operationalPointDao = mock(OperationalPointDao::class.java),
        )

    private val translation = Translation(LocalizationLanguage.FI, "{}")
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val before = now.minusSeconds(60)
    private val trackNumberId = IntId<LayoutTrackNumber>(1)
    private val ownerId = IntId<LocationTrackOwner>(1)

    @Test
    fun `should not crash and should omit location fields when start and end points are null`() {
        val changes = baseChanges(startPoint = null, endPoint = null)

        val diff = callDiffLocationTrack(changes)

        val keys = diff.map { it.propKey.key.toString() }
        assertTrue("start-location" !in keys)
        assertTrue("end-location" !in keys)
        assertTrue("start-address" !in keys)
        assertTrue("end-address" !in keys)
    }

    @Test
    fun `should return empty list when nothing has changed`() {
        val diff = callDiffLocationTrack(baseChanges())

        assertTrue(diff.isEmpty())
    }

    @Test
    fun `should report name change`() {
        val changes = baseChanges().copy(name = Change(AlignmentName("OLD"), AlignmentName("NEW")))

        val diff = callDiffLocationTrack(changes)

        assertEquals(1, diff.size)
        assertEquals("location-track", diff[0].propKey.key.toString())
    }

    @Test
    fun `should report state change`() {
        val changes = baseChanges().copy(state = Change(LocationTrackState.IN_USE, LocationTrackState.NOT_IN_USE))

        val diff = callDiffLocationTrack(changes)

        assertEquals(1, diff.size)
        assertEquals("state", diff[0].propKey.key.toString())
    }

    @Test
    fun `should report geometry change when km numbers changed`() {
        val kmNumbers = setOf(KmNumber(1))

        val diff = callDiffLocationTrack(baseChanges(), changedKmNumbers = kmNumbers)

        assertEquals(1, diff.size)
        assertEquals("geometry", diff[0].propKey.key.toString())
    }

    @Test
    fun `should report start and end location when points differ`() {
        val oldStart = Point(0.0, 0.0)
        val newStart = Point(100.0, 0.0)
        val oldEnd = Point(1000.0, 0.0)
        val newEnd = Point(1100.0, 0.0)
        val changes = baseChanges().copy(startPoint = Change(oldStart, newStart), endPoint = Change(oldEnd, newEnd))

        val diff = callDiffLocationTrack(changes)

        val keys = diff.map { it.propKey.key.toString() }
        assertTrue("start-location" in keys)
        assertTrue("end-location" in keys)
    }

    @Test
    fun `should not report location when points are within threshold`() {
        val base = Point(0.0, 0.0)
        val nudged = Point(0.0001, 0.0)
        val changes = baseChanges().copy(startPoint = Change(base, nudged), endPoint = Change(base, nudged))

        val diff = callDiffLocationTrack(changes)

        val keys = diff.map { it.propKey.key.toString() }
        assertTrue("start-location" !in keys)
        assertTrue("end-location" !in keys)
    }

    @Test
    fun `should not report address when geocoding context is unavailable`() {
        val changes =
            baseChanges()
                .copy(
                    startPoint = Change(Point(0.0, 0.0), Point(100.0, 0.0)),
                    endPoint = Change(Point(1000.0, 0.0), Point(1100.0, 0.0)),
                )

        val diff = callDiffLocationTrack(changes, geocodingContext = { _, _ -> null })

        val keys = diff.map { it.propKey.key.toString() }
        assertTrue("start-address" !in keys)
        assertTrue("end-address" !in keys)
    }

    @Test
    fun `should report owner change when owner name is found`() {
        val oldOwner = IntId<LocationTrackOwner>(1)
        val newOwner = IntId<LocationTrackOwner>(2)
        val owners =
            listOf(
                LocationTrackOwner(oldOwner, MetaDataName("Old Owner")),
                LocationTrackOwner(newOwner, MetaDataName("New Owner")),
            )
        val changes = baseChanges().copy(owner = Change(oldOwner, newOwner))

        val diff = callDiffLocationTrack(changes, getOwners = { owners })

        assertEquals(1, diff.size)
        assertEquals("owner", diff[0].propKey.key.toString())
    }

    private fun callDiffLocationTrack(
        changes: LocationTrackChanges,
        changedKmNumbers: Set<KmNumber> = emptySet(),
        geocodingContext: (IntId<LayoutTrackNumber>, Instant) -> GeocodingContext<ReferenceLineM>? = { _, _ -> null },
        getOwners: (() -> List<LocationTrackOwner>)? = null,
    ) =
        service.diffLocationTrack(
            translation,
            changes,
            PublicationReferencedAssetSetChanges.empty(),
            { _ -> error("unexpected switch lookup") },
            { _ -> error("unexpected operational point lookup") },
            LayoutBranch.main,
            now,
            before,
            emptyList(),
            changedKmNumbers,
            switchOids = emptyMap(),
            operationalPointOids = emptyMap(),
            getGeocodingContext = geocodingContext,
            getOwners = getOwners,
        )

    private fun baseChanges(startPoint: Point? = Point(0.0, 0.0), endPoint: Point? = Point(10.0, 0.0)) =
        LocationTrackChanges(
            id = IntId(1),
            oid = Change(null, null),
            name = Change(AlignmentName("TEST"), AlignmentName("TEST")),
            namingScheme = Change(LocationTrackNamingScheme.FREE_TEXT, LocationTrackNamingScheme.FREE_TEXT),
            description = Change(FreeText("desc"), FreeText("desc")),
            descriptionBase = Change(LocationTrackDescriptionBase("base"), LocationTrackDescriptionBase("base")),
            descriptionSuffix = Change(LocationTrackDescriptionSuffix.NONE, LocationTrackDescriptionSuffix.NONE),
            state = Change(LocationTrackState.IN_USE, LocationTrackState.IN_USE),
            duplicateOf = Change(null, null),
            type = Change(LocationTrackType.MAIN, LocationTrackType.MAIN),
            length = Change(100.0, 100.0),
            startPoint = Change(startPoint, startPoint),
            endPoint = Change(endPoint, endPoint),
            trackNumberId = Change(trackNumberId, trackNumberId),
            geometryChangeSummaries = emptyList(),
            owner = Change(ownerId, ownerId),
        )
}
