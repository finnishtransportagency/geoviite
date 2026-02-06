package fi.fta.geoviite.api.tracklayout.v1

const val EXT_TRACK_LAYOUT_BASE_PATH = "/geoviite"

const val TRACK_LAYOUT_VERSION_FROM = "alkuversio"
const val TRACK_LAYOUT_VERSION_TO = "loppuversio"
const val TRACK_LAYOUT_VERSION = "rataverkon_versio"
const val TRACK_LAYOUT_VERSIONS = "rataverkon_versiot"
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
const val TIMESTAMP = "aikaleima"
const val DESCRIPTION = "kuvaus"
const val OWNER = "omistaja"
const val START_LOCATION = "alkusijainti"
const val END_LOCATION = "loppusijainti"
const val TRACK_KMS = "ratakilometrit"
const val TRACK_NUMBER_KMS = "ratanumeron_ratakilometrit"
const val TRACK_NUMBER_KMS_COLLECTION = "ratanumeroiden_ratakilometrit"
const val TRACK_ADDRESS = "rataosoite"

const val KM_NUMBER = "km_tunnus"
const val KM_START_M = "alkupaalu"
const val KM_END_M = "loppupaalu"
const val KM_LENGTH = "ratakilometrin_pituus"
const val OFFICIAL_LOCATION = "virallinen_sijainti"
const val OFFICIAL_LOCATION_CONFIRMED = "vahvistettu"

const val SWITCH = "vaihde"
const val SWITCH_COLLECTION = "vaihteet"
const val SWITCH_OID = "vaihde_oid"
const val SWITCH_NAME = "vaihdetunnus"
const val SWITCH_HAND = "katisyys"
const val SWITCH_JOINTS = "pisteet"
const val SWITCH_JOINT_NUMBER = "numero"
const val PRESENTATION_JOINT_NUMBER = "esityspisteen_numero"
const val STATE_CATEGORY = "tilakategoria"
const val TRAP_POINT = "turvavaihde"
const val SWITCH_TRACK_LINKS = "raidelinkit"

const val OPERATIONAL_POINT = "toiminnallinen_piste"
const val OPERATIONAL_POINT_OID = "toiminnallinen_piste_oid"
const val OPERATIONAL_POINT_COLLECTION = "toiminnalliset_pisteet"
const val RINF_ID = "rinf_id"
const val NAME = "nimi"
const val ABBREVIATION = "lyhenne"
const val SOURCE = "lähde"
const val TYPE_RATO = "tyyppi_rato"
const val TYPE_RINF = "tyyppi_rinf"
const val UIC_CODE = "uic_koodi"
const val LOCATION = "sijainti"
const val TRACKS = "raiteet"
const val SWITCHES = "vaihteet"
const val AREA = "alue"
const val CODE = "koodi"
const val TYPE_DESCRIPTION = "selite"
const val RINF_TYPE_DESCRIPTION = "selite_en"
const val POINTS = "pisteet"

const val SOURCE_LOCATION_TRACK_OID = "geometrian_lahderaide_oid"
const val SOURCE_LOCATION_TRACK_NAME = "geometrian_lahderaide_tunnus"
const val TARGET_LOCATION_TRACK_OID = "geometrian_kohderaide_oid"
const val TARGET_LOCATION_TRACK_NAME = "geometrian_kohderaide_tunnus"
const val TRACK_BOUNDARY_CHANGES = "rajojen_muutokset"
const val CHANGE_COLLECTION = "muutokset"
const val GEOMETRY_CHANGE_TYPE = "geometrian_muutos"

const val EXT_OPENAPI_INVALID_ARGUMENTS = "Yhden tai useamman hakuargumentin muoto oli virheellinen."

const val EXT_OPENAPI_SERVER_ERROR = "Palvelussa tapahtui sisäinen virhe. Ota tarvittaessa yhteyttä ylläpitoon."

const val EXT_OPENAPI_TRACK_LAYOUT_VERSION =
    "Rataverkon UUID-tunnus, johon haku kohdistetaan. Oletuksena haussa käytetään uusinta rataverkon versiota."

const val EXT_OPENAPI_TRACK_LAYOUT_VERSION_DESCRIPTION = "Rataverkon UUID-tunnus."

const val EXT_OPENAPI_TRACK_LAYOUT_VERSION_FROM = "Rataverkon UUID-tunnus, josta lähtien vertaillaan."

const val EXT_OPENAPI_TRACK_LAYOUT_VERSION_TO =
    "Uudemman rataverkon UUID-tunnus, johon vertailu kohdistetaan. Oletuksena vertailussa käytetään uusinta rataverkon versiota."

const val EXT_OPENAPI_NO_MODIFICATIONS_BETWEEN_VERSIONS =
    "Muutoksia vertailtavien rataverkon versioiden välillä ei ole."

const val EXT_OPENAPI_LOCATION_TRACK_OID_DESCRIPTION = "Sijaintiraiteen OID-tunnus."

const val EXT_OPENAPI_SWITCH_OID_DESCRIPTION = "Vaihteen OID-tunnus."

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

const val EXT_OPENAPI_SWITCH_OR_TRACK_LAYOUT_VERSION_NOT_FOUND =
    "Vaihdetta ei löytynyt OID-tunnuksella tai annettua rataverkon versiota ei ole olemassa."

const val EXT_OPENAPI_TRACK_NUMBER_OR_TRACK_LAYOUT_VERSION_NOT_FOUND =
    "Ratanumeroa ei löytynyt OID-tunnuksella tai annettua rataverkon versiota ei ole olemassa."

const val EXT_OPENAPI_TRACK_LAYOUT_VERSION_NOT_FOUND = "Annettua rataverkon versiota ei ole olemassa."

const val EXT_OPENAPI_ONE_OR_MORE_TRACK_LAYOUT_VERSION_NOT_FOUND =
    "Yhtä tai useampaa rataverkon versiota ei ole olemassa."

const val OPERATIONAL_POINT_OID_PARAM = "toiminnallinen_piste_oid"

const val EXT_OPENAPI_OPERATIONAL_POINT_OID_DESCRIPTION = "Toiminnallisen pisteen OID-tunnus."

const val EXT_OPENAPI_OPERATIONAL_POINT_OR_TRACK_LAYOUT_VERSION_NOT_FOUND =
    "Toiminnallista pistettä ei löytynyt OID-tunnuksella tai annettua rataverkon versiota ei ole olemassa."

const val STATION_LINK_COLLECTION = "liikennepaikkavalit"
const val STATION_LINK_LENGTH = "pituus"
const val STATION_LINK_START = "alku"
const val STATION_LINK_END = "loppu"
