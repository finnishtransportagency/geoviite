package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.ratko.model.OperationalPointType
import fi.fta.geoviite.infra.ratko.model.RatkoOperationalPoint
import fi.fta.geoviite.infra.ratko.model.RatkoOperationalPointParse
import fi.fta.geoviite.infra.ratko.model.RatkoRouteNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.someOid
import fi.fta.geoviite.infra.tracklayout.trackNumber
import fi.fta.geoviite.infra.util.FreeText
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class RatkoLocalServiceIT
@Autowired
constructor(
    private val ratkoLocalService: RatkoLocalService,
    private val operatingPointDao: RatkoOperationalPointDao,
    private val trackNumberDao: LayoutTrackNumberDao,
) : DBTestBase() {

    @BeforeEach
    fun clearOperatingPoints() {
        val sql =
            """
                truncate integrations.ratko_operational_point cascade;
                truncate integrations.ratko_operational_point_version cascade;
            """
                .trimIndent()
        jdbc.execute(sql) { it.execute() }
    }

    @Test
    fun `Operating points can be found by exact name`() {
        val operatingPointNames = listOf("Some operating point", "Foo", "Bar")

        operatingPointNames
            .map { name -> createTestOperatingPoint(name = name, abbreviation = "Unused") }
            .let(operatingPointDao::updateOperationalPoints)

        operatingPointNames.forEach { operatingPointName ->
            val result = ratkoLocalService.searchOperationalPoints(FreeText(operatingPointName))

            assertEquals(1, result.size)
            assertEquals(operatingPointName, result[0].name)
        }
    }

    @Test
    fun `Operating points can be found by partial name`() {
        val operatingPointNames = listOf("Some operating point", "Foo", "Bar")

        operatingPointNames
            .map { name -> createTestOperatingPoint(name = name, abbreviation = "Unused") }
            .let(operatingPointDao::updateOperationalPoints)

        operatingPointNames.forEach { operatingPointName ->
            val result = ratkoLocalService.searchOperationalPoints(FreeText(operatingPointName.takeLast(2)))

            assertEquals(1, result.size)
            assertEquals(operatingPointName, result[0].name)
        }
    }

    @Test
    fun `Multiple operating points can be found in same search`() {
        val operatingPointNames = listOf("FOO AAA BBB", "AAA FOO BBB", "BBB AAA")

        operatingPointNames
            .map { name -> createTestOperatingPoint(name = name, abbreviation = "Unused") }
            .let(operatingPointDao::updateOperationalPoints)

        ratkoLocalService.searchOperationalPoints(FreeText("FOO")).let { result -> assertEquals(2, result.size) }

        ratkoLocalService.searchOperationalPoints(FreeText("AAA")).let { result -> assertEquals(3, result.size) }

        ratkoLocalService.searchOperationalPoints(FreeText("BBB")).let { result -> assertEquals(3, result.size) }
    }

    @Test
    fun `Operating points can be found by abbreviation`() {
        val operatingPointAbbreviations = listOf("BBB", "AAA")

        operatingPointAbbreviations
            .map { abbreviation -> createTestOperatingPoint(name = "unused", abbreviation = abbreviation) }
            .let(operatingPointDao::updateOperationalPoints)

        ratkoLocalService.searchOperationalPoints(FreeText("BBB")).let { result -> assertEquals(1, result.size) }

        ratkoLocalService.searchOperationalPoints(FreeText("AAA")).let { result -> assertEquals(1, result.size) }
    }

    @Test
    fun `Operating points search limit restricts the amount of results`() {
        val stringThatMatchesAll = "Operating point"

        (1..20)
            .map { index -> "$stringThatMatchesAll $index" }
            .map { name -> createTestOperatingPoint(name = name, abbreviation = "unused") }
            .let(operatingPointDao::updateOperationalPoints)

        ratkoLocalService
            .searchOperationalPoints(FreeText(stringThatMatchesAll)) // Limit should be 10 by default.
            .let { result -> assertEquals(10, result.size) }

        ratkoLocalService.searchOperationalPoints(FreeText(stringThatMatchesAll), resultLimit = 5).let { result ->
            assertEquals(5, result.size)
        }

        ratkoLocalService.searchOperationalPoints(FreeText(stringThatMatchesAll), resultLimit = 30).let { result ->
            assertEquals(20, result.size)
        }
    }

    @Test
    fun `Operating points search results are sorted by name`() {
        val operatingPointNames = listOf("BBB 1", "CCC 1", "AAA 1")

        operatingPointNames
            .map { name -> createTestOperatingPoint(name = name, abbreviation = "unused") }
            .let(operatingPointDao::updateOperationalPoints)

        ratkoLocalService.searchOperationalPoints(FreeText("1")).let { result ->
            assertEquals(3, result.size)

            assertEquals(operatingPointNames[2], result[0].name)
            assertEquals(operatingPointNames[0], result[1].name)
            assertEquals(operatingPointNames[1], result[2].name)
        }
    }

    @Test
    fun `Operating points search matching both name and abbreviation returns the result only once`() {
        listOf(createTestOperatingPoint(name = "AAA", abbreviation = "AAA"))
            .let(operatingPointDao::updateOperationalPoints)

        ratkoLocalService.searchOperationalPoints(FreeText("A")).let { result ->
            assertEquals(1, result.size)

            assertEquals("AAA", result[0].name)
            assertEquals("AAA", result[0].abbreviation)
        }
    }

    @Test
    fun `Operating points search finds mixed results (match by name or abbreviation)`() {
        listOf(
                createTestOperatingPoint(name = "AAA", abbreviation = "BBB"),
                createTestOperatingPoint(name = "BBB", abbreviation = "AAA"),
            )
            .let(operatingPointDao::updateOperationalPoints)

        ratkoLocalService.searchOperationalPoints(FreeText("A")).let { result -> assertEquals(2, result.size) }

        ratkoLocalService.searchOperationalPoints(FreeText("B")).let { result -> assertEquals(2, result.size) }
    }

    @Test
    fun `Operating points search finds matches case-insensitively`() {
        listOf(
                createTestOperatingPoint(name = "AAA", abbreviation = "unused"),
                createTestOperatingPoint(name = "unused", abbreviation = "bbb"),
            )
            .let(operatingPointDao::updateOperationalPoints)

        ratkoLocalService.searchOperationalPoints(FreeText("a")).let { result ->
            assertEquals(1, result.size)
            assertEquals("AAA", result[0].name)
        }

        ratkoLocalService.searchOperationalPoints(FreeText("B")).let { result ->
            assertEquals(1, result.size)
            assertEquals("bbb", result[0].abbreviation)
        }
    }

    @Test
    fun `Operating points can be found by their exact oid`() {
        listOf(
                createTestOperatingPoint(name = "AAA", abbreviation = "unused", externalId = someOid()),
                createTestOperatingPoint(name = "BBB", abbreviation = "unused", externalId = someOid()),
            )
            .also(operatingPointDao::updateOperationalPoints)
            .forEach { testOperatingPoint ->
                ratkoLocalService.searchOperationalPoints(FreeText(testOperatingPoint.externalId.toString())).let { result
                    ->
                    assertEquals(1, result.size)
                    assertEquals(testOperatingPoint.name, result[0].name)
                }
            }
    }

    @Test
    fun `Operating points cannot be found by partial oid`() {
        listOf(createTestOperatingPoint(name = "AAA", abbreviation = "unused", externalId = someOid()))
            .also(operatingPointDao::updateOperationalPoints)
            .forEach { testOperatingPoint ->
                ratkoLocalService
                    .searchOperationalPoints(FreeText(testOperatingPoint.externalId.toString().substring(0, 4)))
                    .let { result -> assertEquals(0, result.size) }
            }
    }

    private fun createTestOperatingPoint(
        name: String,
        abbreviation: String,
        externalId: Oid<RatkoOperationalPoint> = someOid(),
        ratkoRouteNumberOid: Oid<RatkoRouteNumber>? = null,
    ): RatkoOperationalPointParse {
        val trackNumberExternalId: Oid<RatkoRouteNumber> =
            when {
                ratkoRouteNumberOid != null -> ratkoRouteNumberOid
                else ->
                    someOid<RatkoRouteNumber>().also { oid ->
                        val trackNumber = mainDraftContext.save(trackNumber(testDBService.getUnusedTrackNumber())).id
                        trackNumberDao.insertExternalId(trackNumber, LayoutBranch.main, Oid(oid.toString()))
                    }
            }

        return RatkoOperationalPointParse(
            externalId = externalId,
            name = name,
            abbreviation = abbreviation,
            uicCode = "",
            type = OperationalPointType.LP,
            location = Point(0.0, 0.0),
            trackNumberExternalId = trackNumberExternalId,
        )
    }
}
