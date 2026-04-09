# Reititys

Reititys on rataverkon graafimallin päälle rakennettu ominaisuus, joka etsii lyhimmän reitin kahden rataverkon pisteen
välillä. Reitti kulkee raiteita pitkin, noudattaen linkitettyjen vaihteiden tarjoamia kytkentäkohtia toisille raiteille.
Toisin sanoen, reitti voi haarautua vaihteissa ja kääntyä takaisin raiteiden päissä, mutta ei voi hypätä raiteelta toiselle
ilman topologista kytkentää, tai kääntyä vaihteilla vastoin vaihdetyypin mukaisia kääntymissääntöjä.

Reititys on tarjolla käyttöliittymällä karttatyökaluna, jolla voi valita kaksi pistettä kartalta ja visualisoida niiden
välinen reitti. Taustapalveluiden puolella reititystä hyödynnetään sisäisesti esimerkiksi liikennepaikkavälien
pituuksien laskennassa.

## Reitinhaku

Reitinhaku tapahtuu ylätasolla seuraavasti:

- Luodaan varsinainen reititysgraafi rataverkkodatasta
- Haetaan raiteiden spatiaalisesta välimuistista (`LocationTrackSpatialCache`) lähin raide alku- ja loppupisteelle
- Luodaan tilapäisgraafi alku- ja loppupisteille sekä niiden kytkennöille varsinaiseen graafiin
- Muodostetaan tilapäisgraafista ja varsinaisesta graafista unioni ja haetaan sen päältä lyhin Dijkstra-algoritmilla
- Muutetaan löydetty (kaarilistauksena esitetty) reitti niitä vastaaviksi raideosuuksiksi

Varsinaista graafihakua ei tarvitse lainkaan jos alku- ja loppupisteet ovat sama piste tai sijaitsevat samalla kaarella
tai vaihdelinjalla. Näissä tapauksissa reitinhaku voidaan oikosulkea pelkäksi raideosuuden mappaukseksi.

Reitityksen hitain osuus on graafin rakentaminen koko rataverkon pohjalta. Tämän vuoksi se on syytä pitää välimuistissa
ja tuottaa uudelleen vain kun rataverkko muuttuu. Sinällään tarvetta esimerkiksi kantaan tallennukselle ei toistaiseksi
ole, sillä graafin tuottaminen on sekin vain sekunnin osia. Välimuisti on toteutettu `RoutingService`-luokassa ja nojaa
paikannuspohjan muutosaikoihin perustuen.

## Reititysgraafin toteutus JGraphT-kirjaston avulla

Reititys on toteutettu [JGraphT](https://jgrapht.org/)-kirjastolla. Kirjasto tarjoaa tietorakenteet mm. suunnatulle,
painotetulle monigraafille (`DirectedWeightedMultigraph`) sekä lyhimmän polun etsinnän Dijkstran algoritmilla
(`DijkstraShortestPath`).

Reititysgraafi rakennetaan [rataverkon graafimallin](rataverkko_graafi.md) käsitteistä: solmuista (`LayoutNode`) ja
kaarista (`LayoutEdge`). Koska Geoviitteen oma graafimalli on suuntaamaton, se ei sinällään sovellu reititykseen, jonka
tulee huomioida vaihteiden kääntymissäännöt. Reitityksen graafi on siis geoviitteen oman graafin pohjalta rakennettu,
JGraphT:n rakenteina kuvattu suunnattu graafi vaihdekohtaisilla yhteyssäännöillä. Luotu graafi kääritään `RoutingGraph`
-olioon, joka sisältää itse graafin lisäksi kaari- ja vaihdekohtaisen metadatan, jonka avulla löytynyt reitti voidaan
tulkita takaisinpäin rataverkon raideosuuksiksi.

Reititystä varten muodostetaan käytännössä aina kaksi reititysgraafia. Ensimmäinen näistä on varsinainen rataverkosta
rakennettu graafi joka sisältää kaikki raiteet ja vaihteet, eli kuvaa koko rataverkon mahdollisen reitityksen. Toinen on
tilapäinen lisägraafi, johon luodaan solmut haetun reitin alku- ja loppupisteelle sekä kaaret jotka yhdistävät ne päägraafin
solmuihin molempiin suuntiin molemmista päätypisteistä. Varsinainen reititys tehdään näiden graafien unionin päällä.

Erillinen tilapäisgraafi mahdollistaa sen että rataverkon päägraafi on muuttumaton useiden reittihakujen välillä,
ja voidaan pitää välimuistissa niin kauan kuin itse rataverkko ei muutu.

## Graafin rakenne

Reititysgraafi on suunnattu painotettu monigraafi, jossa solmut ovat suunnallisia liittymäpisteitä ja kaaret ovat
painotettuja yhteyksiä niiden välillä. Painotus on kaaren pituus metreinä, joten Dijkstra löytää lyhimmän fyysisen
reitin.

Jotta graafi pystyy mallintamaan vaihteiden mukaiset kääntymissäännöt, tulee sen olla suunnattu. Vaikka rataverkolla
on tietty vaihdepiste vain kerran, suunnatun graafin kannalta pisteestä pääsee jatkamaan eri suuntiin riippuen siitä
mistä suunnasta pisteelle tultiin, eli graafiin tulee luoda pisteestä kaksi eri solmua. Jokaisesta vaihteen ulkoreunan
pisteestä tai raiteen päätepisteestä luodaan siis solmut **IN** (saapuva) ja **OUT** (lähtevä). IN/OUT-termien valinta
kuvaa solmun roolia suhteessa raiteeseen/vaihteeseen, jonka osa se on: solmut ovat raiteen/vaihteen ulkoreunoja ja IN
tarkoittaa raiteeseen/vaihteeseen sisäänpäin tulevaa suuntaa ja OUT siitä poistuvaa.

### Vaihteen kuvaus suunnatussa graafissa

Suunnatut solmut mahdollistavat sen, että vaihteen läpi kulkeva liikenne seuraa oikeita kääntymissääntöjä: liikenne
saapuu vaihteeseen jonkun vaihdepisteen IN-solmun kautta ja voi poistua kaarien kuvaamilla yhteyksillä vain tiettyjen
pisteiden OUT-solmujen kautta. Koska graafiin lisätään vain vaihteen rakenteen (`SwitchStructure`) sisältämien linjausten
(`SwitchStructureAlignment`) mukaiset yhteydet, reititys on mahdollista vain noita linjauksia pitkin. Yksittäinen
vaihdelinja kuvataan kaarena tietyn pisteen IN-solmusta toisen pisteen OUT-solmuun. Vaihdelinjan sisäisille pisteille
(esim. piste 5 linjalla 1-5-2) ei siis luoda graafiin mitään esitystä, koska niiden kautta ei voi saapua vaihteeseen eikä
lähteä vaihteesta pois. 

Esimerkiksi yksinkertaisessa (YV) vaihteessa, on linjat 1-5-2 ja 1-3. Näistä muodostetaan graafiin solmut 1 (IN), 1 (OUT),
2 (IN), 2 (OUT), 3 (IN) ja 3 (OUT). Solmujen välille luodaan yhteydet 1 (IN) -> 2 (OUT), 1 (IN) -> 3 (OUT), sekä niiden
vastakkaiset suunnat 2 (IN) -> 1 (OUT) ja 3 (IN) -> 1 (OUT). Yhteyttä pisteiden 2 ja 3 välillä ei ole, koska rakenteessa
ei ole sellaista linjaa. Tällöin jos tullaan vaihteeseen vaikkapa pisteen 2 kautta, saavutaan solmuun 2 (IN), josta
voidaan jatkaa eteenpäin vain pisteen 1 (OUT) kautta.

### Raideosuudet osana graafia

Raideosuudet vaihteiden välillä kytkevät niiden alun ja lopun solmut toisiinsa niin että esimerkiksi toisen pään vaihteen
OUT-solmu on yhteydessä toisen pään vaihteen IN-solmuun. Lisäksi luodaan kaari päinvastaiseen suuntaan, jälleen yhden vaihteen
OUT-solmusta toisen IN-solmuun, jolloin reitti voi kulkea kumpaankin suuntaan raiteella, mutta sen täytyy jatkaa raideosuuden
jälkeen samaan suuntaan. Toisessa päässä (tai teoriassa jopa molemmissa) voi olla vaihteen sijaan myös raiteen päätesolmu.
Tämä toimii muutoin samoin kuin vaihdesolmukin, mutta IN/OUT termit menevät käänteisesti, koska ne kuvaavat reitin jatkumista
sisään/ulos raiteelta (vs sisään/ulos raiteen päässä olevalle vaihteelle). Tämä havainnollistuu alla olevassa esimerkissä.

On huomattavaa että kun raideosuudet rakennetaan graafinluonnissa raiteiden geometrioista, tässä logiikassa sivuutetaan kokonaan
ne osat raidegeometriaa, jotka ovat myös osa vaihteen sisäistä geometriaa. Nuo graafin kaaret on jo rakennettu vaihteiden ja
vaihderakenteiden pohjalta yllä kuvatulla logiikalla.

Koska raideosuudet rakennetaan paikannuspohjan `LayoutEdge`-olioista, duplikaattiraiteista ei synny reititysgraafiin
duplikaattikaaria, vaan jokainen raideosuus syntyy vain kerran. On kuitenkin oleellista muistaa että millä vain
osuudella saattaa kulkea kuitenkin useampi sijaintiraide.

### Nollapituiset yhdyskaaret

Varsinaisten raideosuuksien lisäksi luodaan vielä nollapituiset yhdyskaaret kytkemään pisteitä joiden välillä ei ole varsinaista
matkaa. Näitä syntyy kahdessa eri tilanteessa.

Ensimmäinen tilanne on yhdistelmäsolmut, eli geoviitteen graafimallin solmut, joissa kaksi vaihdetta ovat välitömästi peräkäin
ilman raideosuutta niiden välissä (2 eri vaihteen pistettä samassa solmussa). Tällöin luodaan molempiin suuntiin nollapituinen
kaari kummankin vaihdepisteen OUT-solmulta vastakkaisen pisteen IN-solmuun, mikä mahdollistaa siirtymisen vaihteelta toiselle. 

Toinen tilanne on raiteiden päät, joissa lisätään nollapituinen kaari raidepäädyn OUT-solmusta IN-solmuun, mikä mahdollistaa
reitin kääntymisen ympäri raiteen päässä. Tähän ei tarvita kytkentää toiseen suuntaan (IN -> OUT), eikä sille olisi mielekästä
tulkintaa.

### Esimerkkigraafi

Alla oleva kuva esittää esimerkkinä YV-vaihteen IN/OUT-kytkennät siihen liittyvien kolmen raideosuuden kanssa. Kukin
vaihdepiste on kuvattu IN- ja OUT-solmuparinaan. Vaihteen linjaukset kulkevat IN-solmusta OUT-solmuun (esim. 1 IN -> 2 OUT).
Raideosuudet kulkevat raiteen alun IN-solmusta ("sisään raiteelle") vaihdepisteen IN-solmuun ("sisään vaihteeseen") ja
vastaavasti vaihdepisteen OUT-solmusta ("ulos vaihteesta") raiteen päädyn OUT-solmuun ("ulos raiteelta"). Raiteiden päädyissä
nähdään yhdyskaaret päädyn OUT-solmusta saman pisteen IN-solmuun, mahdollistaen kääntymisen takaisin linjalle. Seuraamalla
yhdyskaarien kulkusuuntia voidaan todentaa että suunnattu graafi toteuttaa YV-vaihteen kääntymissäännöt.

![reititys_solmut_ja_kaaret.png](./images/reititys_solmut_ja_kaaret.png)

### Kaarien suunnat

Kaikilla kaarilla on myös suunta, UP tai DOWN, joka kuvaa reitin kulkusuunnan. Itse reititykselle tuolla ei ole merkitystä
sillä kaari liitetään graafiin lähde-solmusta kohde-solmuun (mikä itsessään kertoo reitin suunnan), mutta erillinen suunta
-kenttä auttaa löydetyn reitin (lista kaaria) tulkinnassa rataverkon raideosuuksiksi.

### Kaarien pituudet (painot)

Raideosuuksilla vaihteiden (tai raiteen pään ja vaihteen) välillä kaaren pituus on raideosuuden pituus metreinä.
Vaihteen sisäiseltä osuudelta raiteesta itsestään ei kuitenkaan luoda reititysgraafiin lainkaan kaaria. Sen sijaan
kaikista vaihteista luodaan kaaret vain niiden rakenteen mukaisten linjausten mukaisesti, kuten yllä on kuvattu.
Näiden kaarien pituus on linjauksen geometrinen pituus, siten kuin se vaihdetyypille on määritetty. Oikeasti maastossa
raideosuus ei välttämättä vastaa millin tarkkuudella rakenteen mukaista linjan pituutta, mutta sen pitäisi olla
käytännössä lähes sama.

### Solmutyypit (RoutingVertex)

Reititysgraafin solmutyypeissä on oleellista huomata että niissä kaikissa solmun sisältö kuvaa sen identiteetin, eli
kahta samansisältöistä vertexiä ei voi olla graafissa yhtä aikaa. 

- **SwitchJointVertex**: Vaihteen suunnattu vaihdepiste. Solmuja luodaan vaihteen linjausten päätepisteille, eli niille
  vaihdepisteille joihin raide voi kytkeytyä vaihteen ulkopuolelta.

- **TrackBoundaryVertex**: Raiteen pää, joko alku (START) tai loppu (END). Syntyy vain raiteille, jotka eivät kytkeydy
  mihinkään vaihteeseen kyseisessä päässä (muutoin luodaan SwitchJointVertex).

- **TrackMidPointVertex**: Väliaikainen solmu, joka luodaan reitinhakua varten mielivaltaiseen pisteeseen raiteen
  varrella. Tämä mahdollistaa sen, että reitinhaku voi alkaa ja päättyä mihin tahansa kohtaan raiteella, eikä vain
  solmujen kohdalla.

### Kaarityypit (RoutingEdge)

Vastaavasti kuin solmuilla, myös kaarilla sisältö kuvaa sen identiteetin eikä kahta identtistä kaarta voi olla samassa
graafissa.

- **TrackEdge**: Raideosuus joka vastaa jotain `LayoutEdge`ä, eli raiteen osuutta kahden vaihteen (tai raiteen päädyn)
  välillä. Suunta on UP kasvavien m-arvojen suuntaan tai DOWN vastakkaiseen suuntaan.

- **PartialTrackEdge**: Tilapäisgraafissa käytettävä osittainen `LayoutEdge`, tietyllä m-arvovälillä. Näiden avulla
  kytketään tilapäisgraafin alku- ja loppusolmut (mielivaltaisessa kohdassa raideosuutta) päägraafiin. Suunta on UP
  kasvavien m-arvojen suuntaan, samoin kuin `TrackEdge`illä.

- **SwitchInternalEdge**: Vaihteen sisäinen yhteys tietyn linjauksen läpi, yhdeltä ulkoiselta vaihdepisteeltä toiselle.
  Suunta on UP vaihdelinjan määriteltyyn suuntaan, esim 1-5-2 linjalle 1 (IN) -> 2 (OUT) on UP. Vastakkainen reitti, eli
  2 (IN) -> 1 (OUT) on vastaavasti suunta DOWN.

- **PartialSwitchInternalEdge**: Tilapäisgraafissa käytettävä osittainen vaihteen sisäinen yhteys, jota käytetään kun
  reitti alkaa tai päättyy vaihdelinjan sisällä. Suunta määritellään vaihdelinjan mukaisesti kuten `SwitchInternalEdge`illä.

- **DirectConnectionEdge**: Nollapituinen yhteys kahden samassa sijainnissa olevan solmun välillä. Käytetään kun kaksi
  vaihdepistettä tai raiteen päätä sijaitsevat samassa solmussa (eli yhdistelmäsolmussa) sekä raiteen päissä
  kääntymiseen. Näiden kaarien pituus on aina nolla. Raiteen päässä niiden suunta on aina UP, sillä tällaisia kaaria
  tarvitaan vain yksi (OUT -> IN). Yhdistelmäsolmuilla luodaan sekä UP että DOWN suuntaiset kaaret erottelemaan molemmat
  kulkusuunnat.

## Reitin muuntaminen raideosuuksiksi

Jotta reitityksen ulospäin annettava tulos voidaan kuvata Geoviitteen käsitteistön rataosuuksina, reititysgraafin mukana
kulkee myös alkuperäinen rataverkkodata, josta se luotiin. Reititys tuottaa löydetyn reitin listana `JGraphT`-graafin
kaaria, joita pitkin reitti kulkee. Nämä mapataan takaisin raiteiksi ja vaihdelinjoiksi joista kyseiset kaaret luotiin,
muuntaen reittiosuuksien kaaren sisäiset m-arvot takaisin raiteiden m-arvoiksi.

Koska kunkin kaaren kohdalla saattaa kulkea useampi sijaintiraide (duplikaattiraiteet), tässä mappauksessa joudutaan
valitsemaan joku raide jolla reitti määritellään. Reitin pituuden kannalta raiteen valinta ei kuitenkaan muuta mitään,
joten valinta voi olla mielivaltainen.

### M-arvomuunnokset

Raideosuuksia kuvaavilla kaarilla m-arvot ovat paikannuspohjan `LayoutEdge`:n sisäisiä m-arvoja, eli
`LineM<EdgeM>`. Ne muunnetaan raiteen m-arvoiksi (`LineM<LocationTrackM>`) samoin kuin paikannuspohjassa muuallakin:
yhdistämällä sisäinen m-arvo kyseisen kaaren alku-m-arvoon kyseisellä raiteella.

Vaihteilla puolestaan vaihdelinja kuvataan yhtenä kokonaisuutena (`SwitchEdge`), joten sen m-arvot kuvataan linjan
sisäisinä, tyypillä `LineM<SwitchStructureAlignmentM>`. Jotta nämä voidaan muuntaa raiteen m-arvoiksi, täytyy
vaihdeosuus ensin mapata niihin kaariin jotka linjan kulkevat, ja muuntaa vaihdelinjan m-arvot kaaren m-arvoiksi. Tämän
jälkeen ne voidaan taas muuntaa normaalisti kaarelta raiteen m-arvoiksi.
