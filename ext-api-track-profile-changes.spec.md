# Feature spec: ext-API muutos-endpoint sijaintiraiteiden pystygeometrialle

## Käyttötapaus

Toisen järjestelmän omistajana haluan pystyä lukemaan Geoviitteestä pystygeometrian muutokset, jotta voin ylläpitää pystygeometriatietoja omassa järjestelmässäni tehokkaasti.

## Ratkaisu: Uudet API:t

Toteutetaan Geoviitteeseen API, josta voi lukea raiteen pystygeometrian muutokset. Raiteen versiokohtaisen pystygeometrian lukeminen on kuvattu speksillä `ext-api-track-profile.spec.md`

## Endpoint

### URL

```
GET /paikannuspohja/v1/sijaintiraiteet/{sijaintiraide_oid}/pystygeometria/muutokset
```

Tämä noudattaa olemassa olevaa muutos-endpointtien URL-rakennetta, jossa `/muutokset` lisätään resurssien perään (vrt. `/sijaintiraiteet/{oid}/geometria/muutokset` ja `/sijaintiraiteet/{oid}/muutokset`).

Rekisteröidään myös vaihtoehtoiset polut olemassa olevan käytännön mukaisesti:
- `/geoviite/paikannuspohja/v1/sijaintiraiteet/{sijaintiraide_oid}/pystygeometria/muutokset`
- `/geoviite/dev/paikannuspohja/v1/sijaintiraiteet/{sijaintiraide_oid}/pystygeometria/muutokset`

### Kyselyparametrit

| Parametri | Pakollinen | Kuvaus |
|-----------|-----------|--------|
| `alkuversio` | Kyllä | Rataverkon UUID-tunnus, josta lähtien vertaillaan. |
| `loppuversio` | Ei | Uudemman rataverkon UUID-tunnus, johon vertailu kohdistetaan. Oletuksena käytetään uusinta versiota. |
| `koordinaatisto` | Ei | EPSG-tunnus (esim. `EPSG:3067`). Oletuksena `EPSG:3067` (ETRS-TM35FIN). |

### HTTP-paluukoodit

| Koodi | Kuvaus |
|-------|--------|
| 200 | Pystygeometrian muutokset haettiin onnistuneesti kahden rataverkon version välillä. |
| 204 | Sijaintiraiteen OID-tunnus löytyi, mutta pystygeometrian muutoksia vertailtavien versioiden välillä ei ole. Palautetaan myös silloin, kun alkuversio ja loppuversio ovat sama julkaisu. |
| 400 | Hakuargumenttien muoto virheellinen. |
| 404 | Sijaintiraidetta ei löytynyt OID-tunnuksella tai yhtä tai useampaa rataverkon versiota ei ole olemassa. |
| 500 | Palvelussa tapahtui sisäinen virhe. |

Esimerkkejä HTTP-paluukoodien arvoista tietyissä tilanteissa (vrt. `ExtLocationTrackControllerV1.getExtLocationTrackModifications`):
- **200**: Sijaintiraiteen pystygeometria on muuttunut versioiden välillä (esim. uusi suunnitelma linkitetty tai suunnitelmaa päivitetty).
- **200**: Sijaintiraiteen pystygeometria on luotu versioiden välillä (ei pystygeometriaa alkuversiossa, mutta on loppuversiossa).
- **204**: Alkuversio ja loppuversio ovat sama julkaisu.
- **204**: Sijaintiraiteen pystygeometria ei ole muuttunut versioiden välillä.
- **204**: Sijaintiraiteella ei ole pystygeometriaa kummassakaan versiossa.

### Autorisointi

Käytetään samaa `@PreAuthorize(AUTH_API_GEOMETRY)` -auktorisointia kuin muissa ext-API-controllereissa.

## Muutostunnistuksen logiikka

### Yleinen lähestymistapa

Muutostunnistus noudattaa samaa mallia kuin olemassa olevat muutos-endpointit (esim. `ExtLocationTrackServiceV1.getExtLocationTrackModifications` ja `ExtLocationTrackGeometryServiceV1.getExtLocationTrackGeometryModifications`):

1. **Versioiden vertailu**: `PublicationService.getPublicationsToCompare(alkuversio, loppuversio)` hakee `PublicationComparison`-olion.
2. **Samanlaisuustarkistus**: Jos `publications.areDifferent()` palauttaa `false`, palautetaan `null` → 204 No Content.
3. **Muuttuneiden raiteen versioiden haku**: `PublicationDao.fetchPublishedLocationTrackBetween(trackId, startMoment, endMoment)` palauttaa uusimman version, jos sijaintiraide on julkaistu versioiden välillä. Jos `null`, raiteeseen ei ole tehty muutoksia → 204 No Content.
4. **Pystygeometrian haku molemmissa versioissa**: Haetaan `VerticalGeometryListing` sekä alkuversion (`startMoment`) että loppuversion (`endMoment`) ajanhetkellä ja vertaillaan muutoksia.

### Muutoksen tunnistaminen

Pystygeometrian muutos tunnistetaan vertaamalla `VerticalGeometryListing`-listoja alkuversion ja loppuversion välillä. Vertailun jälkeen palautetaan loppuversion pystygeometriatiedot muuttuneille osoiteväleille.

Jos raide itsessään ei ole muuttunut (onko sijaintiraide julkaistu `alkuversion` ja `loppuversion` välillä -- `fetchPublishedLocationTrackBetween`), sen pystygeometriakaan ei ole voinut muuttua. Jos muutos kuitenkin on olemassa, joudutaan pystygeometriat laskemaan sekä alku- että loppuversiolla muutosvälien tuottamista varten, vastaavasti kuin vaakageometriankin kohdalla tehdään.

Muutos voidaan todeta, jos jokin seuraavista pitää paikkansa:
- Sijaintiraiteen linkitetyt geometriasuunnitelmat ovat muuttuneet (uusia, poistettuja tai päivitettyjä)
- Taitepisteiden lukumäärä on eri
- Yksittäisten taitepisteiden arvot ovat muuttuneet (korkeus, sijainti, pyöristyssäde jne.)

### Datan tuottaminen

Muutosvastauksessa palautetaan **loppuversion** pystygeometriadata muuttuneiden osoitevälien osalta, vastaavasti kuin vaakageometrian muutoksissa. Pystygeometriatiedot tuotetaan samalla `ExtLocationTrackProfileServiceV1`-palvelulla kuin perusendpointissa, mutta `PublicationComparison`-kontekstissa. Korkeusmuunnokset, osoitevälien ryhmittely ja huomiot toimivat identtisesti.

## Toteutuksen arkkitehtuuri

Noudatetaan olemassa olevaa ext-API-kerrosrakennetta ja jaetaan perusendpointin kanssa mahdollisimman paljon logiikkaa:

1. **Controller** (`ExtLocationTrackControllerV1`): Lisätään uusi muutos-endpoint olemassa olevaan controlleriin (vastaavasti kuin `/geometria/muutokset`). Käytetään samaa `ExtLocationTrackProfileServiceV1`-palvelua kuin perusendpointissa.
2. **Service** (`ExtLocationTrackProfileServiceV1`): Lisätään uusi metodi muutosten hakuun. Hyödynnetään samaa DTO-muunnoslogiikkaa kuin perusendpointissa. Uutta logiikkaa tarvitaan versiovertailun ja `PublicationDao`-kutsujen orchestraatioon.
3. **DTO-luokat**: Uusi vastaus-DTO (`ExtLocationTrackModifiedProfileResponseV1`) muutosvastausta varten. Pystygeometriaolio ja taitepisteolio ovat samoja kuin perusendpointissa.
4. **Vakiot**: `alkuversio`/`loppuversio`-vakiot ovat jo olemassa (`TRACK_LAYOUT_VERSION_FROM`/`TRACK_LAYOUT_VERSION_TO` `ExtTrackLayoutConstantsV1`-tiedostossa).

## Esimerkki JSON:t

### Vastauksen päätaso

```json
{
  "alkuversio": "e079915c-fe4a-45e8-8ad7-a54db5497d54",
  "loppuversio": "f18a226d-ab3b-49f9-9c1e-b67dc8a12e85",
  "sijaintiraide_oid": "1.2.246.578.13.123.456",
  "koordinaatisto": "EPSG:3067",
  "osoitevalit": [
    { "...pystygeometriaolio..." }
  ]
}
```

> **Huom**: Vastausrakenteessa käytetään `alkuversio`/`loppuversio`-kenttänimiä (ei `rataverkon_versio`), mikä on yhdenmukaista muiden muutosvastausten kanssa (vrt. `ExtModifiedLocationTrackResponseV1` joka käyttää `layoutVersionFrom`/`layoutVersionTo`). Alkuversion ja loppuversion UUID:t ovat eri arvoja (toisin kuin alkuperäisessä esimerkissä).

### Pystygeometriaolio

Pystygeometriaolio on identtinen perusendpointin kanssa (ks. `ext-api-track-profile.spec.md`). Yksi osoiteväli, joka sisältää yhden tai useamman taitepisteen:

```json
{
  "alku": "0193+0097.308",
  "loppu": "0341+0919.306",
  "taitepisteet": [
    {
      "pyoristyksen_alku": {
        "korkeus_alkuperäinen": 111.663,
        "korkeus_n2000": 111.663,
        "kaltevuus": -0.001200,
        "sijainti": {
          "rataosoite": "0193+0097.308",
          "x": 24483330.215,
          "y": 6822308.879
        }
      },
      "taite": {
        "korkeus_alkuperäinen": 111.657,
        "korkeus_n2000": 111.657,
        "sijainti": {
          "rataosoite": "0193+0102.003",
          "x": 24483325.710,
          "y": 6822310.200
        }
      },
      "pyoristyksen_loppu": {
        "korkeus_alkuperäinen": 111.653,
        "korkeus_n2000": 111.653,
        "kaltevuus": -0.000824,
        "sijainti": {
          "rataosoite": "0193+0106.698",
          "x": 24483321.202,
          "y": 6822311.512
        }
      },
      "pyoristyssade": 25000,
      "tangentti": 4.695,
      "kaltevuusjakso_taaksepain": {
        "pituus": 193.827,
        "suora_osa": 189.133
      },
      "kaltevuusjakso_eteenpain": {
        "pituus": 335.996,
        "suora_osa": 325.645
      },
      "paaluluku": {
        "alku": 189.133,
        "taite": 193.827,
        "loppu": 198.522
      },
      "suunnitelman_korkeusjärjestelmä": "N2000",
      "suunnitelman_korkeusasema": "Korkeusviiva",
      "huomiot": [
        {
          "koodi": "kaltevuusjakso_limittain",
          "selite": "Kaltevuusjakso on limittäin toisen jakson kanssa"
        }
      ]
    }
  ]
}
```

### Kenttien tyypit ja kuvaukset

#### Vastauksen päätaso (muutosendpoint)

| Kenttä | Tyyppi | Nullable | Kuvaus |
|--------|--------|----------|--------|
| `alkuversio` | string (UUID) | Ei | Vertailun lähtöversion tunnus. |
| `loppuversio` | string (UUID) | Ei | Vertailun kohdeversion tunnus. |
| `sijaintiraide_oid` | string (OID) | Ei | Sijaintiraiteen OID-tunnus (esim. `"1.2.246.578.13.123.456"`). |
| `koordinaatisto` | string | Ei | Käytetty koordinaatisto (esim. `"EPSG:3067"`). |
| `osoitevalit` | array | Ei | Lista pystygeometriaolioita loppuversion tilassa. Sisältää kaikki osoitevälit, joissa pystygeometria on muuttunut versioiden välillä. |

#### Muut oliot

Pystygeometriaolio, taitepisteolio, piste-oliot, sijainti-olio, kaltevuusjakso-olio, paaluluku-olio ja huomio-olio ovat identtisiä perusendpointin kanssa. Katso tarkemmat kenttäkuvaukset: `ext-api-track-profile.spec.md` → "Kenttien tyypit ja kuvaukset".
