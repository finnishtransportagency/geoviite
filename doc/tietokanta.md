# Tietokanta

Tietokanta on jaettu useisiin skeemoihin, jotka kuvataan tässä erikseen. Lisäksi tämä dokumentti kuvaa geoviitteen 
tietokannassa käytetyt yleiset mekanismit ja periaatteet. Itse datan merkitystä on puolestaan helpompi ymmärtää
katsomalla sen yleiskuvausta dokumentista [Geoviite tietomalli](tietomalli.md).

## Audit, metatiedot ja versiointi

Audit-tarkoituksiin ja muutoksien jäljitykseen käytetään Geoviitteessä automaattista metatiedotusta ja versiointia.
Metatietoja tai versiohistoriaa ei ole tarkoitus koskaan muuttaa sovelluksesta, vaan ne päivittyvät automaattisesti
tietokantatriggereillä.

Versiointi geoviitteessä tehdään erillisen versiotaulun avulla. Aina kun päätaulun riviä muokataan, triggeri päivittää
ensin riville sen metatiedot ja sitten kopioi rivin kokonaisuudessaan versiotauluun. Siellä rivejä ei enää muokata tai
poisteta, vaan versiotauluun jää aina asumaan kaikki rivin aiemmat tilat, jolloin tiedon muuttaminen tai poistaminen
päätaulusta ei hävitä mitään peruuttamattomasti.

### Metatiedot ja niiden päivitys

Ensimmäinen päivitystrigger on metadatan päivitys (`version_update_trigger`). Tämä ylikirjoittaa aina SQL:ssä
mahdollisesti annetut tiedot, joten näihin sarakkeisiin ei ole mahdollista kirjoittaa normaalisti päivityksessä.

Metatietosarakkeita ovat:
- **change_user**: muutoksen tekijä. Tämä täytyy olla kaikissa kirjoitustransaktioissa asetettu sessiomuuttujaan
 `geoviite.edit_user`, tai muutoin versioidun taulun muutokset eivät mene läpi.
- **change_time**: muutoshetki. Tämä generoituu automaattisesti tietokannan kellon mukaan.
- **version**: rivin versionumero. Inkrementoidaan automaattisesti rivin nykyisestä versiosta. Huom. tämä osaa myös
 tarkastella versiotaulusta mahdollisen samalla ID:llä olevan deletoidun rivin version, joten jos samaa luonnollista
 avainta käytetään uudelleen deletoinnin jälkeen, versiointi ei ala alusta uudelleenluonnissa.

### Versiotaulu ja sen päivitys

Toinen päivitystrigger on versiorivin luonti (`version_row_trigger`), joka kopioi muutoksen jälkeisen tilan
(metatietojen päivityksen jälkeen) uutena rivinä päätaulua vastaavaan versiotauluun. Tämä ajetaan sekä insert, update
että delete operaatioissa.

Rakenteellisena erona päätauluun, versiotaulussa rivin pääavaimeen lisätään mukaan sen versionumero, eli päätaulun avain
[id] vastaa versiotaulussa avainta [id,version]. Versioinnissa ei kuitenkaan aseteta foreign-key viitteitä osoittamaan
toisiin tauluihin versiokohtaisesti, sillä tämä edellyttäisi muutoksia viittaajiin aina kun viittauksen kohde muuttuu.
Versiodatan immutable-luonteen takia viitteen kohteen voidaan kuitenkin olettaa olevan olemassa, jos sen hakee
viittaajan versiota vastaavalla aikaleimalla.

Toinen ero versiotaulussa on lisätty sarake `deleted: boolean`. `deleted` -sarakkeella merkataan päätaulun rivin
poistoja, jotta versiotaulusta voidaan nähdä myös poistotapahtumat metatietoineen. `deleted` on siis true jos kyseessä
on tällainen "poistoversio", false kaikissa muissa päätaulun rivin muutoksissa.

Versiotauluissa siis uusimman version sisältö on identtinen päätaulun rivin kanssa. Mielivaltaisen menneisyyden
ajanhetken tila voidaan puolestaan hakea poimimalla uusin ennen ko. ajanhetkeä luotu versiorivi. On huomattavaa että
tässä haussa deleted-rivejä ei tule suodattaa vastaavasti kuin ajanhetkeä uudempien muutoksien rivejä, sillä noin
saataisiin vain uusin poistoa edeltänyt versio. Jos tuorein rivi on `deleted=true`, tämä tarkoittaa että haettua riviä
ei ole ko. ajanhetkellä olemassa.

Versiotaulujen data on sovelluksen kannalta muuttumatonta, eli automaattinen triggeri vain lisää sinne rivejä kun
päätaulua päivitetään ja sovellus ei koskaan muuta niitä. Tietokannan rakenteellisissa muutoksissa (migraatioissa, esim.
uuden sarakkeen lisäys) voi kuitenkin olla tarve päivittää versiotaulua. Tällaisten migraatioiden ajaksi triggerit
tulee disabloida tai poistaa ja luoda migraation jälkeen uudelleen. 

### Versioinnin lisääminen tauluuun

Taulujen metatietojen lisäystä ja versionnin luontia varten on valmiit tietokantaproseduurit. Niitä ei siis luoda käsin
kullekin taululle. Sen sijaan versiointi lisätään seuraavilla kutsuilla:

```
select common.add_metadata_columns('skeeman-nimi', 'taulun-nimi');
select common.add_table_versioning('skeeman-nimi', 'taulun-nimi');
```

Ensimmäinen näistä lisää tauluun edellytetyt metatietocolumnit (version, change_user, change_time). Toinen kutsu luo
taululle versiotaulun sekä lisää triggerit (`version_update_trigger` ja `version_row_trigger`).

Versiotauluihin voidaan tarvittaessa lisätä haluttuja indeksejä suorituskykyisempiä historiahakuja varten.

## Migraatiot

Tietokanta-skeemat ylläpidetään versioituvilla Flyway-migraatioilla:

* Migraatiot versionhallinnassa:
  https://github.com/finnishtransportagency/geoviite/tree/main/infra/src/main/resources/db/migration
* Flywayn oma dokumentaatio: https://flywaydb.org/documentation/

Flywayn käytäntöjen mukaisesti, migraatiot jakautuvat versioituihin (versioned) ja toistettaviin (repeatable).

Versioidut migraatiot vievät tietokannan yhdestä tilasta toiseen ja ne tulee aina ajaa tietyssä järjestyksessä ja kukin
vain kerran. Flyway hoitaa tämän automaattisesti, mutta kehittäjien on huolehdittava siitä että versiotiedostojen
numerointi (osana tiedostonimeä) on oikein järjestyksessä. Jos järjestys menee väärin, Flyway heittää virheen ja
lopettaa migraatioiden ajon. Kun versioitu migraatio on kerran ajettu tuotantokantaan, sitä ei voida enää muuttaa vaan
mahdolliset korjaukset pitää toteuttaa uutena versioituna migraationa edellisten päälle. Poikkeustilanteissa on
kuitenkin olemassa mekanismit korjata virheelliseen tilanteeseen jääneet migraatiot (kts. Flywayn dokumentaatio tarpeen
mukaan).

Toistettavat migraatiot järjestetään niinikää tiedostonimen mukaan, joten ne voivat luottaa siihen että aiemmat
toistettavat on ajettu ensin. Toistettaa migraatiota voidaan kuitenkin muuttaa, jolloin se ajetaan uudelleen (vain
itse muuttunut migraatio, ei kaikkia). Jokaisen toistettavan migraation on siis aina oltava sellainen että sen voi
idempotentisti ajaa uusimman tietokantaversion (kaikkien versioitujen migraatioiden) päälle riippumatta siitä onko se
mahdollisesti jo aiemmin ajettu (jollain versiolla).

Toistettavat migraatiot sopivat hyvin esimerkiksi indeksien ja näkymien luontiin, kunhan em. idempotenttius
huolehditaan. Eli migraatio ei saa olettaa että indeksi/näkymä on tai ei ole olemassa vaan se poistaa vanhan jos
sellainen on ja luo sitten uuden. Näin toteutettun toistettavan migraation sisältämää määritystä voi huoletta päivittää
aivan kuin muutakin koodia ja tiedoston muutosten myötä indeksit/näkymät päivitetään automaattisesti kantaan. Sama
mekanismi toimii hyvin myös datalle jos sitä halutaan ylläpitää migraationa (esim. data, jolle ei ole mielekästä
toteuttaa käyttäjän muokkaustoimintoa). Tällöin datan kirjoitus on tehtävä idempotenttina upsert+delete -toimintona,
joka vertaa uutta datajoukkoa kannan nykytilaan ja poistaa sellaiset rivit jota uusi datajoukko ei sisällä, päivittää ne
jotka ovat muuttuneet ja lisää ne jotka ovat uusia.

## Skeemat

Geoviitteen data on jaoteltu useampaan eri skeemaan. Geoviite ei käytä posgresin oletusskeemaa (public) lainkaan, vaan
kaikkien taulujen kanssa tulee aina käyttää koko skeeman nimeä.

### Flyway

Flyway-skeema sisältää Flyway-kirjaston tuottamat migraatiotaulut, jotka ylläpitävät päivityksissä tapahtuvaa
migraatioiden tilaa. Flyway-kirjasto muokkaa näitä itse tarpeen mukaan eikä niihin viitata Geoviitteen datasta.
Käytännössä migraatiotauluun voi joskus olla tarve koskea jos migraatiot ovat päässeet virheellisenä tuotantoon,
mutta tämä on harvinaista.

### Postgis

Postgis-skeema sisältää Postgresin PostGIS-laajennoksen omat rakenteet, muunmuassa koordinaattijärjestelmien tiedot.
Sitä ei muokata Geoviitteestä, mutta sen metodeita käytetään laajasti ja joihinkin tauluihin voidaan viitata kun
esimerkiksi halutaan käyttää viite-eheyttä varmistamaan toimivan koordinaattijärjestelmän käyttö.

### Common

Common-skeema sisältää Geoviitteen jaetut käsitteet, joita hyödynnetään sekä Geometry- että Layout- puolelta,
erityisesti enumeraatioita, vaihdeomistaja, ja vaihteen rakenteet (vaihdekirjasto). Lisäksi sieltä löytyy
käyttäjärooleihin (autorisointi) liittyvät asiat, sekä geometrialaskentaan ja koordinaattimuunnoksiin liittyviä
kolmioverkkoja ja vastaavia rakenteita.

### Geometry

Geometry-skeema sisältää geometriasuunnitelmat, eli alkuperäiset suunnitelmatiedostot sekä niistä jäsennetyn
geometriatietomallin. Geometriatietomalli kuvaa suunnitelmatiedoston sisällön matemaattisina määreinä (suorina, kaarina,
siirtymäkaarina) sekä rataverkkoon liittyvinä lisätietoina kuten vaihteina.

Selkeimmän kuvan geometriasuunnitelmien tietomallista saa katsomalla kuvausta
[Tietomalli: tarkat geometriat](tietomalli.md#tarkat-geometriat).

### Layout

Layout-skeema sisältää paikannuspohjan, eli yhtenäiskoordinaatistoon muunnetun koko suomen rataverkon, joka on
muodostettu linkittämällä geometrioita. Koska layout luodaan geometry-skeeman sisältöjen pohjalta, layoutin osat
viittaavat niiden lähteenä olleeseen geometria-puolen tietoon.

Selkeimmän kuvan paikannuspohjan tietomallista saa katsomalla kuvausta
[Tietomalli: paikannuspohja](tietomalli.md#paikannuspohja).
Toisaalta paikannuspohjan eri konteksteja (luonnos/virallinen/suunnitelma) kuvaa [Paikannuspohjan kontekstit](paikannuspohjan_kontekstit.md).

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
