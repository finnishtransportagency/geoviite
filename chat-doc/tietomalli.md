# Geoviite — Tietomalli

> Tietomalli on kompleksinen. Tämä dokumentti on tarkoitettu erityisesti uusien kehittäjien perehdyttämiseksi.
> Käsitteiden kuvaukset perustuvat haastatteluihin — katso raakadata tiedostosta [haastattelut.md](./haastattelut.md).

## Käsiteluettelo

Alla listattu kaikki tunnistetut ydinkäsitteet. ✅ = kuvattu, ⬜ = kuvaus puuttuu (kerätään myöhemmissä haastatteluissa).

| Käsite | Tila |
|---|---|
| Raide (sijaintiraide) | ✅ |
| Vaihde | ✅ |
| Ratanumero | ✅ |
| Pituusmittauslinja | ✅ |
| Tasakilometripiste | ✅ |
| Rataosoitejärjestelmä | ✅ |
| Geometriasuunnitelma | ✅ |
| Geometriaelementti | ✅ |
| Segmentti | ✅ |
| Vaakageometria | ✅ |
| Pystygeometria | ✅ |
| Taitepiste | ✅ |
| Kaltevuusjakso | ✅ |
| Toiminnallinen piste | ✅ |
| Paikannuspohja | ✅ |
| Rataverkko | ✅ |
| Linkitys | ✅ |
| Suunnitelmatila | ✅ |
| Luonnostila | ✅ |
| Inframodel | ✅ |

---

## Rataverkko — periaate

Rataverkkoa voi ajatella **graafina**:
- **Solmukohdat** = vaihteet
- **Linkit** = vaihteiden välinen geometria (suunniteltu geometrinen pätkä)

> Huom: Maastossa linkkejä vastaavat fyysiset kiskot vaihteiden välillä, mutta Geoviitteen tietomallissa kuvataan raiteen **suunniteltua geometriaa**, ei kiskoja.

---

## Käsitteet

### Raide (sijaintiraide)

Sijaintiraide on tunniste ja joukko ominaisuustietoja yhtenäiselle geometriselle osuudelle rataverkolla. Se kattaa n kappaletta peräkkäisiä linkkejä graafissa.

**Tärkeä huomio — looginen vs. fyysinen raide:**
- Geoviitteessä raiteen geometria on raiteen **suunniteltu muoto**, ei fyysinen sijainti maastossa.
- Fyysinen raide pyritään rakentamaan suunnitelman mukaan, mutta käytännössä poikkeamaa esiintyy.
- Maastossa raide liikkuu ajan saatossa (maan liikkuminen, liikenne). Kunnossapito palauttaa raiteita suunniteltuihin sijainteihinsa.

---

### Vaihde

Vaihde kuvaa rataverkon solmukohtaa, jossa junan on mahdollista siirtyä raiteelta toiselle. Se sisältää tunnisteen, tyypin ja joukon ominaisuustietoja.

#### Vaihteen tyyppi

Vaihteen **koko tyyppi** koostuu vaihdetyypistä ja tarkemmista mitoista.

Esimerkki: `YV54-200N-1:9-O`

| Osa | Arvo | Selitys |
|---|---|---|
| Vaihdetyyppi | `YV` | Yksinkertainen vaihde |
| Kiskopaino | `54` | kg/m |
| Poikkeavan raiteen kaaren säde | `200` | metriä |
| Versio | `N` | Levittämätön versio |
| Risteyksen suhde | `1:9` | Suoran ja poikkeavan raiteen kulmasuhde |
| Suunta | `O` | Oikealle kääntyvä poikkeava raide |

#### Vaihdetyyppejä

| Lyhenne | Nimi | Kuvaus |
|---|---|---|
| YV | Yksinkertainen vaihde | Suoraan tai kääntyy yhdelle poikkeavalle raiteelle |
| KV | Kaksoisvaihde | Suoraan tai kääntyy toiselle kahdesta poikkeavasta raiteesta |

**Tärkeä huomio — looginen vs. fyysinen vaihde:**
Geoviitteen vaihde on **looginen esiintymä** — siinä on tieto siitä, millainen vaihde missäkin kohtaa on. Periaatteessa maastossa voisi vaihtaa vaihteen uuteen samantyyppiseen ilman muutoksia Geoviitteeseen. Käytännössä kuitenkin vaihteen vaihto maastossa aiheuttaa tarpeen päivittää myös Geoviitettä. Fyysisen ja loogisen maailman ero on syytä pitää mielessä tulevaisuuden ratkaisuja suunniteltaessa.

---

### Ratanumero

Ratanumero on **hallinnollinen käsite**, joka yhdistää osuuden rataverkon raiteista loogiseksi kokonaisuudeksi. Ratanumeroon kuuluvat raiteet sijoittuvat tyypillisesti jollekin yhteysvälille (esim. Helsinki–Turku) tai muodostavat ratapihalla yhtenäisen alueen. Kukin raide kuuluu johonkin ratanumeroon.

Ratanumero on perusta **rataosoitejärjestelmälle** — ks. alla.

---

### Pituusmittauslinja

Ratanumeron **lineaarinen muoto** rataverkolla. Jotta rataosoite voidaan määrittää ratanumeroa pitkin, ratanumerolla täytyy olla lineaarinen referenssireitti — tämä on pituusmittauslinja. Pituusmittauslinja on useimmiten jokin ratanumeron raiteista.

---

### Tasakilometripiste

**Koordinaattisijainti**, joka määrittää tietyn ratakilometrin alkamiskohdan pituusmittauslinjalla.

Ratakilometrit eivät ole tarkalleen kilometrin mittaisia, koska niiden rajat määräytyvät tasakilometripisteiden koordinaattisijaintien mukaan — eivät pituusmittauslinjan alusta kuljetun matkan mukaan. Tämä on tarkoituksellinen ratkaisu: kun rataverkkoon tehdään geometriamuutoksia, vain muutosalueen ratakilometrien rataosoitteet muuttuvat. Muutos ei "siirrä" kaikkia myöhempiä rataosoitteita.

Tasakilometripisteiden sijainti tallennetaan **GK-koordinaatistossa** (Gauss–Krüger), jotta niitä voidaan hyödyntää rataverkon geometriasuunnittelussa ilman koordinaattimuunnoksia.

---

### Rataosoitejärjestelmä

Menetelmä sijainnin määrittämiseksi rataverkolla. Sijainti kuvataan **rataosoitteena**, joka koostuu:
- **Ratanumerosta** (esim. `003`)
- **Ratakilometrisijainnista** ratanumeroa pitkin (esim. `0071+0408`)

#### Rataosoitteen tulkinta

Esimerkki: rataosoite `003 / 0071+0408` tarkoittaa:
1. Etsi ratanumeron `003` tasakilometripiste `71`.
2. Kulje pituusmittauslinjaa pitkin `408 metriä` eteenpäin.

Ennen GPS-aikaa menetelmä toimi maastossa: etsittiin kilometripylväs ja mitattiin mittapyörällä.

#### Tärkeä rajoitus — yksiulotteisuus

Rataosoite on **yksiulotteinen**: se määrittää sijainnin pituusmittauslinjalla. Kaikki koordinaatit, jotka sijaitsevat kohtisuoraan pituusmittauslinjalta, jakavat saman rataosoitteen — riippumatta siitä, kuinka kaukana pituusmittauslinjasta ne ovat. Rataosoitteella voidaan siis paikantaa myös pituusmittauslinjan viereisiä kohteita, mutta etäisyystieto ei sisälly rataosoitteeseen.

---

### Geometriasuunnitelma

Suunnitelma, joka sisältää raiteiden **suunnitellun geometrisen muodon**. Sisältää aina vähintään vaakageometrian, mahdollisesti myös pystygeometrian. Joissakin suunnitelmissa (erityisesti Sweco-yrityksen tuottamissa) on myös vaihdetietoja.

#### Suunnitelmien laatu

| Laatu | Kuvaus | Käyttötarkoitus |
|---|---|---|
| Laadukas | Ratasuunnittelijan tuottama ratahankkeessa | Rakentaminen, kunnossapito |
| Heikompi | Operaattorin (Welado) tai mittauksen pohjalta tuotettu | Paikannuspohjan muodostus alueilla, joissa laadukkaita suunnitelmia ei ole |

Suomen rataverkolla on alueita ilman laadukkaita digitaalisia suunnitelmia, koska ne on suunniteltu ennen digitaalista aikakautta.

#### Tiedostomuodot

- **Nykytila:** Inframodel-muotoiset XML-tiedostot (ks. [Inframodel](#inframodel))
- **Tuleva:** IFC-tiedostot (ei vielä yleisesti käytössä ratasuunnittelujärjestelmissä)

Alkuperäinen tiedosto tallennetaan Geoviitteeseen, mutta järjestelmä käyttää sisäistä tietorakennetta. Alkuperäistä tiedostoa käytetään vain vientitilanteessa.

#### Pääsy ja käyttö

Geometriasuunnitelmat **eivät ole vapaasti näkyvissä** kaikille käyttäjille — huonolaatuinen suunnitelma voisi muutoin päätyä liian vaativaan käyttöön (esim. kunnossapito). Suunnitelmia pyytävä taho ottaa yhteyttä Geoviite-operaattoriin, joka valitsee soveltuvat suunnitelmat.

---

### Geometriaelementti

Geometriasuunnitelman raiteen **vaakageometrian perusyksikkö**. Vaakageometria koostuu peräkkäisistä geometriaelementeistä.

#### Elementtityypit

| Tyyppi | Kuvaus |
|---|---|
| **Suora** | Kahden pisteen välinen suora viiva |
| **Kaari** | Ympyrän kaari |
| **Siirtymäkaari** | Spiraalimainen muoto, jossa kaaren säde muuttuu elementin matkalla |

Siirtymäkaaria käytetään nopeilla reiteillä estämään liian suuri sivuttaiskiihtyvyys kaarteessa.

Elementtien muoto on **matemaattisesti määritetty** — äärettömän tarkka. Elementti sisältää vain muodon määrittämiseen tarvittavat tiedot.

#### Linkitys sijaintiraiteelle

Geoviite-operaattori koostaa sijaintiraiteen geometrian linkittämällä geometriaelementtejä (tai niiden osia) sijaintiraiteelle. Tästä syntyy **segmentti**, jolla on viittaus alkuperäiseen geometriaelementtiin.

Segmentin geometria tallennetaan **pisteviivana**: kuljetaan matemaattista elementtiä pitkin metrin pituisin askelin.

Jos uusi linkitetty geometria ei yhdisty saumattomasti olemassa olevaan, väliin generoidaan **suora segmentti** — sillä ei ole viitettä geometriaelementtiin.

---

### Vaakageometria

Raiteen tai pituusmittauslinjan **kaksiulotteinen muoto** ylhäältä katsottuna (karttanäkymä). Geoviitteen kartalla esitetään juuri vaakageometria.

- **Geometriasuunnitelmassa:** koostuu geometriaelementeistä (matemaattinen esitys)
- **Sijaintiraiteella / pituusmittauslinjalla:** koostuu pisteviivasta, jonka pisteet sisältävät myös korkeustiedon → pisteviiva on käytännössä **3D-muoto**

---

### Pystygeometria

Raiteen **pystysuuntainen muoto** — kuinka korkealla raide kulkee suhteessa merenpintaan.

- **Geometriasuunnitelmassa:** määritetty **taitepisteinä** (matemaattinen, äärettömän tarkka esitys)
- **Sijaintiraiteella / pituusmittauslinjalla:** korkeus on laskettu taitepisteiden ja vaakageometrian avulla ja tallennettu pisteviivan pisteisiin

Pystygeometriaa esitetään Geoviitteessä **viivadiagrammina**: vaaka-akselilla raidetta pitkin kuljettu pituus, pystyakselilla korkeus merenpinnasta. Kuvaajassa näkyvät myös taitepisteiden tiedot ja kaltevuusjaksot.

---

### Toiminnallinen piste

**Eri käsite kuin vaihde** — toiminnallinen piste on korkeamman abstraktiotason käsite, joka voi sisältää yhden tai useamman vaihteen ja raiteita. Toiminnallinen piste on solmukohta rataverkolla, jossa on matkustajien tai rahdin käsittelyyn liittyviä toimintoja, tai joka on merkittävä risteyskohta rataverkolla. Esimerkkejä: rautatieasema, varikko, linjavaihde.

> **Huom:** Yksittäinen vaihde voi olla toiminnallinen piste, jonka RINF-tyyppi on `linjavaihde`. Tällöin toiminnallinen piste koostuu vain yhdestä vaihteesta. Tyypillisesti toiminnallinen piste kuitenkin kokoaa useamman vaihteen ja raiteen yhteen.

#### Rooli verkossa

Toiminnallinen piste yhdistää joukon vaihteita ja raiteita yhdeksi kokonaisuudeksi. Tällä muodostetaan **korkeamman abstraktiotason verkko**:
- **Solmut** = toiminnalliset pisteet
- **Linkit** = liikennepaikkojen väliset yhteydet

Toiminnalliset pisteet ja liikennepaikkojen väliset yhteydet ovat **RINF-raportoitavia** tietoja.

#### Tietojen ylläpito

| Kohde | Ylläpito |
|---|---|
| Liikennepaikat (perustiedot, elinkaari) | Ratko-järjestelmässä |
| Liikennepaikat (polygoni, RINF-tyyppi jne.) | Geoviitteessä |
| Muut toiminnalliset pisteet (kaikki tiedot) | Geoviitteessä |

#### RINF-tyypit

asema, asema (pieni), matkustaja-asema, tavara-asema, varikko, tekninen ratapiha, seisake, kohtauspaikka, valtakunnan raja, vaihtotyöratapiha, raideleveyden vaihtumiskohta, linjavaihde, yksityinen, omistusraja, ylikulku.

---

### Paikannuspohja / Rataverkko

Geoviitteen kontekstissa **"paikannuspohja"** ja **"rataverkko"** ovat synonyymejä. Paikannuspohja on kuitenkin kuvaavampi termi, sillä se tuo esille kaksi olennaista ominaisuutta:

1. **Käyttötarkoitus:** Paikannuspohjaa käytetään kohteiden paikantamiseen rataverkolla.
2. **Epätarkkuusolettama:** Paikannuspohja ei ole geometrisesti matemaattisen tarkka.

#### Rakenne

Paikannuspohja koostuu:
- **Sijaintiraiteiden geometrioista** (tallennettu pisteviivana)
- **Rataosoitejärjestelmän tiedoista:** ratanumerot ja tasakilometripisteet

#### Muodostaminen

Rataverkko muodostuu linkittämällä geometriasuunnitelmien geometriaa sijaintiraiteille. Koska geometriasuunnitelmat kattavat yleensä vain pienen osan rataverkosta, yhtenäinen rataverkko on yhdistelmä monesta eri suunnitelmasta. Eri suunnitelmien geometriat eivät ole aina täysin yhteensopivia, mistä syntyy lisää epätarkkuutta.

#### Tärkeä rajoitus

> Paikannuspohjan geometriatietoja **ei tule käyttää sellaisenaan** suurta tarkkuutta vaativissa tehtävissä, kuten uusien geometriasuunnitelmien lähtökohtana.

---

### Linkitys

Geoviite-operaattorin suorittama toimenpide, jolla käsitteiden välille luodaan yhteys (linkki). Linkkiä seuraamalla voidaan jäljittää tiedon alkuperä.

#### Linkitystoiminnot

| Linkitys | Kuvaus |
|---|---|
| Geometriasuunnitelman raide → sijaintiraide | Luo pisteviivamaista geometriaa sijaintiraiteelle geometriaelementtien mukaan; samalla syntyy jäljitettävä yhteys suunnitelmaan |
| Geometriasuunnitelman vaihde → paikannuspohjan vaihde | Yhdistää suunnitelman vaihteen verkon vaihteeseen |
| Geometriasuunnitelman tasakilometripiste → paikannuspohjan tasakilometripiste | Yhdistää suunnitelman tasakilometripisteen verkon pisteeseen |
| Paikannuspohjan vaihde → sijaintiraide | Kytkee vaihteen sijaintiraiteeseen verkossa |
| Vaihteet ja sijaintiraiteet → toiminnallinen piste | Kokoaa rataverkon elementtejä toiminnallisen pisteen alle |

---

### Segmentti

Sijaintiraiteen geometrian **pienin yksikkö**. Segmentti sisältää geometrian pisteviivana.

Segmenttejä on kahta tyyppiä:
- **Linkitetty segmentti:** syntynyt geometriaelementin linkityksessä, sisältää viittauksen geometriaelementtiin → geometrian alkuperä on jäljitettävissä.
- **Generoitu segmentti:** suora täydennyssegmentti, joka generoidaan kahden peräkkäisen linkitetyn osuuden väliin, jos ne eivät yhdisty saumattomasti. Sillä ei ole viittausta geometriaelementtiin.

#### Hierarkia

```
Geometriaelementti  (matemaattinen, suunnitelmassa)
     ↓ linkitys
  Segmentti         (pisteviivaosanen, paikannuspohjassa)
     ↓ peräkkäin
  Linkki / Edge     (linkin geometria = peräkkäiset segmentit)
     ↓ peräkkäin
  Sijaintiraide     (= peräkkäiset linkit)
```

---

### Taitepiste

Geometriasuunnitelman pystygeometrian **matemaattinen perusyksikkö**. Taitepiste kuvaa miten pystygeometria muuttuu taitepisteessä — esitys on matemaattinen ja siten äärettömän tarkka.

Käyttö Geoviitteessä:
- Taitepisteiden tietojen avulla lasketaan sijaintiraiteen pisteviivan pisteille korkeus merenpinnasta.
- Taitepisteet näkyvät raiteen pystygeometrian kuvaajassa (**pituusleikkaus**).

---

### Kaltevuusjakso

Pystygeometrian kahden taitepisteen välinen osuus. Kaltevuusjakso on tasaisesti kalteva osuus, joka lasketaan Geoviitteessä taitepisteiden tietojen perusteella. Kaltevuusjaksot esitetään raiteen pituusleikkauskuvaajassa.

---

### Suunnitelmatila (suunnitelmakonteksti)

Erillinen **versio rataverkosta**, joka rakentuu virallisen rataverkon päälle ja sisältää vain suunnitelmatilassa muokattujen kohteiden tiedot. Analogia sovelluskehittäjälle: suunnitelmatila vastaa GIT-versionhallinnan haaraa (branch).

#### Toimintaperiaate

- Jos viralliseen rataverkkoon tekee muutoksen kohteeseen, jota **ei ole muokattu** suunnitelmatilassa → muutos näkyy myös suunnitelmatilassa.
- Jos viralliseen rataverkkoon tekee muutoksen kohteeseen, jota **on muokattu** suunnitelmatilassa → suunnitelmatilassa näkyy suunnitelmatilan versio (ei virallisen rataverkon muutos).
- Muutos on **kohdetason granulariteetilla**: jos yhtäkin raiteen ominaisuutta tai geometrian osaa muuttaa suunnitelmatilassa, koko raide katsotaan muuttuneeksi suunnitelmatilassa.

#### Julkaisuprosessi

```
Suunnitelmatilan luonnostila
     ↓ julkaisu
Suunnitelmatilan rataverkko
     ↓ siirto (kevyt validointi)
Virallinen luonnostila
     ↓ julkaisu (kattava validointi)
Virallinen rataverkko
```

#### Tunnettu haaste

Koska suunnitelmatilassa olevat muutokset voivat odottaa toteutumistaan vuosia, viralliseen rataverkkoon tehdään samaan aikaan muutoksia samoihin kohteisiin (esim. datan eheyskorjauksia). Kun suunnitelmatilan muutokset siirretään viralliseen rataverkkoon, täytyy valita kumpi versio jää voimaan ja toistaa toisen rataverkon muutokset manuaalisesti. Yhdistävää (merge) toiminnallisuutta ei kirjoitushetkellä vielä ollut.

#### Käyttötapaus: rataverkon muutoshanke

1. Geoviite tarjoaa pohjatietoja päätöksenteolle ja ratasuunnittelulle.
2. Rakennussuunnitelma valmistuu → geometriasuunnitelmat toimitetaan Geoviite-operaattorille.
3. Operaattori tallentaa geometriasuunnitelmat ja luo uuden suunnitelmatilan.
4. Operaattori linkittää geometrian suunnitelmatilan rataverkolle.
5. Ratko lukee suunnitelmatilaisen rataverkon tiedot *(ei vielä toteutettu)*.
6. Rakentamisen edetessä ratakohteet kirjataan Ratkoon ja yhdistetään suunnitelmatilan raiteisiin.
7. Kun muutos on valmis, operaattori siirtää suunnitelmatilan muutokset viralliseen rataverkkoon.
8. Ratko huomaa suunnitelmatilaisen rataverkon toteutuneen ja ottaa Ratkon suunnitelmatilaiset raiteet käyttöön *(ei vielä toteutettu)*.

---

### Luonnostila

Rataverkon versio, johon käyttäjä tekee muutoksia ennen niiden julkaisua viralliseen rataverkkoon. Muutokset esikatsellaan esikatselunäkymässä ennen julkaisua.

Sekä **virallisella rataverkolla** että kaikilla **suunnitelmatilaisilla rataverkoilla** on oma luonnostilansa:

```
Luonnostila  →  julkaisu  →  Virallinen rataverkko
Suunnitelmatilan luonnostila  →  julkaisu  →  Suunnitelmatilan rataverkko
```

Virallisen rataverkon julkaisu vaatii kattavien validointisääntöjen läpäisyä. Suunnitelmatilan julkaisu on kevyemmin validoitu.

---

### Inframodel

Suomessa vakiintunut standardi infrarakenteiden digitaaliseen kuvaamiseen. Perustuu kansainväliseen **LandXML**-formaattiin, mutta on sovitettu suomalaisiin käytäntöihin. Tiedostomuoto on XML.

#### Geoviitteen tuki

- **Tuettu versio:** Inframodel 4.0.3
- **Mitä luetaan:** Raiteiden vaaka- ja pystygeometria (ei esim. maaston pintamalleja)
- **Mitä ei tueta:** Inframodel-tiedostojen luonti — Geoviite ainoastaan lukee tiedostoja tuonnissa

Alkuperäinen tiedosto tallennetaan Geoviitteeseen, mutta järjestelmä käyttää sisäistä tietorakennetta. Alkuperäistä tiedostoa käytetään vain vientitilanteessa.

> **Tuleva:** IFC-tiedostojen tuki suunnitteilla, mutta ratasuunnittelujärjestelmät eivät vielä tue IFC:tä riittävän hyvin.

<!-- Lisää käsitteet tähän sitä mukaa kun haastatteluja tehdään -->
