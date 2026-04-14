# Riippuvuuksien hallinta

Riippuvuuksien haavoittuvuusskannaukset ja automatisoidut päivitykset on automatisoitu
[GitHub Dependabotin](https://docs.github.com/en/code-security/tutorials/secure-your-dependencies) avulla.

## Haavoittuvuusskannaus

Dependabot skannaa projektin riippuvuudet tunnettujen haavoittuvuuksien varalta. Skannaustulokset ja hälytykset löytyvät
repositorion [Security-välilehdeltä](https://github.com/finnishtransportagency/geoviite/security/dependabot).

## Automaattiset päivitykset

Dependabot tarkistaa riippuvuuspäivitykset viikoittain ja avaa automaattisesti pull requesteja **minor**- ja **patch**
-tason päivityksille. Major-version päivitykset on rajattu pois automaatiosta, ja ne tehdään kehittäjien toimesta
manuaalisesti. PR:n yhdeydessä ajettavat automatisoidut workflow:t varmistavat että tulos ainakin kääntyy ja testit
menevät läpi. Dependabot ei kuitenkaan osaa tehdä asialle mitään jos build epäonnistuu, vaan ongelma jää kehittäjien
ratkaistavaksi.

Riippuvuudet on ryhmitelty siten, että toisiinsa kytkeytyvät kirjastot päivitetään yhdessä, mutta mahdolliset build/test
ajojen epäonnistumiset kohdistuu hiukan tarkemmin johonkin tiettyyn päivitykseen. Ryhmittelyn konfiguraatio löytyy
tiedostosta [`.github/dependabot.yml`](../.github/dependabot.yml), jossa ryhmittelyä voi säätää, jos automaattiset PR:t
eivät toimi toivotulla tavalla. Lisää ohjeita konfiguraatioon
löytyy [dependabotin dokumentaatiosta](https://docs.github.com/en/code-security/reference/supply-chain-security/dependabot-options-reference)

## Docker-kuvien skannaus

Docker-kuvat skannataan haavoittuvuuksien varalta image-repositoriossa AWS:ssä (ECR) ja tuloksia voi tarkastella AWS:n
Security Hubista tai ECR-imagen kuvauksesta.
