# Julkaisu

Kuten osiossa [Paikannuspohjan kontekstit](paikannuspohjan_kontekstit.md) on kuvattu, paikannuspohjan muutokset
siirtyvät luonnosmuutoksista virallisiksi julkaisun kautta (tai ne perutaan poistamalla luonnos).

Julkaisutoiminto vie muutokset koostettuina paketteina, julkaisuina (Publication). Jokainen julkaisu vie virallisen
paikannuspohjan yhdestä eheästä tilasta toiseen eheään tilaan, mikä varmistetaan julkaisuvalidoinnilla. Julkaistut
muutokset menevät välittömästi Geoviitteen viralliseen paikannuspohjaan. Tämän jälkeen ne viedään Ratkoon asynkronisella
tausta-ajolla, jonka tilaa tarkastellaan käyttölittymässä julkaisutasolla.

## Julkaisun tietomalli

Tämä on yksinkertaistettu malli julkaisun käsitteistöstä. Todellisuudessa mukana on vielä kohtuullinen
määrä valmiiksi laskettua dataa julkaisuhistorian esittämistä varten. Käsitteellisesti mallin voi kuitenkin mieltää
koostuvan julkaisusta johon sisältyy tarkat versiokiinnitykset sen muuttamille käsitteille. Varsinainen datasisältö, joka julkaisussa muuttui, voidaan hakea paikannuspohjan
versiotauluista kiinnitetyillä versioilla tai julkaisun aikaleimalla.

```mermaid
classDiagram
    class Publication {
        publicationTime: Instant
    }
    class LocationTrack {
        id: Int
        version: Int
    }
    class Switch {
        id: Int
        version: Int
    }
    class TrackNumber {
        id: Int
        version: Int
    }
    class ReferenceLine {
        id: Int
        version: Int
    }
    class KmPost {
        id: Int
        version: Int
    }
    Publication <-- LocationTrack
    Publication <-- Switch
    Publication <-- TrackNumber
    Publication <-- ReferenceLine
    Publication <-- KmPost
```

## Julkaisun lukitus ja versioiden kiinnitys

Julkaisut itsessään lukitaan tapahtumaan yksi kerrallaan tietokantalukolla. Kaikkia luonnosmaailmaan vaikuttavia
muutoksia ei kuitenkaan ole rajattu tapahtumaan saman lukon takana. Julkaisun aluksi kiinnitetään ne tarkat riviversiot
joista julkaisu tullaan tekemään ja validointi suoritetaan niiden avulla. Kun validointi on mennyt läpi, juuri nuo
riviversiot päädytään tallentamaan viralliseen paikannuspohjaan. Jos siis jostain syystä julkaisuprosessin vierestä
päätyy tapahtumaan muutoksia johonkin julkaistavan olioon samaan aikaan, niin nämä muutokset häviävät julkaisun
yhteydessä.

## Validointi

Julkaisuvalidoinnissa varmistetaan että julkaistavat luonnosmuutokset vie virallisen paikannuspohjan ehjään tilaan.
Tässä keskeisiä kohtia varmistaa on mm:

- Käsitteiden eheys
    - Kaikilla käsitteillä on kaikki vaaditut kentät määritelty ja valideja (luonnosten välitilat eivät enää käy)
- Viite-eheys
    - Olemassaolevat käsitteet eivät viittaa poistettuihin käsitteisiin
    - Virallisesta paikannuspohjasta ei voi viitata "vain luonnos" -olioihin
- Rataverkon eheys
    - Vaihteen linjoihin on kytkettynä riittävät raiteet
    - Vaihteet ja raiteet ovat samaa mieltä liityntäpisteiden koordinaateista
- Osoitteiston eheys (yhteensopivuus pituusmittauslinjaan)
    - Kaikille raiteille on mahdollista laskea osoitteet koko matkalta
    - Osoitteet ovat jatkuvia, kaikki raiteen metrit voidaan laskea ja metrit eivät ole liian pitkiä/lyhyitä
    - Tuotetut osoitteet ovat valideja (eli ylipitkiä kilometreja)
