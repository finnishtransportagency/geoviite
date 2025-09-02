package fi.fta.geoviite.api.tracklayout.v1

const val EXT_TRACK_LAYOUT_BASE_PATH = "/geoviite"

const val TRACK_LAYOUT_VERSION_FROM = "alkuversio"
const val TRACK_LAYOUT_VERSION_TO = "loppuversio"
const val TRACK_LAYOUT_VERSION = "rataverkon_versio"
const val LOCATION_TRACK_OID = "sijaintiraide_oid"
const val LOCATION_TRACK = "sijaintiraide"
const val LOCATION_TRACK_NAME = "sijaintiraidetunnus"
const val LOCATION_TRACK_COLLECTION = "sijaintiraiteet"
const val TRACK_KILOMETER_START = "ratakilometri_alku"
const val TRACK_KILOMETER_END = "ratakilometri_loppu"
const val TRACK_NUMBER = "ratanumero"
const val TRACK_NUMBER_OID = "ratanumero_oid"
const val TRACK_NUMBER_COLLECTION = "ratanumerot"
const val COORDINATE_SYSTEM = "koordinaatisto"
const val ADDRESS_POINT_RESOLUTION = "osoitepistevali"
const val TRACK_INTERVALS = "osoitevalit"
const val TYPE = "tyyppi"
const val STATE = "tila"
const val DESCRIPTION = "kuvaus"
const val OWNER = "omistaja"
const val START_LOCATION = "alkusijainti"
const val END_LOCATION = "loppusijainti"

const val EXT_OPENAPI_INVALID_ARGUMENTS = "Yhden tai useamman hakuargumentin muoto oli virheellinen."

const val EXT_OPENAPI_SERVER_ERROR = "Palvelussa tapahtui sisäinen virhe. Ota tarvittaessa yhteyttä ylläpitoon."

const val EXT_OPENAPI_TRACK_LAYOUT_VERSION =
    "Rataverkon UUID-tunnus, johon haku kohdistetaan. Oletuksena haussa käytetään uusinta rataverkon versiota."

const val EXT_OPENAPI_TRACK_LAYOUT_VERSION_FROM = "Rataverkon UUID-tunnus, josta lähtien vertaillaan."

const val EXT_OPENAPI_TRACK_LAYOUT_VERSION_TO =
    "Uudemman rataverkon UUID-tunnus, johon vertailu kohdistetaan. Oletuksena vertailussa käytetään uusinta rataverkon versiota."

const val EXT_OPENAPI_NO_MODIFICATIONS_BETWEEN_VERSIONS =
    "Muutoksia vertailtavien rataverkon versioiden välillä ei ole."

const val EXT_OPENAPI_LOCATION_TRACK_OID_DESCRIPTION = "Sijaintiraiteen OID-tunnus."

const val EXT_OPENAPI_TRACK_NUMBER_OID_DESCRIPTION = "Ratanumeron OID-tunnus."

const val EXT_OPENAPI_COORDINATE_SYSTEM =
    "Hyödynnettävän koordinaattijärjestelmän EPSG-tunnus. Oletuksena käytetään paikannuspohjan koordinaatistoa EPSG:3067 (ETRS-TM35FIN)."

const val EXT_OPENAPI_RESOLUTION =
    "Palautettavien osoitepisteiden metriväli. Oletuksena osoitepisteet palautetaan yhden metrin välein."

const val EXT_OPENAPI_TRACK_KILOMETER_START =
    "Osoitepisteitä haetaan tästä ratakilometristä lähtien. Oletuksena osoitepisteitä haetaan raiteen alusta lähtien."

const val EXT_OPENAPI_TRACK_KILOMETER_END =
    "Osoitepisteitä haetaan tähän ratakilometriin asti. Oletuksena osoitepisteitä haetaan raiteen loppuun asti."

const val EXT_OPENAPI_REFERENCE_LINE_KILOMETER_START =
    "Osoitepisteitä haetaan tästä ratakilometristä lähtien. Oletuksena osoitepisteitä haetaan ratanumeron alusta lähtien."

const val EXT_OPENAPI_REFERENCE_LINE_KILOMETER_END =
    "Osoitepisteitä haetaan tähän ratakilometriin asti. Oletuksena osoitepisteitä haetaan ratanumeron loppuun asti."

const val EXT_OPENAPI_LOCATION_TRACK_OR_TRACK_LAYOUT_VERSION_NOT_FOUND =
    "Sijaintiraidetta ei löytynyt OID-tunnuksella tai annettua rataverkon versiota ei ole olemassa."

const val EXT_OPENAPI_TRACK_NUMBER_OR_TRACK_LAYOUT_VERSION_NOT_FOUND =
    "Ratanumeroa ei löytynyt OID-tunnuksella tai annettua rataverkon versiota ei ole olemassa."

const val EXT_OPENAPI_TRACK_LAYOUT_VERSION_NOT_FOUND = "Annettua rataverkon versiota ei ole olemassa."

const val EXT_OPENAPI_ONE_OR_MORE_TRACK_LAYOUT_VERSION_NOT_FOUND =
    "Yhtä tai useampaa rataverkon versiota ei ole olemassa."
