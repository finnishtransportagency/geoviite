# Paikannuspohjan kontekstit (Layout Context)

Paikannuspohjaa ei juuri koskaan käsitellä kaikkinensa, vaan melkein aina jonkin kontekstin kautta: Kukin konteksti
muodostaa yhtenäisen näkymän. Sama olio voi olla eri konteksteissa eri tiloissa: Raiteella voi olla eri nimi, eri
geometria, vaihteella eri sijainti, jne.. Kukin eri tila esitetään rivinä oliotyypin taulussa: Kutakin oliota, joka on
jollain tavalla paikannuspohjassa olemassa, edustaa tietokannassa vähintään yksi rivi.

Kontekstit muodostavat lyhyen, enintään kaksitasoisen puurakenteen, jossa oliorivien näkyvyys periytyy lehtiä
päin. Puussa on:

- Juuri: Virallinen paikannuspohja, "main-official"
- Juuresta haarautuu yksi luonnuspaikannuspohja, "main-draft"
- Juuresta haarautuu erikseen myös n kappaletta suunnitelmatilojen julkaistuja paikannuspohjia, "design-official"
- ... joista kustakin haarautuu yksi suunnitelman luonnospaikannuspohja, "design-draft"

Siis main-officialissa oleva rivi on näkyvissä kaikissa konteksteissa, kun taas kaikkien draft-kontekstien oliot ovat
näkyvissä vain omassa kontekstissaan, ja suunnitelmien julkaistujen paikannuspohjien rivit ovat näkyvissä myös tämän
suunnitelman luonnospaikannuspohjassa.

Jos samasta oliosta on rivi useammassa kontekstissa, juurta kauempi rivi korvaa alapuussaan juurta lähemmän. Kustakin
oliosta on kussakin kontekstissa näkyvillä enintään yksi rivi. Enimmillään siis design-draft-rivi voi korvata
kontekstissaan design-official-rivin, joka taas korvaisi kontekstissaan main-officialin; tai design-official-rivi voi
korvata main-officia-rivin ja olla näkyvillä design-draftiinsa.

## Pikaopas

| Konteksti                                     | Kuvaus                                         | Näkyvyys                                             |
|-----------------------------------------------|------------------------------------------------|------------------------------------------------------|
| **Main-official (virallinen paikannuspohja)** | Virallinen rataverkon nykytila                 | Näkyy kaikkiin konteksteihin                         |
| **Main-draft (luonnos)**                      | Työtila virallisen paikannuspohjan muutoksille | Näkyy vain main-draft -kontekstissa                  |
| **Design-official (julkaistu suunnitelma)**   | Julkaistu suunnitelma                          | Näkyy design-official ja design-draft -konteksteihin |
| **Design-draft (suunnitelmaluonnos)**         | Työtila suunnitelman muutoksille               | Näkyy vain kyseiseen design-draft -kontekstiin       |

## Virallinen paikannuspohja (Official Layout)

Virallinen paikannuspohja on se data joka viedään Ratkoon ja joka esittää rataverkon todellista nykytilaa parhaan
saatavilla olevan tiedon mukaan. Jos joku käsite on kerran ollut virallisessa paikannuspohjassa, se ei ikinä enää poistu
täydellisesti vaan sen poistot tehdään nk. soft deletenä, eli asettamalla ne "poistettu" (Deleted) tilaan.

## Luonnospaikannuspohja (Draft Layout)

Luonnospaikannuspohja on työtila jossa paikannuspohjaan voidaan tehdä muutoksia: uusien käsitteiden luontia ja
olemassaolevien muokkauksia. Koska luonnos on oma kontekstinsa, mikään muutos siellä ei vaikuta viralliseen
paikannuspohjaan suoraan.

Jos luonnosmuutosta ei haluta viedä viralliseen paikannuspohjaan, se voidaan myös perua (Revert). Tämä tapahtuu
yksinkertaisesti poistamalla luonnosolio, jolloin voimaan jää sen virallinen versio, jos sellaista on. Jos luonnos on
uusi luonti, sen peruminen poistaa koko käsitteen, mikä tarkoittaa että myös siihen viittaavat käsitteet täytyy poistaa
tai muuttaa niin että viittaus poistuu.

Luonnosmuutokset voidaan viedä viralliseen paikannuspohjaan julkaisu-toiminnon (Publication) kautta. Luonnostilassa on
mahdollista olla tilapäistä / osittaista dataa, koska sitä ollaan vasta luomassa, mutta sellaista ei voi olla
virallisessa paikannuspohjassa. Tästä johtuen, julkaisuun sisältyy joukko validointeja (julkaisuvalidointi, publication
validation, kts. [Julkaisu](julkaisu.md)), jonka avulla varmistetaan että julkaistavat tiedot muodostavat eheän
kokonaisuuden. Tässä varmistetaan myös että virallisesta paikannuspohjasta ei voida viitata pelkästään luonnoksena
olevaan käsitteeseen.

## Suunniteltu paikannuspohja (Design Layout)

Suunniteltuja paikannuspohjia voi olla monta, sillä ne koostuvat erillisistä suunnitelmista jotka tarjoavat itsenäiset
joukot suunniteltuja muutoksia. Käyttäjän kannalta ne toimivat pitkälti kuten luonnospaikannuspohja, mutta ne
ovat kukin omia erillisiä kontekstejaan. Suunniteltu paikannuspohja rakentuu aina virallisen paikannuspohjan päälle,
eikä siis ole mitenkään tietoinen varsinaisen paikannuspohjan luonnoskontekstista.

Suunnitelmilla on myös omat luonnosversionsa ja niistä tehdään omia julkaisujaan, jotka siis menevät julkaistuksi
suunnitelmaksi, ei varsinaiseen paikannuspohjaan. Vasta julkaistu suunnitelma viedään Ratkoon suunniltuina käsitteinä,
jolloin niihin voidaan suunnitella myös muita kohteita Ratkon puolella.

Kun suunnitelman käsitteet valmistuvat, ne viedään ensin varsinaisen paikannuspohjan luonnoksiksi. Luonnospuolella niitä
voidaan edelleen muokata ja täydentää ennen kun ne julkaistaan normaalin luonnoksen tapaan viralliseksi. Tässä kohtaa
myös itse suunnitelma päivitetään valmistuneeksi.

Rivi voidaan merkitä suunnitelmasta hylätyksi (cancelled). Tämä tarkoittaa, että jo kerran suunnitelmassa julkaistu
olion muutos tai lisäys halutaankin jättää tekemättä; mutta koska muutos on jo julkaistu Ratkoon, pitää sen
tekemättäjättäminen viedä myöskin julkaisun validointiprosessin läpi. Suunnitelman design-draft-kontekstiin ei oteta
huomioon hylättyjä design-draft-rivejä eikä myöskään saman ID:n omaavia design-official-riviä.

## Kontekstien väliset siirtymät

Sekä virallisen paikannuspohjan että suunnitelmien muutokset tehdään aina luonnosten kautta. Suunnitelmien
valmistuminenkin on virallisen paikannuspohjan muutos ja menee luonnoksen kautta. Mahdollisia siirtymiä kontekstien
välillä ovat siis:

- Virallinen paikannuspohja -> Luonnospaikannuspohja (luonnosmuutos)
- Luonnospaikannuspohja -> Virallinen paikannuspohja (paikannuspohjan julkaisu)
- Virallinen paikannuspohja -> Luonnossuunnitelma (suunniteltu muutos)
- Luonnossuunnitelma -> Julkaistu suunnitelma (suunnitelman julkaisu)
- Julkaistu suunnitelma -> Luonnospaikannuspohja (suunnitelman valmistuminen)

Siirtymät toteutetaan tallentamalla olion tila kohdekontekstiin (mahdollisen olemassaolevan rivin päälle), ja
julkaisuiden tapauksessa poistamalla lisäksi luonnoskontekstin rivi.

## Kontekstien tunnisteet (Layout Context ID)

Kaikissa paikannuspohjan tauluissa konteksti tunnistetaan `layout_context_id` -kentällä, joka on generoitu kenttä
muodossa `{design_id|'main'}_{draft|official}`:

- `main_official` - Virallinen paikannuspohja
- `main_draft` - Luonnospaikannuspohja
- `{id}_official` - Suunnitelman julkaistu versio (esim. `123_official`)
- `{id}_draft` - Suunnitelman luonnos (esim. `123_draft`)

### Layout Context ID:n suunnitteluratkaisu

`layout_context_id` yhdistää kolme käsitettä yhteen kenttään: onko kyseessä main-haara vai suunnitelma,
minkä suunnitelman konteksti on kyseessä, ja onko kyseessä luonnos vai julkaistu versio. Tämä yhdistelmä
toimii paikannuspohjan oliotaulujen primääriavaimen osana yhdessä olion ID:n kanssa.

Main-haaran luonnollinen tunniste olisi `null`, koska main ei ole suunnitelma. Tietokantojen null-arvojen
erikoisluonteen vuoksi tämä ei kuitenkaan toimi hyvin primääriavaimessa, joten null-arvo täytyy korvata
merkkijonolla `main`. Samalla on järkevää yhdistää myös draft-tieto samaan kenttään muodostamaan yhtenäinen
kontekstitunniste.

Kentän arvo generoidaan sovellustasolla eikä tietokannassa, koska se sopii paremmin yhteen Geoviiten
versiointitriggerien toiminnan kanssa. Tietokanta kuitenkin varmistaa arvon oikeellisuuden constrainteilla.

### Layout Context ID:t REST-rajapinnoissa

Kaikissa Geoviitteen rajapinnoissa ei tarvita tietoa sekä suunnitelmahaarasta että siitä kohdistuuko operaatio ka.
branchin viralliseen vai luonnospaikannuspohjaan, vaan joissain tilanteissa . Täten rajapinnoissa Layout Context ID on
hajotettu kahteen kenttään, jotka voi määrittää rajapintoihin erillään. Kentät kulkevat yleisesti nimellä
`LayoutBranch` (haaratieto) ja `PublicationState` (official/draft).

`LayoutBranch` saa arvoja allaolevan mukaisesti:

- `main` - Varsinainen paikannuspohja
- `design_{suunnitelman_int_id}` - Suunnitelmahaara (esim. `design_int_123`)

### Esimerkkejä:

Molemmat määrittävä kutsu:

```
GET /track-layout/location-tracks/{LayoutBranch}/{PublicationState}

GET /track-layout/location-tracks/main/official
GET /track-layout/location-tracks/design_int_123/draft
```

Vain haaraa käyttävä kutsu (muokkaus, joten kohdistuu aina draftiin):

```
POST /linking/{LayoutBranch}/location-tracks/geometry

POST /linking/main/location-tracks/geometry
POST /linking/design_int_123/location-tracks/geometry
```
