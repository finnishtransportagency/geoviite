package fi.fta.geoviite.infra.geography

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.Srid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class GeographyServiceIT @Autowired constructor(private val geographyService: GeographyService) : DBTestBase() {

    @Test
    fun someCoordinateSystemsAreReturnedAsDefault() {
        assertFalse(geographyService.getCoordinateSystems().isEmpty())
    }

    @Test
    fun fetchingDefaultCoordinateSystemsWorks() {
        val system = geographyService.getCoordinateSystem(Srid(3129))
        assertEquals(CoordinateSystemName("ETRS-GK22"), system.name)
        assertEquals(Srid(3129), system.srid)
    }

    @Test
    fun fetchingNonDefaultCoordinateSystemsWorks() {
        val system = geographyService.getCoordinateSystem(Srid(4216))
        assertEquals(CoordinateSystemName("Bermuda 1957"), system.name)
        assertEquals(Srid(4216), system.srid)
    }

    @Test
    fun coordinateSystemSridMappingContainsDefaultSystems() {
        val mapping = geographyService.getCoordinateSystemNameToSridMapping()
        assertEquals(Srid(3129), mapping[CoordinateSystemName("ETRS-GK22")])
        assertEquals(Srid(3129), mapping[CoordinateSystemName("ETRS89 / ETRS-GK22FIN")])
        assertEquals(Srid(2393), mapping[CoordinateSystemName("KKJ3")])
        assertEquals(Srid(2393), mapping[CoordinateSystemName("KKJ / Finland zone 3")])
        assertEquals(Srid(2393), mapping[CoordinateSystemName("KKJ")])
        assertEquals(Srid(2393), mapping[CoordinateSystemName("KKJ / Finland Uniform Coordinate System")])
    }
}
