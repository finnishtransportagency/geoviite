package fi.fta.geoviite.api.tracklayout.v1

const val EXT_TRACK_LAYOUT_BASE_PATH = "/geoviite"

const val TRACK_LAYOUT_VERSION_FROM = "alkuversio"
const val TRACK_LAYOUT_VERSION_TO = "loppuversio"
const val TRACK_LAYOUT_VERSION = "rataverkon_versio"
const val LOCATION_TRACK_OID = "sijaintiraide_oid"
const val LOCATION_TRACK = "sijaintiraide"
const val LOCATION_TRACK_NAME = "sijaintiraidetunnus"
const val LOCATION_TRACK_COLLECTION = "sijaintiraiteet"
const val ADDRESS_POINT_FILTER_START = "osoitepistevali_alku"
const val ADDRESS_POINT_FILTER_END = "osoitepistevali_loppu"
const val TRACK_NUMBER = "ratanumero"
const val TRACK_NUMBER_OID = "ratanumero_oid"
const val TRACK_NUMBER_COLLECTION = "ratanumerot"
const val COORDINATE_SYSTEM = "koordinaatisto"
const val COORDINATE_LOCATION = "sijainti"
const val ADDRESS_POINT_RESOLUTION = "osoitepistevali"
const val TRACK_INTERVAL = "osoitevali"
const val TRACK_INTERVALS = "osoitevalit"
const val TYPE = "tyyppi"
const val STATE = "tila"
const val DESCRIPTION = "kuvaus"
const val OWNER = "omistaja"
const val START_LOCATION = "alkusijainti"
const val END_LOCATION = "loppusijainti"
const val TRACK_KMS = "ratakilometrit"
const val TRACK_NUMBER_KMS = "ratanumeron_kilometrit"
const val TRACK_NUMBER_KMS_COLLECTION = "ratanumeroiden_kilometrit"

const val KM_NUMBER = "km_tunnus"
const val KM_START_M = "alkupaalu"
const val KM_END_M = "loppupaalu"
const val KM_LENGTH = "ratakilometrin_pituus"
const val OFFICIAL_LOCATION = "virallinen_sijainti"
const val OFFICIAL_LOCATION_CONFIRMED = "vahvistettu"

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

const val EXT_OPENAPI_ADDRESS_POINT_FILTER_START =
    "Osoitepisteitä haetaan tästä ratakilometristä tai rataosoitteesta lähtien. Välin alku käsitellään suljettuna (alku <= osoitepiste). Oletuksena osoitepisteväliä ei rajoiteta alusta."

const val EXT_OPENAPI_ADDRESS_POINT_FILTER_END =
    "Osoitepisteitä haetaan tähän ratakilometriin tai rataosoitteeseen asti. Välin loppu käsitellään suljettuna (osoitepiste <= loppu). Oletuksena osoitepisteväliä ei rajoiteta lopusta."

const val EXT_OPENAPI_LOCATION_TRACK_OR_TRACK_LAYOUT_VERSION_NOT_FOUND =
    "Sijaintiraidetta ei löytynyt OID-tunnuksella tai annettua rataverkon versiota ei ole olemassa."

const val EXT_OPENAPI_TRACK_NUMBER_OR_TRACK_LAYOUT_VERSION_NOT_FOUND =
    "Ratanumeroa ei löytynyt OID-tunnuksella tai annettua rataverkon versiota ei ole olemassa."

const val EXT_OPENAPI_TRACK_LAYOUT_VERSION_NOT_FOUND = "Annettua rataverkon versiota ei ole olemassa."

const val EXT_OPENAPI_ONE_OR_MORE_TRACK_LAYOUT_VERSION_NOT_FOUND =
    "Yhtä tai useampaa rataverkon versiota ei ole olemassa."
