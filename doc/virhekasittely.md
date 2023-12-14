# Virhekäsittely

## Backend

Poikkeuksia ei ole tarkoitus käyttää normaaliin kommunikaatioon vaan poikkeustilanteisiin. Siten niitä ei ole
lähtökohtaisesti tarkoitus catchata virhetilanteissa vaan niiden annetaan keskeyttää käyttäjän pyytämä operaatio. Tämä
johtaa aina yleisen tason poikkeuskäsittelyyn, joka hoitaa automaattisesti järkevän statuskoodin, lokituksen ja viestin
käyttäjälle. Lisäksi poikkeuksen myötä myös mahdollinen auki oleva transaktio rollbackataan automaattisesti.

Samasta syystä manuaalinen poikkeuksen heittäminen on paras tapa keskeyttää operaatio (paluuarvojen sijaan) jos
backendissä todetaan ettei sitä kannata jatkaa.

### Yleinen poikkeusten käsittely

Lokiviesti sekä clientille lähtevä paluuarvo muodostetaan koostetussa virheenkäsittelyssä
(paketti `fi/fta/geoviite/infra/error`).

Kunkin poikkeuksen käsittelytapaa on mahdollista muokata tarvittaessa `ErrorHandling.kt`:ssa. Yleensä riittää kuitenkin
käyttää valmiita ClientException/ServerException tyyppejä manuaalisissa poikkeuksen heitoissa, joilla voi kommunikoida
frontille halutun statuskoodin sekä lokalisoidun virheviestin parametreineen.

Yleinen käsittelytapa menee seuraavasti:

- Poikkeukset käsitellään uloimmasta alken, seuraten niiden cause-ketjua aina ensimmäiseen asti
    - Ensimmäinen tunnistettu tyyppi määrää mihin kategoriaan virhe kuuluu (virhekoodi)
    - Kaikista tunnistetuista tyypeistä koostetaan virheviestit taulukkoon, jos virhe on 4xx kategoriaa
- **Springin request mappauksen virheet**: client-virheitä (väärä osoite, laiton argumentti, jne)
    - Statuskoodi on 4xx-sarjaa ja tuotetaan springin virhetyypin mukaan (autentikaatio, autorisaatio, väärä url, väärä
      argumentti, jne)
    - **Huom!** Kaikki sisääntulevien objektien luonnista lentävät virheet (data-olioiden init-validointi jne) päätyy
      tähän kategoriaan automaattisesti. Validoinnit voivat siis vain heittää suoraan olion luonnissa ja front saa siitä
      järkevän 4xx virheen.
- **ClientException**: backendin eksplisiittinen tapa kertoa clientin virheestä
    - Statuskoodi (4xx) annetaan poikkeusta heittäessä
    - ClientException voi sisältää myös lokalisaatioavaimen, joka laitetaan mukaan paluuviestiin käyttäjälle esittämistä
      varten
- **ServerException**: eksplisiittisesti palvelinpään virhe
    - Virhekoodi 5xx, lähtökohtaisesti bugi tai ympäristövirhe
- **Kaikki muut poikkeukset**: oletetaan olevan odottamattomia palvelinpään virheitä -> 500
- Kaikki poikkeukset päätyvät lokiin
- 4xx virheistä palautetaan aina mahdollisimman avulias viesti clientille
- Tietoturvasyistä palvelinpään virheistä palautetaan vain geneerinen "500 internal server error" -viesti ilman
  lisätietoja, ettei odottamattomassa tilanteessa vahingossa vuodeta dataa.

## Frontend

Frontend tekee kaikki kutsut `api-fetch.ts`-tiedoston helper-funktioiden kautta.

### get / put / post / delete

Suurin osa kutsupaikoista menee näillä perusfunktioilla, ja ne hoitavat automaattisesti virhe-toast:n näyttämisen.
Useimmissa tilanteissa tämä riittää: lomake saa jäädä auki ja käyttäjän tulee joko korjata tiedot ja tallentaa
uudelleen, tai perua operaatio. Näin ollen, valtaosassa UI-komponentteja ei tarvitse tehdä mitään virhekäsittelyä. Jos
UI:lla on kuitenkin jotain tilaa jonka pitää valmistua kun pyyntö valmistuu (esim tallennuksen progress-indikaattori),
tämä kannattaa tehdä promisen finally-lohkossa.

### getAdt / putAdt / postAdt / deleteAdt

Jos frontissa tarvitsee jostain syystä erikseen käsitellä virhetilanteita, voidaan fetch-funktioiden Adt-versioita. Nämä
palauttaa Result-olion jonka sisällä on joko tulos tai epäonnistuneen pyynnön virheolio.
