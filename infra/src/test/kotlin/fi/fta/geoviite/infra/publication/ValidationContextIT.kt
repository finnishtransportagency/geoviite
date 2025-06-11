package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.split.SplitService
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory.EXISTING
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.asMainDraft
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.locationTrackAndGeometry
import fi.fta.geoviite.infra.tracklayout.switch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class ValidationContextIT
@Autowired
constructor(
    val trackNumberDao: LayoutTrackNumberDao,
    val referenceLineDao: ReferenceLineDao,
    val kmPostDao: LayoutKmPostDao,
    val locationTrackDao: LocationTrackDao,
    val alignmentDao: LayoutAlignmentDao,
    val switchDao: LayoutSwitchDao,
    val switchLibraryService: SwitchLibraryService,
    val publicationDao: PublicationDao,
    val geocodingService: GeocodingService,
    val splitService: SplitService,
    val locationTrackService: LocationTrackService,
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
        val tn1OfficialVersion = mainOfficialContext.createLayoutTrackNumber()
        val tn1Id = tn1OfficialVersion.id
        val trackNumber1 = trackNumberDao.fetch(tn1OfficialVersion)
        val tn1DraftVersion = mainDraftContext.save(asMainDraft(trackNumber1))
        val tn2DraftVersion = mainDraftContext.createLayoutTrackNumber()
        val tn2Id = tn2DraftVersion.id
        val trackNumber2 = trackNumberDao.fetch(tn2DraftVersion)
        assertEquals(trackNumberDao.fetch(tn1OfficialVersion), validationContext().getTrackNumber(tn1Id))
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

        assertEquals(emptyList<LayoutTrackNumber>(), validationContext().getTrackNumbersByNumber(trackNumber2.number))
        assertEquals(
            listOf(trackNumberDao.fetch(tn2DraftVersion)),
            validationContext(trackNumbers = listOf(tn2Id)).getTrackNumbersByNumber(trackNumber2.number),
        )
    }

    @Test
    fun `ValidationContext returns correct versions for LocationTrack`() {
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id
        val lt1OfficialVersion = mainOfficialContext.saveLocationTrack(locationTrackAndGeometry(trackNumberId))
        val lt1Id = lt1OfficialVersion.id
        val lt1DraftVersion =
            locationTrackDao.save(
                asMainDraft(locationTrackDao.fetch(lt1OfficialVersion)),
                alignmentDao.fetch(lt1OfficialVersion),
            )
        val lt2DraftVersion = mainDraftContext.saveLocationTrack(locationTrackAndGeometry(trackNumberId))
        val lt2Id = lt2DraftVersion.id

        assertEquals(locationTrackDao.fetch(lt1OfficialVersion), validationContext().getLocationTrack(lt1Id))
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
        val switchName1 = testDBService.getUnusedSwitchName()
        val s1OfficialVersion =
            mainOfficialContext.save(switch(name = switchName1.toString(), stateCategory = EXISTING))
        val s1Id = s1OfficialVersion.id
        val s1DraftVersion = switchDao.save(asMainDraft(switchDao.fetch(s1OfficialVersion)))
        val switchName2 = testDBService.getUnusedSwitchName()
        val s2DraftVersion = mainDraftContext.save(switch(name = switchName2.toString(), stateCategory = EXISTING))
        val s2Id = s2DraftVersion.id

        assertEquals(switchDao.fetch(s1OfficialVersion), validationContext().getSwitch(s1Id))
        assertEquals(switchDao.fetch(s1DraftVersion), validationContext(switches = listOf(s1Id)).getSwitch(s1Id))
        assertEquals(null, validationContext().getSwitch(s2Id))
        assertEquals(switchDao.fetch(s2DraftVersion), validationContext(switches = listOf(s2Id)).getSwitch(s2Id))

        assertEquals(listOf(switchDao.fetch(s1OfficialVersion)), validationContext().getSwitchesByName(switchName1))
        assertEquals(
            listOf(switchDao.fetch(s1DraftVersion)),
            validationContext(switches = listOf(s1Id)).getSwitchesByName(switchName1),
        )
        assertEquals(emptyList<LayoutSwitch>(), validationContext().getSwitchesByName(switchName2))
        assertEquals(
            listOf(switchDao.fetch(s2DraftVersion)),
            validationContext(switches = listOf(s2Id)).getSwitchesByName(switchName2),
        )
    }

    @Test
    fun `ValidationContext returns correct versions for KM-Post`() {
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id
        val kmp1OfficialVersion = mainOfficialContext.save(kmPost(trackNumberId, KmNumber(1)))
        val kmp1Id = kmp1OfficialVersion.id
        val kmp1DraftVersion = kmPostDao.save(asMainDraft(kmPostDao.fetch(kmp1OfficialVersion)))
        val kmp2DraftVersion = mainDraftContext.save(kmPost(trackNumberId, KmNumber(2)))
        val kmp2Id = kmp2DraftVersion.id
        assertEquals(kmPostDao.fetch(kmp1OfficialVersion), validationContext().getKmPost(kmp1Id))
        assertEquals(kmPostDao.fetch(kmp1DraftVersion), validationContext(kmPosts = listOf(kmp1Id)).getKmPost(kmp1Id))
        assertEquals(null, validationContext().getKmPost(kmp2Id))
        assertEquals(kmPostDao.fetch(kmp2DraftVersion), validationContext(kmPosts = listOf(kmp2Id)).getKmPost(kmp2Id))
    }

    private fun validationContext(
        branch: LayoutBranch = LayoutBranch.main,
        trackNumbers: List<IntId<LayoutTrackNumber>> = listOf(),
        locationTracks: List<IntId<LocationTrack>> = listOf(),
        referenceLines: List<IntId<ReferenceLine>> = listOf(),
        switches: List<IntId<LayoutSwitch>> = listOf(),
        kmPosts: List<IntId<LayoutKmPost>> = listOf(),
    ): ValidationContext {
        val target = draftTransitionOrOfficialState(PublicationState.DRAFT, branch)
        val candidateContext = target.candidateContext
        return ValidationContext(
            trackNumberDao = trackNumberDao,
            referenceLineDao = referenceLineDao,
            kmPostDao = kmPostDao,
            locationTrackDao = locationTrackDao,
            switchDao = switchDao,
            geocodingService = geocodingService,
            alignmentDao = alignmentDao,
            publicationDao = publicationDao,
            switchLibraryService = switchLibraryService,
            splitService = splitService,
            locationTrackService = locationTrackService,
            publicationSet =
                ValidationVersions(
                    target = target,
                    trackNumbers = trackNumberDao.fetchCandidateVersions(candidateContext, trackNumbers),
                    referenceLines = referenceLineDao.fetchCandidateVersions(candidateContext, referenceLines),
                    kmPosts = kmPostDao.fetchCandidateVersions(candidateContext, kmPosts),
                    locationTracks = locationTrackDao.fetchCandidateVersions(candidateContext, locationTracks),
                    switches = switchDao.fetchCandidateVersions(candidateContext, switches),
                    splits = splitService.fetchPublicationVersions(branch, locationTracks, switches),
                ),
        )
    }
}
