package fi.fta.geoviite.api.frameconverter.v1

const val FRAME_CONVERTER_TAG_COORDINATE_TO_TRACK_ADDRESS = "Koordinaatista rataosoitteeseen"
const val FRAME_CONVERTER_TAG_TRACK_ADDRESS_TO_COORDINATE = "Rataosoitteesta koordinaatteihin"

const val FRAME_CONVERTER_OPENAPI_INVALID_ARGUMENTS =
    "Yhden tai useamman syötteen muoto tai arvo oli virheellinen."

const val FRAME_CONVERTER_OPENAPI_SERVER_ERROR =
    "Palvelussa tapahtui sisäinen virhe. Ota tarvittaessa yhteyttä ylläpitoon."

const val FRAME_CONVERTER_OPENAPI_COORDINATE_SYSTEM =
    "Koordinaatisto, jossa syöte- ja tuloskoordinaatit käsitellään (oletus EPSG:3067, eli ETRS-TM35FIN). " +
        "Sallitut arvot ovat EPSG-tunnuksia, esim. EPSG:4326."

const val FRAME_CONVERTER_OPENAPI_X =
    "Koordinaatti X annetussa koordinaatistossa."

const val FRAME_CONVERTER_OPENAPI_Y =
    "Koordinaatti Y annetussa koordinaatistossa."

const val FRAME_CONVERTER_OPENAPI_TRACK_KILOMETER =
    "Rataosoitteen ratakilometri."

const val FRAME_CONVERTER_OPENAPI_TRACK_METER =
    "Rataosoitteen ratametri."

// Request-specific field descriptions

const val FRAME_CONVERTER_OPENAPI_REQUEST_IDENTIFIER =
    "Asettaa pyynnölle tunnisteen. Palautetaan GeoJSON-tuloksen properties-kentässä kaikille saman pyynnön tunnisteeseen liittyville muunnoksille."

const val FRAME_CONVERTER_OPENAPI_REQUEST_SEARCH_RADIUS =
    "Hakusäde metreissä annetusta koordinaattisijainnista."

const val FRAME_CONVERTER_OPENAPI_REQUEST_TRACK_NUMBER =
    "Rajaa haun ratanumeron nimen perusteella."

const val FRAME_CONVERTER_OPENAPI_REQUEST_TRACK_NUMBER_OID =
    "Rajaa haun ratanumeron OID-tunnuksen perusteella."

const val FRAME_CONVERTER_OPENAPI_REQUEST_TRACK_NUMBER_EXACTLY_ONE =
    "Rajaa haun ratanumeron nimen perusteella. " +
        "*Huom*: Hakua tulee rajata yhdellä kentistä \"ratanumero\", \"ratanumero_oid\"."

const val FRAME_CONVERTER_OPENAPI_REQUEST_TRACK_NUMBER_OID_EXACTLY_ONE =
    "Rajaa haun ratanumeron OID-tunnuksen perusteella. " +
        "*Huom*: Hakua tulee rajata yhdellä kentistä \"ratanumero\", \"ratanumero_oid\"."

const val FRAME_CONVERTER_OPENAPI_REQUEST_LOCATION_TRACK =
    "Rajaa haun sijaintiraiteen tunnuksen perusteella."

const val FRAME_CONVERTER_OPENAPI_REQUEST_LOCATION_TRACK_OID =
    "Rajaa haun sijaintiraiteen OID-tunnuksen perusteella."

const val FRAME_CONVERTER_OPENAPI_REQUEST_LOCATION_TRACK_TYPE =
    "Rajaa haun sijaintiraidetyypin perusteella. Sallitut arvot: \"pääraide\", \"sivuraide\", \"turvaraide\", \"kujaraide\"."

const val FRAME_CONVERTER_OPENAPI_REQUEST_FEATURE_GEOMETRY =
    "GeoJSON-tulos sisältää geometry-kentässä hakutuloksen geometriatiedot."

const val FRAME_CONVERTER_OPENAPI_REQUEST_FEATURE_BASIC =
    "GeoJSON-tulos sisältää properties-kentässä x- ja y-koordinaatit sekä valimatka-kentän."

const val FRAME_CONVERTER_OPENAPI_REQUEST_FEATURE_DETAILS =
    "GeoJSON-tulos sisältää properties-kentässä kentät: ratanumero, ratanumero_oid, sijaintiraide, " +
        "sijaintiraide_oid, sijaintiraide_kuvaus, sijaintiraide_tyyppi, ratakilometri, ratametri, ratametri_desimaalit."

// Response-specific field descriptions

const val FRAME_CONVERTER_OPENAPI_RESPONSE_IDENTIFIER =
    "Pyynnössä annettu tunniste."

const val FRAME_CONVERTER_OPENAPI_RESPONSE_DISTANCE =
    "Etäisyys annetusta koordinaatista lähimpään raiteeseen metreinä."

const val FRAME_CONVERTER_OPENAPI_RESPONSE_TRACK_NUMBER =
    "Ratanumeron nimi."

const val FRAME_CONVERTER_OPENAPI_RESPONSE_TRACK_NUMBER_OID =
    "Ratanumeron OID-tunniste."

const val FRAME_CONVERTER_OPENAPI_RESPONSE_LOCATION_TRACK =
    "Sijaintiraiteen tunniste."

const val FRAME_CONVERTER_OPENAPI_RESPONSE_LOCATION_TRACK_OID =
    "Sijaintiraiteen OID-tunniste."

const val FRAME_CONVERTER_OPENAPI_RESPONSE_LOCATION_TRACK_DESCRIPTION =
    "Sijaintiraiteen kuvaus."

const val FRAME_CONVERTER_OPENAPI_RESPONSE_LOCATION_TRACK_TYPE =
    "Sijaintiraiteen tyyppi."

const val FRAME_CONVERTER_OPENAPI_RESPONSE_TRACK_METER_DECIMALS =
    "Rataosoitteen ratametrin desimaaliosuus."

const val FRAME_CONVERTER_OPENAPI_RESPONSE_ERRORS =
    "Lista virheistä, jotka estivät muunnoksen suorittamisen."

// Operation summaries and descriptions

const val FRAME_CONVERTER_OPENAPI_COORDINATE_TO_TRACK_ADDRESS_SINGLE_SUMMARY =
    "Yksittäismuunnos koordinaatista rataosoitteeseen"

const val FRAME_CONVERTER_OPENAPI_COORDINATE_TO_TRACK_ADDRESS_SINGLE_DESCRIPTION =
    "Palauttaa hakuehtoihin täsmäävän ja annettua koordinaattisijaintia lähinnä olevan raiteen ja rataosoitteen " +
        "raiteella. Mikäli annetuilla syötteillä ei löydy sijaintia, palautetaan virhe."

const val FRAME_CONVERTER_OPENAPI_COORDINATE_TO_TRACK_ADDRESS_BATCH_SUMMARY =
    "Erämuunnos koordinaateista rataosoitteisiin"

const val FRAME_CONVERTER_OPENAPI_COORDINATE_TO_TRACK_ADDRESS_BATCH_DESCRIPTION =
    "Tämä toiminnallisuus on muutoin sama kuin yhden koordinaattisijainnin muuntaminen rataosoitteeksi, " +
        "mutta tässä toiminnossa voi yhdessä HTTP-pyynnössä välittää useampia muunnospyyntöjä kerrallaan. " +
        "Usean muunnospynnön suorittaminen kerralla on tehokkaampaa kuin pyyntöjen suorittaminen erikseen. " +
        "Erämuunnos hyväksyy korkeintaan 1000 muunnospyyntöä kerrallaan. Erämuunnoksessa yleiset syötteet " +
        "annetaan URL-kyselyparametreina ja muunnospyyntökohtaiset syötteet HTTP-pyynnön sisältönä, JSON-muotoisena taulukkona."

const val FRAME_CONVERTER_OPENAPI_TRACK_ADDRESS_TO_COORDINATE_SINGLE_SUMMARY =
    "Yksittäismuunnos rataosoitteesta koordinaatteihin"

const val FRAME_CONVERTER_OPENAPI_TRACK_ADDRESS_TO_COORDINATE_SINGLE_DESCRIPTION =
    "Palauttaa rataosoitetta vastaavat koordinaattisijainnit kaikille niille raiteille, joilla kyseinen " +
        "rataosoite on olemassa ja jotka täsmäävät muihin annettuihin hakuehtoihin. " +
        "Mikäli yhtään sijaintia ei löydy, palautetaan virhe."

const val FRAME_CONVERTER_OPENAPI_TRACK_ADDRESS_TO_COORDINATE_BATCH_SUMMARY =
    "Erämuunnos rataosoitteista koordinaatteihin"

const val FRAME_CONVERTER_OPENAPI_TRACK_ADDRESS_TO_COORDINATE_BATCH_DESCRIPTION =
    "Tämä toiminnallisuus on muutoin sama kuin yhden rataosoitesijainnin muuntaminen koordinaattisijainneiksi, " +
        "mutta tässä toiminnossa voi yhdessä HTTP-pyynnössä välittää useampia muunnospyyntöjä kerrallaan. " +
        "Usean muunnospynnön suorittaminen kerralla on tehokkaampaa kuin pyyntöjen suorittaminen erikseen. " +
        "Erämuunnos hyväksyy korkeintaan 1000 muunnospyyntöä kerrallaan. Erämuunnoksessa yleiset syötteet " +
        "annetaan URL-kyselyparametreina ja muunnospyyntökohtaiset syötteet HTTP-pyynnön sisältönä, JSON-muotoisena taulukkona."
