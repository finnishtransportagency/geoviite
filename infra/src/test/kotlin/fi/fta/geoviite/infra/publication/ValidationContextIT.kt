package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.split.SplitService
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.trackBoundaryMove.TrackBoundaryMoveService
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
import fi.fta.geoviite.infra.tracklayout.M_CALC
import fi.fta.geoviite.infra.tracklayout.OperationalPoint
import fi.fta.geoviite.infra.tracklayout.OperationalPointDao
import fi.fta.geoviite.infra.tracklayout.StationLinkIssue
import fi.fta.geoviite.infra.tracklayout.StationLinkIssueSeverity
import fi.fta.geoviite.infra.tracklayout.StationLinkIssueType
import fi.fta.geoviite.infra.tracklayout.StationLinkService
import fi.fta.geoviite.infra.tracklayout.asMainDraft
import fi.fta.geoviite.infra.tracklayout.edge
import fi.fta.geoviite.infra.tracklayout.kmPost
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.locationTrackAndGeometry
import fi.fta.geoviite.infra.tracklayout.operationalPoint
import fi.fta.geoviite.infra.tracklayout.referenceLineGeometryOfPoints
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.trackGeometry
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
    val kmPostDao: LayoutKmPostDao,
    val locationTrackDao: LocationTrackDao,
    val alignmentDao: LayoutAlignmentDao,
    val switchDao: LayoutSwitchDao,
    val operationalPointDao: OperationalPointDao,
    val switchLibraryService: SwitchLibraryService,
    val publicationDao: PublicationDao,
    val geocodingService: GeocodingService,
    val splitService: SplitService,
    val trackBoundaryMoveService: TrackBoundaryMoveService,
    val stationLinkService: StationLinkService,
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

    @Test
    fun `ValidationContext finds station link issues for operational points`() {
        val tnVersion =
            mainOfficialContext.createLayoutTrackNumber(
                geometry = referenceLineGeometryOfPoints(Point(0.0, 0.0), Point(60.0, 0.0))
            )
        val op1 = mainOfficialContext.save(operationalPoint("OP1", location = Point(20.0, 0.0)))
        val op2 = mainOfficialContext.save(operationalPoint("OP2", location = Point(80.0, 0.0)))
        // An unrelated operational point with no connecting track: should never have any issues
        val op3 = mainOfficialContext.save(operationalPoint("OP3", location = Point(40.0, 0.0)))

        val track =
            mainOfficialContext.save(
                locationTrack(trackNumberId = tnVersion.id, operationalPointIds = setOf(op1.id, op2.id)),
                trackGeometry(
                    edge(segments = listOf(segment(Point(15.0, 2.0), Point(55.0, 2.0), calc = M_CALC.LAYOUT)))
                ),
            )

        testDBService.createPublication()

        val expectedIssue =
            StationLinkIssue(
                type = StationLinkIssueType.UNREACHABLE_STATION_MIDPOINT,
                severity = StationLinkIssueSeverity.ERROR,
                operationalPointId = op2.id,
                otherOperationalPointId = op1.id,
                locationTrackId = track.id,
                trackNumberId = tnVersion.id,
            )

        // Both OP1 and OP2 should see the issue, since it concerns the link between them
        assertEquals(listOf(expectedIssue), validationContext().getStationLinkIssuesByOperationalPoint(op1.id))
        assertEquals(listOf(expectedIssue), validationContext().getStationLinkIssuesByOperationalPoint(op2.id))

        // An unrelated operational point should not have any issues
        assertEquals(emptyList<StationLinkIssue>(), validationContext().getStationLinkIssuesByOperationalPoint(op3.id))
    }

    private fun validationContext(
        branch: LayoutBranch = LayoutBranch.main,
        trackNumbers: List<IntId<LayoutTrackNumber>> = listOf(),
        locationTracks: List<IntId<LocationTrack>> = listOf(),
        switches: List<IntId<LayoutSwitch>> = listOf(),
        kmPosts: List<IntId<LayoutKmPost>> = listOf(),
        operationalPoints: List<IntId<OperationalPoint>> = listOf(),
    ): ValidationContext {
        val target = LayoutContextTransition.publicationIn(branch)
        val candidateContext = target.candidateContext
        return ValidationContext(
            trackNumberDao = trackNumberDao,
            kmPostDao = kmPostDao,
            locationTrackDao = locationTrackDao,
            switchDao = switchDao,
            geocodingService = geocodingService,
            alignmentDao = alignmentDao,
            publicationDao = publicationDao,
            switchLibraryService = switchLibraryService,
            splitService = splitService,
            trackBoundaryMoveService = trackBoundaryMoveService,
            operationalPointDao = operationalPointDao,
            stationLinkService = stationLinkService,
            publicationSet =
                ValidationVersions(
                    target = target,
                    trackNumbers = trackNumberDao.fetchCandidateVersions(candidateContext, trackNumbers),
                    kmPosts = kmPostDao.fetchCandidateVersions(candidateContext, kmPosts),
                    locationTracks = locationTrackDao.fetchCandidateVersions(candidateContext, locationTracks),
                    switches = switchDao.fetchCandidateVersions(candidateContext, switches),
                    operationalPoints = operationalPointDao.fetchCandidateVersions(candidateContext, operationalPoints),
                    splits = splitService.fetchPublicationVersions(branch, locationTracks, switches),
                    trackBoundaryMoves =
                        trackBoundaryMoveService.fetchPublicationVersions(branch, locationTracks, switches),
                ),
        )
    }
}
