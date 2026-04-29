# Geoviite — Tietomalli

> Tietomalli on kompleksinen. Tämä dokumentti on tarkoitettu erityisesti uusien kehittäjien perehdyttämiseksi.
> Käsitteiden kuvaukset perustuvat haastatteluihin — katso raakadata tiedostoista [haastattelu-2026-04-24.md](./haastattelu-2026-04-24.md) ja [haastattelut.md](./haastattelut.md).

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
| Vaakageometria | ✅ |
| Pystygeometria | ✅ |
| Toiminnallinen piste | ✅ |
| Paikannuspohja | ✅ |
| Rataverkko | ✅ |
| Linkitys | ✅ |

---

## Rataverkko — periaate

Rataverkkoa voi ajatella **suunnattuna graafina**:
- **Solmukohdat** = vaihteet
- **Linkit** = vaihteiden väliset kiskot

---

## Käsitteet

### Raide (sijaintiraide)

Sijaintiraide on tunniste ja joukko ominaisuustietoja yhtenäiselle geometriselle osuudelle rataverkolla. Se kattaa n kappaletta peräkkäisiä linkkejä graafissa.

**Tärkeä huomio — looginen vs. fyysinen raide:**
- Geoviitteessä raiteen geometria on raiteen **suunniteltu muoto**, ei fyysinen sijainti maastossa.
- Fyysinen raide pyritään rakentamaan suunnitelman mukaan, mutta käytännössä poikkeamaa esiintyy.
- Maastossa raide liikkuu ajan saatossa (maan liikkuminen, liikenne). Kunnossapito palauttaa raiteita suunniteltuihin sijainteihinsa.

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

- **Nykytila:** Inframodel-muotoiset XML-tiedostot
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

**Solmukohta rataverkolla**, jossa on matkustajien tai rahdin käsittelyyn liittyviä toimintoja, tai joka on rataverkon risteyskohta (vaihde). Esimerkkejä: rautatieasema, varikko, linjavaihde.

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

<!-- Lisää käsitteet tähän sitä mukaa kun haastatteluja tehdään -->
