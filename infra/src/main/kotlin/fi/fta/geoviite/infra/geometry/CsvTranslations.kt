package fi.fta.geoviite.infra.geometry

val ELEMENT_LISTING = "Elementtilistaus"

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
