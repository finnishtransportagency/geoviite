# Pystygeometria

Raiteen pystygeometria eli profiili lasketaan taitepisteistä (PVI, point of vertical intersection), jotka tallentuvat
kannassa tauluun `geometry.vertical_intersection`. Taitepiste määrittää ympyrän, jonka keskipiste on jossain kaukana
raiteen ylä- tai alapuolella, ja jonka kaarella raide kulkee pyöristysjaksolla. Pyöristysjaksojen välillä on
kaltevuusjaksoja, joilla raiteen pystygeometria kulkee suoraan. Koodissa nimet CurvedProfileSegment ja
LinearProfilesegment.

Taitepisteen `point`-tieto on sijanti koordinaatistossa, jossa x-koordinaatti tarkoittaa paalulukua raiteella,
ja y-koordinaatti korkeutta; tosin pyöristysjaksoilla raide ei käy y-koordinaatissa asti, vaan se on sen pisteen
korkeus, jossa edeltävä ja jälkimmäinen kaltevuusjakso törmäisivät, jos ne jatkuisivat suorina.

Geometriaprofiililta pystyy siis kysymään, mikä geometriaraiteen korkeus on minkäkin paaluluvun kohdalla. Saman tiedon
saa linkityksen kautta paikannuspohjan raiteille: Geometriaraiteesta linkitetystä paikannuspohjan raiteen segmentistä
pystyy katsomaan aina tietyn m-arvon kohdalta, minkä geometriraiteen mihin paalulukuun tämä kohta viittaa, ja sitten
kysyä geometriaraiteen profiililta, mikä sen korkeus on.

Pystygeometrian korkeus on oletuksena raiteen korkeustasoa N2000-korkeusjärjestelmässä, johon se voidaan joutua
muuntamaan HeightTransformation#transformHeightValue()lla N60-järjestelmästä.

## Pystygeometrian elementtilista

Geometriaraiteen elementtilista on varsin yksiselitteinen: Se sisältää vaan profiilin tiedot. Ellei
geometrialaskennassa mene jotain vikaan, listaan päätyy aina yhtenä rivinä yksittäinen pyöristysjakso, ja
pyöristysjakson tietoina sitä ympäröivät kaltevuusjaksot.

Paikannuspohjan sijaintiraiteen elementtilistassa on periaatteessa mahdollista olla rajattoman paljon linkityksen kautta
löytyviä taitepisteitä: Geometriaraiteen korkeuteen kussakin pisteessä vaikuttaa yksi tai kaksi kyseisen raiteen
profiilin taitepistettä, ja sille ei ole rajoitusta, kuinka monesta eri geometriaraiteesta ja siksi eri profiilista
olisi mahdollista päätyä taitepisteitä, jotka vaikuttavat sijaintiraiteen laskettuun korkeuteen.

Käytännössä on hyvin yleistä, että samalle sijaintiraiteelle linkitetään useita eri suunnitelmina sisään tuotuja
versioita raiteesta, jolla on geometriassa tasan sama profiili. Tästä syystä elementtilista deduplikoidaan
CurvedProfileSegment-sisällön perusteella.

## Pystygeometrian kuvaaja

Kuvaajan esitys yhdistelee nämä tiedot: Se hakee erikseen katsottavan raiteen pystygeometrian elementtilistan ja
ratametreissä tasaväleittäin lasketun korkeusviivan, ja näyttää tulokset samassa kaaviossa. Vaakaetäisyys
pystygeometrian kuvaajassa tarkoittaa m-arvoa sijaintiraiteen tai geometrian pituudella. Pystygeometrian kuvaaja
esittää:

- Suunnitelmien linkitysalueet ja segmenttien alku- ja loppukohdat (paksummat koko kuvaajan yli menevät pystyviivat)
- Suunnitelman korkeusjärjestelmän (N60 tai N2000)
- PVI-pisteet ja niiden päällä pisteen rataosoitteen sekä pyöristysjakson ympyrän säteen
- PVI-pisteiden välisellä apuviivalla taitepisteiden välisen pituuden (m-arvojen ero) ja kulman
- PVI-pisteiden vierellä niiden tangenttipisteet (ylös osoittavat nuolet), joilla pyöristysjakso alkaa ja päättyy, ja
  tekstinä tangenttipisteen etäisyys taitepisteestä (viivan pituus)
- Varsinaisen korkeusviivan, edustettuna sekä pisteviivana että korkeustasoina kuvaajan alaosassa
- Rataosoiteviivoittimen, jolla siis vaan sijainnit
