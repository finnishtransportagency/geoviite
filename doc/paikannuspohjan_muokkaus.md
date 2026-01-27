# Paikannuspohjan muokkaus

Linkitys on prosessi, jolla muokataan Geoviitteen paikannuspohjan käsitteiden geometriaa ja niiden välisiä yhteyksiä. Geoviitteessä voi linkittää ratanumeroita/pituusmittauslinjoja, sijaintiraiteita, vaihteita ja kilometripylväitä. Suurin osa linkityksistä tapahtuu geometriasuunnitelmien ja Geoviitteen paikannuspohjan välillä ja se on Geoviitteen pääasiallinen mekanismi paikannuspohjan geometrioiden ja sen käsitteiden välisten yhteyksien muokkaamiseen.

Linkitys koskee _lähes_ pelkästään kohteiden geometriatietoja. Paikannuspohjan käsitteiden metatiedot (nimi/tunnus, tila jne.) on siis määriteltävä käsin. Tähän on yksi poikkeus (sijaintiraidetunnus), josta on tarkempi maininta Sijaintiraiteiden ja pituusmittauslinjojen linkityksen yhteydessä. Linkitys ei koskaan muokkaa suoraan virallista paikannuspohjaa, vaan se muodostaa luonnoksia. Luonnosten tulee läpäistä julkaisuvalidointi ja ne julkaistaan Geoviitteen julkaisuprosessin mukaisesti.

**Huomautus toiminnallisista pisteistä**: Toiminnallisten pisteiden muokkaaminen ei ole linkitystä, vaikka se sitä muistuttaakin käyttöliittymässä.

## Geometriasuunnitelmien käsitteistön suhde paikannuspohjan käsitteistöön

Geometriasuunnitelmissa käytetty käsitteistö eroaa jonkin verran paikannuspohjan käsitteistöstä, sillä suunnitelmissa kaikki nauhamaiset kohteet on esitetty Alignmenteina. Paikannuspohjassa nauhamaiset kohteet voivat olla joko sijaintiraiteita tai pituusmittauslinjoja. Alla on esitetty taulukko, joka yhdistää nämä toisiinsa:

| Linkitystyyppi               | Lähde             | Kohde         |
|------------------------------|-------------------|---------------|
| Sijaintiraiteen linkitys     | GeometryAlignment | LocationTrack |
| Pituusmittauslinjan linkitys | GeometryAlignment | ReferenceLine |
| Vaihteen linkitys            | GeometrySwitch    | LayoutSwitch  |
| Kilometripylvään linkitys    | GeometryKmPost    | LayoutKmPost  |

## Kohteiden linkitys

Geometriasuunnitelmien käsitteitä linkitettäessä paikannuspohjan kohteeseen jää aina viite alkuperäiseen geometriapuolen kohteeseen. Yhteys purkautuu jos käsite linkitetään jostain muualta tai jos sen geometriaa muokataan käsin.

### Nauhamaisten kohteiden (sijaintiraiteiden ja pituusmittauslinjojen) linkitys

Geometriatiedostossa määritellyt alignmentit voidaan linkittää joko paikannuspohjan sijaintiraiteisiin tai pituusmittauslinjoihin. Alignmentia ei ole pakko linkittää kokonaan, vaan siitä voidaan valita myös vain osa. Jos paikannuspohjan kohteella oli jo geometriaa kyseisessä kohdassa, linkitys ylikirjoittaa sen. 

Nauhamaisten kohteiden linkitys perustuu segmentteihin. Kokonaan linkitettävät geometria-alignmentin segmentit linkitetään sellaisinaan. Jos linkitys katkaisee paikannuspohjan ja/tai geometrian segmentin, paikannuspohjaan generoidaan aiemman geometrian perusteella segmentti joka sisältää vain jäljellejäävän osuuden (näin voi käydä vain linkitettävän osuuden alussa tai lopussa). Linkityksessä poistetut segmentit poistetaan kokonaan paikannuspohjan kohteelta. Jos raiteen/pituusmittauslinjan aiempi geometria ei yhdisty saumattomasti suunnitelmasta linkitettävään geometriaan, niin niiden välille generoidaan välisegmentti. Tämä segmentti eroaa muista segmenteistä siten, että Geoviitteen generoimana sillä ei ole suunnitelmaviittausta. Sille ei myöskään generoida pystygeometriatietoja.

#### Hyvä tietää

- Nauhamaisia kohteita on myös mahdollista lyhentää käyttöliittymältä. Tämä ei ole varsinainen linkitysoperaatio, mutta se poistaa ja typistää nauhamaisen kohteen segmenttejä samalla tavalla kuin linkitys.
- Olemassaolevien pituusmittauslinjojen geometrian muokkaaminen muuttaa herkästi kyseisen pituusmittauslinjan osoitteistoa, joten se aiheuttaa laskettuja muutoksia myös kaikkiin sen varrella oleviin kohteisiin muuttuneiden ratakilometrien matkalta.
- Jos sijaintiraiteen linkitys muuttaa raiteen vaihdelinkityksiä, niin [rataverkon graafimalli](rataverkko_graafi.md) päivittyy automaattisesti linkitysten seurauksena.
- Sijaintiraiteen nimeämisskeemasta riippuen sen päihin linkitettyjen vaihteiden nimet voivat kuulua osaksi ka. raiteen nimeä. Tällöin linkitysoperaatiot effektiivisesti muokkaavat kyseisten raiteiden metatietoja.

### Vaihteiden linkitys

Vaihteen linkitys tarkoittaa käytännössä vaihteen vaihdepisteiden sijoittamista haluttuihin sijainteihin ja lisäämällä niihin viitteet niiden kohdilla meneviin raiteisiin. Ennen linkityksen tallentamista vaihteelle generoidaan niinkutsuttu vaihde-ehdotus, joka kuvaa sitä miten vaihde lopullisesti sijoittuisi paikannuspohjaan linkityksen tallentamisen jälkeen. Vaihteiden linkitystä voi tehdä kahdella tavalla:

#### Vaihteiden linkitys geometriasuunnitelmasta

Geometriasuunnitelmasta linkittäessä suunnitelman vaihteen geometriatietoja käytetään sellaisenaan ja vaihde-ehdotus luodaan puhtaasti geometriatiedoston perusteella. Tällainen linkitys geometriatiedostosta edellyttää, että siihen geometriatiedostossa liittyvät raiteet on myös linkitetty samasta tiedostosta ka. vaihteen geometrian matkalta. Tällä tavalla linkittäessä vaihteelle syntyy viittaus geometriasuunnitelman vaihteeseen.

#### Vaihteen linkitys käsin

Geoviitteen käyttöliittymältä on mahdollista linkittää vaihteita käsin osoittamalla käyttöliittymästä vaihteelle paikka. Tällöin vaihde-ehdotus luodaan etsimällä vaihdepisteille paikat vaihderakenteen ja annetussa paikassa kulkevien paikannuspohjan raiteiden perusteella. Tällä tavalla linkittäessä viittausta mihinkään geometriasuunnitelmaan ei synny. Mahdollinen olemassaoleva viittaus puretaan.

#### Hyvä tietää

- Vaihteen linkittäminen käsin nykyiseen sijaintiinsa on operaattoreilla yleisesti käytössä oleva keino lähtödatan vajavaisten linkitysten korjaamiseen.
- Raiteiden pilkkominen linkittää kaikki pilkottavan raiteen vaihteet automaattisesti uudelleen ylläolevan mukaisesti vajavaisten linkitysten minimoimiseksi.
- Jos vaihteen linkitys muuttaa raiteiden vaihdelinkityksiä, niin [rataverkon graafimalli](rataverkko_graafi.md) päivittyy automaattisesti linkitysten seurauksena.

### Kilometripylväiden linkitys

Kilometripylvään linkityksessä paikannuspohjan kilometripylvään sijainti asetetaan geometriatiedoston kilometripylvään perusteella. Kilometripylväiden erikoisuutena niiden virallinen sijainti määräytyy GK-koordinaatistossa. Mikäli geometriasuunnitelma on jo valmiiksi määritelty oikean GK-koordinaatiston mukaan, niin sijainti kopioidaan suoraan suunnitelman kilometripylväältä. Muulloin sijainti muunnetaan suunnitelman koordinaatistosta oikeaan GK-koordinaatistoon. Paikannuspohjan kilometripylvääseen luodaan linkitettäessä aina viite kyseiseen geometriasuunnitelman kilometripylvääseen.

#### Hyvä tietää

- Kilometripylväiden paikkaa on mahdollista muokata ja uusia pylväitä on mahdollista jopa luoda syöttämällä GK-sijainnin koordinaatit käsin. Tämä ei ole varsinainen linkitysoperaatio, mutta olemassaolevaa kilometripylvästä tällä tavoin muokatessa mahdollinen aiempi viittaus geometriatiedoston kilometripylvääseen purkautuu.
- Koska rataosoitteisto perustuu kilometripylväiden sijainteihin, niin kilometripylväiden linkitys aiheuttaa lähes poikkeuksetta laskettuja muutoksia kyseisellä ratanumerolla sijaitseviin kohteisiin.

## Liittyvät dokumentit

- [Geokoodaus](geokoodaus.md) - Osoitteiden laskenta linkityksessä
- [Paikannuspohjan kontekstit](paikannuspohjan_kontekstit.md) - Kontekstit joissa linkitys tapahtuu
- [Julkaisut](julkaisut.md) - Linkitetyt muutokset julkaistaan
