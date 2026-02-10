# Feature: Fix Switch Names

## Overview

Lisätään Geoviitteeseen työkalu, jolla operaattori voi korjata vaihteiden nimiä massaoperaationa.

Vaihteen nimen korjaaminen tarkoittaa, että nimen keskellä olevat useampi perättäinen välilyönti korvataan yhdellä
välilyönillä.

Työnkulku on seuraava:

- Operaattori asettaa kartan sellaiselle alueelle, jonka vaihteiden nimetä hän haluaa korjata
- Geoviite näyttää vasemman reunan valintapaneelissa, vaihteiden valikossa
    - Kartan alueella sijaitsevan vaihteiden lukumäärän
        - Tätä varten täytyy tehdä uusi toiminnallisuus, joka hakee kartan alueella sijaitsevien vaihteiden lukumäärän
    - Valikko-painikkeen
        - On ikonipainike, jossa kolme pistettä
        - Valikossa on yksi alkio
            - "Korjaa vaihteiden nimet (<vaihteiden lukumäärä>)"
- Operaattori valitsee valikosta "Korjaa vaihteiden nimet"
- Geoviite näyttää operaattorille dialogin, jossa
    - Näytetään, kuinka monen vaihteen nimi muuttuisi korjauksessa
    - Näytetään muuttuvat vaihteiden nimet
        - Skrollautuva lista
    - Operaattori voi sulkea/peruuttaa dialogin, jolloin ei tehdä mitään
    - Operaattori voi hyväksyä nimien korjaamisen "Korjaa nimet"-napilla
        - Ilmoitettujen vaihteiden nimet korjataan, eli vaihteisiin tehdään luonnostilainen muutos
    - Työkalun osuus on valmis, operaattori voi julkaista muutokset normaalia prosessia käyttäen

Tehdään kokonaisuus kahdessa vaiheessa

- Muutetaan vaihteiden esittäminen valintapaneelissa
    - Korvataan nykyinen tapa hakea valintalaatikossa esitetyt vaihteet
        - Tehdään backendille uusi endpoint
            - Joka saa seuraavat syötteet
                - alue, eli bounding boxin
                - layout context
                - vaihteiden maksimimäärän
            - Endpoint suodattaa annetun contextin vaihteista ne, jotka osuvat alueelle
            - Enpoint palautta aina vaihteiden lukumäärän
            - Jos suodatettuja vaihteita on maksimissaan syötteenä ilmoitettu maksimimäärä
                - Endpoint palauttaa myös suodatetut vaihteet olioina
        - UI kutsuu uutta endpointtia kun kartalla näytettävä alue muuttuu
            - Jos kartta on zoomattu sellaiselle tasolle, että vaihteita esitetään
                - Annetaan kutsuun vaihteiden maksimimääräksi 30
            - Muutoin annetaan kutsuun vaihteiden maksimimääräksi 0
    - Vaihdetietojen esittäminen
        - Näytetään endpointin palauttama vaihteiden lukumäärä vaihteiden otsikkorivillä
            - Korvaa nykyisen vaihteiden lukumäärän esittämisen
        - Jos kartta on zoomattu sellaiselle tasolle, että vaihteita esitetään
            - Jos vaihteita on maksimissaan esitettävä maksimimäärä
                - Näytetään endpointin palauttamat vaihteet
                - Vaihteet esitetään nykyisellä tyylillä
            - Jos vaihteita on enemmän kuin esitettävä maksimimäärä
                - Näytetään "zooma lähemmäksi" viesti
        - Jos kartta on zoomattu kauemmaksi
            - Näytetään "zooma lähemmäksi" viesti
- Muutostyökalu/dialogi

## Purpose

Jotta nimistä saadaan ylimääräiset välilyönnit pois

- Kuitenkin hallitusti, niin että normaali julkaisuprosessi toteutuu
- Tehokkaasti, ettei nimiä tarvitse muokata yksitellen
- Kuitenkin niin, että muutokset voi tehdä osissa

## Requirements

## Technical Tips

- Kartalla näytettävä alue on tallennettuna Redux storeen
- UI:ssa kutsutaan uutta endpointtia, kun näytettävä alue on ollut hetken paikallaan, debounce-tyylisesti
- Tiedon hakeminen container-komponentissa, ei-container komponentti lähinnä vain näyttää tietoja
- Reactissa ei käytetä komponentin tilaa (useState), vain mikäli oikeasti tarvitaan

Katso mallia

- Olemassa olevista Controller, Service ja DAO luokista

## Technical Design

