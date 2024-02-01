# Projektivelho

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
