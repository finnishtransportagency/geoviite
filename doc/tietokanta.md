# Tietokanta

Tietokanta on jaettu useisiin skemoihin, jotka kuvataan tässä kehitystyön näkökulmasta. Datan merkitys on helpointa
ymmärtää katsomalla tietomallin yleiskuvausta: [Geoviite tietomalli](tietomalli.md)

## Audit ja versiointi

Audit-tarkoituksiin ja muutoksien jäljitykseen käytetään Geoviitteessä automaattista metatiedotusta ja versiointia.
Metatietoja tai versiotauluja ei ole tarkoitus koskaan muuttaa sovelluksesta, vaan ne päivitetään automaattisesti
tietokantatriggereillä. Versiotauluun jää aina asumaan kaikki aiemmat tilat joissa rivi on ollut, joten tiedon
muuttaminen tai poistaminen päätaulusta ei hävitä sitä peruuttamattomasti. Lähes kaikille Geoviitteen tauluille on
olemassa myös versiotaulu.

Versioiden sisältämiä automaattisia metatietoja ovat:

- Päivityshetki `change_time`: ajanhetki, jolla päätaulun rivi muuttui ko. version kuvaamaan tilaan
- Päivityksen tekijä `change_user`: käyttäjä (tunnus), joka muutoksen teki
- Version voimassaolon päättymishetki `expiry_time`: ajanhetki, jolla päätaulun rivi muuttui seuraavaan tilaan (`null`
  mikäli versio on edelleen voimassa)
- Version poisto-operaation lippu `deleted`: `true` jos rivi on poistettu päätaulusta tämän version ajanhetkellä,
  muutoin `false`

Versiotaulusta voidaan hakea sen automaattisten metatietojen avulla minkä vain ajanhetken tila päätaulussa. Käytännössä
haun `where` ehto voidaan kirjoittaa näin:

```
where change_time <= [haettu ajanhetki] -- change_time on inklusiivinen (tästä hetkestä alken tila oli x)
  and expiry_time > [haettu ajanhetki] -- expiry_time on eksklusiivinen (tästä hetkestä alken tila oli jotain muuta)
  and deleted = false -- Ei huomioida poisto-versioita sillä niiden voimassaollessa riviä ei ollut päätaulussa
```

### Versiorivit ovat (lähes) muuttumattomia

Versiotaulujen data on sovelluksen kannalta muuttumatonta, eli sinne vain lisätään rivejä aina kun päätaulua
päivitetään. Poikkeuksen tähän tekee `expiry_time` kenttä, joka kuvaa version voimassaolon päättymista ja siksi
täydentyy vasta kun ko. rivin seuraava versio syntyy. Koska tuo tieto muuttuu version syntymisen jälkeen, sitä ei saa
lukea välimuistissa säilytettäviin olioihin. Kaikki muut tiedot yksilöityvät kuitenkin pääavaimella ja versionumerolla,
joten tuolla parilla voidaan tallentaa olion sisältö välimuistiin eikä sen invalidoinnista tarvitse huolehtia. Tämä
onkin tyypillinen rakenne Geoviitteen koodipohjassa. Lisää välimuistin käytöstä Geoviitteessä löytyy dokumentista
[Välimuisti Geoviitteessä](valimuisti.md)

On myös huomattava että tietokannan rakenteellisissa muutoksissa (esim uuden sarakkeen lisäys default-arvolla) voi
olla tarve päivittää versiotaulua migraation yhteydessä, joten oletus versioiden muuttumattomuudesta ei päde
vesiopäivitysten yli. Koska tietomalli ei kuitenkaan muutu sovelluksen ajon aikana, tällä on harvoin merkitystä.

### Poistot versiotaulussa

Versiotaulut päivitetään myös poistojen yhteydessä siten että poisto itsessään näkyy rivinä, joka sisältää poistoa
edeltävän tilan ja `deleted` (boolean) sarakkeen arvolla `true`. Myös tällä poistorivillä on muutoin normaalit metadata
-sarakkeet. Vaikka olion data deletoidussa versiossa on oleellisesti sama kuin sitä edeltävälläkin versiolla, itse rivi
on silti oleellinen, sillä ilman sitä versiotauluun ei kertyisi metatietoja (muutoksen tekijä) itse poisto-operaatiosta.

Jos taulun pääavain mahdollistaa saman ID:n uudelleenkäytön myöhemmin (luonnolliset avaimet) poistunut rivi voi myös
efektiivisesti palata takaisin. Tällöin samalla ID:llä luotu uusi rivi saa seuraavan vapaan versionumeron, huomioiden
aiemman poistetun rivin versionumerot. Eli toisin sanoen, olion versiohistoriaan jää kyseiseen kohtaan normaalien
versiorivien väliin `deleted=true` -tilainen rivi merkkaamaan aikaväliä, jolloin oliota ei ollut päätaulussa lainkaan.

### Automaattiset päivitykset triggereillä

Ensimmäinen trigger on metadatan päivitys `version_update_trigger`, joka päivittää rivin versionumeron `version`,
muutosaikaleiman `change_time` ja muutoksen tehneen käyttäjän `change_user` itse päätaulun riville. Trigger
ylikirjoittaa aina SQL:ssä annetut tiedot, joten kyseisiin sarakkeisiin ei voi kirjoittaa normaalisti päivityksen
tekevässä SQL:ssä.

Toinen trigger on versiorivin luonti `version_row_trigger`, joka kopioi muutoksen jälkeisen tilan (mukaan lukien
edellisen triggerin kirjaamat metatiedot) uutena rivinä taulua vastaavaan versiotauluun. Lisäksi trigger päivittää
saman avaimen edelliselle versioriville ko. version päättymisajan `expiry_time` samaksi kuin uuden versiorivin
muutosajan `change_time`. Tämä trigger ajetaan myös rivin delete:ssä, jolloin versiotauluun lisättävällä rivillä on
merkattu `deleted=true` ja muutoin tila on poistohetken viimeinen tila.

### Versioinnin lisääminen tauluuun

Taulujen metatietojen lisäystä ja versionnin luontia varten on valmiit tietokantaproseduurit. Niitä ei siis luoda käsin
kullekin taululle. Sen sijaan versiointi lisätään seuraavilla kutsuilla:

```
select common.add_metadata_columns('skeeman-nimi', 'taulun-nimi');
select common.add_table_versioning('skeeman-nimi', 'taulun-nimi');
```

Ensimmäinen näistä lisää tauluun edellytetyt metatietosarakkeet (version, change_user, change_time, expiry_time).
Toinen kutsu luo taululle versiotaulun sekä lisää triggerit (`version_update_trigger` ja `version_row_trigger`)

Versiotauluilla on pääavaimena aina samat sarakkeet kuin päätaulun pääavaimena + version-sarake. Foreign key viitteitä
versiotauluissa ei kuitenkaan ole, sillä niiden eheyden ylläpitäminen menneisyyteen olisi hankalaa. Versiodatan
immutable-luonteen takia datan voi kuitenkin olettaa oikeasti olevan olemassa, jos sen hakee versiota vastaavalla
aikaleimalla.

Versiotauluihin voidaan tarvittaessa lisätä haluttuja indeksejä suorituskykyisempiä hakuja varten.

## Skeemat

Tietokanta-skeemat ylläpidetään versioituvilla Flyway-migraatioilla:

* Migraatiot
  versionhallinnassa: https://github.com/finnishtransportagency/geoviite/tree/main/infra/src/main/resources/db/migration
* Dokumentaatio: https://flywaydb.org/documentation/

### Flyway

Flyway-skeema sisältää Flyway-kirjaston tuottamat migraatiotaulut, jotka ylläpitää päivityksissä tapahtuvaa
migraatioiden
tilaa. Flyway-kirjasto muokkaa näitä itse tarpeen mukaan eikä niihin viitata Geoviitteen datasta. Käytännössä
migraatiotauluun voi joskus olla tarve koskea jos migraatiot ovat päässeet virheellisenä tuotantoon, mutta tämä on
harvinaista.

### Postgis

Postgis-skeema sisältää Postgresin PostGIS-laajennoksen omat rakenteet, muunmuassa koordinaattijärjestelmien tiedot.
Sitä
ei muokata Geoviitteestä, mutta sen metodeita käytetään laajasti ja joihinkin tauluihin voidaan viitata kun esimerkiksi
halutaan käyttää viite-eheyttä varmistamaan toimivan koordinaattijärjestelmän käyttö.

### Common

Common-skeema sisältää Geoviitteen jaetut käsitteet, joita hyödynnetään sekä Geometry- että Layout- puolelta,
erityisesti
enumeraatioita, vaihdeomistaja, ja vaihteen rakenteet (vaihdekirjasto). Lisäksi sieltä löytyy käyttäjärooleihin
(autorisointi) liittyvät asiat, sekä geometrialaskentaan ja koordinaattimuunnoksiin liittyviä kolmioverkkoja ja
vastaavia rakenteita.

### Geometry

Geometry-skeema sisältää geometriasuunnitelmat, eli alkuperäiset suunnitelmatiedostot sekä niistä jäsennetyn
geometriatietomallin. Geometriatietomalli kuvaa suunnitelmatiedoston sisällön matemaattisina määreinä (suorina, kaarina,
siirtymäkaarina) sekä rataverkkoon liittyvinä lisätietoina kuten vaihteina. Selkeimmän kuvan siitä saa katsomalla
kuvausta [Tietomalli](tietomalli.md).

### Layout

Layout-skeema sisältää paikannuspohjan, eli yhtenäiskoordinaatistoon muunnetun koko suomen rataverkon, joka on
muodostettu linkittämällä geometrioita. Koska layout luodaan geometry-skeeman sisältöjen pohjalta, layoutin osat
viittaavat niiden lähteenä olleeseen geometria-puolen tietoon.
Selkeimmän kuvan paikannuspohjan tietomallista saa katsomalla kuvausta [Tietomalli](tietomalli.md).
Toisaalta paikannuspohjan eri konteksteja (luonnos/virallinen/suunnitelma)
kuvaa [Paikannuspohjan kontekstit](paikannuspohjan_kontekstit.md).

### Publication

Publication-skeema sisältää tiedot julkaisuista, eli versioihin kytketyt viitteet siitä millaisena joukkona tieto
hyväksyttiin viralliseen paikannuspohjaan. Ratkoon viennit tehdään näiden pohjalta, mutta itse viennin status ei ole
julkaisun asiaa vaan oma taulunsa integrations-skeemassa.

Tarkempaa kuvausta julkaisuprosessista löytyy kuvauksesta [Julkaisut](julkaisut.md)

### Integrations

Integrations-skeema sisältää Geoviitteen integraatiohin liittyvän tilan, eli niiden lukkotaulun ja Ratko-integraation
operaatioiden tilaa kuvaavat taulut (`ratko_push`). Ratko-integraation tiloja kuvaava malli löytyy
kuvauksesta [Julkaisut](julkaisut.md).

### Projektivelho

Projektivelho-skeema sisältää projektivelho-integraation (synkronointi-ajon) tilaa kuvaavat taulut. Lisäksi samassa
skeemassa säilytetään myös tuodut dokumentit ja niihin littyvät projektitiedot sekä tarvittavat Projektivelhon
nimikkeistöt (Dictionary). Näistä on tarkempaa tietoa kuvauksessa [Projektivelho](projektivelho.md)
