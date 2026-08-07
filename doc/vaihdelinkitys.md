Vaihdelinkityksessä Geoviite asettelee paikannuspohjan vaihteen sijainnin, ja päivittää rataverkon raiteiden topologian
vastaamaan tätä asettelua.

Putken keskeisin luokka on SuggestedSwitch, joka sisältää valmiin vaihde-ehdotuksen. Vaihdelinkitys tapahtuu
luonnollisesti aina sellaisessa järjestyksessä, että vaihde-ehdotus pitää luoda ensin, jotta se voidaan tallentaa;
tässä järjestys on kuitenkin esitettynä toisin päin, koska tällä saattaa ehkä päästä helpommin sisään siihen, mitä
vaihde-ehdotus sisältää.

# Eri kutsupolut

Oleellisin vaihdelinkityksen kutsupolku on suora vaihdelinkitys käyttöliittymältä. Käyttäjä tekee joko geometriavaihteen
linkityksen, tai paikannuspohjan vaihteen linkityksen pisteeseen, ja saa siitä tulokseksi vaihde-ehdotuksen
(SuggestedSwitch). Käyttäjä voi sitten tallentaa tämän yksittäisoperaationa.

Käyttäjä voi myös uudelleenlinkittää kaikki raiteen vaihteet kerralla (mikä itse asiassa linkittää vielä nekin vaihteet,
jotka ovat edes fyysisesti raidetta lähellä, mutta eivät vielä siihen linkitettyjä). Tämä ominaisuus tekee koko
uudelleenlinkitysprosessin muistinvaraisena ja tallentaa tulokset tietokantaan vasta lopussa, koska aikoinaan kun
raiteilla saattoi olla satoja vaihteita, tämä oli perffin suhteen välttämätöntä.

# Yleisiä selkeyttäviä huomioita

Vaihteella on vaihderakenne, joka määrittää vaihteen vaihdepisteiden keskinäisen asettelun
(SwitchStructureJoint-joukko) ja vaihdelinjat (SwitchStructureAlignment). Yksittäisen vaihteen
vaihdepisteet (LayoutSwitchJoint) ovat näistä täysin erillisiä olioita. Vastaavasti myös sijaintiraiteilla on linjat
(ne toteuttavat IAlignment-rajapinnan), mutta raiteen linja on täysin eri asia kuin vaihteen linja.

Vaihteen omilla vaihdepisteillä on käsitys sijainnistaan; mutta vaihteen sijainti raiteella ei välttämättä vastaa
tätä käsitystä, ja raidegeometrian laadusta riippuen saman vaihteen sama piste voi sijaita eri raiteilla eri pisteissä.

# Vaihde-ehdotuksen sisältö ja tallennus

Vaihde-ehdotuksen pääsisältö on trackLinks-kenttä, joka kertoo raiteittain, mihin kohtaan mitäkin raidetta vaihteen
vaihdepisteiden pitää osua. Tarkkaan ottaen tämä kenttä kohdistuu raiteittain tasan yhteen kaareen: Koska vaihteita
ei voi olla limittäin tai sisäkkäin, ja vaihteen oma mahdollinen entinen linkitys puretaan raiteelta pois, linkitys
saadaan pakotettua osumaan yhdelle kaarelle.

## Entisten linkitysten purku

Vaihde-ehdotuksen ratakohtainen edgeIndex-tieto viittaa kaaren indeksiin raiteella sen jälkeen, kun entiset linkitykset
on purettu, mikä rataverkon topologiamallissa tarkoittaa peräkkäisten kaarien yhdistämistä yhdeksi. Esimerkissä
YV-vaihteella 1-5-2-linjan raiteella kaaret sattuvat kulkemaan vasemmalle päin, ja oikealla päin on jo paljon kaaria
niin, että oikeanpuoleisimman (2-pisteeseen) loppuvan kaaren indeksi on 10:

```
      -----3--
     /
----1---5----2--
 ^    ^   ^    ^
 13   12  11   10
  \-(vaihteen läpivasemmalle menevän raiteen kaari-indeksit)
```

Entisen linkityksen purkamisen jälkeen kaari 10 meneekin tämän koko jakson läpi, ja siksi vaihde-ehdotus myös
kohdistuu sille.

Linkityksen tallennusta varten puretaan aina linkitettävän vaihteen omat entiset linkitykset, ts. kaikki trackLinks-
kentässä mainitut raiteet (joissa on mahdollista, että uusia linkityksiä tälle raiteelle ei enää tulekaan, vaan linkitys
ainoastaan puretaan), ja myös mahdolliset limittäiset vaihteet, joista tulee tieto detachSwitches-kentässä.

## Uusien linkitysten tallennus

Logiikka raidetopologiamallissa on varsin yksinkertainen: Kaari, jolle linkitys kohdistuu, pilkotaan vaan osiin
vaihde-ehdotuksen pisteiden perusteella. SwitchLinking.kt:linkJointToEdge sisältää tähän liittyvät case-käsittelyt.

Vaihdelinkityksen oma uusien linkitysten tallennus koskee lähinnä käsityksiin siitä, mitkä osat raidetta ovat
vaihteen sisällä, mutta ottaa vain minimimäärän verran kantaa varsinaiseen rataverkon topologiaan.

Varsinainen topologian päivitys tapahtuu erikseen LocationTrackService#recalculateTopology()-kutsulla, jotta
vaihdelinkityksen ei tarvitse ottaa kantaa topologian hallintaan. Vaihde-ehdotuksessa mukana olevaa
topologicallyLinkedTracks-tietoa käytetään ainoastaan käyttöliittymällä näyttämään, miten vaihde yhdistyy
topologialinkeillä (ts. raiteen päässä olevilla outer-linkeillä) rataverkkoon, mutta ehdotuksen tallennuksessa muuten ei
mihinkään.

## Muut vaihdelinkityksen tallennuksen osat

Vaihteen omat vaihdepisteet (omine käsityksineen niiden sijainneista) tallennetaan vaihteelle itselleen. Koska
vaihde-ehdotus on Geoviitteessä tavallista monimutkaisempi ja siten herkkäluontoisempi olio (eritoten raiteiden
kaari-indeksien muutos tekisi vaihde-ehdotuksesta täysin järjettömän olion), vaihde-ehdotus sisältää tiedon siitä,
mille versioille raiteista se on tehty, ja tallennuksessa tarkistetaan, että muutosta ollaan tekemässä juuri näiden
versioiden päälle.

# Vaihde-ehdotuksen luominen

## Pisteittäin ryhmittely PointAssociation-luokalla

Vaihde-ehdotuksen laskeminen paikannuspohjan vaihteelle on itsessään yksinkertaisimmillaan funktio, joka ottaa
syötteikseen nykyisen rataverkon tilan, vaihde-ID:n, ja koordinaattipisteen. Vaihde-ehdotusten esikatselussa rataverkon
tila ja vaihde-ID pysyvät samoina, mutta ehdotusten tekemistä halutaan kokeilla sadoille tai tuhansille pisteille.
Usein on niin, että lähekkäisistä pisteistä syntyy myös tasan sama vaihde-ehdotus; ja koska prosessi on monivaiheinen,
voi olla myös, että lähekkäisiin pisteisiin liittyy samat välivaiheen tiedot.

PointAssociation-luokka hallinnoi tätä kokonaisuutta: Se pitää kirjaa siitä, mihin joukkoon pisteitä mikäkin tieto
liittyy.

## Vaihteen asettelu (SwitchFittingService)

Asettelu tehdään eri tavalla riippuen siitä, onko kyseessä paikannuspohjan vaihteen linkitys paikalle, vai
geometriavaihteen linkitys paikannuspohjaan. Molemmissa tapauksissa, jos laskenta onnistuu, tulos on asettelu
FittedSwitch-olion muodossa.

### Geometriavaihteen asettelu

Vaihdepisteet asetellaan vaihderakenteen mukaan, sillä oletuksella, että geometriavaihteen pisteet vastaavat
rakennetta. Sitten haetaan vaan, mille raiteille nämä vaihdepisteet sitten osuvat, ja miten.

### Paikannuspohjan vaihteen asettelu pisteeseen

Keskeisin funktio on findBestSwitchFitForAllPointsInSamplingGrid, joka hoitaa ylätasolla koko laskennan vuon, missä:

- findPossibleSwitchTransformations hakee kohdat, joissa raiteet varsinaisesti risteävät (ts. kohta, johon vaihteen
  pääpiste osuu), ja kulmat, joihin raidegeometrian perusteella asettelua voisi yrittää tehdä
- fitSwitch hakee kustakin eri asettelumahdollisuudesta, miten sen pisteet osuvat raiteille
- selectBestFitForEachGridPoint laskee asettelun laadun, ja valitsee hakupisteittäin laadultaan "parhaimman" asettelun.
  Algoritmi on itsessään varsin ad-hoc: Siihen on vaan koottu painoja, jotka heittävät asetteluvalintoja suuntaan tai
  toiseen, käytännössä sen perusteella, mikä tuntuu jotakuinkin toimivan.

## Asettelun rikastaminen vaihde-ehdotukseksi (SwitchMatching)

FittedSwitch-olio yhtäältä sisältää paljon ylimääräistä tietoa, jota tarvittiin ainoastaan parhaan asettelun laskentaan,
mutta ei varsinaiseen vaihteen linkitykseen; ja toisaalta ei lainkaan varmista, että vaihteen linkityksestä seuraa
mitenkään järkevä raidetopologia.

Nämä tehtävät tekee SwitchMatching#matchFittedSwitchToTracks. Tarkkaan ottaen se siis:

- Merkkaa muiden vaihteiden linkityksen poistettavaksi kokonaan, jos asettelu osuisi sen päälle
- Siirtää vaihdepisteiden sijaintia raiteella sen verran, ettei se mene limittäin muiden raiteiden kanssa.

Suuri osa SwitchMatchingin tekemästä työstä on tätä limittäisyyden poistoa, vaikka sitä ei välttämättä suoraan näe
algoritmista. Käytännössähän jos vaihde menee limittäin toisen vaihteen kanssa, mutta raidegeometria itsessään on
kunnossa, niin vaihteen asettelu kyllä osaa löytää kaikille pisteille raiteen, jolle se osuu, mutta eri kaarille, ja
mahdollisesti eri raiteille. SwitchMatching ei lähde hakemaan näitä harhapoluille eksyneitä osumia, vaan heittää ne
vain pois, ja laskee tarpeen tullen uudet tilalle oikean kaaren päihin; jos ne sattuvat osumaan riittävän lähelle sitä,
mihin niiden asettelun perusteella pitäisi osua, niitä käyttämällä saa sitten ikään kuin saman tuloksen kuin olisi
saanut siirtämällä pisteet kaarelle.

## Topologialinkkitietojen rikastaminen vaihde-ehdotukseen

Koska käyttöliittymällä halutaan näyttää, mitkä raiteet päättyvät vaihteelle, vaihde-ehdotuksen luomisen viimeisenä
vaiheena ajetaan muistinvaraisesti raidetopologian päivitys, mutta jätetään sen tulokset tallentamatta.


