package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.ElevationMeasurementMethod
import fi.fta.geoviite.infra.common.SwitchName

const val ELEMENT_LISTING = "Elementtilistaus"
const val ELEMENT_LISTING_ENTIRE_RAIL_NETWORK = "$ELEMENT_LISTING (koko rataverkko)"
const val VERTICAL_GEOMETRY = "Pystygeometria"
const val VERTICAL_GEOMETRY_ENTIRE_RAIL_NETWORK = "$VERTICAL_GEOMETRY (koko rataverkko)"
const val VERTICAL_SECTIONS_OVERLAP = "Kaltevuusjakso on limittäin toisen jakson kanssa"
const val IS_PARTIAL = "Raide sisältää vain osan geometriaelementistä"
val connectedToSwitch = { switchName: SwitchName -> "Vaihteen $switchName elementti" }

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
    CRS,
    REMARKS
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
        ElementListingHeader.REMARKS -> "Huomiot"
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
    TRACK_NUMBER,
    PLAN_NAME,
    LOCATION_TRACK,
    CREATION_DATE,
    CRS,
    PLAN_TRACK,
    TRACK_ADDRESS_START,
    HEIGHT_START,
    ANGLE_START,
    STATION_START,
    LOCATION_E_START,
    LOCATION_N_START,
    TRACK_ADDRESS_END,
    HEIGHT_END,
    ANGLE_END,
    STATION_END,
    LOCATION_E_END,
    LOCATION_N_END,
    TRACK_ADDRESS_POINT,
    HEIGHT_POINT,
    STATION_POINT,
    LOCATION_E_POINT,
    LOCATION_N_POINT,
    RADIUS,
    TANGENT,
    LINEAR_SECTION_FORWARD_LENGTH,
    LINEAR_SECTION_FORWARD_LINEAR_SECTION,
    LINEAR_SECTION_BACKWARD_LENGTH,
    LINEAR_SECTION_BACKWARD_LINEAR_SECTION,
    VERTICAL_COORDINATE_SYSTEM,
    ELEVATION_MEASUREMENT_METHOD,
    REMARKS,
}

fun translateVerticalGeometryListingHeader(header: VerticalGeometryListingHeader) =
    when (header) {
        VerticalGeometryListingHeader.TRACK_NUMBER -> "Ratanumero"
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
        VerticalGeometryListingHeader.VERTICAL_COORDINATE_SYSTEM -> "Korkeusjärjestelmä"
        VerticalGeometryListingHeader.ELEVATION_MEASUREMENT_METHOD -> "Korkeusasema"
        VerticalGeometryListingHeader.REMARKS -> "Huomiot"
        VerticalGeometryListingHeader.CRS -> "Koordinaattijärjestelmä"
        VerticalGeometryListingHeader.CREATION_DATE -> "Luotu"
        VerticalGeometryListingHeader.LOCATION_E_START -> "Pyöristyksen alkupisteen sijainti E"
        VerticalGeometryListingHeader.LOCATION_N_START -> "Pyöristyksen alkupisteen sijainti N"
        VerticalGeometryListingHeader.LOCATION_E_END -> "Pyöristyksen loppupisteen sijainti E"
        VerticalGeometryListingHeader.LOCATION_N_END -> "Pyöristyksen loppupisteen sijainti N"
        VerticalGeometryListingHeader.LOCATION_E_POINT -> "Taitepisteen sijainti E"
        VerticalGeometryListingHeader.LOCATION_N_POINT -> "Taitepisteen sijainti N"
    }

enum class SplitTargetListingHeader {
    SOURCE_NAME,
    SOURCE_OID,
    TARGET_NAME,
    TARGET_OID,
    OPERATION,
    START_ADDRESS,
    END_ADDRESS,
}

fun translateSplitTargetListingHeader(header: SplitTargetListingHeader) =
    when (header) {
        SplitTargetListingHeader.SOURCE_NAME -> "Alkuperäinen raide"
        SplitTargetListingHeader.SOURCE_OID -> "Alkuperäisen raiteen OID"
        SplitTargetListingHeader.TARGET_NAME -> "Kohderaide"
        SplitTargetListingHeader.TARGET_OID -> "Kohderaiteen OID"
        SplitTargetListingHeader.OPERATION -> "Toimenpide"
        SplitTargetListingHeader.START_ADDRESS -> "Alkuosoite"
        SplitTargetListingHeader.END_ADDRESS -> "Loppuosoite"
    }

fun translateElevationMeasurementMethod(elevationMeasurementMethod: ElevationMeasurementMethod?) =
    when (elevationMeasurementMethod) {
        ElevationMeasurementMethod.TOP_OF_SLEEPER -> "Korkeusviiva"
        ElevationMeasurementMethod.TOP_OF_RAIL -> "Kiskon selkä"
        null -> "Ei tiedossa"
    }
