# Rajapintapalvelu

Geoviitteeseen on muodostettu erillinen rajapintapalvelu, jonka voi suorittaa erillisenä HTTP-palveluna taikka
samanaikaisesti Geoviitteen normaalin backend-palvelun yhteydessä. Rajapintapalvelun käynnistystä varten sovelluksen
aloitukseen käytetään Spring-profiilia `ext-api`, jota voi hyödyntää normaalin `backend`-profiilin kanssa
samanaikaisesti taikka erikseen.

## Ohjelmistorakenne

Erilliseen rajapintapalveluun liittyvä koodi on erotettu `fi.fta.geoviite.api`-paketin alle, backend-palvelun
hyödyntämän`fi.fta.geoviite.infra`-paketin rinnalle.

## Tietokanta

Suoritettaessa pelkkää rajapintapalvelua voidaan hyödyntää ainoastaan lukuoikeuksellista tietokantakäyttäjää Geoviitteen
tietokantaan. Tämä tarkoittaa, ettei rajapintapalvelu tee lainkaan kirjoitusoperaatioita tietokantaan, mutta voi
hyödyntää samaa tietokantaa kuin normaalikin Geoviitteen backend-palvelu. Rajapintapalvelun tietokantakäyttäjän ollessa
lukuoikeusrajoitettu, tulee Geoviitteen sovellusversion päivittymisen
yhteydessä varmistaa, että esimerkiksi migraatiot ajetaan normaalin backend-palvelun toimesta ennen
rajapintapalvelun käynnistystä uudella sovellusversiolla.

# Viitekehysmuunnin

Viitekehysmuuntimella tarkoitetaan rajapintapalvelun osaa, joka Geoviitteen yhteydessä tarkoittaa rataverkon
geokoodaukseen sekä käänteiseen geokoodaukseen liittyvien muunnosoperaatioiden rajapintaa. Geokoodaukseen liittyvillä
muunnosoperaatioilla tarkoitetaan esimerkiksi rataosoitteen muuntamista koordinaatiksi halutussa
koordinaattijärjestelmässä taikka päinvastoin.

Viitekehysmuunnin on
käytettävissä [vapaan rajapinnan kautta](https://avoinapi.vaylapilvi.fi/rata-vkm/swagger-ui/index.html), joskin sille
on myös käyttörajoitettu ohjaus. Viitekehysmuuntimen ohjauksia sekä rajapinta-avaimia hallinnoi Väylän SOA-toimisto.

## Versiointirakenne

Viitekehysmuunnin on versioitu HTTP-rajapintaosoitteiden ja viitekehysmuuntimeen liittyvän koodin sekä
tietorakenteiden tasoilla. Viitekehysmuunnin hyödyntää kuitenkin samoja versioimattomia Geoviitteen sisäisiä
palvelurajapintoja normaalin Geoviitteen backend-palvelun tavoin.

Periaatteellisesti viitekehysmuuntimen aiempaan versioon voidaan tehdä taaksepäin yhteensopivia muutoksia. Tämä
tarkoittaa, että esimerkiksi GeoJSON-vastauksiin voidaan lisätä ominaisuuskenttiä sekä uusia HTTP-rajapintoja ja
suodatusmahdollisuuksia voidaan lisätä viitekehysmuuntimen aiempaan versioon, kunhan lisäykset ovat yhteensopivia
aiemman rajapintaversion kanssa. Tätä periaatetta voi verrata minor-versioiden päivitykseen
hyödynnettäessä [semanttista versiointirakennetta](https://semver.org/). Samalla kuvattu periaate kuitenkin tarkoittaa,
ettei esimerkiksi aiempia kenttien nimiä voida uudelleennimetä, eikä viitekehysmuuntimen toimintalogiikkaa tulisi
muuttaa ilman uuden versiorakenteen luomista. Koska viitekehysmuunnin kuitenkin hyödyntää Geoviitteen sisäisiä,
versioimattomia rajapintoja, tulisi viitekehysmuuntimen testien ylläpitää aiemman muunnoslogiikan pysyvyyttä.

Uusi taaksepäin yhteensopimaton versio viitekehysmuuntimesta voidaan muodostaa luomalla uudet tiedostot
viitekehysmuuntimen Controller-, Service-, Data- sekä Error-rakenteista. Tämä on oletettavasti yksinkertaisinta
todennäköisesti luokkaperinnällä aiemmasta versiosta. Vastaavasti testit tulisi kopioida aiemmasta versioista uutta
versiota varten, joskin näihin voi tehdä myös logiikkamuutoksia uuden version yhteydessä.

## Käyttäjäkonteksti lokituksissa

Viitekehysmuuntimen käyttäjiä ei tunnisteta samalla tavoin kuin Geoviitteen normaalin backend-palvelun käyttäjiä. Tämä
tarkoittaa, että julkisen rajapinnan käyttäjänä näkyy HTTP-kutsujen konteksteissa pelkästään  `api-public`. Vastaavasti
rajapinta-avaimellisilla käyttäjillä näkyy `api-private`.

## Swagger ja OpenAPI

Viitekehysmuuntimelle on konfiguroitu Swagger-käyttöliittymä sekä muodostettu OpenAPI-kuvaus. Swagger-käyttöliittymän
polku on oletuksena `/rata-vkm/swagger-ui/index.html`.

Swagger-käyttöliittymä hyödyntää staattisesti määriteltyä OpenAPI-kuvausta. Syy staattisen OpenAPI-kuvauksen käyttöön
dynaamisesti muodostetun sijaan on ollut rajoite pyyntö- sekä vastausesimerkkien viittausrajoitteista, ulkoisiin
.json-tiedostoihin ei ole ollut mahdollista viitata. Viitekehysmuuntimen OpenAPI-määritelmä sekä JSON-esimerkkipyynnöt
ja vastaukset löytyvät resurssipolusta `infra/src/main/resources/static/frameconverter`.
