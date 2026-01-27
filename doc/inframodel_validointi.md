# InfraModel (LandXML) validointi

Geometriadata tuodaan geoviitteeseen InfraModel (LandXML) tiedostoina. Tässä kuvataan tuontiin liittyvä tiedoston
validointi. Tarkempaa kuvausta tuonnista ja tietorakenteesta voi katsoa dokumentista [Tietomalli](tietomalli.md).

## Virheiden luokittelu

Jäsennysvirheet: tiedostoa ei pystytä käsittelemään lainkaan

- Kieltäydytään tuomasta / ei pystytä tuomaan tiedostoa Geoviitteeseen
- Tiedosto tulee korjata Geoviitteen ulkopuolella ja antaa uudelleen
  Validointivirheet: tiedosto on jäsennettävissä, mutta data on virheellistä tai puutteellista
- Käyttäjän voi ottaa kantaa virheelliseen/puutteelliseen dataan tuontivaiheessa
- Vaihtoehtoisesti virhe voidaan korjata suunnittelijan toimesta ja tuoda uusi, päivitetty tiedosto Geoviitteeseen
- Jos halutaan, virheellinenkin suunnitelma voidaan tuoda geoviitteeseen eteenpäinjakelua varten, mutta sen pohjalta ei
  voida tehdä linkityksiä (siltä osin jota virhe koskee)
  Validointihuomiot: tiedoston laatu ei vastaa toivottua tasoa mutta sitä voidaan silti käyttää
- Näytetään virheilmoitus käyttäjälle, mutta tuontia voidaan jatkaa (virhe voidaan haluttaessa sivuuttaa)
- Tämä luokka jakautuu vielä kahteen:
    - Vakavat huomiot: datassa on jotain merkityksellistä selkeästi pielessä
    - Lievät huomiot: datassa on epätarkkuutta tai jokin Geoviitteen kannalta epäoleellinen tieto on väärin

## Validointisäännöt

Validointi tapahtuu kahdessa vaiheessa: tiedoston jäsennys sisäiseen muotoon ja jäsennetyn geometriasuunnitelman
validointi. Jäsennysvirheet estävät tiedoston käsittelyn kokonaan, kun taas muut virheet raportoidaan käyttäjälle ja
käyttäjä voi päättää jatketaanko tuontia.

### Tiedoston jäsennys ja rakenne

| Virhetyyppi                 | Virheen tyyppi | Kuvaus                                                                         |
|-----------------------------|----------------|--------------------------------------------------------------------------------|
| Väärä merkistö              | Jäsennysvirhe  | Tiedoston lukeminen ei onnistu koska sen käyttämää merkistöä ei tunnistettu    |
| Väärä tiedostotyyppi        | Jäsennysvirhe  | Tiedoston MIME-tyyppi (Content-Type) ei ole XML-tyyppinen                      |
| XML-rakenne virheellinen    | Jäsennysvirhe  | Tiedosto ei ole validia XML:ää tai ei vastaa InfraModel (LandXML) XSD-skeemaa  |
| Ei tuettu InfraModel-versio | Jäsennysvirhe  | Tiedoston IM-versio ei ole tuettu                                              |
| Pakollinen osio puuttuu     | Jäsennysvirhe  | Esim. Units, Project tai AlignmentGroup puuttuu tai niitä on useampi kuin yksi |

### Metatiedot

| Virhetyyppi                                    | Virheen tyyppi  | Kuvaus                                                                    |
|------------------------------------------------|-----------------|---------------------------------------------------------------------------|
| Koordinaattijärjestelmä puuttuu tai tuntematon | Validointivirhe | Koordinaattijärjestelmä (CoordinateSystem) puuttuu tai sitä ei tunnisteta |
| Korkeusjärjestelmä puuttuu                     | Validointivirhe | Korkeusjärjestelmän tunniste puuttuu vaikka pystygeometriaa on määritelty |
| Ratanumero puuttuu                             | Vakava huomio   | Ratanumerotieto puuttuu, tai sitä ei löydy paikannuspohjasta              |
| Tasakilometripisteet puuttuvat                 | Vakava huomio   | Suunnitelma ei sisällä yhtään tasakilometripistettä (StaStation)          |
| Vapaaehtoinen metatieto puuttuu                | Lievä huomio    | Suunnitelman luontiaika, yritys tms. metatieto puuttuu                    |

### Keskilinjat (Alignments)

| Virhetyyppi                   | Virheen tyyppi  | Kuvaus                                                                             |
|-------------------------------|-----------------|------------------------------------------------------------------------------------|
| Useita pituusmittauslinjoja   | Validointivirhe | Suunnitelmassa on useampi kuin yksi pituusmittauslinja (featureTypeCode 111)       |
| Ei pituusmittauslinjaa        | Vakava huomio   | Suunnitelma ei sisällä yhtään pituusmittauslinjaa (featureTypeCode 111)            |
| Duplikaatti keskilinja        | Vakava huomio   | Samaa nimeä on käytetty useammalla eri keskilinjalla                               |
| Tyyppi puuttuu tai tuntematon | Vakava huomio   | Linjan tyyppi (featureTypeCode) puuttu tai ei vastaa tunnettuja tyyppejä           |
| Väärä tyyppikoodi             | Lievä huomio    | Linjan tyyppi (featureTypeCode) ei ole raiteen (281) tai pituusmittauslinjan (111) |
| Keskilinjan tila puuttuu      | Lievä huomio    | Keskilinjalle ei ole määritelty tilaa (state)                                      |

### Geometriaelementit (Line, Curve, Spiral)

| Virhetyyppi                           | Virheen tyyppi | Kuvaus                                                               |
|---------------------------------------|----------------|----------------------------------------------------------------------|
| Negatiivinen pituus                   | Vakava huomio  | Elementin ilmoitettu pituus on negatiivinen tai nolla                |
| Virheellinen pituus                   | Vakava huomio  | Elementin ilmoitettu pituus eroaa lasketusta                         |
| Alku- ja loppupiste samat             | Vakava huomio  | Elementin alku- ja loppukoordinaatit ovat identtiset                 |
| Virheellinen alku-/loppupiste         | Vakava huomio  | Elementin ilmoitettu alku-/loppupiste eroaa lasketusta               |
| Geometrian epäjatkuvuus: koordinaatit | Vakava huomio  | Elementin alkupiste ei ole siinä missä edellisen loppupiste          |
| Geometrian epäjatkuvuus: suunta       | Vakava huomio  | Elementin alkusuunta ei vastaa edellisen loppusuuntaa                |
| Paalulukema ei kasvava                | Vakava huomio  | Elementin alun paalulukema (staStart) ei ole suurempi kuin edellisen |
| Kaarteen säde virheellinen            | Vakava huomio  | Kaarteen ilmoitettu säde ei vastaa laskettua                         |
| Kaarteen jänne virheellinen           | Vakava huomio  | Kaarteen ilmoitettu jänteen pituus ei vastaa laskettua               |
| Kaarresäde liian pieni                | Vakava huomio  | Kaarteen tai siirtymäkaarteen säde on alle minimin                   |
| Klotoidivakio virheellinen            | Vakava huomio  | Siirtymäkaaren ilmoitettu klotoidivakio ei vastaa laskettua          |
| Epätarkka ilmoitettu pituus           | Lievä huomio   | Elementin ilmoitettu pituus eroaa lasketusta hieman                  |
| Epätarkka alkupiste                   | Lievä huomio   | Elementin ilmoitettu alku-/loppupiste eroaa lasketusta hieman        |
| Lievä epäjatkuvuus: koordinaatit      | Lievä huomio   | Elementin alkupiste eroaa edellisen loppupisteestä hieman            |
| Lievä epäjatkuvuus: suunta            | Lievä huomio   | Elementin alkusuunta eroaa edellisen loppusuunnasta hieman           |
| Kaarteen säde epätarkka               | Lievä huomio   | Kaarteen ilmoitettu säde eroaa hieman lasketusta                     |
| Kaarteen jänne epätarkka              | Lievä huomio   | Kaarteen ilmoitettu jänteen pituus eroaa lasketusta hieman           |
| Klotoidivakio epätarkka               | Lievä huomio   | Siirtymäkaaren ilmoitettu klotoidivakio eroaa lasketusta hieman      |

### Pystygeometria (Profile)

| Virhetyyppi              | Virheen tyyppi | Kuvaus                                                          |
|--------------------------|----------------|-----------------------------------------------------------------|
| Pystygeometria puuttuu   | Vakava huomio  | Keskilinjalle ei ole määritelty pystygeometriaa (Profile)       |
| Kaarteen pituus puuttuu  | Vakava huomio  | Taitepisteen kaarteelta (VICircularCurve) puuttuu pituus        |
| Kaarteen säde puuttuu    | Vakava huomio  | Taitepisteen kaarteelta (VICircularCurve) puuttuu säde          |
| Paalulukema ei kasvava   | Vakava huomio  | Taitepisteen paalulukema on pienempi kuin edellisen             |
| Laskenta epäonnistui     | Vakava huomio  | Pystygeometrian laskenta epäonnistuu tietyllä kaltevuusjaksolla |
| Liian jyrkkä nousu/lasku | Vakava huomio  | Kahden taitepisteen välillä on liian jyrkkä kaltevuuskulma      |
| Epäjatkuvuus: paalutus   | Vakava huomio  | Laskettujen kaltevuusjaksojen paalutukset eivät ole jatkuvia    |
| Epäjatkuvuus: korkeus    | Vakava huomio  | Laskettujen kaltevuusjaksojen korkeusarvot eivät ole jatkuvia   |
| Epäjatkuvuus: kulma      | Vakava huomio  | Laskettujen kaltevuusjaksojen nousun kulmat eivät ole jatkuvia  |

### Kallistus (Cant)

| Virhetyyppi                       | Virheen tyyppi  | Kuvaus                                                                        |
|-----------------------------------|-----------------|-------------------------------------------------------------------------------|
| Kallistus puuttuu                 | Vakava huomio   | Keskilinjalle ei ole määritelty kallistusta (Cant)                            |
| Kallistuksen kiertopiste puuttuu  | Validointivirhe | Kiertopiste (rotationPoint) puuttuu                                           |
| Kallistuksen kiertopiste keskellä | Validointivirhe | Kiertopisteeksi (rotationPoint) on määritelty CENTER (keskellä raidetta)      |
| Raideleveys virheellinen          | Vakava huomio   | Ilmoitettu raideleveys (gauge) ei ole Suomen raideleveys (1.524m)             |
| Kallistusarvo virheellinen        | Vakava huomio   | Kallistuksen arvo (appliedCant) on negatiivinen tai suurempi kuin raideleveys |
| Epäjatkuvuus: paalulukema         | Vakava huomio   | Kallistuspisteen paalulukema ei ole suurempi kuin edellisen                   |

### Vaihteet

| Virhetyyppi                               | Virheen tyyppi | Kuvaus                                                                    |
|-------------------------------------------|----------------|---------------------------------------------------------------------------|
| Duplikaatti vaihteen nimi                 | Vakava huomio  | Samaa nimeä on käytetty useammalla eri vaihteella                         |
| Vaihdetyyppi tuntematon                   | Vakava huomio  | Vaihteen tyyppi ei vastaa mitään tunnettua vaihderakennetta               |
| Virheelliset vaihdepisteet                | Vakava huomio  | Vaihteelle on määritelty vaihdepisteitä, jotka eivät kuulu vaihdetyyppiin |
| Vaihteen mitat eivät vastaa tyyppiä       | Vakava huomio  | Vaihdepisteiden sijainnit eivät vastaa vaihdetyypin rakennetta            |
| Vaihdepiste eri sijainnissa eri raiteilla | Vakava huomio  | Sama vaihdepiste on määritelty eri raiteilla eri sijainteihin             |
| Raidelinja ei vastaa vaihdetyyppiä        | Vakava huomio  | Raiteella olevat vaihdepisteet eivät vastaa mitään vaihdetyypin linjaa    |
| Liian vähän vaihdepisteitä                | Lievä huomio   | Vaihteella on määritelty vain yksi vaihdepiste (ei voida linkittää)       |
| Epätarkkuus: vaihdepisteiden sijainnit    | Lievä huomio   | Vaihdepisteiden sijainnit eroavat vaihderakenteen mukaisista hieman       |
| Epätarkkuus: raidelinjan vaihdepisteet    | Lievä huomio   | Vaihdepisteiden sijainnit eroavat eri raiteilla hieman                    |

### Tasakilometripisteet (StaEquation)

| Virhetyyppi                                   | Virheen tyyppi  | Kuvaus                                                                  |
|-----------------------------------------------|-----------------|-------------------------------------------------------------------------|
| Duplikaatti tasakilometripisteet              | Validointivirhe | Useammalla ratanumeron tasakilometripisteellä on sama tunnus (kmNumber) |
| Ensimmäisen KM-pisteen paaluluku positiivinen | Validointivirhe | Ensimmäisen tasakilometripisteen paalulukema on positiivinen            |
| Sijainti puuttuu                              | Vakava huomio   | Sijaintitieto puuttuu                                                   |
| Kilometrinumero puuttuu tai virheellinen      | Lievä huomio    | Kilometrinumero (kmNumber) puuttuu tai se on virheellinen               |
