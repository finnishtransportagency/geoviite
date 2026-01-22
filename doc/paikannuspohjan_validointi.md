# Paikannuspohjan validointi

Tässä dokumentissa kuvataan Geoviitteen paikannuspohjan validointisäännöt, eli se mitä Geoviite takaa paikannuspohjan laadusta.
Kuvausta itse käsitteistöstä voi katsoa dokumentista [Tietomalli](tietomalli.md). 

Paikannuspohjan validointi koostuu kahdesta osasta:

1. Muutosta tehdessä sallitaan vain järkevät arvot muutettavaan käsitteeseen. Nämä ovat pääasiassa triviaaleja tarkistuksia, eikä kaikkia niistä listata tässä.
2. Muutoksia julkaistaessa validoidaan ne kokonaisuutena, varmistaen että niistä syntyy yhdessä ehjä paikannuspohja. Näissä voidaan tarkistaa monimutkaisempia suhteita olioiden välillä.
   - Julkaisusta mekanismina voi lukea tarkemmin dokumentista [Julkaisut](julkaisut.md).

## Oliokohtaiset eheysvalidoinnit

Näitä virheitä ei ole mahdollista syntyä, koska Geoviite ei salli virheellisen olion luontia (validoidaan olion rakentajassa).
Tärkeimpiä näistä ovat:

- Sisäinen eheys kaikilla olioilla:
    - Kenttien pakollisuudet
    - Tekstikenttien sallitut merkit ja pituudet
    - Numeeristen arvojen oikeat arvovälit
    - Enumeraatioiden sallitut arvot
- Raiteen / pituusmittauslinjan keskilinjageometrian sisäinen eheys
    - Keskilinjat ja pituusmittauslinjat ovat jatkuvia, eli viiva on ehjä eikä pisteiden välistä puutu mitään
    - Keskilinjat ja pituusmittauslinjat eivät voi tehdä yli 90 asteen kulmia 2 pistevälin välillä
    - Geometria koostuu segmenteistä, joista kukin sisältää vähintään 2 toisistaan eroavaa pistettä (viiva ei ole pistemäinen)
    - Segmenttien sisällä sama piste ei voi toistua
    - Seuraava segmentti alkaa aina siitä mihin edellinen loppui (sama piste)
    - Geometriassa on (automaattisesti generoituva) lineaarinen referointi m-arvoilla: tämä on aina kasvava
- Raiteen vaihdepisteistä muodostuva graafi ([Rataverkko graafi](rataverkko_graafi.md)) on eheä
    - Raide koostuu ketjusta solmuja ja niiden välisiä linkkejä
    - Raiteen keskellä voi olla vain vaihdesolmuja, reunoissa voi olla myös päätesolmuja (jos ei pääty vaihteeseen)
    - Sama solmu ei toistu raiteen matkalla
    - Sama vaihde ei toistu raiteen matkalla
    - Jänne ei koskaan ole tyhjä ja koska segmentti sisältää vähintään 2 pistettä, jänne ei ole pistemäinen
    - Raiteen jänne alkaa aina samasta solmusta johon edellinen päättyi (ketju on ehjä)
    - Raiteen vaihteeseen kytketty geometriaosuus on yhtenäinen

## Julkaisuvalidoinnit

Datan eheys riippuu monessa tilanteessa myös muista olioista ja niiden välisistä suhteista.
Vaikka yksittäiset oliot olisivat sellaisinaan valideja, ne eivät välttämättä silti muodosta ehjää kokonaisuutta.
Geoviite ei estä operaattoria luomasta noita tilanteita datan normaalin muokkauksen yhteydessä, vaan vasta kun
muutosjoukko on valmis ja sitä koitetaan julkaista, varmistetaan julkaisuvalidoinnissa että kokonaisuus on eheä.
Tämän tyyppisiä validointeja ovat esimerkiksi tunnusten uniikkius sekä raiteiden ja vaihteiden väliset kytkennät.

### Luokittelu

Julkaisuvalidoinnissa havaitut ongelmat luokitellaan seuraavasti

- Virhe: Paikannuspohjaa ei voi julkaista virheellisenä sillä se ei muodosta eheää rataverkkoa
- Varoitus: Näytetään käyttäjälle huomio virheestä, mutta sallitaan paikannuspohjan julkaisu siitä huolimatta

### Validointisäännöt

Validointi suoritetaan aina "julkaisujoukolle", johon kerätään koko se tila joka julkaisun myötä syntyisi:

- Ne jo julkaistut oliot joita ei olla julkaisemassa
- Julkaisun sisältämät oliot, sekä uudet että jotain jo julkaistua muokkaavat
- Ei siis näe lainkaan luonnos-olioita joita ei valittu julkaisuun mukaan
- Ei myöskään näe vanhaksi jäävää (ylikirjoitettavaa) tilaa aiemmista olioista

#### Yleiset validoinnit kaikille käsitteille (ei listata alla erikseen)

- Mikään olio ei saa viitata tyhjyyteen, eli jos tuo näkymä ei sisällä jotain viitattua oliota, viittaus on rikki
    - Estää viittamiseen uuteen olioon jota ei ole otettu mukaan julkaisuun
    - Jos viitatusta oliosta on muokkaus, sen ei tarvitse olla julkaisussa mukana: tällöin tuota muokkausta ei huomioida
- Mikään olio ei saa viitata poistettuun olioon jos se ei itse ole poistettu
    - Estää käytössä olevan käsitteen poistamisen tai poistetun palauttamisen ilman että riippuvuudet palautetaan myös

#### Ratanumero

- Ratanumerolla on oltava pituusmittauslinja
- Ratanumeron tunnus on uniikki (ei saman nimisiä ratanumeroita)
- Ratanumeron geokoodauskonteksti on validi (kts. Geokoodauskonteksti)

#### Pituusmittauslinja

- Pituusmittauslinjalla on ratanumero
- Pituusmittauslinjalla on ei-tyhjä keskilinjageometria
    - Huom. sisäinen validius oliokohtaisessa validoinnissa varmistaa että ei-tyhjä geometria on jatkuva jne (kts. yllä)
- Ratanumeron geokoodauskonteksti on validi (kts. Geokoodauskonteksti)

#### Tasakilometripiste

- Tasakilometripisteellä on oltava ratanumero ja pituusmittauslinja
- Tasakilometripisteellä on sijainti jos se ei ole poistettu
- Tasakilometripisteen numero on uniikki ratanumerolla (jos piste ei ole poistettu)
- Ratanumeron geokoodauskonteksti on validi (kts. Geokoodauskonteksti)
- Tasakilometripisteet sijaitsevat pituusmittauslinjan matkalla
    - Vain varoitus: linjan ulkopuolella oleva piste voi olla operaattorille hyödyllistä lisätietoa, vaikkei sillä ole geokoodauksessa merkitystä
- Tasakilometripisteen sijainti on riittävän lähellä pituusmittauslinjaa
    - Vain varoitus: etäisyysraja on mielivaltainen eikä etäisyys suoranaisesti riko mitään

#### Vaihde

- Vaihteen nimi on uniikki
- OID-tunnus on uniikki
    - Ei riski automaattisissa OID:ssa, mutta manuaalisesti syötetty OID voisi muutoin olla duplikaatti
- Vaihteella on määritelty joitain vaihdepisteitä (jos se ei ole poistettu)
- Vaihteen jatkospisteet vastaavat sen määriteltyä vaihderakennetta
- Vaihteeseen on kytketty sijaintiraiteta ja nuo linkitykset ovat eheitä (jos vaihde ei ole poistettu)
    - Vaihteen jatkospisteet vastaavat kytketyn raiteen jatkospisteitä
    - Raiteen vaihdepisteet vastaavat jotain vaihderakenteen linjaa
    - Raidegeometrian osuus on eheä, jatkuva osio
    - Vaihteen jatkospisteiden sijainnit vastaavat raiteen liitoskohtien sijainteja
        - Vain varoitus: raiteiden geometrian epätarkkuudet voivat aiheuttaa eroja
    - Kaikki vaihderakenteen linjat on kytketty johonkin raiteeseen
        - Vain varoitus: esimerkiksi kaikkia yksityisraiteita ei ole mallinnettu Geoviitteessä, joten joku linja voi puuttua mallista

#### Sijaintiraide

- Sijaintiraiteella on oltava ratanumero
- Raidenimi on uniikki ratanumerolla (jos raide ei ole poistettu)
- Duplikaattiraideviittaukset ovat eheitä
    - Poistettuun raiteeseen ei viitata duplikaattina
    - Duplikaattiraiteella ei itsellään ole duplikaatteja
- Raiteen vaihdelinkitykset ovat eheitä
    - Kts. vaihteen eheyssäännöt yllä: validointilogiikka on sama
    - Vaihdeosuuden eheysvalidointi on tästä suunnasta katsoessa vain varoitus, jotta kaikkia raiteen vaihdelinkityksiä ei tarvitse korjata kerralla
- Raiteen rakenteellinen nimi ja kuvaus ovat eheät, eli nimeämiskaavan vaatimat linkit (esim. päätyvaihteet) on määritelty ja valideja tähän käyttöön (parsittavissa)
- Raiteella on ei-tyhjä keskilinjageometria
    - Huom. sisäinen validius oliokohtaisessa validoinnissa varmistaa että ei-tyhjä geometria on jatkuva jne (kts. yllä)
- Raide on koko matkaltaan geokoodattavissa, eli sille löytyy geokoodauskonteksti jonka avulla voidaan tuottaa kaikki tasametripisteet
    - kts. Geokoodauskonteksti
    - Tässä kohdassa ei validoida kaikkia geokoodauskontekstin raiteita, vaan pelkästään tämä yksittäinen (jos kontekstin tiedot muuttuu, validoidaan toki sen muutoksen myötä muutkin)
- Raiteen manuaalisesti syötetty topologisen kytkeytymisen tyyppi vastaa sen oikeaa topologista kytkeytymistä (vaihdelinkkejä)
    - Vain varoitus: tiedolla ei suoranaisesti tehdä mitään (ero kielii ehkä virheellisestä kirjauksesta tai puuttuvasta vaihdetiedosta)
- Raiteella on järkevä pituus (ei alle metrin pituinen)
    - Vain varoitus: lyhytkin raide sinällään toimii ja voi olla välitilana hyödyllinen

#### Toiminnallinen piste

- Pisteellä on uniikki nimi
- Pisteellä on uniikki UIC-koodi
- Pisteen lyhenne on uniikki (jos määritelty)
- Pisteellä on RINF-tyyppi ja uniikki RINF-koodi
- Pisteellä on määritelty sekä pistemäinen sijainti että alue
- Pisteen pistemäinen sijainti on sen alueen sisällä
- Alueen polygoni on yksinkertainen (ei risteä itsensä kanssa)
- Alueen polygoni ei mene päällekkäin muiden toiminnallisten pisteiden alueiden kanssa

#### Geokoodauskonteksti

Geokoodauskonteksti validoidaan kokonaisuutena aina kun kontekstiin kuuluvat asiat muuttuvat: ratanumero, pituusmittauslinja, tasakilometripisteet

- Tasakilometripisteet ovat km-tunnuksen mukaisessa järjestyksessä
- Kilometrit eivät ole liian pitkiä, eli tasakilometripisteiden väli ei ole liian pitkä (osoitteen esityksessä metreille on vain 4 numeroa)
- Kaikki geokoodauskontekstia käyttävät ei-poistetut raiteet ovat kokonaisuudessaan geokoodattavissa sen avulla
    - Koko matkan tasametripisteet tuotettavissa
    - Edellyttää mm. että raiteen geometria on pituusmittauslinjan matkalla ja riittävän samansuuntainen

#### Raiteen jakaminen

Jos julkaisussa on tapahtunut raiteen jakaminen, validoidaan jakamiseen liittyvät lisätiedot:

- Vain yksi jakaminen per julkaisu
- Jaettava raide sekä kaikki jakamisessa syntyvät raiteet sisältyvät julkaisuun
- Kaikki jakamisen yhteydessä uudelleenlinkitetyt vaihteet sisältyvät julkaisuun
- Jaettu raide on merkitty poistetuksi
- Jakamisen tuloksena syntyviä raiteita ei ole muokattu jakamisen jälkeen (muut muutokset tehtävä erillisessä julkaisussa)
- Kaikkien jakamisessa syntyvien raiteiden ratanumero on sama kuin jaetun raiteen
- Jakamisessa raiteen geometria siirtyy kokonaisuudessaan ja muuttumattomana muille raiteille (jaetun raiteen alkuperäinen geometria on sama kuin tulosraiteiden yhdistetty geometria)
- Kaikki jaetun raiteen tasametripisteet löytyvät edelleen, nyt vain jaettuna uusille raiteille
- Uutta raiteen jakamista ei voida julkaista ennen kuin edellinen saman raiteen jakaminen on viety Ratkoon asti
