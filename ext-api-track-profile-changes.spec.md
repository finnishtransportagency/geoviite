# Feature spec: ext-API muutos-endpoint sijaintiraiteiden pystygeometrialle

## Käyttötapaus

Toisen järjestelmän omistajana haluan pystyä lukemaan Geoviitteestä pystygeometrian muutokset, jotta voin ylläpitää pystygeometriatietoja omassa järjestelmässäni tehokkaasti.

## Ratkaisu: Uudet API:t

Toteutetaan Geoviitteeseen API, josta voi lukea raiteen pystygeometrian muutokset. Raiteen versiokohtaisen pystygeometrian lukeminen on kuvattu speksillä `ext-api-track-profile.spec.md`

Erityishuomiot:
- URL tyyliin: `/paikannuspohja/v1/sijaintiraiteet/<raide-oid>/pystygeometria/muutokset?alkuversio=...&loppuversio=...`
  - URL tarkistettava muiden APIen kanssa yhdenmukaiseksi
- Palautetaan vain kahden version välillä muuttuneet pystygeometriaoliot
  - Jos mikään ei ole muuttunut, palautetaan "osoitevalit" arvona tyhjä lista

## Esimerkki JSON:t

Yhden raiteen pystygeometriamuutoksien datan malli JSON-muodossa:

```
{
  "alkuversio": "e079915c-fe4a-45e8-8ad7-a54db5497d54",
  "loppuversio": "e079915c-fe4a-45e8-8ad7-a54db5497d54",  
  "koordinaatisto": "EPSG:3067",
  "osoitevalit": [
    {pystygeometriaolio},
    {pystygeometriaolio},
    ...
  ]
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
 
