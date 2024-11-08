package fi.fta.geoviite.infra.geography

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.Srid

@GeoviiteService
class GeographyService(private val coordinateSystemDao: CoordinateSystemDao) {

    fun getCoordinateSystems(): List<CoordinateSystem> {
        return coordinateSystemDao.fetchApplicationCoordinateSystems()
    }

    fun getCoordinateSystem(srid: Srid): CoordinateSystem {
        return coordinateSystemDao.fetchCoordinateSystem(srid)
    }

    /** Returns a mapping of application coordinate systems' names and aliases to their respective SRID code */
    fun getCoordinateSystemNameToSridMapping(): Map<CoordinateSystemName, Srid> {
        return mapByNameOrAlias(coordinateSystemDao.fetchApplicationCoordinateSystems())
    }
}
