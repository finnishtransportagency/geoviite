# Automaatiotestit Geoviitteessä

## Johdanto

Geoviitteen koodikanta sisältää pääosin kolmea eri testityyppiä:

* Yksikkötestejä (Unit),
* Integraatiotestejä (Integration),
* E2E-testejä (End-to-End).

Lähtökohtaisesti testejä on pyritty rajaamaan suhteellisen pieniksi, sillä testattavan tilanteen tulkinta on
yksinkertaisempaa, kun testattava ominaisuusmäärä on rajatumpi. Vastaavasti jos koodikantaan tehdyt muutokset
aiheuttavat testin epäonnistumisen muutosten jälkeen, on usein yksiselitteisempää huomata ongelma jos vain yksi testi
epäonnistuu verrattuna vaikkapa moneen kymmeneen.

Rakenteellisena tavoitteena yksinkertaisia (yksikkötestejä) olisi määrällisesti eniten, hieman monimutkaisempia testejä
(integraatiotestejä) vähemmän, ja monimutkaisia, laajoja testejä (E2E-testejä) olisi vähiten. On usein helpompaa tulkita
yksittäisen yksikkötestien epäonnistumisesta mikä meni vikaan, kuin tulkita E2E-testin epäonnistumisesta miksi jokin
tietty pieni asia sattuikin epäonnistumaan tietyn E2E-testin suorituksen aikana.

## Testikoodista

Kotlin-koodin testit hyödyntävät Jupiter-kirjastoa. Lähellä Geoviitteen tiedostohierarkiaa olevan intron voi lukea
esimerkiksi
osoitteesta [https://www.jetbrains.com/guide/java/tutorials/working-with-gradle/tour-of-a-gradle-project/](https://www.jetbrains.com/guide/java/tutorials/working-with-gradle/tour-of-a-gradle-project/)

Testiluokat saattavat sisältää annotointeja riippuen hieman minkätyyppistä ympäristöä ne tarvitsevat. Esimerkiksi
integraatio- sekä E2E-testit hyödyntävät `@SpringBootTest`-annotaatiota, sekä usein testiluokille määritellään myös
Springin ympäristöön liittyviä konfiguraatioannotointeja, kuten esimerkiksi `@ActiveProfiles("dev", "test")`.

Jokainen testiluokan sisältämä testifunktio annotoidaan Jupiter-kirjastosta löytyvällä `@Test`-annotaatiolla, jotta
muutkin kirjastot tunnistavat testifunktiot. Testiluokat saattavat kuitenkin myös sisältää datan alustukseen,
tilanteiden tarkistukseen tai vastaavaan muuhun testeihin liittyvään toiminnallisuuteen liittyviä funktioita, jotka
eivät sisällä `@Test`-annotaatiota, sillä ne eivät ole varsinaisia testejä, joita tulisi automaattisesti suorittaa.

Testikooditiedostojen päätteet (*Test, *IT, *UI) merkkaavat testitiedoston tyypin. Näitä päätteitä käytetään myös
tietyntyyppisten testien suorittamiseen Gradlen kautta.

Kotlin tukee testifunktioiden nimeämistä välilyöntejä hyödyntäen, joka olisi suositeltu tapa:

```
@Test
fun äläNimeäTestiäNäin()

@Test
fun `Nimeä testi mielummin näin`()
```

# Yksikkötestit (Unit tests)

Yksikkötestillä eli unit-testillä tarkoitetaan yksinkertaista, yhdessä prosessissa suoritettavaa, rajattua
toiminnallisuutta testaavaa testiä. Tyypillinen yksikkötesti kokeilee, että yksi funktio toimii oikealla tavalla.
Esimerkki yksikkötestistä olisi vaikkapa varmistus, että jotain oliota formatoiva funktio palauttaa oletetun
merkkijonon.

Yksikkötestien kirjoittaminen helpottuu, kun varsinainen testattava logiikka on kirjoitettu puhtaana funktiona. Lyhyesti
kuvattuna puhtaat funktiot ovat sellaisia, joiden arvo perustuu pelkästään funktiolle annettuihin argumentteihin ja joka
palauttaa tismalleen identtisen arvon samoilla argumenteilla, riippumatta ohjelmiston muusta tilasta.

Koska yksikkötestit täytyy olla mahdollista suorittaa nopeasti yhdessä prosessissa, ne eivät
voi esimerkiksi ottaa tietokantaan yhteyksiä.

## Yksikkötestien sijainti ja nimitys

Geoviitteen yksikkötestit sijaitsevat tietylle toiminnallisuudelle varatussa tiedostohierarkiassa, ja testikoodin
polkurakenne vastaa varsinaisen logiikan polkurakennetta. Kun varsinainen logiikka löytyy esimerkiksi polusta

```
infra/src/main/kotlin/fi/fta/geoviite/infra/geometry
```

niin geometry-pakettiin liittyvä yksikkötestikoodi löytyy vastaavasti polusta

```
infra/src/test/kotlin/fi/fta/geoviite/infra/geometry
```

joiden ainut ero on kansio 'main|test' polun alkupuolella.

Yksikkötestitiedostojen nimet noudattavat rakennetta `<TestattavaOminaisuusTiedosto>Test.kt`, eli tiedoston nimi alkaa
main-puolelta löytyvällä tiedostonimellä. Vastaavasti yksikkötestitiedosto taas päättyy `Test`-loppuliitteellä.

Siispä esimerkiksi varsinaista logiikkaa sisältävän tiedoston

```
infra/src/main/kotlin/fi/fta/geoviite/infra/geometry/ElementListing.kt
```

yksikkötestit löytyvät tiedostosta

```
infra/src/test/kotlin/fi/fta/geoviite/infra/geometry/ElementListingTest.kt
```

# Integraatiotestit (integration tests)

Lyhyesti integraatiotestit voivat hyödyntää useampaa kuin yhtä prosessia. Geoviitteen mielessä tämä tarkoittaa, että ne
tyypillisesti hyödyntävät tietokantaa (sekä Springin kontekstia). Geoviitteen integraatiotestit eivät lähtökohtaisesti
käytä valepalveluja (mock service), kuten valetietokantaa, lukuunottamatta ulkoisiin integraatioihin (esim. Ratko,
ProjektiVelho) muodostettuja integraatiotestejä. Pyrkimyksenä voisi kuitenkin pitää oikeiden ulkoisten palveluiden
hyödyntämistä mock-palveluiden sijaan, kunhan palvelut ovat Geoviitteen kehittäjien hallittavissa. Esimerkiksi
Ratko sekä ProjektiVelho ovat Geoviitteestä (ainakin pääosin) riippumattomissa, ja voivat myös muuttua Geoviitteestä
riippumattomasti, jolloin Geoviitteen integraatiotestit olisivat virheherkempiä, minkä vuoksi mock-palveluita on
näiden ulkoisten rajapintojen osalta päätetty kuitenkin hyödyntää.

Geoviitteen integraatiotestit on lähtökohtaisesti muodostettu DAO (data access object) sekä palvelu (Service)
tasoille. DAO-testejä ei voida toteuttaa yksikkötesteinä, sillä ne hyödyntävät tietokantaa. Vastaavasti palvelut
hyödyntävät tyypillisesti logiikassaan tietokannan dataa oman DAO:nsa taikka jonkin toisen palvelun kautta, joten
palveluidenkaan testejä ei lähtökohtaisesti voida toteuttaa yksikkötesteinä.

Esimerkkinä integraatiotestistä voidaan pitää esimerkiksi tietyn tietorakenteen hakemista oikein tietokannasta DAO:n
avulla.

## Integraatiotestien sijainti ja nimitys

Integraatiotestien nimet noudattavat rakennetta `<TestattavaOminaisuusTiedosto>IT.kt`, eli tiedoston nimi alkaa
main-puolelta löytyvällä tiedostonimellä. Vastaavasti testitiedosto taas päättyy `IT`-loppuliitteellä. Integraatiotestit
sijaitsevat yksikkötestien tavoin lähes vastaavassa tiedostopolussa, kuin varsinainen testattava logiikka.

Kun esimerkiksi varsinaista testattavaa logiikkaa sisältävä tiedosto on

```
infra/src/main/kotlin/fi/fta/geoviite/infra/geometry/GeometryService.kt
```

niin sen integraatiotestit löytyvät tiedostosta

```
infra/src/test/kotlin/fi/fta/geoviite/infra/geometry/GeometryServiceIT.kt
```

jossa kannattaa huomioida jälleen polun alussa ero "main|test"-kansion välillä.

# E2E-testit (End-to-End tests)

E2E-testeillä tarkoitetaan Geoviitteen koodikannassa jonkin käyttöliittymällä asti olevan toiminnallisuuden testaamista,
lyhyesti sanottuna Geoviitteen E2E-testit ovat siis käyttöliittymätestejä. Geoviitteen E2E-testit edellyttävät
tietokannan, Spring-backendin suorittamista sekä menetelmää UI-koodibundlen palvelimista testiselaimelle (Webpack).
Koska E2E-testit tekevät automatisoidusti käyttöliittymällä toimintoja, ja käyttöliittymä kutsuu Geoviitteen
Kotlin-backendiä, joka taas hyödyntää tietokantaa, E2E-testit testaavat varsin laajaa osaa toiminnallisuudesta. Siispä
ne ovat hitaampia ja raskaampia suorittaa verrattuna yksikkö- tai integraatiotesteihin. Ne kuitenkin testaavat jotain
rajattua toiminnallisuutta käyttöliittymän näkökulmasta.

E2E-testit on toteuttu Geoviitteessä Kotlin-koodin puolella, sillä tällä tavoin halutun tilanteen datan alustus on
yksinkertaisempaa. E2E-testit voivat siis hyödyntää esimerkiksi samoja alkutilanteen tai -datan alustusfunktioita, kuin
Kotlin-koodin yksikkö- sekä integraatiotestit. Ne voivat lisäksi hyödyntää myös Service- sekä suoraan Dao-luokista
löytyvää toiminnallisuutta.

Geoviitteen E2E-testit on toteutettu Selenium-selainautomatisointikirjastoa hyödyntäen. E2E-testi koostuu
siis datan alustuksesta, selaimen komentamisesta testattavien asioiden tekemiseen Geoviitteen käyttöliittymän kautta,
ja lopuksi tilanteen oikeellisuuden varmistuksesta.

Oletusarvoisesti E2E-testit suoritetaan testiselaimessa, joka ei avaudu tyypillisen selaimen kaltaisesti ikkunaan. Näin
E2E-testit voidaan suorittaa myös ympäristössä, jossa ei esimerkiksi ole näyttöä käytettävissä eli esimerkiksi
palvelimella. Tätä testiselainta kutsutaan englanniksi headless browseriksi. E2E-testien kirjoittamista kuitenkin
helpoittaa runsaasti, kun devaaja näkee testiselaimen tilan suoritettaessa testiä esimerkiksi tiettyyn pisteeseen asti.
Testiselaimen voi kytkeä ikkunan avaavaksi muuttamalla tiedostosta

```
infra/src/test/kotlin/fi/fta/geoviite/infra/ui/util/Browser.kt
```

arvon `DEV_DEBUG = false -> DEV_DEBUG = true`. Ennen E2E-testin viemistä Gittiin tämä kannattaa kuitenkin kytkeä pois
päältä taikka jättää commitoimatta, jotta E2E-testit voidaan edelleen suorittaa palvelimella.

## E2E-testien sijainti ja nimitys

E2E-testitiedostot sijaitsevat hieman eri polussa verrattuna yksikkö- ja integraatiotesteihin. Niiden juuri on

```
infra/src/test/kotlin/fi/fta/geoviite/infra/ui/
```

joka kuitenkin sisältää useamman `testgroup<N>`-kansion. E2E-testejä on ryhmitelty eri kansioihin, jotta
E2E-testikokonaisuuden suorittaminen olisi nopeampaa ympäristössä, jossa on useampi kuin yksi palvelin käytettävissä
testien suorittamista varten. Jokainen E2E-testiryhmä ajetaan siis dev-ympäristössä samanaikaisesti eri palvelimilla,
jotta E2E-testikokonaisuuden suoritus olisi nopeampaa. Ajettaessa E2E-testejä lokaalisti tätä ryhmittelyä ei kuitenkaan
hyödynnetä.

E2E-testitiedostojen nimitys noudattaa rakennetta `<JokinTestattavaOminaisuus>UI.kt`. Huomionarvoisesti E2E-testeillä
ei ole vastinparitiedostoa varsinaisen logiikkakoodin puolella verrattuna yksikkö- ja integraatiotesteihin.

# Testidatan alustusperiaatteita

Testien tulisi alustaa kaikki tarvitsemansa data tai tila testin alussa, jotta ylimääräisiä riippuvuuksia testien
välille ei synny ja testattavan tilan tulkinta on helpompaa. Geoviitteen koodikanta kuitenkin sisältää eritasoisia
testidatan luontiin tarkoitettuja funktioita, jotka helpottavat testikoodin kirjoittajaa muodostamaan haluamansa
tilanteen sekä usein auttavat myös testitilanteen tulkinnassa. Tavoitteena olisi, että mitä yksinkertaisempi tilanne,
sitä yksinkertaisempia datanalustusfunktioita hyödynnetään. Jos taas testattava tilanne on monimutkaisempi, käytetään
laajemman testattavan tilan muodostavia funktioita. Näitä voi myös muodostaa lisää tarpeiden mukaisesti.

Testidatan alustuksen tavoitteena olisi välttää käyttämästä liian suuren tilanteen alustusta yksinkertaisen tilanteen
testaamista varten: testikoodin pitäisi alustaa minimaalinen määrä dataa, jotta testi on mahdollista suorittaa.
Minimaalisen tilan muodostamisella pyritään välttämään riippuvuussuhteiden muodostumista testien välillä. Jos
esimerkiksi parikymmentä testiä hyödyntäisi samaa testidataa, ja yksi testi tarvitsisikin hieman laajemman tilan, niin
tämän pienen tilamuutoksen tekeminen testidataan aiheuttaisi myös lisätilan päätymisen kaikkiin muihinkin testeihin,
jotka hyödyntäisivät samaa testidataa. Tämä taas saattaa aiheuttaa testibugeja tai muutosedellytyksiä muihinkin
testeihin, jotka hyödyntäisivät tätä testidatajoukkoa. Esimerkiksi jokin toinen samaa testidataa hyödyntävä testi ei
välttämättä menisi enää ilman muutoksia läpi, tai se testaisi väärää tilannetta eli se aiheuttaisi esimerkiksi vääriä
positiivisia tai vääriä negatiivisia testituloksia (tyypin I ja II virheitä, hylkäämis- ja hyväksymisvirheitä).
