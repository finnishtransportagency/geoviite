# Projektivelho

## Yleistä

ProjektiVelho-integraatio on tiedostojen käsin uploadaamisen ohella toinen tapa tuoda rata-aineistoja Geoviitteeseen.
Tätä tarkoitusta varten on toteutettu integraatio, jossa Geoviite kyselee periodisesti ProjektiVelholta
listauksen uusimmista sinne syötetyistä InfraModel-tiedostoista ja suodattaa niistä pois valistuneella arvauksella
kaikki ei-rata-aineistot. Koska koneellinen suodatus ei ole pomminvarma, haettuja aineistoja ei lisätä käytettäviksi
inframalleiksi, vaan ne ainoastaan listataan operaattoreille manuaalista läpikäyntiä varten. Lopullinen valta
aineistojen hyväksymiseen ja hylkäämiseen on siis Geoviite-operaattorilla. 

## Tekninen yleiskuvaus

ProjektiVelho-integraatio on pull-tyyppinen integraatio, jossa Geoviite käy periodisesti hakemassa ProjektiVelhosta 
kaikki sinne viimeisimmän onnistuneesti Geoviitteeseen tallennetun aineiston jälkeen lisätyt aineistot. Koska aineistoja on potentiaalisesti paljon, haku tehdään batcheissa (max. 100 aineistoa.) Prosessi menee jotakuinkin seuraavasti:
- Geoviite käynnistää ProjektiVelhoon haun. Haku itsessään on async, ja ProjektiVelho palauttaa tässä vaiheessa 
Geoviittelle haun id:n, jonka se tallentaa kantaan.
- Haun suorittumista seurataan periodisesti. Tässä yhteydessä päivitetään myös ProjektiVelhon nimikkeistö.
- Kun haku on valmis, haetaan sen tulokset erillisellä kutsulla.
- Tämän jälkeen jokaisesta aineistosta haetaan metadatat (toimeksianto, projekti ja projektijoukko) sekä itse tiedosto.
- Tiedosto koitetaan parsia IM-tiedostoksi. Jos parsinta epäonnistuu kokonaan, tiedosto hylätään.
- Kaikista tiedostoista tallennetaan kantaan vähintään metadatat, paitsi tilanteessa jossa tiedoston tai sen metadatan hakemisessa menee jotain vikaan. Tätä käytetään

Insert kaavio here

## Tietomalli

Geooviitteeseen tallennetaan erikseen Projektivelhosta tuodut dokumentit sekä niihin liittyvät toimeksiannot, projektit
ja projektijoukot. Näistä voidaan haluttaessa tuoda tietyt dokumentit geoviitteen inframalli-listaukseen erillisenä
käyttäjän operaationa.

Geoviitteeseen synkronoidaan tarvittavilta osin myös Projektivelhon nimikkeistöt (Dictionary), joita käytetään sieltä
saatujen tietojen tulkitsemiseen. Näitä on mm. materiaalin luokitteluun ja tilaan liittyvät enumeraatiot. Käytännössä ne
ovat koodi-nimi pareja, joista koodia käytetään dokumenttien sisällön kuvaamisessa ja nimeä siinä kohtaa kun arvo
halutaan esittää käyttöliittymällä.

```mermaid
classDiagram
    Assignment "0..1" <-- "*" Document
    Project "0..1" <-- "*" Document
    ProjectGroup "0..1" <-- "*" Document
    MaterialState "1" <-- "*" Document
    MaterialGroup "1" <-- "*" Document
    MaterialCategory "1" <-- "*" Document
    Document *-- "1" DocumentContent
    Document *-- "*" DocumentRejection
    ProjectState "1" <-- "*" Assignment
    ProjectState "1" <-- "*" Project
    ProjectState "1" <-- "*" ProjectGroup
    class Document {
        oid: String
        status: NOT_IM/SUGGESTED/REJECTED/ACCEPTED
        fileName: String
        description: String
        documentVersion: String
    }
    class DocumentContent {
        content: XML
    }
    class DocumentRejection

    class Assignment {
        oid: String
        name: String
    }
    class Project {
        oid: String
        name: String
    }
    class ProjectGroup {
        oid: String
        name: String
    }

    class ProjectState
    class MaterialState
    class MaterialGroup
    class MaterialCategory
```
