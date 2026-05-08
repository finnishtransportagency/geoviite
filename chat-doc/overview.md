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
| Nykyinen tila | Aktiivinen kehitys jatkuu |

## Keskeisimmät toiminnallisuudet

- Yhtenäisen rataverkon geometrioiden ylläpito
- Rataverkon tiedoista hakeminen ja raportointi
- Geometriasuunnitelmakirjaston ylläpito

## Sidosryhmät

### Tilaaja
- **Väylävirasto**

### Loppukäyttäjät
- **Geoviite-operaattorit** — ylläpitävät rataverkon tietoja
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
