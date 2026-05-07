# AI:n käyttö Geoviitteessä

## Mitä AI-työkaluja käytetään?

Geoviitteessä käytetään GitHub Copilot:ia ja siitä nimenomaisesti
[enterprise versiota](https://docs.github.com/en/enterprise-cloud@latest/admin/overview/about-github-for-enterprises).

Käytännön työkaluina Copilotia on käytetty:
- [IDE:en (Intellij Idea) integroituna Githubin virallisen pluginin avulla](https://plugins.jetbrains.com/plugin/17718-github-copilot--your-ai-pair-programmer)
- [Copilot CLI:n kautta komentoriviltä](https://github.com/features/copilot/cli/)
- [GitHubiin web-käyttöliittymään integroidun review-toiminnon kautta](https://docs.github.com/en/copilot/concepts/agents/code-review)

## Mihin AI:ta on käytetty?

AI:ta käytetään kehitystyön tukena: ratkaisuvaihtoehtojen kartoittamiseen, automaattiseen
tekstinsyöttöön koodatessa, koodipohjan tutkimisen nopeuttamiseen, teknisten toteutussuunnitelmien
tekoon, sekä avustamaan dokumentoinnissa. Lisäksi AI:ta käytetään koodin generointiin kehittäjän
käskyttämänä sekä koodin katselmoinnin apuna niin paikallisesti kuin pull requesteissa GitHubissa.

## Mihin AI:ta EI ole käytetty?

Committeja tai pull requesteja ei ole luotu suoraan AI:lla, eikä kehitysprosessissa ole automaattisia
AI-toimintoja, jotka käynnistyisivät ilman kehittäjän käskytystä. AI ei pääse käsiksi Geoviitteen
dataan tai ajoympäristöihin, vaan vain koodiin. Geoviite itsessään ei myöskään sisällä mitään
AI-toimintoja.

## Vastuu AI:n tuottamasta sisällöstä

AI ei kehitä Geoviitettä vaan Geoviitteen kehittäjät voivat käyttää AI:ta työnsä apuna. Kehittäjät
ovat aina viimekädessä vastuussa siitä koodista ja niistä muutoksista jotka he Geoviitteeseen
tuovat, riippumatta siitä onko koodi osin tai jopa kokonaan AI:n avulla tuotettu. Kehittäjät käyvät
siis läpi kaikki AI:nkin tekemät muutokset ennen eteenpäin viemistä ja tämän jälkeen muutokset käyvät
vielä läpi normaalin review/testaus prosessin aivan kuten manuaalisestikin tehdyt muutokset.

