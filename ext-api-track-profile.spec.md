# Feature spec: ext-API endpoint sijaintiraiteiden pystygeometrialle

## Käyttötapaus

Toisen järjestelmän käyttäjänä haluan nähdä ajantasaiset pystygeometriat osana muuta rataverkkoa, jotta voin suorittaa työni tehokkaammin/helpommin.

Tässä kuvataan uusi endpoint raiteen pystygeometrian nykytilan haulle.

Pystygeometrian muutosten lukeminen on kuvattu speksillä `ext-api-track-profile-changes.spec.md`

## Ratkaisu: Uudet API:t

Toteutetaan Geoviitteeseen API, josta voi lukea raiteen pystygeometriatiedot. Pystygeometriatiedot näytetään jo tietotuotteina (`VerticalGeometryListing` ja `GeometryService.getVerticalGeometryListing`), joten kyseistä toiminnallisuutta voi hyödyntää/jalostaa tätä tarkoitusta varten. Suurin ero tietotuotteeseen verrattuna on geometriasuunnitelmatietojen puuttuminen (esim. suunnitelman nimi, tiedostonimi, luontiaika, koordinaatiston nimi), koska suunnitelmien tietoja luovutetaan toistaiseksi vain operaattorin välityksellä.

## Endpoint

### URL

```
GET /paikannuspohja/v1/sijaintiraiteet/{sijaintiraide_oid}/pystygeometria
```

Tämä noudattaa olemassa olevaa ext-API URL-rakennetta, jossa `geometria`-endpoint on jo sijaintiraiteen alla (`/sijaintiraiteet/{sijaintiraide_oid}/geometria`). Vastaavasti pystygeometria tulee sen rinnalle.

Rekisteröidään myös vaihtoehtoiset polut olemassa olevan käytännön mukaisesti:
- `/geoviite/paikannuspohja/v1/sijaintiraiteet/{sijaintiraide_oid}/pystygeometria`
- `/geoviite/dev/paikannuspohja/v1/sijaintiraiteet/{sijaintiraide_oid}/pystygeometria`

### Kyselyparametrit

| Parametri | Pakollinen | Kuvaus |
|-----------|-----------|--------|
| `rataverkon_versio` | Ei | Rataverkon UUID-tunnus, johon haku kohdistetaan. Oletuksena käytetään uusinta versiota. |
| `koordinaatisto` | Ei | EPSG-tunnus (esim. `EPSG:3067`). Oletuksena `EPSG:3067` (ETRS-TM35FIN). |

### HTTP-paluukoodit

| Koodi | Kuvaus |
|-------|--------|
| 200 | Pystygeometrian haku onnistui. |
| 204 | Sijaintiraiteen OID löytyi, mutta sille ei ole pystygeometriaa annetussa versiossa (esim. ei linkitettyjä suunnitelmia). |
| 400 | Hakuargumenttien muoto virheellinen. |
| 404 | Sijaintiraidetta ei löytynyt OID-tunnuksella tai annettua rataverkon versiota ei ole olemassa. |
| 500 | Palvelussa tapahtui sisäinen virhe. |

### Autorisointi

Käytetään samaa `@PreAuthorize(AUTH_API_GEOMETRY)` -auktorisointia kuin muissa ext-API-controllereissa.

## Datan lähde ja muunnos

### Olemassa oleva infrastruktuuri

Pystygeometriatiedot tuotetaan `GeometryService.getVerticalGeometryListing(layoutContext, locationTrackId)` -metodilla, joka palauttaa `List<VerticalGeometryListing>`. Kukin `VerticalGeometryListing` vastaa yhtä taitepistettä (pyöristyskaaren kohtaa) ja sisältää:

- **start/end** (`CurvedSectionEndpoint`): pyöristyksen alku/loppu — korkeus, kaltevuus (angle), rataosoite, sijainti, paaluluku (station)
- **point** (`IntersectionPoint`): taite — korkeus, rataosoite, sijainti, paaluluku
- **radius**: pyöristyssäde
- **tangent**: tangentti
- **linearSectionForward/Backward** (`LinearSection`): kaltevuusjaksotiedot — pituus ja suoran osan pituus
- **verticalCoordinateSystem**: suunnitelman alkuperäinen korkeusjärjestelmä (`N2000`, `N60`, `N43`)
- **elevationMeasurementMethod**: korkeusasema (`TOP_OF_SLEEPER` = "Korkeusviiva", `TOP_OF_RAIL` = "Kiskon selkä")
- **overlapsAnother**: huomio — onko kaltevuusjakso limittäin toisen kanssa

### Korkeusarvojen käsittely

Korkeusarvot ilmoitetaan sekä alkuperäisessä (suunnitelman) että N2000-korkeusjärjestelmässä:

- `korkeus_alkuperäinen`: suoraan `VerticalGeometryListing`-olion korkeusarvo (suunnitelman korkeusjärjestelmässä)
- `korkeus_n2000`: muunnettu arvo N2000-järjestelmään

N2000-muunnos riippuu suunnitelman alkuperäisestä korkeusjärjestelmästä (`verticalCoordinateSystem`):
- **N2000** → sama arvo (ei muunnosta tarvita)
- **N60** → muunnetaan käyttäen `transformHeightValue`-funktiota ja kolmioverkkointerpolaatiota (`HeightTriangle`)
- **N43** → muunnosta ei tueta tällä hetkellä (`transformHeightValue` heittää `IllegalArgumentException`)
  - Ei yritetäkään muuntaa näitä, vaan palautetaan `korkeus_n2000` arvona `null` ja dokumentoidaan rajoitus
>
### Osoitevälien muodostus

Yksi sijaintiraide voi saada pystygeometriadatan useista eri suunnitelmista, jotka kattavat eri osoitevälejä raiteen pituudelta. `VerticalGeometryListing` palauttaa jo taitepistelistausta, jossa kukin taitepiste tietää oman osoitevälinsä (start.address → end.address).

Nämä taitepisteet tulee ryhmitellä yhtenäisiksi osoiteväleiksi (`osoitevalit`-lista). Taitepisteet, jotka ovat peräkkäisiä ja muodostavat katkeamattoman ketjun (edellisen loppu = seuraavan alku tai ne limittyvät), kuuluvat samaan osoiteväliin. Katko osoitevälissä (gap ilman pystygeometriaa) aloittaa uuden osoitevälin.

> **Huom**: Vastauksessa `osoitevalit` on lista, koska yhdellä raiteella voi olla pystygeometriatietoja useille erillisille osoiteväleille.

## Toteutuksen arkkitehtuuri

Noudatetaan olemassa olevaa ext-API-kerrosrakennetta:

1. **Controller** (`ExtLocationTrackControllerV1`): Lisätään uusi endpoint olemassa olevaan controlleriin (vastaavasti kuin `/geometria`). Uusi service injektoidaan controlleriin.
2. **Service** (uusi `ExtLocationTrackProfileServiceV1`): Vastaa liiketoimintalogiikasta — hakee `VerticalGeometryListing`-datan, muuntaa N2000-korkeuksiksi, ryhmittelee osoiteväleiksi ja rakentaa vastaus-DTO:t.
3. **DTO-luokat** (uudet `Ext*V1`-luokat): Vastaus- ja datamallien JSON-serialisointi `@JsonProperty`-annotaatioilla suomenkielisillä kenttänimillä.
4. **Vakiot** (`ExtTrackLayoutConstantsV1`): Lisätään uudet JSON-kenttänimet vakioiksi (esim. `VERTICAL_GEOMETRY = "pystygeometria"`, `BREAK_POINTS = "taitepisteet"` jne.).

Ei tarvita uusia DAO-luokkia — käytetään olemassa olevia `GeometryService`- ja `PublicationService`-palveluja.

## Esimerkki JSON:t

### Vastauksen päätaso

```json
{
  "rataverkon_versio": "e079915c-fe4a-45e8-8ad7-a54db5497d54",
  "sijaintiraide_oid": "1.2.246.578.13.123.456",
  "koordinaatisto": "EPSG:3067",
  "osoitevalit": [
    { "...pystygeometriaolio..." },
    { "...pystygeometriaolio..." }
  ]
}
```

### Pystygeometriaolio

Yksi osoiteväli, joka sisältää yhden tai useamman taitepisteen:

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

#### Vastauksen päätaso

| Kenttä | Tyyppi | Nullable | Kuvaus |
|--------|--------|----------|--------|
| `rataverkon_versio` | string (UUID) | Ei | Haetun rataverkon version tunnus |
| `sijaintiraide_oid` | string (OID) | Ei | Sijaintiraiteen OID-tunnus (esim. `"1.2.246.578.13.123.456"`) |
| `koordinaatisto` | string | Ei | Käytetty koordinaatisto (esim. `"EPSG:3067"`) |
| `osoitevalit` | array | Ei | Lista pystygeometriaolioita. Tyhjä lista, jos pystygeometriaa ei löydy. |

#### Pystygeometriaolio (osoiteväli)

| Kenttä | Tyyppi | Nullable | Kuvaus |
|--------|--------|----------|--------|
| `alku` | string | Kyllä | Osoitevälin alkurataosoite (esim. `"0193+0097.308"`). Null, jos geocoding ei onnistu. |
| `loppu` | string | Kyllä | Osoitevälin loppurataosoite. Null, jos geocoding ei onnistu. |
| `taitepisteet` | array | Ei | Lista taitepisteolioita tässä osoitevälissä. |

#### Taitepisteolio

| Kenttä | Tyyppi | Nullable | Kuvaus |
|--------|--------|----------|--------|
| `pyoristyksen_alku` | object | Ei | Pyöristyskaaren alkupiste (sisältää korkeuden, kaltevuuden ja sijainnin). |
| `taite` | object | Ei | Taitepiste / leikkauspiste (ei kaltevuutta). |
| `pyoristyksen_loppu` | object | Ei | Pyöristyskaaren loppupiste (sisältää korkeuden, kaltevuuden ja sijainnin). |
| `pyoristyssade` | number | Ei | Pyöristyssäde metreinä (kokonaisluku). Lähde: `VerticalGeometryListing.radius`. |
| `tangentti` | number | Kyllä | Tangentin pituus metreinä (3 desimaalia). Lähde: `VerticalGeometryListing.tangent`. |
| `kaltevuusjakso_taaksepain` | object | Ei | Edellisen kaltevuusjakson tiedot. |
| `kaltevuusjakso_eteenpain` | object | Ei | Seuraavan kaltevuusjakson tiedot. |
| `paaluluku` | object | Ei | Paalulukuarvot (etäisyys raiteen alusta metreinä). |
| `suunnitelman_korkeusjärjestelmä` | string | Kyllä | Suunnitelman alkuperäinen korkeusjärjestelmä: `"N2000"`, `"N60"` tai `"N43"`. Lähde: `VerticalGeometryListing.verticalCoordinateSystem`. |
| `suunnitelman_korkeusasema` | string | Kyllä | Korkeusasema: `"Korkeusviiva"` (TOP_OF_SLEEPER) tai `"Kiskon selkä"` (TOP_OF_RAIL). Lähde: `VerticalGeometryListing.elevationMeasurementMethod`. Käytetään suomenkielistä käännöstä. |
| `huomiot` | array | Ei | Lista huomioita. Tyhjä lista, jos ei huomioita. |

#### Piste-olio (pyoristyksen_alku / pyoristyksen_loppu)

| Kenttä | Tyyppi | Nullable | Kuvaus |
|--------|--------|----------|--------|
| `korkeus_alkuperäinen` | number | Ei | Korkeus suunnitelman alkuperäisessä korkeusjärjestelmässä (3 desimaalia). |
| `korkeus_n2000` | number | Kyllä | Korkeus N2000-järjestelmässä (3 desimaalia). Null, jos muunnos ei ole mahdollinen (esim. N43). |
| `kaltevuus` | number | Ei | Kaltevuus (gradientti) desimaalilukuna (6 desimaalia). Lähde: `CurvedSectionEndpoint.angle`. |
| `sijainti` | object | Ei | Sijainti-olio. |

#### Piste-olio (taite)

Kuten yllä, mutta **ilman** `kaltevuus`-kenttää (taitepiste on pyöristyskaaren huippu, jossa kaltevuutta ei ilmoiteta).

#### Sijainti-olio

| Kenttä | Tyyppi | Nullable | Kuvaus |
|--------|--------|----------|--------|
| `rataosoite` | string | Kyllä | Rataosoite muodossa `"KM+METRIT"` (esim. `"0193+0097.308"`). Null, jos geocoding ei onnistu. |
| `x` | number | Kyllä | X-koordinaatti (E) valitussa koordinaatistossa. Null, jos sijaintia ei voida laskea. |
| `y` | number | Kyllä | Y-koordinaatti (N) valitussa koordinaatistossa. Null, jos sijaintia ei voida laskea. |

#### Kaltevuusjakso-olio

| Kenttä | Tyyppi | Nullable | Kuvaus |
|--------|--------|----------|--------|
| `pituus` | number | Kyllä | Kaltevuusjakson kokonaispituus (paalulukuero edelliseen/seuraavaan taitepisteeseen, 3 desimaalia). Lähde: `LinearSection.stationValueDistance`. |
| `suora_osa` | number | Kyllä | Kaltevuusjakson suoran osuuden pituus metreinä (3 desimaalia). Lähde: `LinearSection.linearSegmentLength`. |

#### Paaluluku-olio

| Kenttä | Tyyppi | Nullable | Kuvaus |
|--------|--------|----------|--------|
| `alku` | number | Kyllä | Pyöristyksen alkupisteen paaluluku (etäisyys metreinä raiteen alusta, 3 desimaalia). |
| `taite` | number | Kyllä | Taitepisteen paaluluku. |
| `loppu` | number | Kyllä | Pyöristyksen loppupisteen paaluluku. |

> **Huom**: Paalulukuarvot lasketaan `alignmentStartStation`/`alignmentPointStation`/`alignmentEndStation` -arvoista, jotka ovat sijaintiraiteen (layout track) etäisyyksiä — eivät suunnitelman (geometry alignment) paalulukuja.

#### Huomio-olio

| Kenttä | Tyyppi | Nullable | Kuvaus |
|--------|--------|----------|--------|
| `koodi` | string | Ei | Koneluettava tunniste. |
| `selite` | string | Ei | Ihmisluettava kuvaus (suomeksi). |

Tunnetut huomiokoodit:
- `kaltevuusjakso_limittain`: Kaltevuusjakso on limittäin toisen jakson kanssa. Lähde: `VerticalGeometryListing.overlapsAnother`.

