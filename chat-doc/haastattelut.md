# Haastattelut — raakatallennus

---

## 2026-04-24 — KS

**K: Mikä on projektisi nimi ja lyhyt kuvaus siitä, mitä se tekee?**

V: Projekti on Geoviite, se on tietojärjestelmä, jolla ylläpidetään Suomen rataverkon ratageometrioita.

---

**K: Ketkä ovat projektin pääkäyttäjäryhmät tai sidosryhmät? (esim. loppukäyttäjät, tilaajat, integraatiokumppanit)**

V: Tilaaja on Väylävirasto. Loppukäyttäjät ovat pääasiassa Geoviite-operaattoreita, jotka ylläpitävät tietoja. Geoviitettä käyttää myös joukko lukuoikeuksilla varustettuja konsulttikäyttäjiä, jotka tarkastavat rataverkon tietoja. Geoviite on integroitunut Ratko-järjestelmään, jonne Geoviitteestä viedään mm. raiteiden, vaihteiden ja ratanumeroiden geometriset tiedot sekä hallinnolliset perustiedot. Geoviitteestä rataverkon tietoja lukee myös Projektivelho, Kuvatieto, Raita ja Väylän analytiikka.

---

**K: Mikä on projektin tekninen arkkitehtuuri pääpiirteissään? (esim. käytetyt teknologiat, frontend/backend, tietokanta)**

V: Arkkitehtuuri on pääpiirteissään: Selainkäyttöliittymä (Typescript, React, Openlayers), Backend (Kotlin, Spring), tietokanta Postgres + PostGis. Järjestelmä on kontitettu ja suoritetaan AWS ympäristössä.

---

**K: Mitkä ovat projektin keskeisimmät toiminnallisuudet tai ominaisuudet tällä hetkellä?**

V: Keskeisimmät ominaisuudet ovat yhtenäisen rataverkon geometrioiden ylläpito ja rataverkon tiedoista hakeminen/raportointi, geometriasuunnitelmakirjaston ylläpito.

---

**K: Missä vaiheessa projekti on tällä hetkellä? (esim. kehitysvaihe, tuotanto, ylläpito) Ja onko projektilla erityisiä lähiajan tavoitteita tai kehityskohteita?**

V: Projekti on ollut kehityksessä 1-2021 alken ja tuotannossa 11-2022 alkaen, kehitystä on jatkettu koko tuotannossaolon ajan. Kehitys jatkuu edelleen. Lähiajan suurempia tavoitteita ovat: 1. Geoviitteen ja Ratkon välisen integraation kääntäminen niin, että Ratko lukee tiedot Geoviiteestä (PULL-integraatioksi) 2. Suunnitelmatilaisen rataverkon tarjoaminen API:sta, niin että suunnitelmatilaisuus saataisiin käytöön myös Ratkon puolella. Pienempinä tavoitteina on mm. hallinnollisten alueiden (esim. tilirataosa) geometrian ylläpidon siirto Geoviitteeseen.

---

**K: Onko projektilla erityisiä teknisiä haasteita tai tunnettuja ongelmakohtia, joita kannattaa dokumentoida?**

V: Erityisiä teknisiä haasteita ei ole tiedossa, muita kuin Geoviite-Ratko integraation suunnan kääntäminen PUSH mallista PULL malliin, mutta kaikkiaan tietomalli on melko kompleksinen, joten sitä on varmaan syytä dokumentoida uusia kehittäjiä varten.

---

**K: Kuvaile lyhyesti keskeisimmät tietomallin käsitteet — esim. mitä tarkoittavat "raide", "ratageometria", "ratanumero" ja muut projektin ydinkäsitteet?**

V: Voisin ensin luetella keskeisimmät käsitteet ja sitten yksitellen kuvailla kunkin käsitteen. Käsitteet ovat: raide, vaihde, ratanumero, pituusmittauslinja, tasakilometripiste, rataosoitejärjestelmä, geometriasuunnitelma, geometriaelementti, vaakageometria, pystygeometria, toiminnallinen piste, paikannuspohja, rataverkko, linkitys

---

**K: Kuvaile "raide" — mitä se tarkoittaa Geoviitteessä?**

V: "raide" tai tarkemmin sanottuna "sijaintiraide" on Geoviitteessä tunniste ja joukko muita ominaisuustietoja yhtenäiselle geometriselle osuudelle rataverkolla. Rataverkkoa voi ajatella graaffina, jossa vaihteet ovat solmukohtia ja vaihteiden väliset kiskot linkkejä. Sijaintiraide määrittää tunnisteen ja ominaisuustietojoukon n-kappaleelle peräkkäisiä linkkejä. Vaikka rataverkkoa on helpompi hahmottaa maastoon rakennetun rataverkon avulla, Geoviitteessä raiteen geometrinen muoto on raiteen suunniteltu muoto. Maastoon raide (eli fyysinen raide) pyritään kuitenkin rakentamaan tarkasti suunnitelman mukaan, joten Geoviitteen raiteen muodon pitäisi melko hyvin täsmätä maastossa olevaan raiteeseen, mutta todellisuudessa näiden välillä on poikkeamaa. Maastossa raide myös liikkuu ajan saatossa, maan liikkumisen ja liikennöinnin seurauksena. Raiteiden kunnossapito palauttaa raiteita maastossa niiden suunniteltuihin sijainteihin.

---

**K: Kuvaile "vaihde" — mitä se tarkoittaa Geoviitteessä?**

V: "vaihde" kuvaa Geoviitteessä rataverkon solmukohtaa, jossa junan on mahdollista siirtyä raiteelta toiselle. Vaihde sisältää tunnisteen, tyypin ja joukon ominaisuustietoja. Vaiheita on eri tyyppisiä. Vaihteen koko tyyppi koostuu vaihdetyypistä sekä vaihteen tarkemmista mitoista. Vaihdetyyppi määrittää vaihteen periaatteellisen toiminnan. Vaihdetyyppejä ovat esim. yksinkertainen vaihde (lyhenne YV), josta voi kulkea suoraan tai kääntyä yhdelle poikkeavalle raiteelle, tai kaksoisvaihde (lyhenne KV), josta voi kulkea suoraan tai kääntyä toiselle poikkeavista raiteista. Vaihteen koko tyyppi voi olla esim. "YV54-200N-1:9-O", jossa "YV" on vaihdetyyppi, "54" tarkoittaa kiskopainoa, "200" tarkoittaa poikkeavan raiteen kaaren sädettä, "N" tarkoittaa vaihdetyypin levittämätöntä versiota, "1:9" tarkoittaa risteyksen suhdetta (eli juinka "jyrkkään" suoraan menevä ja poikkeava raide sijoittuvat toisiinsa nähden), "O" tarkoittaa oikealle kääntyvää poikkeavaa raidetta. Geoviitteen vaihde on periaatteessa luonteeltaan looginen esiintymä rataverkon vaihteesta, eli Geoviitteessä on tieto siitä, millainen vaihde missäkin kohtaa on, joten maastossa pitäisi voida vaihtaa vaihteen tilalle uusi samanlainen vaihde, ilman että Geoviitteen rataverkkoon tehdään muutoksia. Tämä loogisuus on kuitenkin edelleen periaatteellisella tasolla ja vaihteen vaihto maastossa aiheuttaa tarpeen vaihtaa vaihde myös Geoviitteessä. Tämä fyysisen ja loogisen maailman eroavaisuus on kuitenkin hyvä pitää mielessä, kun tulevaisuuden ratkaisuja mietitään.

---

**K: Haluatko jatkaa muiden käsitteiden (ratanumero, pituusmittauslinja, tasakilometripiste jne.) kuvailua nyt, vai tallennetaanko tähän mennessä kerätyt tiedot ensin?**

V: Tallennetaan ensin kerätyt tiedot.

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

## 2026-04-29 — KS

**K: Kuvaile "segmentti" — mitä se tarkoittaa Geoviitteessä, ja miten se eroaa geometriaelementistä?**

V: "segmentti" kuuluu paikannuspohjan käsitteisiin on sijaintiraiteen geometrian pienin osanen. Segmentti sisältää geometrian pisteviivana. Mikäli segmentti on syntynyt linkityksessä geometriaelementin geometrian, se sisältää myös linkin geometriaelementtiin. Geoviitteen linkki-solmu-mallin linkin (edge) geometria koostuu peräkkäisitä segmenteistä, sijaintiraiteen geometria koostuu peräkkäisistä linkeistä.

---

**K: Kuvaile "taitepiste" — mitä se tarkoittaa Geoviitteessä?**

V: "taitepiste" on pystygeometrian määrityksessä käytettävä elementti, joka kertoo kuinka pystygeometria muuttuu taitepisteen matkalla. Taitepiste on matemaattinen esitys pystygeometrian muutoksesta ja siten äärettömän tarkka. Taitepisteitä käytetään Geoviitteessä laskettaessa sijaintiraiteen pisteille korkeus merenpinnasta. Taitepisteiden tietojen mukaan esitetään myös raiteen pystygeometrian kuvaaja, eli pituusleikkaus. Taitepisteiden välissä sijaitsee kaltevuusjakso.

"kaltevuusjakso" on pystygeometrian kahden taitepisteiden välinen osuus. Kaltevuusjaksot lasketaan Geoviitteessä taitepisteiden tietojen mukaan.

---

**K: Kuvaile "liikennepaikka" — miten se eroaa toiminnallisesta pisteestä, ja mitä tietoja siihen liittyy Geoviitteessä?**

V: "liikennepaikka" ja sen ero toiminnalliseen pisteesen on kuvattu "toiminnallinen piste" käsitteessä.

---

**K: Mitä tarkoittaa "RINF" — mikä se on ja mikä on Geoviitteen rooli siihen liittyen?**

V: RINF tarkoittaa Geoviitteen kontekstissa Euroopan laajuista rataverkon infrastruktuurirekisteriä (Register of Infrastructure, https://rinf.data.era.europa.eu). Geoviitteeseen RINF liittyy siksi, että EU:n säädösten mukaan RINF:ille täytyy toimittaa kansallisen rataverkon tietoja ja osa toimitettavista tiedoista on Geoviitteessä ylläpidettäviä, mm. raiteiden perustiedot sekä pystygeometria, toiminnalliset pisteet, toiminnallisten pisteiden väliset yhteydet (on sama kuin liikennepaikkojen väliset yhteydet) sekä rataverkon topologia. Geoviitteessä on toteutettuna API, josta suurimman osan tarvittavista tiedoista voi jo lukea. Geoviitteen API ei suoraan palauta tietoja RINF:in tarvitsemassa muodossa, vaan Geoviitteen ja RINF:in välissä on muita järjestelmiä tiedon kokoamista ja tiedon muodon muuntamista varten (Väylän analytiikka ja Paikkatietopalvelu PTP).

---

**K: Kuvaile "suunnitelmatilainen rataverkko" — mitä se tarkoittaa ja miksi se on tärkeä kehitystavoite?**

V: "suunnitelmatilainen rataverkko" on tapa mallintaa tulevaisuudessa rakennettavia rataverkon muutoksia Geoviitteen tietomalliin. Rataverkolla suuremmat muutokset tapahtuvat yleensä melko pitkäkestoisen prosessin seurauksena. Ensimmäistä kertaa Geoviite osallistuu prosessiin tarjoamalla pohjatietoja päätöksenteolle ja ratasuunnittelulle. Kun ratasuunnittelija on saanut hankkeen rakennussuunnitelman toteutettua, tiedot toimitetaan Geoviite-operaattorille. Rakennussuunnitelma sisältää myös rataverkon muutokseen liittyvät geometriasuunnitelmat. Geoviite-operaattori tallentaa geometriasuunnitelmat Geoviitteeseen, luo muutosta varten uuden suunnitelmatilan (eli suunnitelmakontekstin) ja linkittää geometriasuunnitelmien sisältämän geometrian suunnitelmatilan rataverkolle (tämä on Geoviitten osalta mahdollista, mutta ei vielä yleinen menettelytapa). Suunnitelmatilainen rataverkko on erillään virallisesta rataverkosta, eli suunnitelmatilainen rataverkko ei sotke virallisen rataverkon tietoja. Ratko-järjestelmä lukee suunnitelmatilaisen rataverkon tiedot Geoviitteestä (tätä toiminnallisuutta ei vielä ole toteutettu). Geoviite- ja Ratko-järjestelmissä on nyt tulevaisuudessa mahdollisesti rakennettava rataverkko jäsennettynä tietona. Rakentamisen aloittamiseen maastoon voi kulua vuosia, tai rakentamista ei välttämättä aloiteta koskaan. Jos rataverkon muutos rakennetaan maastoon, ratakohteiden valmistuessa niitä kirjataan Ratko-järjestelmään, jolloin ratakohteet yhdistetään suunnitelmatilaisen rataverkon raiteille. Kun rataverkon muutos on kokonaisuudessaan valmis, Geoviite-operaattori siirtää Geoviitteessä suunnitelmatilaisen rataverkon muutokset viralliseen rataverkkoon. Ratko-järjestelmä lukee virallisen rataverkon tiedot Geoviitteestä ja huomaa suunnitelmatilaisen rataverkon toteutuneen, joten Ratko ottaa Ratkon suunnitelmatilassa olevat raiteet käyttöön, jolloin niihin rakennusaikana yhdistetyt ratakohteet tulevat myös käyttöön (tämä Ratko:n toiminnallisuuden kuvaus on suuntaa antava, toimintoa ei vielä ole toteutettu).

Suunnitelmatila (tai suunnitelmakonteksti) on erillinen versio rataverkosta, se rakentuu virallisen rataverkon päälle ja sisältää vain suunnitelmatilassa muokattujen kohteiden (raide, vaihde, ratanumero jne.) tiedot. Jos viralliseen rataverkkoon tekee muutoksen sellaiseen kohteeseen (raide, vaihde, ratanumero jne.), jota ei ole muokattu suunnitelmatilassa, viralliseen rataverkkoon tehty muutos näkyy myös suunnitelmatilassa. Mutta jos viralliseen rataverkkoon tekee muutoksen sellaiseen kohteeseen, jota on muokattu myös suunnitelmatilassa, suunnitelmatilassa näkyy kohteesta suunnitelmatilan versio. Muutos on rajattu kohteen tarkkuudella, jos esim. raiteen nimeä muuttaa suunnitelmatilassa, raide kokonaisuudessaan katsotaan muuttuneksi suunnitelmatilassa. Samoin jos suunnitelmatilassa raiteen toisen pään geometriaa muokkaa vaikka lyhyeltäkin matkalta, koko raide katsotaan muuttuneeksi suunnitelmatilassa, joten samaan raiteeseen tehdyt virallisen rataverkon muutokset eivät näy suunnitelmatilassa. Koska suunnitelmatilaan tehdyt muutokset voivat odottaa viralliseen rataverkkoon siirtämistä useita vuosia, on melko todennäköistä, että suunnitelmatilassa muokattuun pitkään raiteeseen tehdään myös virallisen rataverkon puolella muutoksia, esim. datan eheyteen liittyviä korjauksia. Tällöin syntyy tilanne, että virallisen ja suunnitelmatilaisen rataverkon tiedot pitäisi saada yhdistettyä. Kirjoitushetkellä tällaista yhdistävää toiminnallisuutta ei vielä ollut olemassa, joten siirrettäessä raiteen tietoja suunnitelmatilasta viralliseen rataverkkoon, täytyy valita kumman rataverkon raiteen versio jää voimaan ja toistaa sitten toiseen rataverkkoon tehdyt muutokset raiteelle manuaalisesti. Sovelluskehittäjälle voi olla luontevaa verrata suunnitelmatilaa GIT-versionhallinnan haaraksi.

Samalla tavoin kuin virallisen rataverkon muutokset tehdään ensin luonnostilaiseen rataverkkoon ja julkaistaan sieltä viralliseen rataverkkoon, myös suunnitelmatilan muutokset tehdään ensin suunnitelmatilan luonnostilan ratavekkoon ja julkaistaan sitten suunnitelmatilan rataverkkoon. Suunnitelmatilan rataverkosta muutoksia on sitten mahdollista siirtä virallisen luonnostilan rataverkkoon ja sieltä julkaista viralliseen rataverkkoon. Tämä lisävaihe on tarpeen, jotta suunnitelmatilan muutokset voidaan luotettavasti sovittaa sen hetkiseen viralliseen rataverkkon ja että rataverkon muutokset saadaan validoitua. Muutosten julkaisu luonnostilasta viralliseen rataverkkoon vaatii kattavien validointisääntöjen läpäisyä, koska virallinen rataverkko halutaan pitää mahdollisimman ehyenä. Muutosten siirto suunnitelmatilasta viralliseen luonnostilaan on kevyemmin validoitu prosessi.

---

**K: Kuvaile "tilirataosa" — mitä se tarkoittaa ja miksi sen geometrian ylläpito halutaan siirtää Geoviitteeseen?**

V: "tilirataosa" on tällä hetkellä Ratko-järjestelmässä ylläpidettävä ja hyödynnettävä tieto. Tilirataosa on polygonimaisella alueella rajattu joukko raiteita (ja varmaan myös muita kohteita), joita halutaan käsitellä yhtenä kokonaisuutena. Nykyisessä raiteiden ylläpitoprosessissa on sellainen hankaluus, että raiteen geometrian muokkaaminen tapahtuu Geoviitteessä ja raiteen muuttunut geometria voi sijaita (osin) tilirataosan alueen ulkopuolella, jolloin raide (tai ehkä sen kohteet) eivät sisälly oikein tilirataosaan. Geoviite-operaattori usein kyllä huomaa tämän, kun hän raiteen geometrian muutoksen jälkeen tarkastelee tilannetta Ratko-järjestelmässä. Tässä tilanteessa Geoviite-operaattori pyytää tilirataosan alueen kasvattamista niin, että raiteen muuttunut geometria mahtuu alueen sisälle. Alueen päivittäminen Ratkoon kestää kuitenkin nykyisellä prosessilla viikkoja (tapahtuu erillisillä GIS-työkaluilla ja semi-manuaalisilla toimenpiteillä), ja koko päivittämistä odottava ajan tiedot ovat tilirataosan näkökulmasta väärin.

Geoviitteeseen on toteutettu toiminnallisten pisteiden polygonimaisten alueiden ylläpitämiseksi työkalu, jota Geoviite-operaattori käyttää. Operaattori on esittänyt toiveen, että myös tilirataosan aluetta voisi muokata Geoviitteessä, jolloin muokkaaminen tapahtuisi yhtä aikaa raiteen geometrian muokkaamisen kanssa, jolloin operaattori voisi varmistaa, että raiteen geometria pysyy tilirataosan alueen sisällä ja tilirataosan alue myös päivittyisi Ratkoon yhtä aikaa raiteen geometriamuutoksen kanssa. Tällä tavoin tiedot pysyisivät tilirataosan näkökulmasta oikeina jatkuvasti. Haasteena tilirataosien alueiden ylläpitoon Geoviitteessä on ainakin se, että tilirataosien muita tietoja ja elinkaarta hallittaisiin edelleen Ratkossa, jolloin integraatio olisi melko erikoinen. Integraatiossa Geoviitteen pitäisi lukea muuttuneet tilirataosat Ratkosta, Geoviitteessä tilirataosan alueita muokattaisiin ja Ratko lukisi muokatutu alueet takaisin Ratkoon. Tilanne on tosin jokseenkin sama jo liikennepaikkojen osalta, mutta Ratko ei toistaiseksi lue niiden alueita Geoviitteestä.

---

**K: Tarkennuskysymys rataverkon graafimallista: Käsiteluettelossa "Rataverkko — periaate" -osio sanoo verkon linkkien olevan "vaihteiden väliset kiskot", mutta toiminnallinen piste -kuvauksessa sanotaan "geometriat linkkejä solmujen välillä". Tarkoittavatko nämä samaa asiaa, vai onko eroa?**

V: Jos linkkejä ajattelee maastossa olevan rataverkon näkökulmasta, silloin "kiskot vaihteiden välillä" on oikea käsite, mutta Geoviitteen tietomallissa ei kuvata kiskoja, vaan raiteen suunniteltua geometriaa, joten Geoviitteen rataverkossa linkit ovat geometrisia pätkiä vaihteiden välillä.

---

**K: Tarkennuskysymys toiminnallisesta pisteestä: Toiminnallinen piste on määritelty osin "risteyskohta rataverkolla (eli käytännössä vaihde)". Onko toiminnallinen piste kuitenkin eri käsite kuin vaihde — eli voiko yksittäinen vaihde olla toiminnallinen piste, vai onko toiminnallinen piste aina useammasta vaihteesta koostuva kokonaisuus?**

V: Toiminnallinen piste on eri käsite kuin vaihde, kuitenkin yksi vaihde voi olla toiminnallinen piste, jonka RINF-tyyppi on "linjavaihde".

---

---

## 2026-04-30 — KS

**K: Kuvaile "luonnostila" — mitä se tarkoittaa Geoviitteessä ja miten se eroaa virallisesta rataverkosta?**

V: "luonnostila" on rateverkon versio, johon käyttäjä tekee rataverkon muutoksia ja josta muutokset julkaistaan varsinaiseen rataverkkoon. Sekä virallisella rataverkolla että kaikilla suunnitelmatilaisilla rataverkoilla on oma luonnostilansa. Luonnostilan muutokset julkaistaan esikatselunäkymässä.

---

**K: Kuvaile "ratakohteet" — mitä ne ovat Ratko-järjestelmässä ja miten ne liittyvät Geoviitteen rataverkkoon?**

V: Ratkossa ratakohteet ovat rataverkkoon liittyviä konkreettisia asioita, esim. radan liikennemerkkejä tai kiskotus. Ratkon tietomallissa ratakohteet ovat ilmeisesti aina yhdistetty raiteeseen. Geoviite näyttää kartalla Ratkon opastimia, jotka ovat myös ratakohteita, mutta muutoin Geoviite ei käsittele Ratkon ratakohteita.

---

**K: Kuvaile "Inframodel" — mikä se on ja miksi sitä käytetään geometriasuunnitelmien tiedostomuotona?**

V: "Inframodel" on standardi, jolla kuvataan infrarakenteita digitaalisessa muodossa niin, että eri ohjelmistot ja toimijat ymmärtävät tiedot samalla tavalla. Se perustuu kansainväliseen LandXML-formaattiin, mutta on sovitettu Suomen käytäntöihin. Kirjoitushetkellä Inframodel on Suomessa vakiintunut formaatti tarkan ratageometrian siirtämiseen.

Geoviite osaa lukea inframodel-tiedostoista raiteiden geometriaan liittyviä tietoja, mm. vaaka- ja pystygeometria, mutta ei esim. maaston pintamalleja. Geoviitte tukee inframodel versiota 4.0.3. Geoviite ei luo inframodel-tiedostoja.

---
