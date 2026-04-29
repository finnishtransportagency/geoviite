# Haastattelut — raakatallennus

---

## 2026-04-28 — KS

**K: Kuvaile "ratanumero" — mitä se tarkoittaa Geoviitteessä?**

V: "ratanumero" on hallinnollinen käsite, joka yhdistää kokonaisuudeksi osuuden rataverkon raiteista. Ratanumeroon kuuluvat raiteet muodostavat usein loogisen kokonaisuuden, sijoittuvat esim. jollekin yhteysvälille (esim. Helsinki-Turku) tai muodostavat ratapihalla yhtenäisen alueen. Kukin raide kuuluu johonkin ratanumeroon. Ratanumeroa tarvitaan mm. rataosoitteen määrittämiseen. Rataosoitetta käytetään usein sijainnin määrittämiseksi rataverkolla. Rataosoite koostuu ratanumerosta ja ratakilometrisijainnista ratanumeroa pitkin. Jotta voidaan määrittää sijainti ratanumeroa pitkin, ratanumerolla täytyy olla lineaarinen muoto, tätä muotoa sanotaan ratanumeron pituusmittauslinjaksi. Esim. Helsingin ja Tampereen välillä "kulkee" ratanumero 003. Riihimäen asema voidaan sijoittaa rataverkolle ratanumeron 003 ja ratakilometrisijainnin 0071+0408 mukaan. Eli se sijaitsee ratanumerolla 003, kilometripaalulta 71, 408 metriä ratanumeron pituusmittauslinjaa pitkin eteenpäin. Helsingin ja Turun välillä kulkee ratanumero 001, kyseisellä ratanumerolla sama ratakilometrisijainti 0071+0408 sijaitsee lähellä Inkoota, eli ratanumero ja siten sen pituusmittauslinja määrittää, millainen koordinaattisijainti ratakilometrisijainnista muodostuu. Ratakilometrisijainti on keskimäärin matka Helsingistä raidetta pitkin.

---

**K: Kuvaile "tasakilometripiste" — mitä se tarkoittaa Geoviitteessä?**

V: "tasakilometripiste" on koordinaattisijainti, joka määrittää tietyn ratakilometrin alkamiskohdan pituusmittauslinjalla. Ratakilometrijärjestelmässä ratakilometrien alkukohdat siis määräytyvät koordinaattisijaintien mukaan, eivät pituusmittauslinjan alusta kuljetun matkan mukaan, joten ratakilometrit eivät välttämättä (eivätkä edes yleensä) ole tarkalleen yhden kilometrin mittaisia. Tässä on taustalla sellainen ajatus, että kun raiteeseen ja samalla mahdollisesti pituusmittauslinjaukseen tehdään muutoksia, vain muutoksen alueelle osuvilla ratakilometreillä rataosoitteet muuttuvat. Jos esim. pitkän ratanumeron (esim. ratanumero 003, pituus noin 350 km) ratakilometrillä 4 tehdään geometriaan muutoksia, ei ole tarvetta päivittää kaikkia rataosoitteita kilometrista 4 alkaen, koska tasakilometripisteen 5 sijainti säilyy ennallaan jolloin ratakilometri 5 alkaa edelleen sen sijainnista. Tasakilometripisteiden sijainti tallennetaan Geoviitteeseen GK-koordinaatistossa, jotta niiden sijainteja voidaan hyödyntää rataverkon geometriasuunnittelussa ilman koordinaattimuutoksia.

---

**K: Kuvaile "geometriasuunnitelma" ja "geometriaelementti" — mitä ne tarkoittavat Geoviitteessä?**

V: Kuvailen ensin vielä tarkemmin "rataosoitejärjestelmä" käsitteen, eli "rataosoitejärjestelmä" on menetelmä määrittää sijainti rataverkolla. Rataosoitejärjestelmässä sijainti kuvataan rataosoitteena. Rataosoite koostuu ratanumerosta sekä ratakilometrisijainnista ratanumeroa pitkin. Esim. ratanumeron 003 ratakilometrisijainti 0071+0408 löydetään siten, kun etsitään ratanumeron 003 tasakilometripiste 71, ja kuljetaan tasakilometripisteen kohdalta ratanumeron pituusmittauslinjaa pitkin 408 metriä. Pituusmittauslinja on useimmiten jokin raiteista. Tällaista paikantamismenetelmää on ollut helpohkoa käyttää maastossa myös ennen GPS järjestelmää, maastosta on etsitty ratanumeron (esim. 003) kilometripylväs (esim. 71) ja mittapyörällä mitattu kiskoa pitkin tarvittava metrimäärä (esim. 408 metriä).

Koska rataosoite määrittää sijainnin lineaarista muotoa pitkin (pituusmittauslinja), paikannusjärjestelmä on yksiulotteinen, eli kaikki rataosoitteet sijaitsevat pituusmittauslinjalla. Kuitenkin rataosoitteilla määritetään myös pituusmittauslinjan vieressä olevien kohteiden sijaintia, mutta tällöin ei ole tiedossa kuinka kaukana pituusmittauslinjasta kohde sijaitsee. Kaikilla koordinaattisijainneilla, jotka sijaitsevat pituusmittauslinjaa pitkin kohtisuoraan olevalla suoralla linjalla, on sama rataosoite, riippumatta siitä, kuinka kaukana pituusmittauslinjasta ne ovat.

---

**K: Kuvaile "geometriasuunnitelma" ja "geometriaelementti" — mitä ne tarkoittavat Geoviitteessä?**

V: "geometriasuunnitelma" tarkoittaa Geoviitteessä suunnitelmaa, joka sisältää raiteiden suunnitellun geometrisen muodon. Suunnitelma sisältää vähintään raiteen vaakageometrian, mutta mahdollisesti myös pystygeometrian. Lisäksi suunnitelma voi sisältää tietoja vaihteista, mutta niitä on lähinnä Sweco-yrityksen tuottamissa suunnitelmissa. Geometriasuunnitelman vaakageometria koostuu geometriaelementeistä, pystygeometria taitepisteistä.

Geometriasuunnitelmia on eri laatuisia. Laadukkaat geometriasuunnitelmat syntyvät ratahankkeissa ratasuunnittelun myötä, ratasuunnittelijan tuottamana. Sellaisten suunnitelmien mukaan rata pyritään rakentamaan maastoon ja niiden mukaan radan kunnossapito pyrkii palauttamaan liikkuneet raiteet suunniteltuun sijaintiinsa. Geoviitteessä on myös heikompilaatuisia geometriasuunnitelmia, joita käytetään Geoviitteen yhtenäisen rataverkon (eli paikannuspohjan) muodostamiseen, silloin kun parempilaatuisia geometriasuunnitelmia ei ole käytettävissä. Suomen rataverkolla on alueita, joista ei ole olemassa laadukkaita digitaalisia suunnitelmia, koska kyseiset alueet on suunniteltu ennen digitaalista suunnittelua. Geoviitteen paikannuspohjan ylläpitoon tarkoitettuja geometriasuunnitelmia on tuottanut myös nykyinen Geoviite-operaattori (Welado), mutta vaikka näitä geometriasuunnitelmia ei käytetä radan rakentamiseen tai kunnossapitoon, tarvitaan niiden tuottamiseen kuitenkin ratasuunnittelun osaaminen.

Maastossa olevaa rataverkkoa voidaan myös mitata ja muodostaa laadukas digitaalinen geometriasuunnitelma mittaustiedon sekä vanhojen paperisten suunnitelmien pohjalta, mutta tämä työ vaatii paljon resursseja, joten sitä ei tehdä ilman painavaa syytä. Usein vanhan alueen rataverkkoa mitataan uudestaan silloin, kun kyseiseen rataverkon osaan kohdistuu muutostarpeita.

Tällä hetkellä geometriasuunnitelmat tuodaan Geoviitteeseen inframodel muotoisina XML-tiedostoina. Tulevaisuudessa on tarkoitus tukea myös IFC-tiedostoja, mutta niitä ei vielä käytetä yleisesti ratasuunnittelussa, koska ratasuunnittelujärjestelmät eivät vielä tue niiden käyttöä riittävän hyvin. Riippumatta tiedoston muodosta, tiedoston sisältö parsitaan ja muutetaan Geoviitteessä omaan tietorakenteeseen. Myös alkuperäinen tiedosto tallennetaan Geoviitteeseen, mutta sitä käytetään vain silloin, kun geometriasuunnitelma ladataan ulos Geoviitteestä.

Geoviite toimii geometriasuunnitelmien tietovarastona. Olemassa olevia suunnitelmia tarvitaan mm. uusien ratasuunnitelmien pohjatiedoksi, radan kunnossapitoon sekä tilastointiin. Geometriasuunnitelmien tarvitsija pyytää suunnitelmia Geoviite-operaattorilta, joka hakee ja valitsee tarpeesseen soveltuvat suunnitelmat ja palauttaa ne pyytäjälle. Geometriasuunnitelmat eivät ole vapaasti näkyvissä kaikille käyttäjille, koska muutoin huonolaatuista suunnitelmaa voitaisiin käyttää liian vaativaan tarkoitukseen, esim. kunnossapitoon.

---

**K: Kuvaile "geometriaelementti" — minkälaisia elementtejä on ja mitä tietoja niihin liittyy?**

V: "geometriaelementti" tarkoittaa Geoviitteessä raiteen vaakageometria osasta, eli geometriasuunnitelman sisältämän raiteen vakaageometria koostuu peräkkäisistä geometriaelementeistä. Geometriaelementtejä on kolmen tyyppisiä: suora, kaari ja siirtymäkaari. Suora on kahden pisteen välinen suora, kaari on ympyrän kaari ja siirtymäkaari on spiraalimainen muoto, jossa kaaren säde muuttuu geometriaelementin matkalla. Siirtymäkaaria käytetään nopeilla nopeuksilla liikennöitävissä reiteissä (esim. raiteilla tai maanteillä), jotta nopeasti liikkuvaan kohteeseen ei kaaressa synny liian suurta sivuttaissuuntaista kiihtyvyyttä, koska liian jyrkässä mutkassa nopeasti liikkuva kohde suistuu reitiltä.

Geometriaelementtien muoto on kuvattu matemaattisesti, eli ne ovat äärettömän tarkkoja. Geometriaelementti sisältää vain tarvittavat tiedot muodon määrittämiseksi.

Geometriaelementeistä Geoviite-operaattori koostaa sijaintiraiteen geometriaa. Koostaminen tapahtuu linkittämällä geometriasuunnitelman raiteesta osuuksia sijaintiraiteelle. Sijaintiraiteen geometria voi koostua eri geometriasuunnitelmien geometriaelementeistä, ja pitkillä raiteilla se on jopa todennäköistä. Linkittämisessä geometriaelementistä tai sen osasta syntyy sijaintiraiteelle segmentti. Segmentistä on viittaus geometriaelementtiin, joten on mahdollista selvittää, mistä geometriaelementeistä sijaintiraiteen geometria koostuu. Segmentin geometria on tallennettu Geoviitteeseen pisteviivana, joka muodostetaan kulkemalla matemaattista geometriaelementin muotoa pitkin metrin pituisin askelin. Kun sijaintiraiteelle linkitetään geometriaa, uusi geometria ei useinkaan yhdisty saumattomasti sijaintiraiteen olemassa olevaan geometriaan, tämän vuoksi olemassa olevan ja uuden geometrian väliin generoidaan suora segmentti, joka yhdistää sijaintiraiteen geometrian jatkuvaksi linjaksi. Generoidulla segmentillä ei luonnollisesti ole viitettä geometriaelementtiin, koska se ei ole muodostunut minkään geometriaelementin pohjalta.

---

**K: Kuvaile "vaakageometria" ja "pystygeometria" — miten ne eroavat toisistaan ja mitä ne sisältävät?**

V: "vaakageometria" tarkoittaa Geoviitteessä raiteen ja pituusmittauslinjan kaksiulotteista geometriaa, ylhäältä päin katsottuna. Geoviitteen kartalla raiteista esitetään juurikin vaakageometrinen muoto. Geometriasuunnitelmian raiteilla vaakageometria koostuu kaksiulotteisista geometriaelementeistä. Sijaintiraiteella ja pituusmittauslinjalla geometria koostuu pisteviivasta. Pisteviivan pisteet sisältävät myös korkeuden merenpinnasta, koska pisteviivan muodostuksessa on huomioitu myös pystygeometrinen tieto, eli pisteviiva on varsinaistesti kolmiulotteinen muoto.

"pystygeometria" tarkoittaa Geoviitteessä raiteen pystysuuntaista muotoa, eli kuinka korkealla raide kulkee suhteessa merenpintaan. Geometriasuunnitelmassa pystygeometria on määritetty taitepisteinä, joka on matemaattinen ja siten äärettömän tarkka esitys. Taitepisteiden ja vaakageometrian tietojen avulla voidaan laskea raiteen korkeus kaikille raiteen pisteille. Sijaintiraiteen ja pituusmittauslinjan pisteviivan pisteisiin korkeus on laskettu ja tieto on tallennettu tietokantaan. Geoviitteessä ja joissain ratasuunnittelujärjestelmissä pystygeometriaa esitetään käyttöliittymässä viivadiagrammina, jossa vaaka-akselilla on raidetta pitkin kuljettu pituus ja pystyakselilla korkeus merenpinnasta. Kuvaajassa on esitetty myös taitepisteiden tietoja sekä kaltevuusjaksot.

---

**K: Kuvaile "toiminnallinen piste" — mitä se tarkoittaa Geoviitteessä?**

V: "toiminnallinen piste" on solmukohta rataverkolla, jossa on matkustajien tai rahdin käsittelyyn liittyviä toimintoja tai se on risteyskohta rataverkolla (eli käytännössä vaihde). Toiminnallisia pisteitä ovat esim. rautatieasema, varikko, linjavaihde.

Geoviitteessä rataverkko on tallennettu vaihteina ja vaihteiden välisinä geometrioina. Vaihteet ovat verkon solmuja ja geometriat linkkejä solmujen välillä. Toiminnallinen piste yhdistää joukon vaihteita ja raiteita yhdeksi kokonaisuudeksi, jolloin syntyy ikään kuin korkeamman abstraktiotason verkko, jossa toiminnalliset pisteet ovat verkon solmuja ja liikennepaikkojen väliset yhteydet ovat verkon linkkejä. Toiminnalliset pisteet ja liikennepaikkojen väliset yhteydet ovat RINF:ille raportoitavia rataverkon tietoja.

Osa toiminnallisista pisteistä on liikennepaikkoja. Liikennepaikkojen perustietoja (mm. nimi, lyhenne, jne.) ja elinkaarta ylläpidetään Ratko-järjestelmässä, mutta niihin sisällytetään lisätietoa Geoviiteessä (mm. polygonimainen alue, RINF-tyyppi jne.). Muiden toiminnallisten pisteiden kaikkia tietoja (mukaan lukien elinkaari) ylläpidetään Geoviitteessä.

Toiminnallisilla pisteillä voi olla jokin seuraavista RINF-tyypeistä: asema, asema (pieni), matkustaja-asema, tavara-asema, varikko, tekninen ratapiha, seisake, kohtauspaikka, valtakunnan raja, vaihtotyöratapiha, raideleveyden vaihtumiskohta, linjavaihde, yksityinen, omistusraja, ylikulku.

---

**K: Kuvaile "paikannuspohja" — mitä se tarkoittaa Geoviitteessä?**

V: "paikannuspohja" tarkoittaa Geoviitteessä yhtenäistä rataverkkoa. Nimitys "paikannuspohja" viittaa siihen, että sitä käytetään kohteiden paikantamiseen. Yhtenäinen rataverkko ei ole Geoviitteessä geometrisesti matemaattisen tarkka, vaan se koostuu sijaintiraiden geometriasta, joka on tallennettu pisteviivana. Epätarkkuuden vuoksi paikannuspohjan tietoja ei tule sellaisenaan käyttää suurta tarkkuutta vaativissa tehtävissä, esim. uusien geometriasuunnitelmien lähtökohtana. Rataverkko muodostuu geometriasuunnitelmien geometriaa sijaintiraiteille linkittämällä. Geometriasuunnitelmat kattavat yleensä vain pienen osan rataverkosta, joten yhtenäinen rataverkko on yhdistetty monen suunnitelman geometrioista. Eri geometriasuunnitelmien geometriat eivät ole aina täysin yhteensopivia, joten myös yhteensovittamisesta syntyy epätarkkuutta paikannuspohjaan

---

**K: Kuvaile "rataverkko" Geoviitteen kontekstissa — onko se sama asia kuin paikannuspohja, vai tarkoittaako se jotain muuta?**

V: "paikannuspohjaan" liittyy myös rataosoitejärjestelmään tarvittavat tiedot, eli ratanumero ja tasakilometripisteet. Eli paikannuspohja on tietyllä tavalla rataverkko laajennetuilla tiedoilla, mutta paikannuspohjaan liittyy lisäksi olettamus epätarkkuudesta. Kuitenkin Geoviitteen kontekstissa "rataverkko" on yhtä kuin "paikannuspohja".

---

**K: Kuvaile "linkitys" — mitä se tarkoittaa Geoviitteessä?**

V: "linkitys" tarkoittaa Geoviitteessä operaattorin suorittamaa toimenpidettä, joilla käsitteiden välille syntyy yhteys, eli linkki. Esim. operaattori voi linkittää paikannuspohjan sijaintiraiteelle geometriaa geometriasuunnitelman raiteelta. Tässä linkityksessä sijaintiraiteelle luodaan pisteviivamaista geometriaa geometriasuunnitelman geometrialementtien mukaan ja samalla sijaintiraiteen ja geometriaelementtien välille syntyy yhteys, eli linkki. Linkkiä on mahdollista seurata ja siten jäljiittää, mistä geometriasuunnitelmasta sjaintiraiteen geometria on peräisin. Muita linkitystoimintoja ovat mm. geometriasuunnitelman vaihteen linkittäminen paikannuspohjan vaihteeseen, geometriasuunnitelman tasakilometripisteen linkittäminen paikannuspohjan tasakilometripisteeseen, paikannuspohjan vaihteen linkittäminen sijaintiraiteelle, vaihteiden ja sijaintiraiteiden linkittäminen toiminalliseen pisteeseen.

---

