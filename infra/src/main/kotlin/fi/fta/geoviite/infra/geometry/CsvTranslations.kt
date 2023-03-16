package fi.fta.geoviite.infra.geometry

const val ELEMENT_LISTING = "Elementtilistaus"
const val VERTICAL_GEOMETRY = "Pystygeometria "

enum class ElementListingHeader {
    TRACK_NUMBER,
    LOCATION_TRACK,
    PLAN_TRACK,
    ELEMENT_TYPE,
    TRACK_ADDRESS_START,
    TRACK_ADDRESS_END,
    LOCATION_START_E,
    LOCATION_START_N,
    LOCATION_END_E,
    LOCATION_END_N,
    LENGTH,
    RADIUS_START,
    RADIUS_END,
    CANT_START,
    CANT_END,
    DIRECTION_START,
    DIRECTION_END,
    PLAN_NAME,
    PLAN_SOURCE,
    CRS
}

fun translateElementListingHeader(header: ElementListingHeader) =
    when (header) {
        ElementListingHeader.TRACK_NUMBER -> "Ratanumero"
        ElementListingHeader.LOCATION_TRACK -> "Sijaintiraide"
        ElementListingHeader.PLAN_TRACK -> "Suunnitelman raide"
        ElementListingHeader.ELEMENT_TYPE -> "Elementin tyyppi"
        ElementListingHeader.TRACK_ADDRESS_START -> "Rataosoite alussa"
        ElementListingHeader.TRACK_ADDRESS_END -> "Rataosoite lopussa"
        ElementListingHeader.LOCATION_START_E -> "Sijainti alussa E"
        ElementListingHeader.LOCATION_START_N -> "Sijainti alussa N"
        ElementListingHeader.LOCATION_END_E -> "Sijainti lopussa E"
        ElementListingHeader.LOCATION_END_N -> "Sijainti lopussa N"
        ElementListingHeader.LENGTH -> "Pituus (m)"
        ElementListingHeader.RADIUS_START -> "Kaarresäde alussa"
        ElementListingHeader.RADIUS_END -> "Kaarresäde lopussa"
        ElementListingHeader.CANT_START -> "Kallistus alussa"
        ElementListingHeader.CANT_END -> "Kallistus lopussa"
        ElementListingHeader.DIRECTION_START -> "Suuntakulma alussa (gooni)"
        ElementListingHeader.DIRECTION_END -> "Suuntakulma lopussa (gooni)"
        ElementListingHeader.PLAN_NAME -> "Suunnitelma"
        ElementListingHeader.PLAN_SOURCE -> "Lähde"
        ElementListingHeader.CRS -> "Koordinaatisto"
    }


fun translateTrackGeometryElementType(type: TrackGeometryElementType) =
    when (type) {
        TrackGeometryElementType.LINE -> "suora"
        TrackGeometryElementType.CURVE -> "kaari"
        TrackGeometryElementType.CLOTHOID -> "siirtymäkaari"
        TrackGeometryElementType.BIQUADRATIC_PARABOLA -> "helmert"
        TrackGeometryElementType.MISSING_SECTION -> "linkitys puuttuu"
    }

enum class VerticalGeometryListingHeader {
    PLAN_NAME,
    LOCATION_TRACK,
    PLAN_TRACK,
    TRACK_ADDRESS_START,
    HEIGHT_START,
    ANGLE_START,
    STATION_START,
    TRACK_ADDRESS_END,
    HEIGHT_END,
    ANGLE_END,
    STATION_END,
    TRACK_ADDRESS_POINT,
    HEIGHT_POINT,
    STATION_POINT,
    RADIUS,
    TANGENT,
    LINEAR_SECTION_FORWARD_LENGTH,
    LINEAR_SECTION_FORWARD_LINEAR_SECTION,
    LINEAR_SECTION_BACKWARD_LENGTH,
    LINEAR_SECTION_BACKWARD_LINEAR_SECTION,
}

fun translateVerticalGeometryListingHeader(header: VerticalGeometryListingHeader) =
    when (header) {
        VerticalGeometryListingHeader.PLAN_NAME -> "Suunnitelma"
        VerticalGeometryListingHeader.LOCATION_TRACK -> "Sijaintiraide"
        VerticalGeometryListingHeader.PLAN_TRACK -> "Suunnitelman raide"
        VerticalGeometryListingHeader.TRACK_ADDRESS_START -> "Pyöristyksen alun rataosoite"
        VerticalGeometryListingHeader.HEIGHT_START -> "Pyöristyksen alun korkeus"
        VerticalGeometryListingHeader.ANGLE_START -> "Pyöristyksen alun kaltevuus"
        VerticalGeometryListingHeader.STATION_START -> "Pyöristyksen alun paaluluku"
        VerticalGeometryListingHeader.TRACK_ADDRESS_END -> "Pyöristyksen lopun rataosoite"
        VerticalGeometryListingHeader.HEIGHT_END -> "Pyöristyksen lopun korkeus"
        VerticalGeometryListingHeader.ANGLE_END -> "Pyöristyksen lopun kaltevuus"
        VerticalGeometryListingHeader.STATION_END -> "Pyöristyksen lopun paaluluku"
        VerticalGeometryListingHeader.TRACK_ADDRESS_POINT -> "Taitepisteen rataosoite"
        VerticalGeometryListingHeader.HEIGHT_POINT -> "Taitepisteen korkeus"
        VerticalGeometryListingHeader.STATION_POINT -> "Taitepisteen paaluluku"
        VerticalGeometryListingHeader.LINEAR_SECTION_BACKWARD_LENGTH -> "Kaltevuusjakson taaksepäin pituus"
        VerticalGeometryListingHeader.LINEAR_SECTION_BACKWARD_LINEAR_SECTION -> "Kaltevuusjakson taaksepäin suora osa"
        VerticalGeometryListingHeader.LINEAR_SECTION_FORWARD_LENGTH -> "Kaltevuusjakson eteenpäin pituus"
        VerticalGeometryListingHeader.LINEAR_SECTION_FORWARD_LINEAR_SECTION -> "Kaltevuusjakson eteenpäin suora osa"
        VerticalGeometryListingHeader.RADIUS -> "Pyöristyssäde"
        VerticalGeometryListingHeader.TANGENT -> "Tangentti"
    }
