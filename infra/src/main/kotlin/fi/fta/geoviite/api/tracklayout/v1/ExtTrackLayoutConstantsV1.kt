package fi.fta.geoviite.api.tracklayout.v1

const val MODIFICATIONS_FROM_VERSION = "muutokset_versiosta"
const val TRACK_LAYOUT_VERSION = "rataverkon_versio"
const val LOCATION_TRACK_OID_PARAM = "sijaintiraide_oid"
const val LOCATION_TRACK_PARAM = "sijaintiraide"
const val LOCATION_TRACK_COLLECTION = "sijaintiraiteet"
const val TRACK_KILOMETER_START_PARAM = "ratakilometri_alku"
const val TRACK_KILOMETER_END_PARAM = "ratakilometri_loppu"
const val COORDINATE_SYSTEM_PARAM = "koordinaatisto"
const val ADDRESS_POINT_RESOLUTION = "osoitepistevali"

const val EXT_OPENAPI_INVALID_ARGUMENTS = "Yhden tai useamman hakuargumentin muoto oli virheellinen."
const val EXT_OPENAPI_SERVER_ERROR = "Palvelussa tapahtui sisäinen virhe. Ota tarvittaessa yhteyttä ylläpitoon."

const val EXT_OPENAPI_TRACK_LAYOUT_VERSION =
    "Rataverkon UUID-tunnus, johon haku kohdistetaan. Oletuksena haussa käytetään uusinta rataverkon versiota."
const val EXT_OPENAPI_TRACK_LAYOUT_VERSION_FROM = "Rataverkon version UUID-tunnus, josta lähtien vertaillaan."
const val EXT_OPENAPI_TRACK_LAYOUT_VERSION_TO =
    "Uudemman rataverkon UUID-tunnus, johon vertailu kohdistetaan. Oletuksena vertailussa käytetään uusinta rataverkon versiota."
const val EXT_OPENAPI_NO_MODIFICATIONS_BETWEEN_VERSIONS =
    "Muutoksia vertailtavien rataverkon versioiden välillä ei ole."
const val EXT_OPENAPI_LOCATION_TRACK_OID_DESCRIPTION = "Sijaintiraiteen OID-tunnus."
const val EXT_OPENAPI_COORDINATE_SYSTEM =
    "Hyödynnettävän koordinaattijärjestelmän EPSG-tunnus. Oletuksena käytetään paikannuspohjan koordinaatistoa EPSG:3067 (ETRS-TM35FIN)."
const val EXT_OPENAPI_RESOLUTION =
    "Palautettavien osoitepisteiden metriväli. Oletuksena osoitepisteet palautetaan yhden metrin välein."
const val EXT_OPENAPI_TRACK_KILOMETER_START =
    "Osoitepisteitä haetaan tästä ratakilometristä lähtien. Oletuksena osoitepisteitä haetaan raiteen alusta lähtien."
const val EXT_OPENAPI_TRACK_KILOMETER_END =
    "Osoitepisteitä haetaan tähän ratakilometriin asti. Oletuksena osoitepisteitä haetaan raiteen loppuun asti."
