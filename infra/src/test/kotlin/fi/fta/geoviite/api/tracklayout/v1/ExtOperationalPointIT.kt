package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.ratko.model.OperationalPointRaideType
import fi.fta.geoviite.infra.tracklayout.OperationalPoint
import fi.fta.geoviite.infra.tracklayout.OperationalPointRinfType
import fi.fta.geoviite.infra.tracklayout.OperationalPointState
import fi.fta.geoviite.infra.tracklayout.operationalPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
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
            extTestDataService.publishInMain(operationalPoints = testOperationalPoints.map { (_, op) -> op.id as IntId })

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
        nonDeletedOps.forEach { (oid, op) -> assertCollectionItemMatches(oid, op, collectionResponse.toiminnalliset_pisteet) }

        // Verify versioned fetch returns correct data
        testOperationalPoints.take(3).forEach { (oid, op) ->
            val response = api.operationalPoint.getAtVersion(oid, publication.uuid)
            assertEquals(publication.uuid.toString(), response.rataverkon_versio)
            assertMatches(oid, op, response.toiminnallinen_piste)
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
                    OperationalPointRinfType.STATION -> "10" to "Asema"
                    OperationalPointRinfType.SMALL_STATION -> "20" to "Asema (pieni)"
                    OperationalPointRinfType.PASSENGER_TERMINAL -> "30" to "Matkustaja-asema"
                    OperationalPointRinfType.FREIGHT_TERMINAL -> "40" to "Tavara-asema"
                    OperationalPointRinfType.DEPOT_OR_WORKSHOP -> "50" to "Varikko"
                    OperationalPointRinfType.TRAIN_TECHNICAL_SERVICES -> "60" to "Tekninen ratapiha"
                    OperationalPointRinfType.PASSENGER_STOP -> "70" to "Seisake"
                    OperationalPointRinfType.JUNCTION -> "80" to "Kohtauspaikka"
                    OperationalPointRinfType.BORDER_POINT -> "90" to "Valtakunnan raja"
                    OperationalPointRinfType.SHUNTING_YARD -> "100" to "Vaihtotyöratapiha"
                    OperationalPointRinfType.TECHNICAL_CHANGE -> "110" to "Raideleveyden vaihtumiskohta"
                    OperationalPointRinfType.SWITCH -> "120" to "Linjavaihde"
                    OperationalPointRinfType.PRIVATE_SIDING -> "130" to "Yksityinen"
                    OperationalPointRinfType.DOMESTIC_BORDER_POINT -> "140" to "Omistusraja"
                    OperationalPointRinfType.OVER_CROSSING -> "150" to "Ylikulku"
                }
            assertEquals(expectedCode, actual.tyyppi_rinf!!.koodi)
            assertEquals(expectedDescription, actual.tyyppi_rinf.kuvaus)
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
            assertEquals(expectedDescription, actual.tyyppi_rato.kuvaus)
        }
        if (operationalPoint.raideType == null) assertNull(actual.tyyppi_rato)
        if (operationalPoint.rinfType == null) assertNull(actual.tyyppi_rinf)
    }
}
