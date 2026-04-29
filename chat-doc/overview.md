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
| **Ratko** | Geoviitteestä viedään raiteiden, vaihteiden ja ratanumeroiden geometriset tiedot sekä hallinnolliset perustiedot |
| **Projektivelho** | Lukee rataverkon tietoja Geoviitteestä |
| **Kuvatieto** | Lukee rataverkon tietoja Geoviitteestä |
| **Raita** | Lukee rataverkon tietoja Geoviitteestä |
| **Väylän analytiikka** | Lukee rataverkon tietoja Geoviitteestä |

## Lähiajan kehitystavoitteet

### Suuremmat tavoitteet
1. **Geoviite–Ratko-integraation suunnan kääntäminen PUSH → PULL** — Ratko alkaa lukea tietoja itse Geoviitteestä sen sijaan, että Geoviite työntää ne Ratkoon.
2. **Suunnitelmatilaisen rataverkon tarjoaminen API:sta** — Suunnitelmatilaisuus saataisiin käyttöön myös Ratkon puolella.

### Pienemmät tavoitteet
- Hallinnollisten alueiden (esim. tilirataosa) geometrian ylläpidon siirto Geoviitteeseen.

## Tekniset haasteet

- Geoviite–Ratko-integraation suunnan kääntäminen PUSH-mallista PULL-malliin.
- Tietomalli on kompleksinen — dokumentaatio tärkeää erityisesti uusien kehittäjien perehdyttämiseksi (ks. [tietomalli.md](./tietomalli.md)).
