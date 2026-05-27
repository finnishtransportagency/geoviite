# Geoviite — Tekninen arkkitehtuuri

## Yleiskuva

Geoviite on kontitettu web-sovellus, joka suoritetaan AWS-pilviympäristössä.

## Teknologiapino

| Kerros | Teknologia |
|---|---|
| **Frontend** | TypeScript, React, OpenLayers |
| **Backend** | Kotlin, Spring |
| **Tietokanta** | PostgreSQL + PostGIS |
| **Infrastruktuuri** | Docker (konttipohjainen), AWS |

## Komponentit

### Frontend
Selainkäyttöliittymä toteutettu TypeScriptillä ja Reactilla. Karttatoiminnallisuus perustuu OpenLayers-kirjastoon. Karttanäkymässä käytetään TM35FIN-koordinaatistoa.

### Backend
REST-rajapinta toteutettu Kotlinilla Spring-kehyksen päälle.

### Tietokanta
PostgreSQL-tietokanta PostGIS-laajennuksella geometriatietojen tallentamiseen ja hakemiseen.

### Koordinaatistomuunnokset

Koordinaattien muuntamiseen koordinaatistosta toiseen käytetään **GeoTools**-kirjastoa. KKJ-koordinaattien muuntamisessa hyödynnetään lisäksi korjausparametreja sisältävää **kolmioverkkoa** tarkkuuden parantamiseksi. Ks. koordinaatistot tarkemmin [tietomalli.md — Koordinaatistot](./tietomalli.md#koordinaatistot).

## Rajapinnat (API)

Geoviitteessä on kolme erillistä API-kokonaisuutta:

### 1. Käyttöliittymä-API (sisäinen)

- **Tarkoitus:** Geoviitteen oman selainkäyttöliittymän käyttämä rajapinta
- **Tyyli:** REST (pääasiassa)
- **Data:** Optimoitu käyttöliittymän tarpeisiin
- **Kirjoitusoperaatiot:** Tietojen päivittäminen tapahtuu tämän rajapinnan kautta
- **Autentikaatio:** Väyläpilven käyttäjätili + Geoviite-rooli

### 2. Viitekehysmuunnin (VKM)

- **Tarkoitus:** Koordinaattisijaintien muuntaminen rataosoitteiksi ja takaisin (geokoodaus)
- **Tyyli:** REST
- **Versiointi:** Semantic Versioning
- **Autentikaatio:**
  - Julkinen versio: ei tunnistautumista; kutsutiheyttä rajoitettu
  - API-avainversio: huomattavasti sallivammat rajoitukset; API-avaimen saa Väylävirastolta
- **Geokoodauksen dokumentaatio:** https://github.com/finnishtransportagency/geoviite/blob/main/doc/geokoodaus.md

### 3. Ulkoinen perustietorajapinta (ext-api)

- **Tarkoitus:** Muiden järjestelmien käyttöön tarkoitettu lukurajapinta
- **Sisältö:** Lähes kaikki virallisen paikannuspohjan tiedot
- **Historiatiedot:** Tiedot luettavissa rataverkon versiokohtaisesti
- **Muutosrajapinnat:** Useimmista tietotyypeistä saatavilla muutokset kahden version (tai versiovälin) väliltä → mahdollistaa tehokkaan synkronoinnin ulkoiseen järjestelmään
- **Versiointi:** Semantic Versioning
- **Autentikaatio:**
  - Ensisijainen: API-avain (saatavissa Väylävirastolta)
  - Vaihtoehto: Väyläviraston käyttäjätili + jokin Geoviite-oikeus (käytännöllinen testaamiseen)

### Rataverkon versiointi

Rataverkon versio syntyy aina, kun viralliseen paikannuspohjaan julkaistaan muutos. ext-apin kautta voidaan:
- Hakea käsitteiden (sijaintiraide, vaihde jne.) tiedot tietyssä versiossa
- Kysyä muutokset kahden version välillä

## Käyttäjäroolit

Käyttöliittymään ja API:hin pääsy perustuu Väyläpilven käyttäjätileihin ja niihin liitettyihin Geoviite-rooleihin. Rooleja on neljä:

| Rooli | Kirjoitusoikeus | Lukuoikeudet | Huomio |
|---|---|---|---|
| **Operaattori** | ✅ Kaikki | Kaikki tiedot | Ainoa rooli, jolla voi muuttaa Geoviitteen sisältämiä tietoja. Kehittäjillä oletuksena kehitys-/testi-/paikallisympäristössä. |
| **Kehitystiimi** | ❌ | Lähes kaikki, paitsi esikatselumuutokset | Esikatselutietojen puuttuminen on tekninen yksinkertaistus, ei tietoturvasyy. |
| **Virastokäyttäjä** | ❌ | Virallinen paikannuspohja (ml. geometriatiedot); ei geometriasuunnitelmien latauksia, ei luonnostilaisia tietoja | Luonnostila piilotetaan, jotta muokkauksen alla olevat tiedot eivät sekoita käyttäjää. |
| **Konsultti** | ❌ | Virallinen paikannuspohja perustiedot; ei geometriasuunnitelmia eikä tietotuotteita | Suppein rooli. |

## Tietotuotteet

Tietotuotteet ovat jalostettuja koosteita/raportteja rataverkon tiedoista. Vaatii vähintään virastokäyttäjän roolin. Operaattori voi toimittaa tietotuotteiden tulosteita (CSV) tietopyyntöjen vastauksena.

| Tietotuote | Sisältö | Käyttötapauksia |
|---|---|---|
| **Elementtiluettelo** | Sijaintiraiteisiin linkitetyt tai geometriasuunnitelman geometriaelementit | Pienisäteisten kaarteiden haku (esim. pitkien kuljetusten reittisuunnittelu) |
| **Pystygeometria** | Taitepisteet ja kaltevuusjaksot sijaintiraiteittain tai suunnitelmittain | Jarrupainolaskelmat, junien seisonta-alueiden määritys |
| **Ratakilometrien pituudet** | Ratakilometrien pituudet | Tilastointi |

> API tulee korvaamaan osan tietotuotteiden tarpeesta, kun käyttötapauksiin osallistuu toinen järjestelmä. Ihmiskäyttöön tietotuotteiden CSV-tulosteet ovat edelleen käytännöllisiä.

## Integraatioarkkitehtuuri

Geoviite integroi useisiin ulkoisiin järjestelmiin. Ks. integraatiokumppanit tarkemmin [overview.md](./overview.md).

### Ratko-integraatio (nykytila ja tuleva muutos)
- **Nykytila (PUSH):** Geoviite työntää tietoja Ratkoon.
- **Tuleva tila (PULL):** Ratko lukee tiedot itse Geoviitteen API:sta. Integraation suunnan kääntäminen on yksi lähiajan kehitysprioriteetit.
