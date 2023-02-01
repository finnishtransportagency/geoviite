package fi.fta.geoviite.infra.geometry

val ELEMENT_LISTING_CSV_HEADERS = listOf(
    "Ratanumero",
    "Raide",
    "Elementin tyyppi",
    "Rataosoite alussa",
    "Rataosoite lopussa",
    "Sijainti alussa E",
    "Sijainti alussa N",
    "Sijainti lopussa E",
    "Sijainti lopussa N",
    "Pituus (m)",
    "Kaarresäde alussa",
    "Kaarresäde lopussa",
    "Kallistus alussa",
    "Kallistus lopussa",
    "Suuntakulma alussa (grad)",
    "Suuntakulma lopussa (grad)",
    "Suunnitelma",
    "Lähde",
    "Koordinaatisto"
)

fun translateTrackGeometryElementType(type: TrackGeometryElementType) =
    when (type) {
        TrackGeometryElementType.LINE -> "suora"
        TrackGeometryElementType.CURVE -> "kaari"
        TrackGeometryElementType.CLOTHOID -> "siirtymäkaari"
        TrackGeometryElementType.BIQUADRATIC_PARABOLA -> "helmert"
        TrackGeometryElementType.MISSING_SECTION -> "linkitys puuttuu"
    }
