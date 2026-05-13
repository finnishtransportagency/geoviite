# Geoviite — Projektin yleiskuvaus

## Kuvaus

Geoviite on tietojärjestelmä, jolla ylläpidetään Suomen rataverkon ratageometrioita.

## Projektin syntyhistoria

Ennen Geoviitettä ratageometrian hallinnasta vastasi **Sweco Oy**. Järjestelmä päätettiin rakentaa kahdesta syystä:

1. **Hallinnan siirto tilaajalle** — Väylävirasto halusi ottaa ratageometrian hallinnan omiin käsiinsä Swecon sijaan.
2. **Datan laadun parantaminen** — Swecon järjestelmässä ei ollut yhtenäistä paikannuspohjaa: geometriaraiteiden osuudet saattoivat olla limittäin tai niiden väliin jäi aukkoja, ja Ratkoon toimitettu pisteviiva-aineisto sisälsi siksak-kuvioita ja pitkiä pistevälejä.

Projektin ensimmäisessä vaiheessa toteutettiin kriittisimmät toiminnallisuudet palvelun hallinnansiirtoa varten:
- Ratageometriasuunnitelmien (inframodel) rekisteri
- Geometrioiden linkitys yhtenäiseen paikannuspohjaan
- Paikannuspohjan tietojen välittäminen Ratkoon

Swecon käyttäjät olivat Swecon omia työntekijöitä. Geoviite-operaattorit valittiin Väyläviraston järjestämällä kilpailutuksella.

### Ramboll asiantuntijatukena

Geoviite-projektissa oli alusta alkaen mukana väyläpuolen (rata + tie) asiantuntijoita **Rambolilta**. He auttoivat tiimiä ymmärtämään ratageometrian hallinnan prosesseja ja dataa sekä koestivat tiimin tuottamia prototyyppejä.

### Pohjadatan alkuperä

Geoviitteen alkuperäinen pohjadata koottiin kahdesta lähteestä ja siihen tehtiin automatisoitua datan korjausta (mm. siksak-kuvioiden poisto):

| Tietolähde | Mitä saatiin |
|---|---|
| **Ratko** | Ratanumerot, pituusmittauslinjojen pisteviiva-geometria, raiteet, raiteiden pisteviiva-geometria, vaihteet |
| **Sweco** | Geometriasuunnitelmat (inframodel-tiedostot), geometrian linkittyminen pisteviiva-aineistoon |

Tasakilometripisteet pääteltiin Ratkon pisteviiva-aineistosta.

Ratkon tietomallin katsottiin soveltuvan hieman paremmin Geoviitteen paikannuspohjan pohjaksi kuin Swecon tietomallin, koska Ratkossa raiteen geometria oli jo jatkuva (vaikka sisälsikin virheitä).

> **Huom:** Suuri osa Geoviitteen geometrian linkityksestä on Swecon *paikannuspalvelun* geometriasuunnitelmista (epätarkempia), ei *geometriapalvelun* suunnitelmista (alkuperäiset, tarkat). Tästä seuraa, että paikannuspohjan linkitystietojen perusteella ei voida aina suoraan jäljittää alkuperäistä tarkkaa geometriasuunnitelmaa.

## Tila

| Vaihe | Ajankohta |
|---|---|
| Kehitys alkanut | tammikuu 2021 |
| Tuotantoon siirtyminen | marraskuu 2022 |
| Toimittajan vaihto | kevät 2026 (Solita → TwoDays) |
| Nykyinen tila | Aktiivinen kehitys jatkuu |

## Kehitystiimi ja -prosessi

### Toimittaja

Geoviitteen kehitystiimi on valittu Väyläviraston kilpailutuksella. Tiimissä on ollut keskimäärin 4–5 henkilöä:

| Rooli | Huomio |
|---|---|
| Projektipäällikkö | Ainoa puhtaasti ei-tekninen rooli |
| Tekninen arkkitehti | Tekee myös kehitystä |
| UX / PO | Tekee myös kehitystä |
| Kehittäjät | — |

- **2021–2026:** Toimittajana **Solita Oy**
- **2026→:** Toimittajana **TwoDays Oy** (uusi kilpailutus keväällä 2026)

### Kehitysprosessi

Tiimi käyttää **Scrumia väljästi sovellettuna**. Sprintit ovat nykyisin neljän viikon mittaisia (aiemmin kolme viikkoa).

| Seremoniat | Kuvaus |
|---|---|
| **Suunnittelupalaveri** | Sovitaan prioriteetit ja konkreettiset tavoitteet; asiakas mukana. Sprintille priorisoidaan usein enemmän työtä kuin ehditään, jotta kaikki työ kohdistuisi tärkeimpiin asioihin. Työmäärät arvioidaan vain karkeasti. |
| **Refinement** | Tarpeen mukaan; käsitellään tulevia tarpeita tarkemmalle tasolle ja muodostetaan yhteinen ymmärrys. |
| **Daily** | Lyhyt päivittäinen tilannekatsaus: mitä tehty, mitä seuraavaksi, mahdolliset ongelmat ja julkaisun tilanne. |
| **Katselmointi (Review)** | Sprintin päätteeksi; esitellään aikaansaannokset asiakkaalle ja operaattoreille. |
| **Retro** | Sprintin lopuksi; käydään läpi työtavat ja hyvinvointi. Työtavat ovat asettuneet vakiintuneiksi projektin alkuvuosien jälkeen. |

**Asiakkaan rooli:** Väylävirasto ohjaa kehitystä asettamalla prioriteetit ja osallistumalla suunnittelupalavereihin. Asiakas on myös aktiivisesti yhteydessä sprintin aikana uusista tarpeista ja kysymyksiin vastaamisessa.

## Keskeisimmät toiminnallisuudet

- Yhtenäisen rataverkon geometrioiden ylläpito
- Rataverkon tiedoista hakeminen ja raportointi
- Geometriasuunnitelmakirjaston ylläpito

## Sidosryhmät

### Tilaaja
- **Väylävirasto**

### Loppukäyttäjät
- **Geoviite-operaattorit** — ylläpitävät paikannuspohjaa ja geometriarekisteriä; noin 5 henkilöä kerrallaan, valitaan Väyläviraston kilpailutuksella muutamaksi vuodeksi. Kaksi ensimmäistä kautta (2022–) operaattorina on toiminut **Welado Oy**. Operaattoreilla tulee olla hyvä tietämys ratageometriasta ja siihen liittyvistä prosesseista. He käyttävät myös Ratko-järjestelmää tietojen oikeellisuuden tarkistamiseen ja päätöksenteon tueksi.
- **Konsulttikäyttäjät** — lukuoikeuksilla, tarkastavat rataverkon tietoja

### Integraatiokumppanit

| Järjestelmä | Integraation kuvaus |
|---|---|
| **Ratko** | Geoviitteestä viedään raiteiden, vaihteiden ja ratanumeroiden geometriset tiedot sekä hallinnolliset perustiedot (nykyinen PUSH-malli; tavoitteena PULL) |
| **Projektivelho** | Lukee rataverkon tietoja Geoviitteestä |
| **Kuvatieto** | Lukee rataverkon tietoja Geoviitteestä |
| **Raita** | Lukee rataverkon tietoja Geoviitteestä |
| **Väylän analytiikka** | Lukee rataverkon tietoja Geoviitteestä; osallistuu RINF-raportointiketjuun |
| **Paikkatietopalvelu PTP** | Osallistuu RINF-raportointiketjuun |
| **RINF** | EU:n rataverkon infrastruktuurirekisteri (Register of Infrastructure). Geoviitteen tietoja raportoidaan RINF:iin EU-säädösten velvoittamana Väylän analytiikan ja PTP:n kautta. |

## Lähiajan kehitystavoitteet

### Suuremmat tavoitteet
1. **Geoviite–Ratko-integraation suunnan kääntäminen PUSH → PULL** — Ratko alkaa lukea tietoja itse Geoviitteestä sen sijaan, että Geoviite työntää ne Ratkoon.
2. **Suunnitelmatilaisen rataverkon tarjoaminen API:sta** — Suunnitelmatilaisuus saataisiin käyttöön myös Ratkon puolella.

### Pienemmät tavoitteet
- **Tilirataosan alueen ylläpito Geoviitteessä:** Tilirataosa on Ratkossa ylläpidettävä polygonimainen alue, joka rajaa yhtenä kokonaisuutena käsiteltävän joukon raiteita. Kun raiteen geometriaa muokataan Geoviitteessä, raide voi siirtyä tilirataosan alueen ulkopuolelle. Alueen korjaaminen Ratkoon kestää nykyisellä prosessilla viikkoja. Tavoitteena on mahdollistaa tilirataosan alueen muokkaus Geoviitteessä samanaikaisesti geometrian muokkaamisen kanssa, jolloin Ratko lukisi päivitetyn alueen suoraan Geoviitteestä.

## Tekniset haasteet

- Geoviite–Ratko-integraation suunnan kääntäminen PUSH-mallista PULL-malliin.
- Tietomalli on kompleksinen — dokumentaatio tärkeää erityisesti uusien kehittäjien perehdyttämiseksi (ks. [tietomalli.md](./tietomalli.md)).
