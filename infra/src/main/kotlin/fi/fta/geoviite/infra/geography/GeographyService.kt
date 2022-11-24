package fi.fta.geoviite.infra.geography

import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.logging.serviceCall
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GeographyService(
    private val coordinateSystemDao: CoordinateSystemDao,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getCoordinateSystems(): List<CoordinateSystem> {
        logger.serviceCall("getCoordinateSystems")
        return coordinateSystemDao.fetchApplicationCoordinateSystems()
    }

    fun getCoordinateSystem(srid: Srid): CoordinateSystem {
        logger.serviceCall("getCoordinateSystem", "srid" to srid)
        return coordinateSystemDao.fetchCoordinateSystem(srid)
    }

    /**
     * Returns a mapping of application coordinate systems' names and aliases to their respective SRID code
     */
    fun getCoordinateSystemNameToSridMapping(): Map<CoordinateSystemName, Srid> {
        logger.serviceCall("getCoordinateSystemNameToSridMapping")
        return mapByNameOrAlias(coordinateSystemDao.fetchApplicationCoordinateSystems())
    }
}
