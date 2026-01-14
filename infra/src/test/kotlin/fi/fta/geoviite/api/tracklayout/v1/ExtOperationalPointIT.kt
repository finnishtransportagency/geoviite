package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Polygon
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.ratko.model.OperationalPointRaideType
import fi.fta.geoviite.infra.tracklayout.OperationalPoint
import fi.fta.geoviite.infra.tracklayout.OperationalPointRinfType
import fi.fta.geoviite.infra.tracklayout.OperationalPointState
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.operationalPoint
import fi.fta.geoviite.infra.tracklayout.referenceLine
import fi.fta.geoviite.infra.tracklayout.referenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.switchJoint
import fi.fta.geoviite.infra.tracklayout.switchStructureYV60_300_1_9
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

@ActiveProfiles("dev", "test", "ext-api")
@SpringBootTest(classes = [InfraApplication::class])
@AutoConfigureMockMvc
class ExtOperationalPointIT
@Autowired
constructor(mockMvc: MockMvc, private val extTestDataService: ExtApiTestDataServiceV1) : DBTestBase() {
    private val api = ExtTrackLayoutTestApiService(mockMvc)

    @BeforeEach
    fun cleanup() {
        testDBService.clearAllTables()
    }

    @Test
    fun `Operational point APIs should return correct object versions`() {
        val op1Id =
            mainDraftContext
                .save(operationalPoint(name = "Test Point 1", abbreviation = "TP1", location = Point(0.5, 0.5)))
                .id
        val op1Oid = mainDraftContext.generateOid(op1Id)

        val op2Id =
            mainDraftContext
                .save(operationalPoint(name = "Test Point 2", abbreviation = "TP2", location = Point(10.5, 10.5)))
                .id
        val op2Oid = mainDraftContext.generateOid(op2Id)

        val baseVersion = extTestDataService.publishInMain(operationalPoints = listOf(op1Id, op2Id)).uuid

        val op1BeforeUpdate = mainOfficialContext.fetch(op1Id)!!
        val op2BeforeUpdate = mainOfficialContext.fetch(op2Id)!!
        assertNotEquals(op1BeforeUpdate, op2BeforeUpdate)

        // Verify initial state is shown as latest
        assertLatestStateInApi(baseVersion, op1Oid to op1BeforeUpdate, op2Oid to op2BeforeUpdate)

        // Update op1
        initUser()
        mainDraftContext.mutate(op1Id) { op -> op.copy(name = op.name.copy(value = "${op.name}-EDIT")) }
        val updatedVersion = extTestDataService.publishInMain(operationalPoints = listOf(op1Id)).uuid
        val op1AfterUpdate = mainOfficialContext.fetch(op1Id)!!
        assertNotEquals(op1BeforeUpdate, op1AfterUpdate)
        assertEquals(op2BeforeUpdate, mainOfficialContext.fetch(op2Id)!!)

        // Verify all fetches show the new publication but only op1 is updated
        assertLatestStateInApi(updatedVersion, op1Oid to op1AfterUpdate, op2Oid to op2BeforeUpdate)

        // Verify that fetching at specific versions also show the same results
        assertVersionStateInApi(baseVersion, op1Oid to op1BeforeUpdate, op2Oid to op2BeforeUpdate)
        assertVersionStateInApi(updatedVersion, op1Oid to op1AfterUpdate, op2Oid to op2BeforeUpdate)
    }

    @Test
    fun `Operational point API does not contain draft objects or changes`() {
        val op1Id =
            mainDraftContext
                .save(operationalPoint(name = "Test Point 1", abbreviation = "TP1", location = Point(0.5, 0.5)))
                .id
        val op1Oid = mainDraftContext.generateOid(op1Id)

        val op2Id =
            mainDraftContext
                .save(operationalPoint(name = "Test Point 2", abbreviation = "TP2", location = Point(10.5, 10.5)))
                .id
        val op2Oid = mainDraftContext.generateOid(op2Id)

        // Only publish op1 at first
        val baseVersion = extTestDataService.publishInMain(operationalPoints = listOf(op1Id)).uuid
        val op1Published = mainOfficialContext.fetch(op1Id)!!

        // API should only show published op1, not draft op2
        assertLatestStateInApi(baseVersion, op1Oid to op1Published)
        assertLatestCollectionState(baseVersion, op1Oid to op1Published)

        // Publish op2
        initUser()
        val updateVersion = extTestDataService.publishInMain(operationalPoints = listOf(op2Id)).uuid
        val op2Published = mainOfficialContext.fetch(op2Id)!!

        // Both should now be visible
        assertLatestStateInApi(updateVersion, op1Oid to op1Published, op2Oid to op2Published)

        // Old version should still show only op1
        assertVersionStateInApi(baseVersion, op1Oid to op1Published)
        assertVersionCollectionState(baseVersion, op1Oid to op1Published)
    }

    @Test
    fun `Deleted operational point should be returned in single-fetch API but not collection API`() {
        val opId =
            mainDraftContext
                .save(operationalPoint(name = "Test Point", abbreviation = "TP", location = Point(0.5, 0.5)))
                .id
        val oid = mainDraftContext.generateOid(opId)

        val baseVersion = extTestDataService.publishInMain(operationalPoints = listOf(opId)).uuid
        val baseOp = mainOfficialContext.fetch(opId)!!.also { assertEquals(OperationalPointState.IN_USE, it.state) }
        assertLatestStateInApi(baseVersion, oid to baseOp)

        // Delete the operational point
        initUser()
        mainDraftContext.mutate(opId) { op -> op.copy(state = OperationalPointState.DELETED) }
        val deletedVersion = extTestDataService.publishInMain(operationalPoints = listOf(opId)).uuid
        val deletedOp = mainOfficialContext.fetch(opId)!!.also { assertEquals(OperationalPointState.DELETED, it.state) }

        // Single fetch should return the deleted point
        assertLatestState(deletedVersion, oid, deletedOp)
        // Collection should not include deleted points
        assertLatestCollectionState(deletedVersion)

        // Old version should still show the non-deleted state
        assertVersionState(baseVersion, oid, baseOp)
        assertVersionCollectionState(baseVersion, oid to baseOp)
        // New version can be fetched explicitly
        assertVersionState(deletedVersion, oid, deletedOp)
        assertVersionCollectionState(deletedVersion)
    }

    @Test
    fun `Operational point modification APIs should return correct versions`() {
        val op1Id =
            mainDraftContext
                .save(operationalPoint(name = "Test Point 1", abbreviation = "TP1", location = Point(0.5, 0.5)))
                .id
        val op1Oid = mainDraftContext.generateOid(op1Id)

        val op2Id =
            mainDraftContext
                .save(operationalPoint(name = "Test Point 2", abbreviation = "TP2", location = Point(10.5, 10.5)))
                .id
        val op2Oid = mainDraftContext.generateOid(op2Id)

        val baseVersion = extTestDataService.publishInMain(operationalPoints = listOf(op1Id, op2Id)).uuid

        // First publication -> no changes (verify as both since and between fetches)
        assertChangesSince(baseVersion, baseVersion, listOf(op1Oid, op2Oid), emptyList())
        assertChangesBetween(baseVersion, baseVersion, listOf(op1Oid, op2Oid), emptyList())

        // Update op2 and add op3
        initUser()
        mainDraftContext.mutate(op2Id) { op -> op.copy(name = op.name.copy(value = "${op.name}-EDIT")) }
        val op3Id =
            mainDraftContext
                .save(operationalPoint(name = "Test Point 3", abbreviation = "TP3", location = Point(20.5, 20.5)))
                .id
        val op3Oid = mainDraftContext.generateOid(op3Id)

        val update1Version = extTestDataService.publishInMain(operationalPoints = listOf(op2Id, op3Id)).uuid
        val op2Update1 = mainOfficialContext.fetch(op2Id)!!
        val op3Update1 = mainOfficialContext.fetch(op3Id)!!

        // Changes since base version (verify as both since and between fetches)
        assertChangesSince(
            baseVersion,
            update1Version,
            listOf(op1Oid),
            listOf(op2Oid to op2Update1, op3Oid to op3Update1),
        )
        assertChangesBetween(
            baseVersion,
            update1Version,
            listOf(op1Oid),
            listOf(op2Oid to op2Update1, op3Oid to op3Update1),
        )

        // Update op2 again
        initUser()
        mainDraftContext.mutate(op2Id) { op -> op.copy(name = op.name.copy(value = "${op.name}2")) }
        val update2Version = extTestDataService.publishInMain(operationalPoints = listOf(op2Id)).uuid
        val op2Update2 = mainOfficialContext.fetch(op2Id)!!

        // Changes since base version: op1 unchanged, op2 changed twice, op3 added
        assertChangesSince(
            baseVersion,
            update2Version,
            listOf(op1Oid),
            listOf(op2Oid to op2Update2, op3Oid to op3Update1),
        )
        assertChangesBetween(
            baseVersion,
            update2Version,
            listOf(op1Oid),
            listOf(op2Oid to op2Update2, op3Oid to op3Update1),
        )

        // Changes since update1 version: op1 & op3 unchanged, op2 changed once
        assertChangesSince(update1Version, update2Version, listOf(op1Oid, op3Oid), listOf(op2Oid to op2Update2))
        assertChangesBetween(update1Version, update2Version, listOf(op1Oid, op3Oid), listOf(op2Oid to op2Update2))

        // Changes between base and update1: op1 unchanged, op2 changed once, op3 added
        assertChangesBetween(
            baseVersion,
            update1Version,
            listOf(op1Oid),
            listOf(op2Oid to op2Update1, op3Oid to op3Update1),
        )
    }

    @Test
    fun `Operational point API returns no changes when versions are the same`() {
        val opId =
            mainDraftContext
                .save(operationalPoint(name = "Test Point", abbreviation = "TP", location = Point(0.5, 0.5)))
                .id
        val oid = mainDraftContext.generateOid(opId)

        val publication = extTestDataService.publishInMain(operationalPoints = listOf(opId))

        // Query for changes with same version
        api.operationalPoint.assertNoModificationBetween(oid, publication.uuid, publication.uuid)
        api.operationalPointCollection.assertNoModificationBetween(publication.uuid, publication.uuid)
    }

    @Test
    fun `Operational point API returns correct values for all enum variations and random data`() {
        val testOperationalPoints = mutableListOf<Pair<Oid<OperationalPoint>, OperationalPoint>>()

        // Test all RINF type variations
        OperationalPointRinfType.entries.forEach { rinfType ->
            val opId = mainDraftContext.save(operationalPoint(rinfType = rinfType)).id
            val oid = mainDraftContext.generateOid(opId)
            testOperationalPoints.add(oid to mainDraftContext.fetch(opId)!!)
        }

        // Test all RATO type variations
        OperationalPointRaideType.entries.forEach { ratoType ->
            val opId = mainDraftContext.save(operationalPoint(rinfType = null).copy(raideType = ratoType)).id
            val oid = mainDraftContext.generateOid(opId)
            testOperationalPoints.add(oid to mainDraftContext.fetch(opId)!!)
        }

        // Test all state variations
        OperationalPointState.entries.forEach { state ->
            val opId = mainDraftContext.save(operationalPoint(state = state)).id
            val oid = mainDraftContext.generateOid(opId)
            testOperationalPoints.add(oid to mainDraftContext.fetch(opId)!!)
        }

        // Test other fields with random values
        repeat(3) {
            val opId =
                mainDraftContext
                    .save(
                        operationalPoint(
                            name = randomString(),
                            abbreviation = randomString(),
                            uicCode = randomNumericString(),
                            location = Point(randomDouble(), randomDouble()),
                        )
                    )
                    .id
            val oid = mainDraftContext.generateOid(opId)
            testOperationalPoints.add(oid to mainDraftContext.fetch(opId)!!)
        }

        // Publish all operational points
        val publication =
            extTestDataService.publishInMain(
                operationalPoints = testOperationalPoints.map { (_, op) -> op.id as IntId }
            )

        // Verify single fetch API returns correct data for all variations
        testOperationalPoints.forEach { (oid, op) ->
            val response = api.operationalPoint.get(oid)
            assertEquals(publication.uuid.toString(), response.rataverkon_versio)
            assertMatches(oid, op, response.toiminnallinen_piste)
        }

        // Verify collection API returns all non-deleted operational points
        val collectionResponse = api.operationalPointCollection.get()
        assertEquals(publication.uuid.toString(), collectionResponse.rataverkon_versio)
        val nonDeletedOps = testOperationalPoints.filter { (_, op) -> op.state != OperationalPointState.DELETED }
        assertEquals(nonDeletedOps.size, collectionResponse.toiminnalliset_pisteet.size)
        nonDeletedOps.forEach { (oid, op) ->
            assertCollectionItemMatches(oid, op, collectionResponse.toiminnalliset_pisteet)
        }

        // Verify versioned fetch returns correct data
        testOperationalPoints.take(3).forEach { (oid, op) ->
            val response = api.operationalPoint.getAtVersion(oid, publication.uuid)
            assertEquals(publication.uuid.toString(), response.rataverkon_versio)
            assertMatches(oid, op, response.toiminnallinen_piste)
        }
    }

    @Test
    fun `Operational point APIs respect coordinate system parameter`() {
        val location3067 = Point(385782.89, 6672277.83)
        val location4326 = Point(24.9414003, 60.1713788)

        val opId = mainDraftContext.save(operationalPoint(location = location3067)).id
        val oid = mainDraftContext.generateOid(opId)

        extTestDataService.publishInMain(operationalPoints = listOf(opId))

        // Default EPSG:3067
        api.operationalPoint.get(oid).let { response ->
            assertEquals("EPSG:3067", response.koordinaatisto)
            assertEquals(location3067.x, response.toiminnallinen_piste.sijainti!!.x, 0.01)
            assertEquals(location3067.y, response.toiminnallinen_piste.sijainti.y, 0.01)
        }

        // EPSG:4326
        api.operationalPoint.get(oid, "koordinaatisto" to "EPSG:4326").let { response ->
            assertEquals("EPSG:4326", response.koordinaatisto)
            assertEquals(location4326.x, response.toiminnallinen_piste.sijainti!!.x, 0.001)
            assertEquals(location4326.y, response.toiminnallinen_piste.sijainti.y, 0.001)
        }

        // Verify collection also respects coordinate system
        api.operationalPointCollection.get("koordinaatisto" to "EPSG:4326").let { response ->
            assertEquals("EPSG:4326", response.koordinaatisto)
            val op = response.toiminnalliset_pisteet.find { it.toiminnallinen_piste_oid == oid.toString() }
            assertNotNull(op)
            assertEquals(location4326.x, op!!.sijainti!!.x, 0.001)
            assertEquals(location4326.y, op.sijainti.y, 0.001)
        }
    }

    @Test
    fun `Operational point with polygon area is returned correctly`() {
        val polygon =
            fi.fta.geoviite.infra.math.Polygon(
                listOf(Point(0.0, 0.0), Point(100.0, 0.0), Point(100.0, 50.0), Point(0.0, 50.0), Point(0.0, 0.0))
            )

        val opId = mainDraftContext.save(operationalPoint(polygon = polygon, location = Point(50.0, 25.0))).id
        val oid = mainDraftContext.generateOid(opId)

        extTestDataService.publishInMain(operationalPoints = listOf(opId))

        val response = api.operationalPoint.get(oid)
        assertPolygonMatches(polygon, response.toiminnallinen_piste.alue)

        // Verify collection also includes polygon
        val collectionResponse = api.operationalPointCollection.get()
        val opInCollection =
            collectionResponse.toiminnalliset_pisteet.find { it.toiminnallinen_piste_oid == oid.toString() }
        assertNotNull(opInCollection)
        assertPolygonMatches(polygon, opInCollection!!.alue)
    }

    @Test
    fun `Operational point with tracks and switches shows associations correctly`() {
        val opId = mainDraftContext.save(operationalPoint(location = Point(5.0, 0.0))).id
        val opOid = mainDraftContext.generateOid(opId)

        // Create track number and reference line
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id
        val segment = segment(Point(0.0, 0.0), Point(100.0, 0.0))
        val rlId = mainDraftContext.save(referenceLine(trackNumberId), referenceLineGeometry(segment)).id

        // Create switch linked to operational point
        val structure = switchStructureYV60_300_1_9()
        val switchId =
            mainDraftContext
                .save(
                    switch(structure.id, joints = listOf(switchJoint(1, Point(0.0, 0.0))))
                        .copy(operationalPointId = opId)
                )
                .id
        val switchOid = mainDraftContext.generateOid(switchId)

        // Create track linked to operational point
        val trackId =
            mainDraftContext
                .save(
                    locationTrack(trackNumberId).copy(operationalPointIds = setOf(opId)),
                    trackGeometryOfSegments(segment),
                )
                .id
        val trackOid = mainDraftContext.generateOid(trackId)

        extTestDataService.publishInMain(
            operationalPoints = listOf(opId),
            switches = listOf(switchId),
            locationTracks = listOf(trackId),
            trackNumbers = listOf(trackNumberId),
            referenceLines = listOf(rlId),
        )

        val response = api.operationalPoint.get(opOid)
        val op = response.toiminnallinen_piste

        assertEquals(listOf(trackOid.toString()), op.raiteet.map { it.sijaintiraide_oid })
        assertEquals(listOf(switchOid.toString()), op.vaihteet.map { it.vaihde_oid })
    }

    @Test
    @Disabled(
        """
        Calculated changes for operational points are not yet implemented.
        When a switch or track is linked to an operational point, the operational point should appear
        as changed in the modification APIs even though the operational point itself wasn't directly edited.
        """
    )
    fun `Operational point shows as changed when track or switch is linked to it`() {
        val opId = mainDraftContext.save(operationalPoint(location = Point(5.0, 0.0))).id
        val opOid = mainDraftContext.generateOid(opId)

        val basePublication = extTestDataService.publishInMain(operationalPoints = listOf(opId))

        // Verify no changes initially
        api.operationalPoint.assertNoModificationSince(opOid, basePublication.uuid)
        api.operationalPointCollection.assertNoModificationSince(basePublication.uuid)

        // Create and link a switch to the operational point (calculated change)
        initUser()
        val trackNumberId = mainDraftContext.createLayoutTrackNumber().id
        val rlId =
            mainDraftContext
                .save(referenceLine(trackNumberId), referenceLineGeometry(segment(Point(0.0, 0.0), Point(100.0, 0.0))))
                .id

        val structure = switchStructureYV60_300_1_9()
        val switchId =
            mainDraftContext
                .save(
                    switch(
                            structure.id,
                            joints = listOf(switchJoint(1, Point(0.0, 0.0)), switchJoint(2, Point(10.0, 0.0))),
                        )
                        .copy(operationalPointId = opId)
                )
                .id

        val updatePublication =
            extTestDataService.publishInMain(
                switches = listOf(switchId),
                trackNumbers = listOf(trackNumberId),
                referenceLines = listOf(rlId),
            )

        // Operational point should show as changed even though it wasn't directly edited
        val modification = api.operationalPoint.getModifiedSince(opOid, basePublication.uuid)
        assertEquals(basePublication.uuid.toString(), modification.alkuversio)
        assertEquals(updatePublication.uuid.toString(), modification.loppuversio)
        assertEquals(1, modification.toiminnallinen_piste.vaihteet.size)

        // Collection changes should also show it
        val collectionChanges = api.operationalPointCollection.getModifiedSince(basePublication.uuid)
        assertEquals(
            listOf(opOid.toString()),
            collectionChanges.toiminnalliset_pisteet.map { it.toiminnallinen_piste_oid },
        )
    }

    private fun assertPolygonMatches(expected: Polygon, actual: ExtTestPolygonV1?) {
        assertNotNull(actual)
        assertEquals("Polygoni", actual!!.tyyppi)
        assertEquals(expected.points.size, actual.pisteet.size)
        expected.points.forEachIndexed { index, point ->
            assertEquals(point.x, actual.pisteet[index].x, 0.001)
            assertEquals(point.y, actual.pisteet[index].y, 0.001)
        }
    }

    private fun randomString(): String = (1..8).map { ('A'..'Z').random() }.joinToString("")

    private fun randomNumericString(): String = (1..6).map { ('0'..'9').random() }.joinToString("")

    private fun randomDouble(): Double = kotlin.random.Random.nextDouble(0.0, 100.0)

    private fun assertChangesSince(
        baseVersion: Uuid<Publication>,
        currentVersion: Uuid<Publication>,
        notChanged: List<Oid<OperationalPoint>>,
        changed: List<Pair<Oid<OperationalPoint>, OperationalPoint>>,
    ) {
        for (oid in notChanged) api.operationalPoint.assertNoModificationSince(oid, baseVersion)
        for ((oid, op) in changed) {
            val response = api.operationalPoint.getModifiedSince(oid, baseVersion)
            assertEquals(baseVersion.toString(), response.alkuversio)
            assertEquals(currentVersion.toString(), response.loppuversio)
            assertMatches(oid, op, response.toiminnallinen_piste)
        }
        if (changed.isEmpty()) api.operationalPointCollection.assertNoModificationSince(baseVersion)
        else
            api.operationalPointCollection.getModifiedSince(baseVersion).also { response ->
                assertEquals(baseVersion.toString(), response.alkuversio)
                assertEquals(currentVersion.toString(), response.loppuversio)
                assertCollectionMatches(response.toiminnalliset_pisteet, *(changed.toTypedArray()))
            }
    }

    private fun assertChangesBetween(
        from: Uuid<Publication>,
        to: Uuid<Publication>,
        notChanged: List<Oid<OperationalPoint>>,
        changed: List<Pair<Oid<OperationalPoint>, OperationalPoint>>,
    ) {
        for (oid in notChanged) api.operationalPoint.assertNoModificationBetween(oid, from, to)
        for ((oid, op) in changed) {
            val response = api.operationalPoint.getModifiedBetween(oid, from, to)
            assertEquals(from.toString(), response.alkuversio)
            assertEquals(to.toString(), response.loppuversio)
            assertMatches(oid, op, response.toiminnallinen_piste)
        }
        if (changed.isEmpty()) api.operationalPointCollection.assertNoModificationBetween(from, to)
        else
            api.operationalPointCollection.getModifiedBetween(from, to).also { response ->
                assertEquals(from.toString(), response.alkuversio)
                assertEquals(to.toString(), response.loppuversio)
                assertCollectionMatches(response.toiminnalliset_pisteet, *(changed.toTypedArray()))
            }
    }

    private fun assertLatestStateInApi(
        layoutVersion: Uuid<Publication>,
        vararg operationalPoints: Pair<Oid<OperationalPoint>, OperationalPoint>,
    ) {
        for ((oid, op) in operationalPoints) assertLatestState(layoutVersion, oid, op)
        assertLatestCollectionState(layoutVersion, *operationalPoints)
    }

    private fun assertVersionStateInApi(
        layoutVersion: Uuid<Publication>,
        vararg operationalPoints: Pair<Oid<OperationalPoint>, OperationalPoint>,
    ) {
        for ((oid, op) in operationalPoints) assertVersionState(layoutVersion, oid, op)
        assertVersionCollectionState(layoutVersion, *operationalPoints)
    }

    private fun assertLatestState(
        layoutVersion: Uuid<Publication>,
        oid: Oid<OperationalPoint>,
        operationalPoint: OperationalPoint,
    ) {
        val response = api.operationalPoint.get(oid)
        assertEquals(layoutVersion.toString(), response.rataverkon_versio)
        assertMatches(oid, operationalPoint, response.toiminnallinen_piste)
    }

    private fun assertVersionState(
        layoutVersion: Uuid<Publication>,
        oid: Oid<OperationalPoint>,
        operationalPoint: OperationalPoint,
    ) {
        val response = api.operationalPoint.getAtVersion(oid, layoutVersion)
        assertEquals(layoutVersion.toString(), response.rataverkon_versio)
        assertMatches(oid, operationalPoint, response.toiminnallinen_piste)
    }

    private fun assertLatestCollectionState(
        layoutVersion: Uuid<Publication>,
        vararg operationalPoints: Pair<Oid<OperationalPoint>, OperationalPoint>,
    ) {
        val collectionResponse = api.operationalPointCollection.get()
        assertEquals(layoutVersion.toString(), collectionResponse.rataverkon_versio)
        assertCollectionMatches(collectionResponse.toiminnalliset_pisteet, *operationalPoints)
    }

    private fun assertVersionCollectionState(
        layoutVersion: Uuid<Publication>,
        vararg operationalPoints: Pair<Oid<OperationalPoint>, OperationalPoint>,
    ) {
        val collectionResponse = api.operationalPointCollection.getAtVersion(layoutVersion)
        assertEquals(layoutVersion.toString(), collectionResponse.rataverkon_versio)
        assertCollectionMatches(collectionResponse.toiminnalliset_pisteet, *operationalPoints)
    }

    private fun assertCollectionMatches(
        resultOps: List<ExtTestOperationalPointV1>,
        vararg operationalPoints: Pair<Oid<OperationalPoint>, OperationalPoint>,
    ) {
        assertEquals(
            operationalPoints.map { it.first.toString() }.toSet(),
            resultOps.map { it.toiminnallinen_piste_oid }.toSet(),
        )
        for ((oid, op) in operationalPoints) {
            assertCollectionItemMatches(oid, op, resultOps)
        }
    }

    private fun assertCollectionItemMatches(
        oid: Oid<OperationalPoint>,
        operationalPoint: OperationalPoint,
        items: List<ExtTestOperationalPointV1>,
    ) = assertMatches(oid, operationalPoint, items.find { it.toiminnallinen_piste_oid == oid.toString() })

    private fun assertMatches(
        oid: Oid<OperationalPoint>,
        operationalPoint: OperationalPoint,
        actual: ExtTestOperationalPointV1?,
    ) {
        requireNotNull(actual) { "Expected operational point $oid not found in response" }
        assertEquals(oid.toString(), actual.toiminnallinen_piste_oid)
        assertEquals(operationalPoint.name.toString(), actual.nimi)
        assertEquals(operationalPoint.abbreviation?.toString(), actual.lyhenne)
        assertEquals(
            when (operationalPoint.state) {
                OperationalPointState.IN_USE -> "käytössä"
                OperationalPointState.DELETED -> "poistettu"
            },
            actual.tila,
        )
        assertEquals("Geoviite", actual.lähde)
        operationalPoint.uicCode?.let { assertEquals(it.toString(), actual.uic_koodi) }
        operationalPoint.location?.let { location ->
            assertNotNull(actual.sijainti)
            assertEquals(location.x, actual.sijainti!!.x, 0.001)
            assertEquals(location.y, actual.sijainti.y, 0.001)
        }
        operationalPoint.polygon?.let { polygon ->
            assertNotNull(actual.alue)
            assertEquals("Polygoni", actual.alue!!.tyyppi)
            assertEquals(polygon.points.size, actual.alue.pisteet.size)
        }
        operationalPoint.rinfType?.let { rinfType ->
            assertNotNull(actual.tyyppi_rinf)
            val (expectedCode, expectedDescription) =
                when (rinfType) {
                    OperationalPointRinfType.STATION -> "10" to "Station"
                    OperationalPointRinfType.SMALL_STATION -> "20" to "Small station"
                    OperationalPointRinfType.PASSENGER_TERMINAL -> "30" to "Passenger terminal"
                    OperationalPointRinfType.FREIGHT_TERMINAL -> "40" to "Freight terminal"
                    OperationalPointRinfType.DEPOT_OR_WORKSHOP -> "50" to "Depot or workshop"
                    OperationalPointRinfType.TRAIN_TECHNICAL_SERVICES -> "60" to "Train technical services"
                    OperationalPointRinfType.PASSENGER_STOP -> "70" to "Passenger stop"
                    OperationalPointRinfType.JUNCTION -> "80" to "Junction"
                    OperationalPointRinfType.BORDER_POINT -> "90" to "Border point"
                    OperationalPointRinfType.SHUNTING_YARD -> "100" to "Shunting yard"
                    OperationalPointRinfType.TECHNICAL_CHANGE -> "110" to "Technical change"
                    OperationalPointRinfType.SWITCH -> "120" to "Switch"
                    OperationalPointRinfType.PRIVATE_SIDING -> "130" to "Private siding"
                    OperationalPointRinfType.DOMESTIC_BORDER_POINT -> "140" to "Domestic border point"
                    OperationalPointRinfType.OVER_CROSSING -> "150" to "Over crossing"
                }
            assertEquals(expectedCode, actual.tyyppi_rinf!!.koodi)
            assertEquals(expectedDescription, actual.tyyppi_rinf.selite_en)
        }
        operationalPoint.raideType?.let { raideType ->
            assertNotNull(actual.tyyppi_rato)
            val (expectedCode, expectedDescription) =
                when (raideType) {
                    OperationalPointRaideType.LP -> "LP" to "Liikennepaikka"
                    OperationalPointRaideType.LPO -> "LPO" to "Liikennepaikan osa"
                    OperationalPointRaideType.OLP -> "OLP" to "Osiinjaettu liikennepaikka"
                    OperationalPointRaideType.SEIS -> "SEIS" to "Seisake"
                    OperationalPointRaideType.LVH -> "LVH" to "Linjavaihde"
                }
            assertEquals(expectedCode, actual.tyyppi_rato!!.koodi)
            assertEquals(expectedDescription, actual.tyyppi_rato.selite)
        }
        if (operationalPoint.raideType == null) assertNull(actual.tyyppi_rato)
        if (operationalPoint.rinfType == null) assertNull(actual.tyyppi_rinf)
    }
}
