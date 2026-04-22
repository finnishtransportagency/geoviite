# Feature spec: ext-API endpoint sijaintiraiteiden pystygeometrialle

## Käyttötapaus

Toisen järjestelmän käyttäjänä haluan nähdä ajantasaiset pystygeometriat osana muuta rataverkkoa, jotta voin suorittaa työni tehokkaammin/helpommin.

Tässä kuvataan uusi endpoint raiteen pystygeometrian nykytilan haulle.

Pystygeometrian muutosten lukeminen on kuvattu speksillä `ext-api-track-profile-changes.spec.md`

## Ratkaisu: Uudet API:t

Toteutetaan Geoviitteeseen API, josta voi lukea raiteen pystygeometriatiedot. Pystygeometriatiedot näytetään jo tietotuotteina, joten kyseistä toiminnallisuutta voi hyödyntää/jalostaa tätä tarkoitusta varten. Suurin ero tietotuotteeseen verrattuna on geometriasuunnitelmatietojen puuttuminen, koska suunnitelmien tietoja luovutetaan toistaiseksi vain operaattorin välityksellä.

Muut erityishuomiot:
- URL tyyliin: `/paikannuspohja/v1/sijaintiraiteet/<raide-oid>/pystygeometria?alkuversio=`
  - URL tarkistettava muiden APIen kanssa yhdenmukaiseksi
- Korkeusarvot ilmoitetaan sekä alkuperäisessä (eli suunnitelman) että N2000 korkeusjärjestelmässä
- "huomiot" -kentässä näytetään myös huomioon liittyvä koodi, jotta automaattinen käsittely olisi luotettavampaa 

## Esimerkki JSON:t

Yhden raiteen pystygeometrian datan malli JSON-muodossa:

```
{
  "rataverkon_versio": "e079915c-fe4a-45e8-8ad7-a54db5497d54",
  "koordinaatisto": "EPSG:3067",
  "osoitevalit": {pystygeometriaolio}
} 
```
 
Pystygeomeriaolion malli JSON-muodossa:
```
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
                "y": 6822311.512,
            }
        },
        "pyoristyssade": 25000,
        "tangentti": 4.695,
        "kaltevuusjakso_taaksepain": {
            "pituus": 193.827,
            "suora_osa": 189.133,
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
 

 

 
