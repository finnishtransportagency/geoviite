package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.ratko.model.OperationalPointType
import fi.fta.geoviite.infra.ratko.model.RatkoOperatingPoint
import fi.fta.geoviite.infra.ratko.model.RatkoOperatingPointParse
import fi.fta.geoviite.infra.ratko.model.RatkoRouteNumber
import fi.fta.geoviite.infra.tracklayout.someOid
import fi.fta.geoviite.infra.util.FreeText
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals

@ActiveProfiles("dev", "test")
@SpringBootTest
class RatkoLocalServiceIT @Autowired constructor(
    private val ratkoLocalService: RatkoLocalService,
    private val operatingPointDao: RatkoOperatingPointDao,
) : DBTestBase() {

    @BeforeEach
    fun clearOperatingPoints() {
        val sql = """
            truncate layout.operating_point cascade;
            truncate layout.operating_point_version cascade;
        """.trimIndent()
        jdbc.execute(sql) { it.execute() }
    }

    @Test
    fun `Operating points can be found by exact name`() {
        val operatingPointNames = listOf(
            "Some operating point",
            "Foo",
            "Bar",
        )

        operatingPointNames.map { name ->
            createTestOperatingPoint(
                name = name,
                abbreviation = "Unused",
            )
        }.let(operatingPointDao::updateOperatingPoints)

        operatingPointNames
            .forEach { operatingPointName ->
                val result = ratkoLocalService.searchOperatingPoints(
                    FreeText(operatingPointName)
                )

                assertEquals(1, result.size)
                assertEquals(operatingPointName, result[0].name)
            }
    }

    @Test
    fun `Operating points can be found by partial name`() {
        val operatingPointNames = listOf(
            "Some operating point",
            "Foo",
            "Bar",
        )

        operatingPointNames.map { name ->
            createTestOperatingPoint(
                name = name,
                abbreviation = "Unused",
            )
        }.let(operatingPointDao::updateOperatingPoints)

        operatingPointNames
            .forEach { operatingPointName ->
                val result = ratkoLocalService.searchOperatingPoints(
                    FreeText(operatingPointName.takeLast(2))
                )

                assertEquals(1, result.size)
                assertEquals(operatingPointName, result[0].name)
            }
    }

    @Test
    fun `Multiple operating points can be found in same search`() {
        val operatingPointNames = listOf(
            "FOO AAA BBB",
            "AAA FOO BBB",
            "BBB AAA",
        )

        operatingPointNames.map { name ->
            createTestOperatingPoint(
                name = name,
                abbreviation = "Unused",
            )
        }.let(operatingPointDao::updateOperatingPoints)

        ratkoLocalService
            .searchOperatingPoints(FreeText("FOO"))
            .let { result -> assertEquals(2, result.size) }

        ratkoLocalService
            .searchOperatingPoints(FreeText("AAA"))
            .let { result -> assertEquals(3, result.size) }

        ratkoLocalService
            .searchOperatingPoints(FreeText("BBB"))
            .let { result -> assertEquals(3, result.size) }
    }

    @Test
    fun `Operating points can be found by abbreviation`() {
        val operatingPointAbbreviations = listOf(
            "BBB",
            "AAA",
        )

        operatingPointAbbreviations.map { abbreviation ->
            createTestOperatingPoint(
                name = "unused",
                abbreviation = abbreviation,
            )
        }.let(operatingPointDao::updateOperatingPoints)

        ratkoLocalService
            .searchOperatingPoints(FreeText("BBB"))
            .let { result -> assertEquals(1, result.size) }

        ratkoLocalService
            .searchOperatingPoints(FreeText("AAA"))
            .let { result -> assertEquals(1, result.size) }
    }


    @Test
    fun `Operating points search limit restricts the amount of results`() {
        val stringThatMatchesAll = "Operating point"

        (1..20).map { index ->
            "$stringThatMatchesAll $index"
        }.map { name ->
            createTestOperatingPoint(
                name = name,
                abbreviation = "unused",
            )
        }.let(operatingPointDao::updateOperatingPoints)

        ratkoLocalService
            .searchOperatingPoints(FreeText(stringThatMatchesAll)) // Limit should be 10 by default.
            .let { result -> assertEquals(10, result.size) }

        ratkoLocalService
            .searchOperatingPoints(FreeText(stringThatMatchesAll), resultLimit = 5)
            .let { result -> assertEquals(5, result.size) }

        ratkoLocalService
            .searchOperatingPoints(FreeText(stringThatMatchesAll), resultLimit = 30)
            .let { result -> assertEquals(20, result.size) }
    }

    @Test
    fun `Operating points search results are sorted by name`() {
        val operatingPointNames = listOf(
            "BBB 1",
            "CCC 1",
            "AAA 1",
        )

        operatingPointNames.map { name ->
            createTestOperatingPoint(
                name = name,
                abbreviation = "unused",
            )
        }.let(operatingPointDao::updateOperatingPoints)

        ratkoLocalService
            .searchOperatingPoints(FreeText("1"))
            .let { result ->
                assertEquals(3, result.size)

                assertEquals(operatingPointNames[2], result[0].name)
                assertEquals(operatingPointNames[0], result[1].name)
                assertEquals(operatingPointNames[1], result[2].name)
            }
    }

    @Test
    fun `Operating points search matching both name and abbreviation returns the result only once`() {
        listOf(
            createTestOperatingPoint(
                name = "AAA",
                abbreviation = "AAA",
            )
        ).let(operatingPointDao::updateOperatingPoints)

        ratkoLocalService
            .searchOperatingPoints(FreeText("A"))
            .let { result ->
                assertEquals(1, result.size)

                assertEquals("AAA", result[0].name)
                assertEquals("AAA", result[0].abbreviation)
            }
    }

    @Test
    fun `Operating points search finds mixed results (match by name or abbreviation)`() {
        listOf(
            createTestOperatingPoint(
                name = "AAA",
                abbreviation = "BBB",
            ),

            createTestOperatingPoint(
                name = "BBB",
                abbreviation = "AAA",
            ),
        ).let(operatingPointDao::updateOperatingPoints)

        ratkoLocalService
            .searchOperatingPoints(FreeText("A"))
            .let { result ->
                assertEquals(2, result.size)
            }

        ratkoLocalService
            .searchOperatingPoints(FreeText("B"))
            .let { result ->
                assertEquals(2, result.size)
            }
    }

    @Test
    fun `Operating points search finds matches case-insensitively`() {
        listOf(
            createTestOperatingPoint(
                name = "AAA",
                abbreviation = "unused",
            ),

            createTestOperatingPoint(
                name = "unused",
                abbreviation = "bbb",
            ),
        ).let(operatingPointDao::updateOperatingPoints)

        ratkoLocalService
            .searchOperatingPoints(FreeText("a"))
            .let { result ->
                assertEquals(1, result.size)
                assertEquals("AAA", result[0].name)
            }

        ratkoLocalService
            .searchOperatingPoints(FreeText("B"))
            .let { result ->
                assertEquals(1, result.size)
                assertEquals("bbb", result[0].abbreviation)
            }
    }

    @Test
    fun `Operating points can be found by their oid`() {
        val oids = (0..1).map { _ -> someOid<RatkoOperatingPoint>() }

        listOf(
            createTestOperatingPoint(
                name = "AAA",
                abbreviation = "unused",
                externalId = oids[0],
            ),

            createTestOperatingPoint(
                name = "BBB",
                abbreviation = "unused",
                externalId = oids[1],
            ),
        ).let(operatingPointDao::updateOperatingPoints)

        ratkoLocalService
            .searchOperatingPoints(FreeText(oids[0].toString()))
            .let { result ->
                assertEquals(1, result.size)
                assertEquals("AAA", result[0].name)
            }

        ratkoLocalService
            .searchOperatingPoints(FreeText(oids[1].toString()))
            .let { result ->
                assertEquals(1, result.size)
                assertEquals("BBB", result[0].name)
            }
    }

    private fun createTestOperatingPoint(
        name: String,
        abbreviation: String,
        externalId: Oid<RatkoOperatingPoint> = someOid(),
        ratkoRouteNumberOid: Oid<RatkoRouteNumber>? = null,
    ): RatkoOperatingPointParse {

        val trackNumberExternalId: Oid<RatkoRouteNumber> = when {
            ratkoRouteNumberOid != null -> ratkoRouteNumberOid
            else -> Oid(getOrCreateTrackNumber(getUnusedTrackNumber()).externalId!!.toString())
        }

        return RatkoOperatingPointParse(
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

