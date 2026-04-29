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
Selainkäyttöliittymä toteutettu TypeScriptillä ja Reactilla. Karttatoiminnallisuus perustuu OpenLayers-kirjastoon.

### Backend
REST-rajapinta toteutettu Kotlinilla Spring-kehyksen päälle.

### Tietokanta
PostgreSQL-tietokanta PostGIS-laajennuksella geometriatietojen tallentamiseen ja hakemiseen.

## Integraatioarkkitehtuuri

Geoviite integroi useisiin ulkoisiin järjestelmiin. Ks. integraatiokumppanit tarkemmin [overview.md](./overview.md).

### Ratko-integraatio (nykytila ja tuleva muutos)
- **Nykytila (PUSH):** Geoviite työntää tietoja Ratkoon.
- **Tuleva tila (PULL):** Ratko lukee tiedot itse Geoviitteen API:sta. Integraation suunnan kääntäminen on yksi lähiajan kehitysprioriteetit.
