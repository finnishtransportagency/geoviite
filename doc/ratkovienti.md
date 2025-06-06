# Ratko-vienti
Täällä on kuvattuna Ratko-viennin prosessi sekä sen tietomalli. Ratko-viennillä tarkoitetaan ratanumeroiden,
sijaintiraiteiden sekä vaihteiden geometrioiden päivittämistä Ratkoon.

Geoviitteen ja Ratkon välinen integraatio on toteutettu push-tyyppisenä eli yksisuuntaisena.  
Järjestelmien välinen kommunikaatio tapahtuu REST-rajapinnoilla. 

Ratko-vienti tapahtuu erillisellä asynkronisella prosessilla ja ylätasolla se koostuu seuraavista vaiheista:
- Haetaan kaikki julkaisut, joita ei vielä ole viety Ratkoon
- Yhdistetään julkaisujen muutokset, jotta saadaan erotus Ratkon ja Geoviitteen nykytilan välillä
- Viedään tähän liittyvät käsitteet käsite kerrallaan Ratkoon (ratanumerot, raiteet, vaihteet)

## Ylätason kuvaus Ratko-viennistä järjestelmien välillä

```mermaid
sequenceDiagram
  participant g as Geoviite
  participant r as Ratko
  loop
    g ->> g: Hae julkaisuja
  end
  rect rgba(3, 129, 255, 0.1)
    note right of g: Ratanumeron vienti
    opt Ratanumeron poistaminen
      g ->> r: Merkkaa sekä päätepisteet että ratanumero poistetuksi
      g ->> r: Poista ratanumeron geometria
    end
    opt Ratanumeron päivittäminen
      g ->> r: Päivitä ratanumeron ominaisuustiedot
      g ->> r: Poista muuttuneet ratakilometrit
      g ->> r: Vie uusi geometria ratakilometri kerrallaan
    end
    opt Ratanumeron luonti
      g ->> r: Luo uusi ratanumero ilman geometriaa
      g ->> r: Vie ratanumeron geometria
    end
  end
  rect rgba(224, 119, 100, 0.1)
    note right of g: Sijaintiraiteen vienti
    opt Sijaintiraiteen poistaminen
      g ->> r: Merkkaa sekä päätepisteet että sijaintiraide poistetuksi
      g ->> r: Poista sijaintiraiteen geometria
    end
    opt Sijaintiraiteen päivittäminen
      g ->> r: Päivitä sijaintiraiteen ominaisuustiedot
      g ->> r: Poista muuttuneet ratakilometrit
      g ->> r: Vie päivittyneet tasametripisteet sekä vaihteiden epätasametripisteet
      g ->> r: Vie sijaintiraiteen suunnitelman metatiedot muuttuneille osuuksille
    end
    opt Sijaintiraiteen luonti
      g ->> r: Luo uusi sijaintiraide ilman geometriaa
      g ->> r: Vie uudet epätasametri- ja tasametripisteet
      g ->> r: Vie sijaintiraiteen suunnitelman metatiedot
    end
  end
  rect rgba(74, 188, 74, 0.1)
    note right of g: Vaihteen vienti
    opt Vaihteen päivittäminen
      g ->> r: Päivitä vaihteen ominaisuustiedot
      g ->> r: Päivitä ainoastaan muuttuneet vaihteen linjat
      g ->> r: Päivitä vaihtepisteiden koordinaattisijainnit
    end
    opt Vaihteen luonti
      g ->> r: Luo uusi vaihde ilman linjoja
      g ->> r: Vie vaihteen linjat
      g ->> r: Vie vaihdepisteiden koordinaattisijainnit
    end
  end
  g ->> g: Merkitse Ratko-vienti onnistuneeksi
  note right of g: M-arvojen laskenta ei vaikuta Ratko-viennin onnistumiseen, <br> sillä Ratko laskee ne joka tapauksessa joka päivä
  g ->> r: Käynnistä M-arvojen laskenta ratanumeroille
  g ->> r: Käynnistä M-arvojen laskenta sijaintiraiteille
```

## Julkaisuiden koostaminen
Julkaisujen yhdistäminen tehdään, jotta virhetilanteissa on mahdollista korjata tilannetta toisella julkaisulla.
Yhteysvirheiden tapauksessa julkaisua yritetään automaattisesti uudelleen seuraavalla ajolla, mutta muissa virheissä ei
ole syytä olettaa että tilanne korjaantuisi automaattisesti. Tällöin virhe voidaan korjata joko Ratkon puolella tai
muuttamalla vietävää datajoukkoa uudella julkaisulla, jolloin datan koostamisen ansiosta virheellistä välitilaa ei
tarvitse saada menemään läpi.

## Tietomalli
Alla on kuvattuna yksikertaistettu malli Ratko-viennin käsitteistä.

```mermaid
classDiagram 
    class RatkoPush {
        startTime: Instant
        endTime: Instant
        status: IN_PROGRESS | SUCCESSFUL | FAILED | CONNECTION_ISSUE
    }
    
    class RatkoPushContent
    
    class RatkoPushError {
        errorType: PROPERTIES | LOCATION | GEOMETRY | STATE
        operation: CREATE | UPDATE | DELETE
    }
    
    class Publication

    RatkoPush <.. RatkoPushContent
    Publication <.. RatkoPushContent
    RatkoPush <.. RatkoPushError
```

## OIDien hakeminen
Geoviitteessä luoduille uusille käsiteille (ratanumeroille, raiteille sekä vaihteille) haetaan Ratkosta OID-tunniste osana paikannuspohjan julkaisua.  
OIDIt ovat globaalisti uniikkeja ja niitä käytetään käsitteiden tunnistamiseen eri järjestelmien välillä.


## Ratanumeroiden, sijaintiraiteiden sekä vaihteiden vienti
Käsitteet viedään tietyssä järjestyksessä Ratkoon, jotta voidaan varmistaa käsitteiden välinen eheys myös viennin aikana:  
Ratanumeron muutokset viedään aina ensimmäisenä Ratkoon, minkä jälkeen voidaan vasta viedä sijaintiraiteisiin tulleet muutokset. 
Vaihteisiin kohdistuvat muutokset viedään viimeisenä koska vaihteen linjoilla on taas riippuvuuksia sijaintiraiteisiin.

Sijaintiraiteen ja ratanumeron päätepisteet käsitellään aina omana operaationa, sillä Ratkossa ne ovat käsitteellisesti eri asioia kuin muut raiteen pisteet.


### Manuaalisesti käynnistetty sijaintiraiteen Ratko-vienti
Geoviitteen käyttöliittymästä on myös mahdollista käynnistää Ratko-vienti yksittäiselle sijaintiraiteelle.
Tällöin Ratkoon päivitetään halutut raiteen ratakilometrit, sekä niille välille osuvat topologisesti kytkeytyneet vaihteet.


### Vaihteen vienti, erikoistilanne
Ratkossa vaihteet kytkeytyvät raiteisiin rataosoitteen perusteella, mikä tarkoittaa siis sitä, että vaihteen kautta kulkevilla raiteilla on oltava sopivat (epä-)tasametripisteet ennen kuin vaihteen linjoja voidaan päivittää.  
Koska historiallisista syistä Geoviitteen ja Ratkon rataosoitteistot poikkeavat hieman, Geoviite päivittää ainoastaan ne vaihteen linjat, joiden geometriat on juuri päivitetty.


## Hyvä tietää integraatiosta
- Ratko käyttää sisäisesti TM35FIN-koordinaattijärjestelmää, mutta rajapinnoissa koordinaatit välitetään WGS84 muodossa. 
  - Tosin Ratkon asset-rajapinta tekee tässä pienen poikkeuksen eli se ottaa vastaan koordinaatit ("geometry") WGS84:ssa, mutta sama rajapinta palauttaa koordinaatit TM35FINinä. 
    WGS84 koordinaatit löytyvät taas toisesta kentästä ("geometryOriginal").
- Rajapinnoissa Ratkon palauttama km+m (rataosoite) on pyöristetty arvo. Tarkka rataosoite saadaan yhdistämällä km- ja m-kentän arvot.
