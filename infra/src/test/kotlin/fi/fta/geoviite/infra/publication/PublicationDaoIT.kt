package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.TEST_USER
import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.LocationTrackName
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.integration.CalculatedChanges
import fi.fta.geoviite.infra.integration.DirectChanges
import fi.fta.geoviite.infra.integration.IndirectChanges
import fi.fta.geoviite.infra.integration.LocationTrackChange
import fi.fta.geoviite.infra.integration.SwitchChange
import fi.fta.geoviite.infra.integration.SwitchJointChange
import fi.fta.geoviite.infra.integration.TrackNumberChange
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutDesignDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.TmpLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.asMainDraft
import fi.fta.geoviite.infra.tracklayout.assertMatches
import fi.fta.geoviite.infra.tracklayout.edge
import fi.fta.geoviite.infra.tracklayout.layoutDesign
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.publishedVersions
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.someSegment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.switchLinkYV
import fi.fta.geoviite.infra.tracklayout.trackGeometry
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import fi.fta.geoviite.infra.tracklayout.trackNumber
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class PublicationDaoIT
@Autowired
constructor(
    val publicationDao: PublicationDao,
    val switchDao: LayoutSwitchDao,
    val trackNumberDao: LayoutTrackNumberDao,
    val kmPostDao: LayoutKmPostDao,
    val referenceLineDao: ReferenceLineDao,
    val locationTrackService: LocationTrackService,
    val locationTrackDao: LocationTrackDao,
    val alignmentDao: LayoutAlignmentDao,
    val layoutDesignDao: LayoutDesignDao,
) : DBTestBase() {

    @BeforeEach
    fun setup() {
        testDBService.clearLayoutTables()
    }

    @Test
    fun noPublicationCandidatesFoundWithoutDrafts() {
        assertTrue(publicationDao.fetchTrackNumberPublicationCandidates(PublicationInMain).isEmpty())
        assertTrue(publicationDao.fetchReferenceLinePublicationCandidates(PublicationInMain).isEmpty())
        assertTrue(publicationDao.fetchLocationTrackPublicationCandidates(PublicationInMain).isEmpty())
        assertTrue(publicationDao.fetchSwitchPublicationCandidates(PublicationInMain).isEmpty())
        assertTrue(publicationDao.fetchKmPostPublicationCandidates(PublicationInMain).isEmpty())
    }

    @Test
    fun referenceLinePublicationCandidatesAreFound() {
        val trackNumberId = insertAndCheck(trackNumber(testDBService.getUnusedTrackNumber(), draft = false)).first.id
        val (_, line) = insertAndCheck(referenceLine(trackNumberId, draft = false))
        val (_, draft) = insertAndCheck(asMainDraft(line).copy(startAddress = TrackMeter("0123", 658.321, 3)))
        val candidates = publicationDao.fetchReferenceLinePublicationCandidates(PublicationInMain)
        assertEquals(1, candidates.size)
        assertEquals(line.id, candidates.first().id)
        assertEquals(draft.trackNumberId, candidates.first().trackNumberId)
        assertEquals(UserName.of(TEST_USER), candidates.first().userName)
        assertEquals(Operation.MODIFY, candidates.first().operation)
    }

    @Test
    fun locationTrackPublicationCandidatesAreFound() {
        val (_, track) = insertAndCheck(locationTrack(mainOfficialContext.createLayoutTrackNumber().id, draft = false))
        val (_, draft) = insertAndCheck(asMainDraft(track).copy(name = LocationTrackName("${track.name} DRAFT")))
        val candidates = publicationDao.fetchLocationTrackPublicationCandidates(PublicationInMain)
        assertEquals(1, candidates.size)
        assertEquals(track.id, candidates.first().id)
        assertEquals(draft.name, candidates.first().name)
        assertEquals(draft.trackNumberId, candidates.first().trackNumberId)
        assertEquals(UserName.of(TEST_USER), candidates.first().userName)
        assertEquals(Operation.MODIFY, candidates.first().operation)
    }

    @Test
    fun switchPublicationCandidatesAreFound() {
        val (_, switch) = insertAndCheck(switch(draft = false))
        val (_, draft) = insertAndCheck(asMainDraft(switch).copy(name = SwitchName("${switch.name} DRAFT")))
        val candidates = publicationDao.fetchSwitchPublicationCandidates(PublicationInMain)
        assertEquals(1, candidates.size)
        assertEquals(switch.id, candidates.first().id)
        assertEquals(draft.name, candidates.first().name)
        assertEquals(UserName.of(TEST_USER), candidates.first().userName)
        assertEquals(Operation.MODIFY, candidates.first().operation)
    }

    @Test
    fun createOperationIsInferredCorrectly() {
        val (_, track) = insertAndCheck(locationTrack(mainOfficialContext.createLayoutTrackNumber().id, draft = true))
        val candidates = publicationDao.fetchLocationTrackPublicationCandidates(PublicationInMain)
        assertEquals(1, candidates.size)
        assertEquals(track.id, candidates.first().id)
        assertEquals(Operation.CREATE, candidates.first().operation)
    }

    @Test
    fun modifyOperationIsInferredCorrectly() {
        val (_, track) = insertAndCheck(locationTrack(mainOfficialContext.createLayoutTrackNumber().id, draft = false))
        val (version, draft) = insertAndCheck(asMainDraft(track).copy(name = LocationTrackName("${track.name} DRAFT")))
        publishAndCheck(version)
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackService.getOrThrow(MainLayoutContext.official, draft.id as IntId).let { lt ->
                lt.copy(name = LocationTrackName("${lt.name} TEST"))
            },
            TmpLocationTrackGeometry.empty,
        )
        val candidates = publicationDao.fetchLocationTrackPublicationCandidates(PublicationInMain)
        assertEquals(1, candidates.size)
        assertEquals(track.id, candidates.first().id)
        assertEquals(Operation.MODIFY, candidates.first().operation)
    }

    @Test
    fun deleteOperationIsInferredCorrectly() {
        val (_, track) = insertAndCheck(locationTrack(mainOfficialContext.createLayoutTrackNumber().id, draft = false))
        val (version, draft) = insertAndCheck(asMainDraft(track).copy(name = LocationTrackName("${track.name} DRAFT")))
        publishAndCheck(version)
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackService
                .getOrThrow(MainLayoutContext.official, draft.id as IntId)
                .copy(state = LocationTrackState.DELETED),
            TmpLocationTrackGeometry.empty,
        )
        val candidates = publicationDao.fetchLocationTrackPublicationCandidates(PublicationInMain)
        assertEquals(1, candidates.size)
        assertEquals(track.id, candidates.first().id)
        assertEquals(Operation.DELETE, candidates.first().operation)
    }

    @Test
    fun restoreOperationIsInferredCorrectly() {
        val (_, track) = insertAndCheck(locationTrack(mainOfficialContext.createLayoutTrackNumber().id, draft = false))
        val (version, draft) =
            insertAndCheck(
                asMainDraft(track)
                    .copy(name = LocationTrackName("${track.name} DRAFT"), state = LocationTrackState.DELETED)
            )
        publishAndCheck(version)
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrackService
                .getOrThrow(MainLayoutContext.official, draft.id as IntId)
                .copy(state = LocationTrackState.IN_USE),
            TmpLocationTrackGeometry.empty,
        )
        val candidates = publicationDao.fetchLocationTrackPublicationCandidates(PublicationInMain)
        assertEquals(1, candidates.size)
        assertEquals(track.id, candidates.first().id)
        assertEquals(Operation.RESTORE, candidates.first().operation)
    }

    @Test
    fun allCalculatedChangesAreRecorded() {
        val trackNumberVersion = mainOfficialContext.createLayoutTrackNumber()
        val locationTrackVersion = insertAndCheck(locationTrack(trackNumberVersion.id, draft = false)).first
        val switchVersion = insertAndCheck(switch(draft = false)).first

        val switchJointChange =
            SwitchJointChange(
                JointNumber(1),
                false,
                TrackMeter(1234, "AA", 12.34, 3),
                Point(100.0, 200.0),
                locationTrackVersion.id,
                Oid("123.456.789"),
                trackNumberVersion.id,
                Oid("1.234.567"),
            )

        val changes =
            CalculatedChanges(
                directChanges =
                    DirectChanges(
                        trackNumberChanges =
                            listOf(
                                TrackNumberChange(
                                    trackNumberVersion.id,
                                    setOf(KmNumber(1234), KmNumber(45, "AB")),
                                    isStartChanged = true,
                                    isEndChanged = false,
                                )
                            ),
                        kmPostChanges = emptyList(),
                        referenceLineChanges = emptyList(),
                        locationTrackChanges =
                            listOf(
                                LocationTrackChange(
                                    locationTrackVersion.id,
                                    setOf(KmNumber(456)),
                                    isStartChanged = false,
                                    isEndChanged = true,
                                )
                            ),
                        switchChanges = listOf(SwitchChange(switchVersion.id, listOf(switchJointChange))),
                    ),
                indirectChanges = IndirectChanges(emptyList(), emptyList(), emptyList()),
            )
        val publicationId =
            publicationDao.createPublication(
                LayoutBranch.main,
                PublicationMessage.of(""),
                PublicationCause.MANUAL,
                parentId = null,
            )
        publicationDao.insertCalculatedChanges(
            publicationId,
            changes,
            publishedVersions(
                trackNumbers = listOf(Change(null, trackNumberVersion)),
                locationTracks = listOf(Change(null, locationTrackVersion)),
                switches = listOf(Change(null, switchVersion)),
            ),
        )

        val publishedTrackNumbers =
            publicationDao.fetchPublishedTrackNumbers(setOf(publicationId)).getValue(publicationId)
        val publishedLocationTracks =
            publicationDao.fetchPublishedLocationTracks(setOf(publicationId)).getValue(publicationId)
        val publishedSwitches = publicationDao.fetchPublishedSwitches(setOf(publicationId)).getValue(publicationId)
        assertTrue(publishedTrackNumbers.directChanges.all { it.id == trackNumberVersion.id })
        assertEquals(
            changes.directChanges.trackNumberChanges.flatMap { it.changedKmNumbers }.sorted(),
            publishedTrackNumbers.directChanges.flatMap { it.changedKmNumbers }.sorted(),
        )

        assertTrue(publishedLocationTracks.directChanges.all { it.id == locationTrackVersion.id })
        assertEquals(
            changes.directChanges.locationTrackChanges.flatMap { it.changedKmNumbers }.sorted(),
            publishedLocationTracks.directChanges.flatMap { it.changedKmNumbers }.sorted(),
        )

        assertTrue(publishedSwitches.directChanges.all { it.id == switchVersion.id })
        assertEquals(listOf(switchJointChange), publishedSwitches.directChanges.flatMap { it.changedJoints })
    }

    @Test
    fun `Publication message is stored and fetched correctly`() {
        val message = PublicationMessage.of("Test")
        val publicationId =
            publicationDao.createPublication(LayoutBranch.main, message, PublicationCause.MANUAL, parentId = null)
        assertEquals(message, publicationDao.getPublication(publicationId).message)
    }

    @Test
    fun fetchOfficialSwitchTrackNumbers() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val (_, switch) = insertAndCheck(switch(name = "Foo", draft = false))
        val switchId = switch.id as IntId
        insertAndCheck(
            locationTrack(trackNumberId, draft = false),
            trackGeometry(edge(listOf(someSegment()), endOuterSwitch = switchLinkYV(switchId, 1))),
        )
        insertAndCheck(asMainDraft(switch.copy(name = SwitchName("FooEdited"))))

        val publicationCandidates = publicationDao.fetchSwitchPublicationCandidates(PublicationInMain)
        val editedCandidate = publicationCandidates.first { s -> s.name == SwitchName("FooEdited") }
        assertEquals(editedCandidate.trackNumberIds, listOf(trackNumberId))
    }

    @Test
    fun fetchDraftOnlySwitchTrackNumbers() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val (_, switch) = insertAndCheck(switch(name = "Foo", draft = true))
        val switchId = switch.id as IntId
        insertAndCheck(
            locationTrack(trackNumberId, draft = true),
            trackGeometry(edge(listOf(someSegment()), endOuterSwitch = switchLinkYV(switchId, 1))),
        )
        val publicationCandidates = publicationDao.fetchSwitchPublicationCandidates(PublicationInMain)
        val editedCandidate = publicationCandidates.first { s -> s.name == SwitchName("Foo") }
        assertEquals(editedCandidate.trackNumberIds, listOf(trackNumberId))
    }

    @Test
    fun `fetchLinkedLocationTracks does not confuse official rows with drafts`() {
        val trackNumber = mainOfficialContext.createLayoutTrackNumber().id
        val switch = mainOfficialContext.save(switch()).id
        val geometryWithoutSwitch = trackGeometryOfSegments(someSegment())
        val geometryWithSwitch =
            trackGeometry(edge(startInnerSwitch = switchLinkYV(switch, 1), segments = listOf(someSegment())))
        val track1Main = mainOfficialContext.save(locationTrack(trackNumber), geometryWithSwitch)
        val track2Main = mainOfficialContext.save(locationTrack(trackNumber), geometryWithSwitch)
        mainDraftContext.save(locationTrackDao.fetch(track1Main), geometryWithoutSwitch)
        mainDraftContext.save(locationTrackDao.fetch(track2Main), geometryWithoutSwitch)

        assertEquals(
            // null locationTrackIdsInPublicationUnit = everything in publication unit = nothing
            // linked
            mapOf(),
            publicationDao.fetchLinkedLocationTracks(ValidateTransition(PublicationInMain), listOf(switch), null),
        )
        assertEquals(
            // nothing in publication unit = everything linked
            mapOf(switch to setOf(track1Main, track2Main)),
            publicationDao.fetchLinkedLocationTracks(ValidateTransition(PublicationInMain), listOf(switch), listOf()),
        )
        assertEquals(
            // just one track in publication unit = only the track not in publication unit is linked
            mapOf(switch to setOf(track2Main)),
            publicationDao.fetchLinkedLocationTracks(
                ValidateTransition(PublicationInMain),
                listOf(switch),
                listOf(track1Main.id),
            ),
        )
    }

    @Test
    fun `fetchLinkedLocationTracks works on publication units`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val switchByAlignment = switchDao.save(switch(draft = false)).id
        val switchByTopo = switchDao.save(switch(draft = false)).id
        val officialLinkedTopo =
            locationTrackDao.save(
                locationTrack(trackNumberId, draft = false),
                trackGeometry(edge(startOuterSwitch = switchLinkYV(switchByTopo, 1), segments = listOf(someSegment()))),
            )
        val draftLinkedTopo =
            locationTrackDao.save(
                locationTrack(trackNumberId, draft = true),
                trackGeometry(edge(startOuterSwitch = switchLinkYV(switchByTopo, 3), segments = listOf(someSegment()))),
            )
        val officialLinkedAlignment =
            locationTrackDao.save(
                locationTrack(trackNumberId = trackNumberId, draft = false),
                trackGeometry(
                    edge(startInnerSwitch = switchLinkYV(switchByAlignment, 1), segments = listOf(someSegment()))
                ),
            )
        val draftLinkedAlignment =
            locationTrackDao.save(
                locationTrack(trackNumberId = trackNumberId, draft = true),
                trackGeometry(
                    edge(startInnerSwitch = switchLinkYV(switchByAlignment, 3), segments = listOf(someSegment()))
                ),
            )
        val target = draftTransitionOrOfficialState(PublicationState.DRAFT, LayoutBranch.main)
        assertEquals(
            setOf(officialLinkedAlignment, draftLinkedAlignment),
            publicationDao.fetchLinkedLocationTracks(target, listOf(switchByAlignment))[switchByAlignment],
        )
        assertEquals(
            setOf(officialLinkedAlignment),
            publicationDao.fetchLinkedLocationTracks(target, listOf(switchByAlignment), listOf())[switchByAlignment],
        )
        assertEquals(
            setOf(officialLinkedAlignment, draftLinkedAlignment),
            publicationDao
                .fetchLinkedLocationTracks(target, listOf(switchByAlignment), listOf(draftLinkedAlignment.id))[
                    switchByAlignment],
        )
        assertEquals(
            setOf(officialLinkedTopo, draftLinkedTopo),
            publicationDao.fetchLinkedLocationTracks(target, listOf(switchByTopo))[switchByTopo],
        )
        assertEquals(
            setOf(officialLinkedTopo),
            publicationDao.fetchLinkedLocationTracks(target, listOf(switchByTopo), listOf())[switchByTopo],
        )
        assertEquals(
            setOf(officialLinkedTopo, draftLinkedTopo),
            publicationDao
                .fetchLinkedLocationTracks(target, listOf(switchByTopo), listOf(draftLinkedTopo.id))[switchByTopo],
        )
    }

    @Test
    fun `fetchLatestPublicationDetails lists design publications in design mode`() {
        val someDesign = DesignBranch.of(layoutDesignDao.insert(layoutDesign("one")))
        val anotherDesign = DesignBranch.of(layoutDesignDao.insert(layoutDesign("two")))
        publicationDao.createPublication(
            someDesign,
            PublicationMessage.of("in someDesign"),
            PublicationCause.MANUAL,
            parentId = null,
        )
        publicationDao.createPublication(
            LayoutBranch.main,
            PublicationMessage.of("in main"),
            PublicationCause.MANUAL,
            parentId = null,
        )
        publicationDao.createPublication(
            anotherDesign,
            PublicationMessage.of("in anotherDesign"),
            PublicationCause.MANUAL,
            parentId = null,
        )
        publicationDao.createPublication(
            LayoutBranch.main,
            PublicationMessage.of("again in main"),
            PublicationCause.MANUAL,
            parentId = null,
        )
        assertEquals(
            listOf("in anotherDesign", "in someDesign"),
            publicationDao.fetchLatestPublications(LayoutBranchType.DESIGN, 2).map { it.message.toString() },
        )
        assertEquals(
            listOf("again in main", "in main"),
            publicationDao.fetchLatestPublications(LayoutBranchType.MAIN, 2).map { it.message.toString() },
        )
    }

    @Test
    fun `getPreviouslyPublishedDesignVersion returns the most recent design version before the given publication`() {
        val someDesign = layoutDesignDao.insert(layoutDesign(name = "foo"))
        val pub1 =
            publicationDao.createPublication(
                DesignBranch.of(someDesign),
                PublicationMessage.of("pub 1"),
                PublicationCause.MANUAL,
                parentId = null,
            )
        layoutDesignDao.update(someDesign, layoutDesign(name = "bar"))
        val pub2 =
            publicationDao.createPublication(
                DesignBranch.of(someDesign),
                PublicationMessage.of("pub 2"),
                PublicationCause.MANUAL,
                parentId = null,
            )
        layoutDesignDao.update(someDesign, layoutDesign(name = "spam"))
        val pub3 =
            publicationDao.createPublication(
                DesignBranch.of(someDesign),
                PublicationMessage.of("pub 3"),
                PublicationCause.MANUAL,
                parentId = null,
            )
        val pub4 =
            publicationDao.createPublication(
                DesignBranch.of(someDesign),
                PublicationMessage.of("pub 4"),
                PublicationCause.MANUAL,
                parentId = null,
            )
        val pub5 =
            publicationDao.createPublication(
                DesignBranch.of(someDesign),
                PublicationMessage.of("pub 5"),
                PublicationCause.MANUAL,
                parentId = null,
            )
        assertEquals(null, publicationDao.getPreviouslyPublishedDesignVersion(pub1, someDesign))
        assertEquals(1, publicationDao.getPreviouslyPublishedDesignVersion(pub2, someDesign))
        assertEquals(2, publicationDao.getPreviouslyPublishedDesignVersion(pub3, someDesign))
        assertEquals(3, publicationDao.getPreviouslyPublishedDesignVersion(pub4, someDesign))
        assertEquals(3, publicationDao.getPreviouslyPublishedDesignVersion(pub5, someDesign))
    }

    @Test
    fun `Publication has a generated uuid`() {
        val publication =
            publicationDao
                .createPublication(
                    MainLayoutContext.official.branch,
                    PublicationMessage.of("test publication"),
                    PublicationCause.MANUAL,
                    parentId = null,
                )
                .let(publicationDao::getPublication)

        assertTrue(publication.uuid.toString().isNotEmpty())
    }

    @Test
    fun `Publication can be found by its generated uuid`() {
        val publication =
            publicationDao
                .createPublication(
                    MainLayoutContext.official.branch,
                    PublicationMessage.of("test publication"),
                    PublicationCause.MANUAL,
                    parentId = null,
                )
                .let(publicationDao::getPublication)

        val publicationByUuid =
            publicationDao.fetchPublicationIdByUuid(publication.uuid)?.let(publicationDao::getPublication)

        assertEquals(publication.id, publicationByUuid?.id)
    }

    private fun insertAndCheck(
        trackNumber: LayoutTrackNumber
    ): Pair<LayoutRowVersion<LayoutTrackNumber>, LayoutTrackNumber> {
        val official = trackNumberDao.save(trackNumber)
        val fromDb = trackNumberDao.fetch(official)
        assertEquals(official.id, fromDb.id)
        assertMatches(trackNumber, fromDb, contextMatch = false)
        assertEquals(DataType.TEMP, trackNumber.dataType)
        assertEquals(DataType.STORED, fromDb.dataType)
        assertTrue { fromDb.id is IntId }
        return official to fromDb
    }

    private fun insertAndCheck(switch: LayoutSwitch): Pair<LayoutRowVersion<LayoutSwitch>, LayoutSwitch> {
        val official = switchDao.save(switch)
        val fromDb = switchDao.fetch(official)
        assertEquals(official.id, fromDb.id)
        assertMatches(switch, fromDb, contextMatch = false)
        assertEquals(DataType.TEMP, switch.dataType)
        assertEquals(DataType.STORED, fromDb.dataType)
        assertTrue(fromDb.id is IntId)
        return official to fromDb
    }

    private fun insertAndCheck(referenceLine: ReferenceLine): Pair<LayoutRowVersion<ReferenceLine>, ReferenceLine> {
        val dbAlignmentVersion = alignmentDao.insert(alignment())
        val lineWithAlignment = referenceLine.copy(alignmentVersion = dbAlignmentVersion)
        val official = referenceLineDao.save(lineWithAlignment)
        val fromDb = referenceLineDao.fetch(official)
        assertEquals(official.id, fromDb.id)
        assertMatches(lineWithAlignment, fromDb, contextMatch = false)
        assertEquals(DataType.TEMP, referenceLine.dataType)
        assertEquals(DataType.STORED, fromDb.dataType)
        assertTrue(fromDb.id is IntId)
        return official to fromDb
    }

    private fun insertAndCheck(
        locationTrack: LocationTrack,
        geometry: LocationTrackGeometry = TmpLocationTrackGeometry.empty,
    ): Pair<LayoutRowVersion<LocationTrack>, LocationTrack> {
        val official = locationTrackDao.save(locationTrack, geometry)
        val fromDb = locationTrackDao.fetch(official)
        assertEquals(official.id, fromDb.id)
        assertMatches(
            locationTrack.copy(
                // These fields are read from DB geometry and aren't set on track itself before saving
                switchIds = geometry.switchIds,
                startSwitchId = geometry.startSwitchLink?.id,
                endSwitchId = geometry.endSwitchLink?.id,
                segmentCount = geometry.segments.size,
                length = geometry.length,
                boundingBox = geometry.boundingBox,
            ),
            fromDb,
            contextMatch = false,
        )
        assertEquals(DataType.TEMP, locationTrack.dataType)
        assertEquals(DataType.STORED, fromDb.dataType)
        assertTrue(fromDb.id is IntId)
        return official to fromDb
    }

    private fun publishAndCheck(rowVersion: LayoutRowVersion<LocationTrack>) {
        publishAndCheck(rowVersion, locationTrackDao, locationTrackService)
    }
}
