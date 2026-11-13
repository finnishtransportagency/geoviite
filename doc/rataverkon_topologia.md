# Rataverkon topologia

Paikannuspohjan topologia -rajapinta palauttaa valitun julkaistun rataverkon version suuntaamattomana verkkona.
Verkon solmut ja kaaret kuvaavat raiteiden ja vaihteiden välisiä yhteyksiä. Solmun kautta sallitut kulkusuunnat
palautetaan erikseen, joten kaaren olemassaolo ei yksin tarkoita, että sen kautta voisi kulkea miten tahansa.

Topologiaa voi käyttää esimerkiksi rataverkon yhteyksien tutkimiseen ja reitityksen lähtötietona. Se ei sisällä
rataverkon geometriaa kokonaisuudessaan, vaan kaarien pituudet ja solmujen sijainnit sekä viittaukset sijaintiraiteisiin
ja vaihteisiin.

## Rajapinta

`GET /paikannuspohja/v1/topologia`

Rajapinta on saatavilla myös rataverkon ulkoisen rajapinnan `/geoviite`- ja `/geoviite/dev`-polkujen alla.

| Parametri | Pakollinen | Kuvaus |
|---|---|---|
| `rataverkon_versio` | Ei | Julkaisun UUID. Oletuksena käytetään uusinta virallisen paikannuspohjan julkaisua. Suunnitelmajulkaisuja ei tueta. |
| `koordinaatisto` | Ei | Solmujen sijaintien EPSG-koordinaatistojärjestelmä. Oletus on `EPSG:3067` (ETRS-TM35FIN). |
| `resoluutio` | Ei | Graafin tarkkuus: `nano` tai `mikro`. Oletus on `nano`. |

Esimerkiksi `GET /paikannuspohja/v1/topologia?rataverkon_versio=<uuid>&resoluutio=mikro` hakee kyseisen julkaisun
mikrotason topologian. Virheellinen parametri palauttaa HTTP 400 -vastauksen. Puuttuva tai topologiaan sopimaton
rataverkon versio palauttaa HTTP 404 -vastauksen.

## Mistä topologia muodostetaan

Topologia muodostetaan valitun julkaisun aikahetken virallisesta paikannuspohjasta. Lähdedatana käytetään julkaistuja
sijaintiraiteita geometrioineen, niiden paikannuspohjan kaaria ja solmuja, vaihteiden linkityksiä sekä vaihdetyypin
määrittelemää rakennetta ja linjauksia. Topologia ei siis perustu vain sijaintiraiteiden geometrioihin: vaihteen sisäiset
yhteydet muodostetaan vaihderakenteen perusteella.

Jokainen sijaintiraiteiden välinen tavallinen rataosuus esitetään kaarena. Sijaintiraiteet voivat jakaa samaa osuutta,
jolloin kaaren `raiteet`-listassa on viittaus kuhunkin osuuden kattavaan sijaintiraiteeseen. Vaihteen rakenteen mukaiset
linjaukset esitetään omina kaarinaan. Kaaren pituus on osuutta vastaavan geometrian pituus metreinä.

Solmu on joko raiteen pää (`raiteen_paa`) tai vaihdepiste (`vaihde`). Solmulla on yksilöivä UUID ja sijainti valitussa
koordinaatistossa. Vaihdesolmussa `vaihteet` kertoo vaihteen OID-tunnuksen ja kyseisen solmun vaihdepistenumeron.

## Kulkusuunnat

Kaaret ovat itsessään suuntaamattomia. Suunta määräytyy `kulkusuunnat`-kentän sallituista siirtymistä: kukin siirtymä
kertoo solmun kautta saapuvan kaaren (`kaari_sisaan`) ja sieltä lähtevän kaaren (`kaari_ulos`) sekä kummankin
kulkusuunnan. Suunta `nouseva` tarkoittaa kaaren alkusolmusta loppusolmuun kulkemista ja `laskeva` päinvastaista.
Siirtymän puuttuminen tarkoittaa, ettei kyseinen kulku ole sallittu. Mitään suoranaista merkitystä nousevalla ja
laskevalla suunnalla ei sen lisäksi ole, että ne ovat toisistaan vastakkaisia suuntia.

Siirtymät ottavat huomioon vaihteen rakenteen ja sen sallitut yhteydet. Raiteen päässä sallitaan kääntyminen takaisin
samaa kaarta pitkin. Näin verkon käyttäjä voi erottaa toisistaan kaarien fyysisen yhteyden ja sallitut kulkureitit.

## Resoluutio

- **`nano`** palauttaa tarkimman topologian: vaihteen jokainen topologisesti linkitetty liittymäkohta on oma solmunsa,
  ja vaihdelinjat ovat omia kaariaan.
- **`mikro`** yksinkertaistaa esitystä: vaihde esitetään yhdellä solmulla vaihteen esityspisteessä ja yhteen
  ketjutettavissa olevia kaaria yhdistetään. Raiteiden päätesolmut säilyvät. Yhdistetyn kaaren pituus on osakaarten
  pituuksien summa, ja sen raidelistaan kootaan osakaarten sijaintiraiteet.

Mikrotason siirtymät vastaavat nanotason sallittuja kulkuja, mutta niiden kaariviittaukset osoittavat yhdistettyihin
kaariin. Käytä nanotason esitystä, kun tarvitset vaihteen liittymäkohtien tai yksittäisten vaihdelinjojen tarkat
yhteydet.

## Vastauksen rakenne

Vastauksen ylätasolla ovat käytetty `rataverkon_versio`, `koordinaatisto` ja `topologia`. Topologia sisältää valitun
`graafin_resoluution` sekä `solmut`- ja `kaaret`-listat.

Kaari sisältää tunnisteen (`id`), alku- ja loppusolmun (`alkusolmu`, `loppusolmu`), pituuden (`pituus`) sekä siihen
liittyvät sijaintiraiteet (`raiteet`, kunkin raiteen `oid`). Solmu sisältää tunnisteen, tyypin, sijainnin, siihen liittyvät
vaihteet sekä sallitut kulkusuunnat. Solmu- ja kaaritunnisteet ovat UUID-tunnisteita.
