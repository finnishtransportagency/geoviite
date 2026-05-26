# Koordinaattimuunnokset

## Vaakakoordinaatistojen muunnokset

Geoviite käsittelee geometrioita useissa eri koordinaatti- ja korkeusjärjestelmissä. Suunnitelmat voivat saapua missä
tahansa tuetuista koordinaattijärjestelmistä, ja paikannuspohjaan linkitystä sekä karttaesitystä varten kaikki tieto
muunnetaan yhteiseen sisäiseen koordinaatistoon. Lisäksi tietoa vietäessä ulospäin (Ratko, Ext API) sitä voidaan haluta
muuntaa vastaanottajan toivomaan järjestelmään.

Muunnokset ovat tarvittaessa myös käytettävissä `GeographyController` tarjoaman API:n kautta frontendille.

### Tyypilliset koordinaattijärjestelmät

Geoviitteen käyttämä yhtenäiskoordinaatisto (sekä kartan esityskoordinaatisto) on **ETRS-TM35FIN** (EPSG:3067).

Yleisimmät koordinaatistot joihin Geoviitteessä törmää ovat listattuna alla. Näiden lisäksi joskus esiintyy myös
muitakin järjestelmiä, erityisesti vanhemmissa suunnitelmissa käytettyjä kunnallisia tasokoordinaatistoja. Itse
muunnosmekanismi ei kuitenkaan rajoitu tässä listattuihin järjestelmiin.

| Koordinaattijärjestelmä | Huomioita                                                                        |
|-------------------------|----------------------------------------------------------------------------------|
| ETRS-TM35FIN            | Geoviitteen sisäinen tasokoordinaatisto (paikannuspohja, karttaesitys)           |
| WGS 84                  | Usein käytetty karttaesityksissä sekä eri API:ssa                                |
| ETRS89                  | Yleiseurooppalainen geodeettinen referenssijärjestelmä                           |
| Finnish GK19-GK31       | Suomen kaistoihin jaettu tasokoordinaatisto, käytössä uusissa suunnitelmissa     |
| KKJ0-KKJ5               | Suomen kaistoihin jaettu tasokoordinaatisto, käytössä vanhemmissa suunnitelmissa |

### Arkkitehtuuri

Kaikki muunnoskoodi sijaitsee paketissa `fi.fta.geoviite.infra.geography` ja siellä keskittyen tiedostoon
`GeoToolsGeometries.kt`. Kaikki GeoTools-kirjaston käyttö on kapseloitu tähän tiedostoon, jotta muu sovelluskoodi ei
ole suoraan riippuvainen GeoToolsista ja erinäisten muunnosolioiden (Crs jne.) välimuisti voidaan toteuttaa
keskitetysti.

Muunnokset perustuvat `Transformation`-luokkahierarkiaan:

```
Transformation (sealed class)
├── GeoToolsTransformation        – GeoTools-pohjainen muunnos (ei-KKJ-järjestelmät)
├── KKJToTM35FINTransformation    – KKJ → TM35FIN triangulointiverkolla
└── TM35FINToKKJTransformation    – TM35FIN → KKJ triangulointiverkolla
```

`CoordinateTransformationService` on Spring-palvelu, joka tarjoaa välimuistissa pidettävät `Transformation`-oliot eri
koordinaattijärjestelmäparien välille.

`Transformation`-oliot tallennetaan välimuistiin kahdella tasolla: `CoordinateTransformationService`-tasolla (myös
KKJ-muunnokset) sekä suoraan `GeoToolsGeometries.kt`-tiedostossa `GeotoolsTransformation`-olioille. Jälkimmäinen
tarkoittaa, että välimuisti toimii myös palvelun ohi tehdyissä suorissa kutsuissa (esim. `transformNonKKJCoordinate`).

#### KKJ ↔ TM35FIN — GeoTools + triangulointiverkko

KKJ-järjestelmien muuntamiseen TM35FIN:iin (ja takaisin) ei käytetä GeoToolsia, koska sen matemaattinen tarkkuus ei
riitä KKJ:n muunnokseen (virheet jopa metrien suuruusluokassa). Sen sijaan käytetään erillistä triangulointiverkkoa,
joka on tallennettu tietokantaan, tauluihin `common.kkj_etrs_triangulation_network` ja
`common.kkj_etrs_triangle_corner_point`.

Huomaa, että KKJ-järjestelmien muunnos on toteutettu vain `LAYOUT_SRID`:n (TM35FIN) kanssa, eli esimerkiksi suora KKJ →
GK-fin -muunnos heittää poikkeuksen. Tarvittaessa KKJ-koordinaatti täytyy siis ensin muuntaa TM35FIN-koordinaatiksi,
jonka jälkeen edelleen haluttuun järjestelmään. Myös suora muunnos olisi toteutettavissa, mutta sille ei ole käytännössä
ollut tarvetta.

Muunnosalgoritmi:

1. KKJ-koordinaatti muunnetaan ensin KKJ3/YKJ-koordinaatistoon GeoToolsilla (datuminsiirto saman
   ellipsoidin sisällä, jossa GeoTools on tarkka)
2. YKJ-koordinaatti muunnetaan TM35FIN-koordinaatiksi triangulointiverkolla: etsitään piste sisältävä
   kolmio R-puusta (`rtree2`) ja lasketaan affinimuunnos kolmion parametreilla

Triangulointiverkko ladataan tietokannasta `KkjTm35finTriangulationDao`-luokassa Spring-välimuistin läpi. Koska nämä
muunnokset riippuvat tietokantaan tallennetuista triangulointiverkoista, täytyy muunnos hakea aina
`CoordinateTransformationService`-palvelun kautta. Jos muunnosta koittaa tehdä puhtailla `GeoToolsGeometries.kt`
funktioilla kuten `transformNonKKJCoordinate`, muunnos heittää poikkeuksen.

#### Muut muunnokset — GeoTools

Kaikki muut koordinaattimuunnokset (WGS84, ETRS89, TM35FIN, GK-fin jne.) toteutetaan suoraan GeoTools-kirjaston
tarjoamilla funktioilla. `GeotoolsTransformation` noutaa `MathTransform`-olion (välimuistin läpi).

Muunnos huomioi myös koordinaattien esitysjärjestyksen (EAST_NORTH / NORTH_EAST) automaattisesti:
`toJtsCoordinate`- ja `toGvtPoint`-funktiot tarkistavat CRS:n mukaisen järjestyksen ja vaihtavat koordinaatit
tarvittaessa.

Nämä Transformation-oliot voi hakea samoin kuin KKJ-muunnoksetkin, `CoordinateTransformationService`-palvelun kautta.
Tarvittaessa niitä voi kuitenkin käyttää myös suoraan tietokantariippumattomasti `GeoToolsGeometries.kt`-tiedoston
funktioilla, kuten `transformNonKKJCoordinate`.

#### Muunnokset tietokannassa — Postgis

Muunnoksia voidaan tehdä myös suoraan tietokannassa postgis-laajennuksen `ST_Transform`-funktioilla. Pääasiassa
Geoviitteessä kuitenkin suositaan Kotlin-puolella tapahtuvaa GeoTools-pohjaista muunnosta, jotta tulokset ovat varmasti
yhteneväiset.

### Tunnettujen koordinaattijärjestelmien listaus tietokannassa

Tietokantataulu `common.coordinate_system` sisältää Geoviitteen "tunnetut" koordinaattijärjestelmät:

| Sarake    | Kuvaus                                                        |
|-----------|---------------------------------------------------------------|
| `srid`    | EPSG-koodi                                                    |
| `name`    | Ihmisluettava nimi                                            |
| `aliases` | Vaihtoehtoisten nimien taulukko (esim. lyhenteet, vanha nimi) |

Näitä järjestelmiä käytetään esimerkiksi käyttöliittymässä järjestelmien listaamiseen ja
`GeographyService.getCoordinateSystems()` palauttaa taulun sisällön.

Joitain erikoisempia järjestelmiä (esim. vanhat kuntakohtaiset järjestelmät) ei välttämättä löydy tuosta taulusta, joten
suunnitelmien tietojen esittäminen ei voi olettaa että järjestelmä on aina määritelty. Jos järjestelmälle on
keksittävissä SRID (esim. suoraan suunnitelman sisältä), se voidaan muuntaa GeoToolsilla riippumatta siitä onko ko.
järjestelmää tässä taulussa määritelty.

## Korkeusjärjestelmämuunnokset

Geoviitteeseen tuotavissa suunnitelmissa on myös useita eri korkeusjärjestelmiä. Yhtenäistä esitystä varten nekin
muunnetaan yhteiseen järjestelmään, joka on N2000. Käytännössä Suomessa on historiallisesti ollut käytössä kolme eri
korkeusjärjestelmää, N43, N60 ja N2000. Näistä tuoreinta, eli N2000:aa, käytetään Geoviitteessä yhtenäistettyihin
esityksiin ja siihen on toteutettu muunnos N60:sta. N43-korkeusjärjestelmän muunnos N2000:aan ei ole toteutettu, koska
se on niin vanha että sitä esiintyi suunnitelmissa vain harvoin.

### N60 → N2000 muunnos — triangulointiverkko

N60-korkeuksien muuntamiseen N2000:een käytetään triangulointiverkkoa, joka on tallennettu tietokantaan, tauluihin
`common.n60_n2000_triangulation_network` ja `common.n60_n2000_triangle_corner_point`.
`HeightTriangleDao.fetchTriangles(boundingPolygon)` hakee ne kussakin muunnostilanteessa kolmiot, jotka leikkaavat
käsiteltävää aluetta. Itse muunnos tapahtuu kahdessa lineaarisessa interpolointivaiheessa: ensin interpoloidaan
N60/N2000-erotus X-akselin suunnassa kolmion ensimmäisen ja toisen kulmapisteen välillä, sitten tästä
interpolointipisteestä kolmanteen kulmaan. Saatu erotusarvo lisätään alkuperäiseen korkeuteen.

## Viittaukset

- **GeoTools** — CRS-muunnokset: https://geotools.org/
- **JTS Topology Suite** — geometriatyypit ja spatiaaliset operaatiot: https://locationtech.github.io/jts/
- **rtree2** — triangulointiverkkojen spatiaalinen indeksointi: https://github.com/davidmoten/rtree2
