# Projektivelho

## Yleistä

ProjektiVelho-integraatio on tiedostojen käsin uploadaamisen ohella toinen tapa tuoda 
rata-aineistoja Geoviitteeseen. Tätä tarkoitusta varten on toteutettu integraatio, jossa Geoviite 
kyselee periodisesti ProjektiVelholta listauksen uusimmista sinne syötetyistä 
InfraModel-tiedostoista ja suodattaa niistä pois valistuneella arvauksella kaikki 
ei-rata-aineistot. Koska koneellinen suodatus ei ole pomminvarmaa, haettuja aineistoja ei lisätä 
käytettäviksi inframalleiksi automaattisesti, vaan ne ainoastaan listataan operaattoreille 
manuaalista läpikäyntiä varten. Lopullinen valta aineistojen hyväksymiseen ja hylkäämiseen on aina
Geoviite-operaattorilla. 

## Tekninen yleiskuvaus

ProjektiVelho-integraatio on pull-tyyppinen integraatio, jossa Geoviite käy periodisesti 
kyselemässä ProjektiVelholta uusia aineistoja. Haku koostuu kahdesta osasta, jotka on ajastettu 
bäkkärille erillisinä tausta-ajoina (ajotiheys konfiguroitavissa.) Nämä tausta-ajot ovat:

1. Uusien aineistojen pollaus, jossa projektivelhosta kysellään mitä uusia aineistoja olisi 
saatavilla (ajetaan harvemmin, esim. muutaman kerran päivässä)
2. Käynnissä olevan pollauksen seuranta ja aineistohaku, jossa aineistot metatietoineen ladataan 
Geoviitteeseen (ajetaan useammin, esim. monta kertaa tunnissa)

Pollaus kysyy ProjektiVelholta viimeistä onnistuneesti tallennettua aineistoa seuraavat N aineistoa
(defaulttina N=100, mutta N on konfiguroitavissa.) Tämä haku kestää pitkään, joten sen tuloksia ei
jäädä odottelemaan tässä osassa.

Aineistohaku kyselee käynnissä olevan pollauksen tilan Velholta ja jos se on valmis, niin se 
suorittaa varsinaisen aineistojen haun ja tallentamisen Geoviitteeseen. Tässä yhteydessä 
päivitetään myös Geoviitteen käsitys Projektivelhon koodistosta. Kukin aineisto käsitellään 
yksitellen. Jos käsittely epäonnistuu, niin nostetaan kädet ilmaan. Seuraavalla kierroksella 
aloitetaan uudestaan epäonnistuneesta aineistosta.

Yksityiskohtaisempi sekvenssikaavio aiheesta:

```mermaid
sequenceDiagram
    participant G as Geoviite
    participant P as Projektivelho

    loop Pollaus
        G->>P: Käynnistä uusien pollaus
        P-->>G: Projektivelhon haku-ID, tallennetaan kantaan
        G->>G: Aloita Geoviitteeseen uusi aineistohaku Odottaa-tilaan
    end

    loop Pollauksen seuranta ja aineistohaku
        opt Uusien aineistojen pollaus käynnissä
            G->>P: Hae uusin versio koodistosta
            P-->>G: Koodisto, kakutetaan kantaan

            G->>P: Kysy pollausten statukset
            P-->>G: Pollausten statukset, haluttu tallennetaan kantaan

            opt Aloitettu Pollaus on valmis
                G-->>G: Merkitse pollaus valmiiksi
            end
        end

        opt Löytyy valmiiksi merkattu pollaus
            P->>G: Merkitse aineistohaku Haetaan-tilaan
            G->>P: Hae pollauksen tulokset
            P-->>G: Aineistolistaus
    
            loop Jokaiselle aineistolle erikseen
                G->>P: Hae aineiston metadatat
                P-->>G: Metadatat, tallennetaan kantaan ja matchataan koodistoon
                G->>P: Hae aineiston tiedosto
                P-->>G: Itse tiedosto, koitetaan parsia InfraModeliksi
    
                alt Saatiin parsittua, vaikuttaa rata-aineistolta
                    G->>G: Tallenna "Odottaa käsittelyä" -tilaisena
                else Saatiin parsittua, ei vaikuta rata-aineistolta
                    G->>G: Tallenna "Hylätty"-tilaisena
                end

                break Tiedoston tai metadatojen hakeminen tai tallentaminen epäonnistui
                    G->>G: Keskeytä aineistojen hakeminen ja merkitse se epäonnistuneeksi
                end
            end
            opt Kaikki meni OK
                G->>G: Merkitse aineistojen haku onnistuneeksi
            end
        end
    end
```

## Tietomalli

Geooviitteeseen tallennetaan erikseen Projektivelhosta tuodut dokumentit sekä niihin liittyvät 
toimeksiannot, projektit ja projektijoukot. Näistä voidaan haluttaessa tuoda tietyt dokumentit 
geoviitteen inframalli-listaukseen erillisenä käyttäjän operaationa.

Geoviitteeseen synkronoidaan tarvittavilta osin myös Projektivelhon nimikkeistöt (Dictionary), 
joita käytetään sieltä saatujen tietojen tulkitsemiseen. Näitä on mm. materiaalin luokitteluun ja 
tilaan liittyvät enumeraatiot. Käytännössä ne ovat koodi-nimi pareja, joista koodia käytetään 
dokumenttien sisällön kuvaamisessa ja nimeä siinä kohtaa kun arvo halutaan esittää 
käyttöliittymällä.

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

## Aineistojen metatiedotus (koodisto ja projektihierarkia)

Projektivelhosta tuotavat inframallit sisältävät InfraModel-tiedostojen ulkopuolista metatietoa, 
josta osa tallennetaan Geoviitteeseen. Aineiston projektihierarkia (projektijoukko,- projekti- ja 
toimeksiantoviittaukset) ja koodistotiedot tallennetaan siksi, että niitä näytetään UI:lla
operaattorille. Operaattorin on näiden avulla mahdollista navigoida niiden kautta Projektivelhoon 
tutkimaan suurempaa kokonaisuutta aineiston ympäriltä. Tämä informaatio on saatavilla vain 
projektivelhon kautta tuoduille aineistoille. Tiedoston versiotiedot tallennetaan siksi, että 
tällä pystytään varmentumaan siitä onko ka. aineisto tuotu jo Geoviitteeseen.

## Muuta teknistä

- Frontti ei ole koskaan suoraan yhteydessä Projektivelhoon (myös redirectit kulkevat bäkkärin 
kautta) 
- Projektivelhoon ei (ainakaan toistaiseksi) suoriteta yhtäaikaisia kutsuja
- Autentikaatio hoidetaan Bearer-authilla. Tokenin ikä tarkastellaan aina jokaisen kutsun alussa 
ja se päivitetään aina hieman ennen sen vanhenemista. Login-osoite on eri kuin varsinaisten 
kyselyiden osoite, joten se tarvitsee oman `WebClient`:in
