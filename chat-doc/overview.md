# Geoviite — Projektin yleiskuvaus

## Kuvaus

Geoviite on tietojärjestelmä, jolla ylläpidetään Suomen rataverkon ratageometrioita.

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
