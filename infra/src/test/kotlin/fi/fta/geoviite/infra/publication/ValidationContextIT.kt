package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.tracklayout.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class ValidationContextIT @Autowired constructor(
    val trackNumberDao: LayoutTrackNumberDao,
    val referenceLineDao: ReferenceLineDao,
    val kmPostDao: LayoutKmPostDao,
    val locationTrackDao: LocationTrackDao,
    val alignmentDao: LayoutAlignmentDao,
    val switchDao: LayoutSwitchDao,
    val switchLibraryService: SwitchLibraryService,
    val publicationDao: PublicationDao,
    val geocodingService: GeocodingService,
) : DBTestBase() {

    @Test
    fun `NullableCache caches both real and null values`() {
        val cache = NullableCache<Int, String>()
        cache.get(1) { "one" }
        cache.get(2) { null }
        cache.preload(listOf(1, 2, 3, 4, 5)) { missing ->
            assertEquals(listOf(3, 4, 5), missing)
            missing.associateWith { n -> if (n == 5) null else "$n" }
        }
        assertEquals("one", cache.get(1) { "second fetch should not happen" })
        assertEquals(null, cache.get(2) { "second fetch should not happen" })
        assertEquals("3", cache.get(3) { "second fetch should not happen" })
        assertEquals("4", cache.get(4) { "second fetch should not happen" })
        assertEquals(null, cache.get(5) { "second fetch should not happen" })
    }

    @Test
    fun `ValidationContext returns correct versions for TrackNumber`() {
        val trackNumber1 = trackNumber(getUnusedTrackNumber())
        val (tn1Id, tn1OfficialVersion) = trackNumberDao.insert(trackNumber1)
        val (_, tn1DraftVersion) = trackNumberDao.insert(draft(trackNumberDao.fetch(tn1OfficialVersion)))
        val trackNumber2 = trackNumber(getUnusedTrackNumber())
        val (tn2Id, tn2DraftVersion) = trackNumberDao.insert(draft(trackNumber2))
        assertEquals(
            trackNumberDao.fetch(tn1OfficialVersion),
            validationContext().getTrackNumber(tn1Id),
        )
        assertEquals(
            trackNumberDao.fetch(tn1DraftVersion),
            validationContext(trackNumbers = listOf(tn1Id)).getTrackNumber(tn1Id),
        )
        assertEquals(null, validationContext().getTrackNumber(tn2Id))
        assertEquals(
            trackNumberDao.fetch(tn2DraftVersion),
            validationContext(trackNumbers = listOf(tn2Id)).getTrackNumber(tn2Id),
        )

        assertEquals(
            listOf(trackNumberDao.fetch(tn1OfficialVersion)),
            validationContext().getTrackNumbersByNumber(trackNumber1.number),
        )
        assertEquals(
            listOf(trackNumberDao.fetch(tn1DraftVersion)),
            validationContext(trackNumbers = listOf(tn1Id)).getTrackNumbersByNumber(trackNumber1.number),
        )

        assertEquals(
            emptyList<TrackLayoutTrackNumber>(),
            validationContext().getTrackNumbersByNumber(trackNumber2.number),
        )
        assertEquals(
            listOf(trackNumberDao.fetch(tn2DraftVersion)),
            validationContext(trackNumbers = listOf(tn2Id)).getTrackNumbersByNumber(trackNumber2.number),
        )
    }

    @Test
    fun `ValidationContext returns correct versions for LocationTrack`() {
        val trackNumberId = getUnusedTrackNumberId()
        val (lt1Id, lt1OfficialVersion) = insertLocationTrack(locationTrackAndAlignment(trackNumberId))
        val (_, lt1DraftVersion) = locationTrackDao.insert(draft(locationTrackDao.fetch(lt1OfficialVersion)))
        val (lt2Id, lt2DraftVersion) = insertLocationTrack(
            locationTrackAndAlignment(trackNumberId).let { (t, a) -> draft(t) to a }
        )

        assertEquals(
            locationTrackDao.fetch(lt1OfficialVersion),
            validationContext().getLocationTrack(lt1Id),
        )
        assertEquals(
            locationTrackDao.fetch(lt1DraftVersion),
            validationContext(locationTracks = listOf(lt1Id)).getLocationTrack(lt1Id),
        )
        assertEquals(null, validationContext().getLocationTrack(lt2Id))
        assertEquals(
            locationTrackDao.fetch(lt2DraftVersion),
            validationContext(locationTracks = listOf(lt2Id)).getLocationTrack(lt2Id),
        )
    }

    @Test
    fun `ValidationContext returns correct versions for Switch`() {
        val switchName1 = getUnusedSwitchName()
        val (s1Id, s1OfficialVersion) = switchDao.insert(switch(name = switchName1.toString()))
        val (_, s1DraftVersion) = switchDao.insert(draft(switchDao.fetch(s1OfficialVersion)))
        val switchName2 = getUnusedSwitchName()
        val (s2Id, s2DraftVersion) = switchDao.insert(draft(switch(name = switchName2.toString())))

        assertEquals(
            switchDao.fetch(s1OfficialVersion),
            validationContext().getSwitch(s1Id),
        )
        assertEquals(
            switchDao.fetch(s1DraftVersion),
            validationContext(switches = listOf(s1Id)).getSwitch(s1Id),
        )
        assertEquals(null, validationContext().getSwitch(s2Id))
        assertEquals(
            switchDao.fetch(s2DraftVersion),
            validationContext(switches = listOf(s2Id)).getSwitch(s2Id),
        )

        assertEquals(
            listOf(switchDao.fetch(s1OfficialVersion)),
            validationContext().getSwitchesByName(switchName1),
        )
        assertEquals(
            listOf(switchDao.fetch(s1DraftVersion)),
            validationContext(switches = listOf(s1Id)).getSwitchesByName(switchName1),
        )
        assertEquals(emptyList<TrackLayoutSwitch>(), validationContext().getSwitchesByName(switchName2))
        assertEquals(
            listOf(switchDao.fetch(s2DraftVersion)),
            validationContext(switches = listOf(s2Id)).getSwitchesByName(switchName2),
        )
    }

    @Test
    fun `ValidationContext returns correct versions for KM-Post`() {
        val trackNumberId = getUnusedTrackNumberId()
        val (kmp1Id, kmp1OfficialVersion) = kmPostDao.insert(kmPost(trackNumberId, KmNumber(1)))
        val (_, kmp1DraftVersion) = kmPostDao.insert(draft(kmPostDao.fetch(kmp1OfficialVersion)))
        val (kmp2Id, kmp2DraftVersion) = kmPostDao.insert(draft(kmPost(trackNumberId, KmNumber(2))))
        assertEquals(
            kmPostDao.fetch(kmp1OfficialVersion),
            validationContext().getKmPost(kmp1Id),
        )
        assertEquals(
            kmPostDao.fetch(kmp1DraftVersion),
            validationContext(kmPosts = listOf(kmp1Id)).getKmPost(kmp1Id),
        )
        assertEquals(null, validationContext().getKmPost(kmp2Id))
        assertEquals(
            kmPostDao.fetch(kmp2DraftVersion),
            validationContext(kmPosts = listOf(kmp2Id)).getKmPost(kmp2Id),
        )
    }

    private fun validationContext(
        trackNumbers: List<IntId<TrackLayoutTrackNumber>> = listOf(),
        locationTracks: List<IntId<LocationTrack>> = listOf(),
        referenceLines: List<IntId<ReferenceLine>> = listOf(),
        switches: List<IntId<TrackLayoutSwitch>> = listOf(),
        kmPosts: List<IntId<TrackLayoutKmPost>> = listOf(),
    ): ValidationContext = ValidationContext(
        trackNumberDao = trackNumberDao,
        referenceLineDao = referenceLineDao,
        kmPostDao = kmPostDao,
        locationTrackDao = locationTrackDao,
        switchDao = switchDao,
        geocodingService = geocodingService,
        alignmentDao = alignmentDao,
        publicationDao = publicationDao,
        switchLibraryService = switchLibraryService,
        publicationSet = ValidationVersions(
            trackNumbers = trackNumberDao.fetchPublicationVersions(trackNumbers),
            referenceLines = referenceLineDao.fetchPublicationVersions(referenceLines),
            kmPosts = kmPostDao.fetchPublicationVersions(kmPosts),
            locationTracks = locationTrackDao.fetchPublicationVersions(locationTracks),
            switches = switchDao.fetchPublicationVersions(switches),
        )
    )
}
