# Haastattelu 2026-04-24

Raakatallennus haastattelukysymyksistä ja käyttäjän vastauksista muokkaamattomina.

---

**K: Mikä on projektisi nimi ja lyhyt kuvaus siitä, mitä se tekee?**

V: Projekti on Geoviite, se on tietojärjestelmä, jolla ylläpidetään Suomen rataverkon ratageometrioita.

---

**K: Ketkä ovat projektin pääkäyttäjäryhmät tai sidosryhmät? (esim. loppukäyttäjät, tilaajat, integraatiokumppanit)**

V: Tilaaja on Väylävirasto. Loppukäyttäjät ovat pääasiassa Geoviite-operaattoreita, jotka ylläpitävät tietoja. Geoviitettä käyttää myös joukko lukuoikeuksilla varustettuja konsulttikäyttäjiä, jotka tarkastavat rataverkon tietoja. Geoviite on integroitunut Ratko-järjestelmään, jonne Geoviitteestä viedään mm. raiteiden, vaihteiden ja ratanumeroiden geometriset tiedot sekä hallinnolliset perustiedot. Geoviitteestä rataverkon tietoja lukee myös Projektivelho, Kuvatieto, Raita ja Väylän analytiikka.

---

**K: Mikä on projektin tekninen arkkitehtuuri pääpiirteissään? (esim. käytetyt teknologiat, frontend/backend, tietokanta)**

V: Arkkitehtuuri on pääpiirteissään: Selainkäyttöliittymä (Typescript, React, Openlayers), Backend (Kotlin, Spring), tietokanta Postgres + PostGis. Järjestelmä on kontitettu ja suoritetaan AWS ympäristössä.

---

**K: Mitkä ovat projektin keskeisimmät toiminnallisuudet tai ominaisuudet tällä hetkellä?**

V: Keskeisimmät ominaisuudet ovat yhtenäisen rataverkon geometrioiden ylläpito ja rataverkon tiedoista hakeminen/raportointi, geometriasuunnitelmakirjaston ylläpito.

---

**K: Missä vaiheessa projekti on tällä hetkellä? (esim. kehitysvaihe, tuotanto, ylläpito) Ja onko projektilla erityisiä lähiajan tavoitteita tai kehityskohteita?**

V: Projekti on ollut kehityksessä 1-2021 alken ja tuotannossa 11-2022 alkaen, kehitystä on jatkettu koko tuotannossaolon ajan. Kehitys jatkuu edelleen. Lähiajan suurempia tavoitteita ovat: 1. Geoviitteen ja Ratkon välisen integraation kääntäminen niin, että Ratko lukee tiedot Geoviiteestä (PULL-integraatioksi) 2. Suunnitelmatilaisen rataverkon tarjoaminen API:sta, niin että suunnitelmatilaisuus saataisiin käytöön myös Ratkon puolella. Pienempinä tavoitteina on mm. hallinnollisten alueiden (esim. tilirataosa) geometrian ylläpidon siirto Geoviitteeseen.

---

**K: Onko projektilla erityisiä teknisiä haasteita tai tunnettuja ongelmakohtia, joita kannattaa dokumentoida?**

V: Erityisiä teknisiä haasteita ei ole tiedossa, muita kuin Geoviite-Ratko integraation suunnan kääntäminen PUSH mallista PULL malliin, mutta kaikkiaan tietomalli on melko kompleksinen, joten sitä on varmaan syytä dokumentoida uusia kehittäjiä varten.

---

**K: Kuvaile lyhyesti keskeisimmät tietomallin käsitteet — esim. mitä tarkoittavat "raide", "ratageometria", "ratanumero" ja muut projektin ydinkäsitteet?**

V: Voisin ensin luetella keskeisimmät käsitteet ja sitten yksitellen kuvailla kunkin käsitteen. Käsitteet ovat: raide, vaihde, ratanumero, pituusmittauslinja, tasakilometripiste, rataosoitejärjestelmä, geometriasuunnitelma, geometriaelementti, vaakageometria, pystygeometria, toiminnallinen piste, paikannuspohja, rataverkko, linkitys

---

**K: Kuvaile "raide" — mitä se tarkoittaa Geoviitteessä?**

V: "raide" tai tarkemmin sanottuna "sijaintiraide" on Geoviitteessä tunniste ja joukko muita ominaisuustietoja yhtenäiselle geometriselle osuudelle rataverkolla. Rataverkkoa voi ajatella graaffina, jossa vaihteet ovat solmukohtia ja vaihteiden väliset kiskot linkkejä. Sijaintiraide määrittää tunnisteen ja ominaisuustietojoukon n-kappaleelle peräkkäisiä linkkejä. Vaikka rataverkkoa on helpompi hahmottaa maastoon rakennetun rataverkon avulla, Geoviitteessä raiteen geometrinen muoto on raiteen suunniteltu muoto. Maastoon raide (eli fyysinen raide) pyritään kuitenkin rakentamaan tarkasti suunnitelman mukaan, joten Geoviitteen raiteen muodon pitäisi melko hyvin täsmätä maastossa olevaan raiteeseen, mutta todellisuudessa näiden välillä on poikkeamaa. Maastossa raide myös liikkuu ajan saatossa, maan liikkumisen ja liikennöinnin seurauksena. Raiteiden kunnossapito palauttaa raiteita maastossa niiden suunniteltuihin sijainteihin.

---

**K: Kuvaile "vaihde" — mitä se tarkoittaa Geoviitteessä?**

V: "vaihde" kuvaa Geoviitteessä rataverkon solmukohtaa, jossa junan on mahdollista siirtyä raiteelta toiselle. Vaihde sisältää tunnisteen, tyypin ja joukon ominaisuustietoja. Vaiheita on eri tyyppisiä. Vaihteen koko tyyppi koostuu vaihdetyypistä sekä vaihteen tarkemmista mitoista. Vaihdetyyppi määrittää vaihteen periaatteellisen toiminnan. Vaihdetyyppejä ovat esim. yksinkertainen vaihde (lyhenne YV), josta voi kulkea suoraan tai kääntyä yhdelle poikkeavalle raiteelle, tai kaksoisvaihde (lyhenne KV), josta voi kulkea suoraan tai kääntyä toiselle poikkeavista raiteista. Vaihteen koko tyyppi voi olla esim. "YV54-200N-1:9-O", jossa "YV" on vaihdetyyppi, "54" tarkoittaa kiskopainoa, "200" tarkoittaa poikkeavan raiteen kaaren sädettä, "N" tarkoittaa vaihdetyypin levittämätöntä versiota, "1:9" tarkoittaa risteyksen suhdetta (eli juinka "jyrkkään" suoraan menevä ja poikkeava raide sijoittuvat toisiinsa nähden), "O" tarkoittaa oikealle kääntyvää poikkeavaa raidetta. Geoviitteen vaihde on periaatteessa luonteeltaan looginen esiintymä rataverkon vaihteesta, eli Geoviitteessä on tieto siitä, millainen vaihde missäkin kohtaa on, joten maastossa pitäisi voida vaihtaa vaihteen tilalle uusi samanlainen vaihde, ilman että Geoviitteen rataverkkoon tehdään muutoksia. Tämä loogisuus on kuitenkin edelleen periaatteellisella tasolla ja vaihteen vaihto maastossa aiheuttaa tarpeen vaihtaa vaihde myös Geoviitteessä. Tämä fyysisen ja loogisen maailman eroavaisuus on kuitenkin hyvä pitää mielessä, kun tulevaisuuden ratkaisuja mietitään.

---

**K: Haluatko jatkaa muiden käsitteiden (ratanumero, pituusmittauslinja, tasakilometripiste jne.) kuvailua nyt, vai tallennetaanko tähän mennessä kerätyt tiedot ensin?**

V: Tallennetaan ensin kerätyt tiedot.
