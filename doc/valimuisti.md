# Suorituskyvyn huomiointi Geoviitteessä

## Backend välimuisti

Backendissä tallennetaan hakujen ja laskennan tuloksia välimuistiin sekä käyttäen Springin cache-abstraktiota
(`@Cacheable`) että suoraan DAO- ja service-luokissa Caffeine cache -kirjastolla.

### Spring cache-abstraktio
`@Cacheable` on käytännöllinen yksittäisten asioiden välimuistia varten tai muihin yksinkertaisiin tapauksiin.
Välimuistia voidaan konfiguroida `CacheConfiguration.kt` tiedostossa toimimaan halutulla kestolla, koolla, jne.
`@Cacheable` ei kuitenkaan sovellu hyvin käyttötapauksiin, joissa samaa dataa halutaan hakea useilla eri tavoilla, tai
sekaisin monihakuina ja yksittäisinä hakuina, jolloin eri haut jouduttaisiin tallentamaan välimuistiin erikseen. 

Springin cache-abstraktiota käyttäessä on syytä muistaa että annotaatiot toimivat wrappaamalla palvelu automattisesti
toiseen, välimuistin toteuttavaan olioon ja vaihtamalla se käyttöön Springin kontekstissa. Tämä tarkoittaa että 
funktioiden annotoinnit eivät tee mitään jos funktiota kutsutaan saman olion sisältä. Jotta välimuisti toimii, funktiota
tulee aina kutsua vain Springin wrappaamalle (auto-injektoidulle) instanssille, tyypillisesti toisesta palvelusta mutta
tarvittaessa myös injektoimalla instanssi takaisin itseensä.

### Caffeine cache suoraan palveluissa

Monimutkaisemmissa tapauksissa on parempi käyttää välimuistia suoraan koodista Caffeine cache:lla. Tällöin
annotaatioiden erikoisuudet eivät ole ongelma ja on helposti toteuttavissa monimutkaisempia ja keskenään erilaisia
hakuja, jotka hyödyntävät samaa välimuistia. Näin on tehtävissä esimerkiksi monihakuja, joissa osa tuloksista löytyy
välimuistista ja loput haetaan yhdellä haulla kannasta tai yhdistää tuollaiseen hakuun vielä yksittäisen käsitteen
haku saman välimuistin läpi.

### Välimuistin esilämmitys

Välimuistin esilämmitys tapahtuu `CachePreloader`-luokassa, joka hakee käsitteiden tuoreet versiot välimuistin läpi
ensin kerran käynnistyksessä ja sen jälkeen periodisesti. Tämä varmistaa että datan voimassaolevat versiot ovat aina
välimuistissa ja niiden haku on nopeaa. Välimuistin konfiguraatiossa sille asetetaan esilämmityksen periodista hakua
pidempi kesto, jolloin vanhentuneet käsitteet tippuvat muistista pois koska esilämmitys ei enää kysele niitä, mutta
aktiiviset versiot säilyvät käytöstä riipumatta.

On huomattavaa että vaikka välimuistin periodinen virkistys hakeekin aina kaikki aktiiviset käsitteet, sen ei tarvitse
hakea kaikista riveistä muuta kuin välimuistin käyttämät avaimet. Itse rivit haetaan vain jos avain on tällä välin
vaihtunut eikä välimuisti sisällä vielä uutta versiota. Näin ollen yleensä vain ensimmäinen lämmitys (käynnistyksessä)
joutuu oikeasti siirtämään kaiken muistettavan datan kannasta. Myöhemmät kierrokset harvoin hakevat itse dataa lainkaan
- päivityksen rooli on vain koskea olioihin välimuistissa, jolloin ne säilytetään taas seuraavalle kierrokselle.

### Välimuistin avaimet

Välimuistin avaimet voivat riippua käyttöpaikasta ja tietenkin itse kyselystä, mutta tyypillisin Geoviitteessä nähtävä
välimuistin avain on [tietokannan riviversio](./tietokanta.md#audit-ja-versiointi). Koska tietokantarivin versio on
muuttumaton, sen id + versionumero yksilöi haettavan olion sisällön täydellisesti, ja siksi muodostaa hyvän avaimen
välimuistille.

Riviversioita on Kotlin-puolella käytännössä kahta tyyppiä: `RowVersion` niille käsitteille joilla yksinkertainen ID
yksilöi itse päätaulun rivin ja `LayoutRowVersion` Layout-puolen käsitteille, joissa ID:n lisäksi päätaulun riviä
yksilöi [Layout konteksti](./paikannuspohjan_kontekstit.md). Välimuistin kannalta molempia voidaan käyttää samoin.

Versioita voidaan myös koostaa jos välimuistin avaimeen tarvitaan useamman käsitteen yhdistelmä. Näin on tehty
esimerkiksi [geokoodauskonteksteilla](./geokoodaus.md#geokoodauskonteksti-geocodingcontext), jonka sisältö riippuu
ratanumerosta, sen pituusmittauslinjasta ja kilometripylväistä. Vasta noiden kaikkien versioiden yhdistelmä on riittävä
yksilöimään koko geokoodauskontekstin, joten välimuistin avaimen on sisällettävä ne kaikki. Kun välimuistin avain on
haettu, sen viittaamat oliot on edelleen nopea hakea kontekstin luomista varten esilämmitetyistä käsitekohtaisista
välimuisteista avaimen sisältämillä versioilla. Luontiin sisältyy kuitenkin myös ei-triviaali määrä laskentaa, joten
valmiit kontekstit tallennetaan omaan välimuistiinsa haetulla avaimella.

### Olioiden haku välimuistin läpi

Välimuistin käytön peruskuvio siis toistuu Geoviitteessä usein ja on hyvä pitää kehittäessä mielessä:
1. Ensin haetaan käsitteelle (ID:llä, hakuehdoilla, kaikki listaamalla) välimuistin avaimet, eli yleensä rivien versiot
   (`RowVersion`, `LayoutRowVersion`)
   * Tämä haku menee aina kantaan asti, sillä vain kanta voi lopulta tietää onko joku mahdollisesti muuttanut käsitettä
   * Haetaan yleensä **päätauluista**, sillä ne sisältävät tuoreimmat versiot ja niiden datamäärä on versiotauluja
     pienempi
   * Tässä kannattaa pyrkiä hakemaan operaation kaipaamat versiot kerralla välttääkseen ylimääräisiä kantaan lähteviä
     pyyntöjä. Itse versioiden data on pieni, mutta jokainen pyyntö maksaa vähintään edestakaisen liikenteen!
2. Saadut versiot mapataan olioiksi erillisellä haulla per-olio
   * Tätä puolestaan ei tarvitse välttää, sillä data on jo luultavasti muistissa ja haku on nopea (lähes välitön)
   * Haku menee toki kantaan esilämmityksessä tai käsitteen muuttuessa, mutta tällöinkin vain kerran ja sen jälkeen
     käsite on välimuistissa
   * Nämä haut tehdään SQL:nä **versiotauluun** jotta välimuistiin päätyy oikean version tiedot eikä kaikkein tuoreimpia
     (jos joku esim. muuttaa käsitettä versiohaun ja datan haun välissä)

Eli aina jos funktio ottaa sisäänsä esimerkiksi Layout-käsitteen ID:n (ja kontekstin) tiedetään että joudutaan tekemään
kantahaku jossa tuo ID mapataan versioksi. Jos funktio puolestaan ottaa sisään rivin version, sen ei luultavasti
tarvitse enää käydä kannassa lainkaan. Tämä on siis käytännössä lähes yhtä hyvä kuin jos olio käsittelisi itse oliota.

### Ohjenuora: optimoi ID->versio haut, jätä versio->olio haut välimuistin murheeksi

Koska geoviitteen keskeiset välimuistit esilämmitetään, käytön kannalta suorituskyky ei toimi aivan niinkuin pelkästä
koodista voisi olettaa. Hitain osa hakuja on usein tietoliikenne edestakaisin kantaan, joten ilman välimuisteja olisi
tehokkainta pyrkiä hakemaan kaikki data kerralla.

Geoviitteessä kuitenkin data muuttuu suhteellisen verkkaiseen ja käsitteiden tuoreimmat versiot pidetään aina
esilämmitettynä välimuistissa. Tällöin nopein tapa hakea dataa on usein hakea vain tarvittavat välimuistin avaimet
kannasta ja hakea sen jälkeen kukin käsite erikseen välimuistin läpi sen versiolla. Ilman välimuistia tuo
olisi tietenkin hidasta n+1 kyselyn takia, mutta välimuistin ansiosta nuo yksittäiset haut ovat käytännössä vain
poimintoja muistissa jo olevasta hashmapista. Toki versioita vastaavat oliot on teknisesti haettavissa myös monihakuna
siltä varalta että ne eivät olisikaan välimuistissa, mutta usein tuolla saadaan vain lisää monimutkaisuutta SQL-hakuun
ilman erityistä hyötyä.

Keskeistä palvelun kutsua suorittaessa on siis hyvin usein optimoida kannassa käynnit ID->versio hauissa, eli tunnistaa
heti pyynnön aluksi tarvittavat käsitteet ja muuttaa frontendistä pyynnössä tulevat ID:t versioksi yhdellä (tai 
mahdollisimman harvalla) haulla. Kun versiot on kerran haettu, niitä voidaan välittää kutsuketjussa eteenpäin ja hakea
olioiksi vaikka useammassakin eri paikassa - se ei luultavasti enää vaikuta suorituskykyyn, sillä samaa versiota ei
kuitenkaan haeta kannasta kahdesti.

## Frontend välimuisti

Frontendissä haettu data säilytetään välimuistissa hakuja suorittavien `...-api.ts` tiedostojen sisällä, `AsyncCache`
luokan toteutuksella, perustuen muutosaikaleimoihin. Koska jokainen rivin muutos saa muutosaikaleiman kannassa, voidaan
kunkin käsitteen osalta välimuisti invalidoida kyseisen käsitetyypin tuoreimman muutosajan perusteella, eli kun
UI-komponentti pyytää jotain käsitettä tai listausta käsitteistä, pyyntö palvellaan välimuistista jos kyseiseen
käsitetyyppiin ei ole tullut muutoksia ja haetaan backendista jos mikään tyypin käsite on muuttunut, eli
muutosaikaleima on kasvanut.
 
Käsitekohtaiset muutosaikaleimat säilytetään Redux-storessa ja bindataan komponenttihierarkian läpi React propertyinä,
jolloin koko sovellus saadaan päivittymään automaattisesti vain hakemalla backendista tuoreet muutosaikaleimat ja
päivittämällä ne Redux-storeen.

Frontendin välimuisti on siis rakenteena selvästi karkeampi kuin backendin rivikohtainen välimuisti ja sen vaikutuksena
kaikki tietyn tyypin oliot haetaan backendista uudelleen minkään kyseisen tyypin olion muuttuessa. Tuo ei ole
käytännössä kuitenkaan ongelma, sillä itse pyynnöt eivät ole erityisen hitaita (backend välimuistin ansiosta) ja datan
muutoksia tulee Geoviitteessä verrattaen rauhalliseen tahtiin. Tarvittaessa tämä on kuitenkin optimoitavissa pidemmälle
tekemällä listaus-haut delta-hakuina, eli haetaan backendista deltana vain ne oliot jotka oikeasti muuttuivat edellisen
ja uuden muutosajan välissä.

Välitön hyöty välimuistista kuitenkin on, että eri UI-komponentit voivat huoletta pyytää dataa uudelleen muistaen itse
pelkän ID:n ja käytännössä tuo ei kuitenkaan aiheuta saman pyynnön pommitusta backendiin yhä uudelleen. ID-olio-mappaus
siis säilyy tallessa välimuistissa, mistä se on kaikkien eri komponenttien käytettävissä.

### Datan haku välimuistin läpi

Tarkemmin katsottuna mekanismi toimii seuraavasti:
1. Kunkin käsitteen muutosaikaleimat säilytetään Redux-storessa
2. Muutosaikaleimat bindataan propertyinä React-komponentteihin, jotka kyseistä käsitettä käyttävät
    * Esim. `changeTimes.locationTrack` bindataan komponentteihin, jotka esittävät raiteita
    * Komponentti voi riippua useammasta aikaleimasta ja päivittää eri osia näkymästä eri aikaleimoilla
    * Osa pyynnöistä voi riippua usammasta käsitteestä, jolloin riippuvuutena käytetään suurinta yksittäisten
      riippuvuuksien aikaleimoista (`getMaxTimeStamp(changeTime1, changeTime2, ...)`)
3. Komponentti hakee käsitteen tiedot `...-api.ts` -tiedoston hakufunktioilla, jotka tekevät kutsun välimuistin läpi
    * Tässä kutsussa on argumenttina muutosaikaleima, jonka perusteella funktio käyttää välimuistia: varsinainen kutsu
      tehdään vain jos aikaleima on päivittynyt, muuten palautetaan olemassaoleva data
    * Vaikka useampi komponentti tekisi kutsun samaan aikaan, välimuisti palauttaa niille kaikille saman `Promise`
      -vastauksen, eli kutsu tapahtuu vain kerran
    * Välimuistissa tallennetaan tyypillisesti myös tyhjät vastaukset, eli jos haetaan jotain aikaleimalla n ja vastaus
      on tyhjä, samaa pyyntöä ei ole hyötyä tehdä uudelleen ennen kuin kohteena olevassa datassa on jokin muuttunut

### Muutosaikaleimojen (välimuistin avainten) päivitys frontendiin

Muutosaikaleimat päivitetään Reduxiin kahdella eri polulla:
1. Kun joku komponentti muokkaa tietoja (onnistuu POST/PUT kutsussa), muutoksen toteuttava `...-api.ts` funktio
   tarkasta kyseisen käsitteen uuden aikaleiman backendista ja päivittää sen Redux-storeen osana samaa funktiokutsua
    * Tämä kattaa käytännössä UI:n kautta itse tekevät muutokset, päivittäen itse muutetun datan heti
2. Taustalla haetaan kaikkien käsitteiden muutosaikaleimat periodisesti ja päivitetään ne kerralla Redux-storeen
    * Tämä varmistaa että muiden käyttäjien tai taustapalveluiden tekemät muutokset päivittyy UI:lle

Riippumatta kumpaa polkua aikaleima päivitetään, tuoreen aikaleimat noudetaan `ChangeTimeController`:n API:sta ja
viedään storeen. Koska aikaleimat on bindattu UI-komponenttien läpi käyttöpaikkaan asti, muutos aiheuttaa 
automaattisesti tarvittavien komponenttien uudelleen renderöinnin. Vaikka tuota voi tapahtua monessakin paikassa
läpi UI:n, ne kaikki päätyvät hakemaan uuden datan saman `...-api.ts` -tiedoston välimuistin läpi, jolloin itse haku
backendiin tapahtuu vain kerran.

### Ohjenuora: Säilytä komponentin/Reduxin tilassa käsitteistä vain ID:t - välimuisti säilyttää oliot

Johtuen frontin välimuistista, komponenttien tai Redux storen ei siis tarvitse, eikä edes kannata säilyttää haettua
dataa näkymässä itsessään. Tuo aiheuttaisi vain mahdollisuuden tuottaa bugeja tiedon päivittymisen suhteen ja lisäisi
Redux-storen kokoa. Sen sijaan, riittää että tila sisältää UI:lla tehdyt valinnat ja muutokset, eli olioista
tyypillisesti vain niiden ID:t, ei haettua käsitettä itseään. Itse käsitteen voi aina "hakea uudelleen" ID:llä
ilman huolta suorituskyvystä, sillä käytännössä data on jo luultavasti välimuistissa ja saadaan sieltä uudelleen ilman
suorituskykyvaikutuksia. Samalla varmistetaan että tilaan ei voi jäädä epähuomiossa vanhentuneita olioita ja kaikki
näkymät päivittyy automaattisesti datan muutoksissa, riippumatta missä muutos tapahtuu. Myös silloin kun muutos tehdään
toisella selaimella toisen käyttäjän toimesta.
