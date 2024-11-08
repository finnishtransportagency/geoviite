# Ajoitetut tehtävät (scheduled tasks)

Geoviite suorittaa suoritusasetuksiin perustuen tiettyjä ajoitettuja tehtäviä, kuten resurssi-intensiivisiä
tietotuotteiden luonteja sekä esimerkiksi yhteydenottoja ulkopuolisiin rajapintoihin.

Rakenteellisena periaatteena ajoitetuille tehtäville on luoda *Scheduler- tai *Task-luokka kontekstikohtaiseen paikkaan
tiedostohierarkiassa. Scheduler-luokat esimerkiksi tietylle Spring-palvelulle (Service) sisältävät useamman kuin yhden
ajoitetun tehtävän hallinnan ja vastaavasti *Task-luokat ovat yksittäisiä tehtäviä varten, mutta nämäkin tyypillisesti
kutsuvat jonkin Spring-palvelun funktiota.

Valinta Scheduler-luokan tai useamman Task-luokan välillä riippuu kontekstista. Järkevät kokonaisuudet tai samoja
ajoituksia käyttävät ajoitetut tehtävät on yleensä järkevämpää koostaa yhteen Scheduler-luokkaan. Vastaavasti
esimerkiksi tietotuotteiden luonnit ovat usein yksiselitteisempiä ymmärtää omina tehtävinään, jolloin käytettäisiin
Task-luokkia niitä varten.

## Asetustiedostot

Tavoitteena voisi pitää, että ajoitetut tehtävät olisi mahdollista kytkeä päälle sekä pois riippuen sovelluksen
suorituksessa käytettävistä Spring-profiileista, sillä eri suoritustilanteissa ei haluta kaikkien ajoitettujen tehtävien
suorittamista. Esimerkiksi testien suorituksen aikana ei tyypillisesti kaivata tietotuotteen automaattista luontia.

Spring-profiilien asetukset näiden ajoitettujen tehtävien
suoritukselle löytyvät tiedostohierarkiasta kahdesta paikasta (huomaa polussa siis main/test-ero):

```
infra/src/main/resources/application*.yml
infra/src/test/resources/application*.yml
```

Ajoitettujen tehtävien asetukset pyritään asettamaan asetustiedostoissa (eli `application*.yml`-tiedostoissa)
yksiselitteisen kontekstin alaisuuteen, eli esimerkiksi ulkoisiin integraatioihin liittyvien ajoitettujen tehtävien
asetukset löytyvät samasta paikasta kuin integraation muutkin asetukset. Esimerkki:

```
# Ei siis esimerkiksi näin:
geoviite:
  ...
  tasks:
    cache-preload:
      enabled: true
    jokin-integraatio-tehtava1:
      enabled: true
    jokin-toinen-integraatio-muu-tehtava:
      enabled: false
      
----

# Vaan mielummin näin:
geoviite:
  cache:
    tasks:
      preload:
        enabled: true
  
  jokin-integraatio:
    tasks:
      tehtava1:
        enabled: true
   
  jokin-toinen-integraatio:
    tasks:
      muu-tehtava:
        enabled: true
      
```

## Käytettävät ajoitukset

Käytettäviä ajoituksia ovat ainakin yksittäinen suoritus sovelluksen käynnistämisen jälkeen (`initial-delay`), toistoajo
riippumatta kellonajasta (`interval`) sekä kellonajasta riippuva suoritus (`cron`). Näistä `initial-delay` sekä
toistoajo `interval` ovat suositeltuja, ja niiden arvoina käytetään PT (Period Time)-formaattia. PT-formaatin käyttö on
suositeltua, sillä ne ovat helpommin tulkittavissa (esim. `PT30S` tai `PT5M`). Vastaavasti `cron`-ajoituksia
hyödynnetään, kun halutaan suorittaa tietty tehtävä tiettyyn kellonaikaan, kuten esimerkiksi raskas CSV-tiedoston
luonti keskellä yötä, jolloin Geoviitteellä on oletettavasti vähemmän aktiivisia käyttäjiä.

Ajoituksia voi olla yhtä tehtävää varten yksi tai useampi, eli tehtävä voi sisältää useamman kuin yhden ajoituksen.
Vastaavasti PT sekä cron-ajoituksia voi hyödyntää samalla tehtävälle.

```
# Kaikki ok
  jokin-integraatio:
    tasks:
      tehtava1:
        enabled: true
        initial-delay: PT2M
        interval: PT1H
        
      toinen-tehtava:
        enabled: true
        initial-delay: PT30S
        cron: "0 0 3 * * *" # (Huom: Ajoitus riippuu suoritusympäristön aikavyöhykkeestä)
```

## Ajoitusten lisäämisestä

Jos lisäät ajoituksen Springin oletusprofiiliin (eli `application.yml`), niin on suositeltua varmistaa, että tehtävä
on hyödyllinen eikä aiheuta ongelmia muissa profiileissa. Kytke siis tehtävä pois päältä sellaisten profiilien
asetustiedostoissa, joissa sitä ei tarvita. Oletusarvoisesti tehtävän kytkeminen pois päältä ja tietyssä profiileissa
aktivointi on myös mahdollista.
