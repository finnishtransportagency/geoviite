# Paikannuspohjan kontekstit (Layout Context)

Kukin paikannuspohjan käsitteistä voi kuulua yhteen tai useampaan eri kontekstiin. Kontekstilla siis tarkoitetaan eri
versiota paikannuspohjan datasta, kuitenkin siten että ne muodostavat aina yhtenäisen kokonaisuuden.

## Virallinen paikannuspohja (Official Layout)

Virallinen paikannuspohja on se data joka viedään ratkoon ja joka esittää rataverkon todellista nykytilaa parhaan
saatavilla olevan tiedon mukaan. Jos joku käsite on kerran ollut virallisessa paikannuspohjassa, se ei ikinä enää poistu
täydellisesti vaan sen poistot tehdään nk. soft deletenä, eli asettamalla ne "poistettu" (Deleted) tilaan.

## Luonnos paikannuspohja (Draft Layout)

Luonnos paikannuspohja on työtila jossa paikannuspohjaan voidaan tehdä muutoksia: uusien käsitteiden luontia ja
olemassaolevien muokkauksia. Koska luonnos on oma kontekstinsa, mikään muutos siellä ei vaikuta viralliseen
paikannuspohjaan suoraan.

Luonnosmuutokset voidaan viedä viralliseen paikannuspohjaan julkaisu-toiminnon (Publication) kautta. Luonnostilassa on
mahdollista olla tilapäistä / osittaista dataa koska sitä ollaan vasta luomassa, mutta sellaista ei voi olla
virallisessa paikannuspohjassa. Tästä johtuen, julkaisuun sisältyy joukko validointia (publication validation), jonka
avulla varmistetaan että julkaistavat tiedot muodostavat eheän kokonaisuuden.

Koska virallisessa paikannuspohjassa olevan käsitteen poisto tehdään vain tilamuutoksella, sekin on oliotasolla vain
muokkaus. Luonnos kontekstista voidaan kuitenkin poistaa oikeasti sellaisia luonnoskäsitteitä, joita ole vielä julkaistu
viralliseen paikannuspohjaan. Tätä sanotaan luonnosmuutoksen perumiseksi (Revert).

## Suunniteltu paikannupohja (Planned Layout)

Suunniteltuja paikannuspohjia voi olla monta, sillä ne koostuvat erillisistä suunnitelmista jotka tarjoavat itsenäiset
joukot suunniteltuja muutoksia. Käyttäjän kannalta ne toimivat pitkälti kuten luonnos paikannuspohja, mutta ne
ovat kukin omia erillisiä kontekstejaan.

Suunnitelmille tehdään omia julkaisuja (toisin kuin luonnokselle, jonka julkaisu on virallisen paikannuspohjan muutos).
Julkaistu suunnitelma viedään Ratkoon suunniltuina käsitteinä, jolloin niihin voidaan suunnitella myös muita kohteita
Ratkon puolella.

Suunniteltu paikannuspohja rakentuu aina virallisen paikannuspohjan päälle, eikä siis ole mitenkään tietoinen luonnos
paikannuspohjasta. Kun suunnitelman käsitteet kuitenkin valmistuvat, ne viedään ensin luonnoksiksi. Luonnospuolella
niitä voidaan edelleen muokata ja täydentää ennen kun ne julkaistaan normaalin luonnoksen tapaan viralliseksi. Tässä
kohtaa myös suunnitelmajulkaisu päivitetään valmistuneeksi.

## ID käsittely ja viittaukset kontekstien välillä

Eri kontekstien versiot samasta käsitteestä ovat kannassa omia rivejään ja siten niillä on omat ID:nsä. Käyttöliittymän
kannalta käsittellä on kuitenkin aina vain yksi ID ja lisätietona konteksti jossa sitä tarkastellaan. Tuo yksi ID on
käsitteenä nimeltään virallinen ID (official ID). Käytännössä se on käsitteen ensimmäisen ilmenemän ID, riippumatta
missä kontekstissa se ensimmäisen kerran syntyi. Koska tuo ID ei muutu käsitteen siirtyessä eri kontekstien välillä,
käyttöliittymä voi helposti siirtyä tarkastelemaan koko paikannuspohjaa eri konteksteissa.

Myös käsitteiden keskinäiset viittaukset tehdään aina tällä virallisella ID:llä, minkä johdosta noita viittauksia ei
tarvitse korjata silloin kun käsiteestä tehdään uusi versio toiseen kontekstiin -- virallinen ID säilyy aina samana.
Koska luonnos ja suunnitelmat rakentuvat virallisen paikannuspohjan päälle, tämä tarkoittaa että niiden sisältämät
käsitteet voivat viitata virallisilla ID:llä sekä oman kontekstin käsitteisiin että virallisiin käsitteisiin.

Lopullinen rivi jota viite tarkoittaa pitää siis aina tulkita huomioiden myös konteksti. Jos esimerkiksi viralliseen
käsitteeseen tulee muutos luonnoskontekstiin, viite olioon säilyy muuttumattomana ja osoittaa nyt luonnosversioon.
Vastaavasti jos käsite poistuu luonnoskontekstista, viite tarkoittaa nyt virallisesta paikannuspohjasta tulevaa oliota.

Käsitteiden elinkaaria uuden luonnissa tai olemassaolevan muutoksissa kuvataan alla tarkemmin eri skenaarioille
kontekstit huomioiden. Huomattavaa on, että ensimmäisenä syntynyt olio (tietokantarivi) säilyy ja siirtyy lopulta
viralliseen paikannuspohjaan, mutta muokattaessa muutokset tehdään kopioon, josta data kopioidaan alkuperäiselle
julkaisussa. Tämä varmistaa että virallinen ID säilyy muita viittaajia varten kaikissa ketjuissa.

### Kaavioiden merkinnät

```mermaid
graph TD
    subgraph legendColor [Värikoodit]
        legendDraft(Luonnos olio)
        legendOfficial(Virallinen olio)
        legendPlan(Suunniteltu olio)
        legendDraft ~~~ legendOfficial ~~~ legendPlan
    end
    subgraph legendSymbol [Symbolit]
        legendOfficialId("Virallinen ID\n❖ id=x")
    end
    subgraph legendLine [Viivat]
        A -- Viite olioiden välillä --> B
        Cv1 == Olio siirtyy ==> Cv2
        D -. Olion data kopioidaan .-> E
    end
    classDef draftObject fill: lightyellow, stroke: black, color: black;
    classDef officialObject fill: lightgreen, stroke: black, color: black;
    classDef planObject fill: pink, stroke: black, color: black;
    class legendDraft draftObject
    class legendOfficial officialObject
    class legendPlan planObject
    linkStyle 3 stroke: grey, stroke-dasharray: 5 5
```

### Kaavio: Uuden käsitteen luonti luonnoksen kautta

Kun olio luodaan uutena, uusi olio ilmestyy ensin luonnokseksi ja muutetaan siitä viralliseksi julkaisussa. Official ID
on siis luodun luonnosrivin ID ja se säilyy, koska itse olio vain päivitetään viralliseksi.

```mermaid
graph TD
    subgraph draftState [Muutos: uudet versiot luonnosolioissa]
        subgraph draftTracks [Muut oliot viittaavat luonnokseen]
            track1InDraft(Raide 1)
        end
        subgraph draftTrackNumber [&nbsp]
            draftTnInDraft("Ratanumero 001\n❖ id=1, version=1")
        end
        track1InDraft --> draftTnInDraft
    end
    subgraph endState [Lopputila: virallinen paikannuspohja muuttunut]
        subgraph endTracks [Muiden viitteet säilyy koska ID ei muutu]
            track1InEnd(Raide 1)
        end
        subgraph endTrackNumber [&nbsp]
            officialTnInEnd("Ratanumero 001\n❖ id=1, version=2")
        end
        track1InEnd --> officialTnInEnd
    end

    draftState == Julkaisu:\nLuonnosolio muuttuu viralliseksi ==> endState
    draftTnInDraft ====> officialTnInEnd
    classDef draftObject fill: lightyellow, stroke: black, color: black;
    classDef officialObject fill: lightgreen, stroke: black, color: black;
    class draftTnInDraft,track1InDraft,track1InEnd draftObject
    class officialTnInEnd officialObject
    linkStyle 3 stroke: grey, stroke-dasharray: 5 5
    classDef phaseCategory font-size: 18px, font-weight: bold
    class draftState,endState phaseCategory
    classDef objectCategory stroke: transparent
    class draftTracks,endTracks,startTrackNumber,draftTrackNumber,endTrackNumber objectCategory
```

### Kaavio: Olemassaolevan käsitteen muokkaus luonnoksen kautta

Kun oliota muutetaan, muutokset kirjataan luonnoksena tehtyyn kopio-olioon. Kun muutokset julkaistaan, tiedot kopioidaan
luonnoksesta alkuperäiseen olioon. Virallinen ID on alkuperäisen virallisen olion ID ja se säilyy koska oliota vain
päivitetään.

```mermaid
graph TD
    subgraph startState [Alkutila: virallinen paikannuspohja]
        subgraph startTracks [Muut oliot viittaavat viralliseen]
            track1InStart(Raide X)
        end
        subgraph startTrackNumber [&nbsp]
            officialTnInStart("Ratanumero 001\n❖ id=1, version=1")
        end
        track1InStart --> officialTnInStart
    end
    subgraph draftState [Muutos: uudet versiot luonnosolioissa]
        subgraph draftTracks [Muut oliot viittaavat viralliseen]
            track1InDraft(Raide X)
            track2InDraft(Raide Y)
        end
        subgraph draftTrackNumber [Luonnos viittaa viralliseen]
            draftTnInDraft(Ratanumero 001\nid=123, version=1)
            officialTnInDraft("Ratanumero 001\n❖ id=1, version=1")
        end
        track1InDraft --> officialTnInDraft
        track2InDraft --> officialTnInDraft
        draftTnInDraft -- draft_of --> officialTnInDraft
    end
    subgraph endState [Lopputila: virallinen paikannuspohja muuttunut]
        subgraph endTracks [Muiden viitteet säilyy koska ID ei muutu]
            track1InEnd(Raide X)
            track2InEnd(Raide Y)
        end
        subgraph endTrackNumber [&nbsp]
            officialTnInEnd("Ratanumero 001\n❖ id=1, version=2")
        end
        track1InEnd --> officialTnInEnd
        track2InEnd --> officialTnInEnd
    end
    startState == Luonnos muutos:\nViralliset oliot ei muutu ==> draftState == Julkaisu:\nVirallinen olio päivittyy\nLuonnosolio poistuu ==> endState
    officialTnInStart ====> officialTnInDraft ====> officialTnInEnd
    draftTnInDraft -. Luonnosdata kopioidaan\nalkuperäiseen olioon .-> officialTnInEnd
    classDef draftObject fill: lightyellow, stroke: black, color: black;
    classDef officialObject fill: lightgreen, stroke: black, color: black;
    class draftTnInDraft,track2InDraft,track2InEnd draftObject
    class officialTnInStart,officialTnInDraft,officialTnInEnd,track1InStart,track1InDraft,track1InEnd officialObject
    linkStyle 8,9 stroke: grey, stroke-dasharray: 5 5
    classDef phaseCategory font-size: 18px, font-weight: bold
    class startState,draftState,endState phaseCategory
    classDef objectCategory stroke: transparent
    class startTracks,draftTracks,endTracks,startTrackNumber,draftTrackNumber,endTrackNumber objectCategory
```

### Kaavio: Uuden käsitteen luonti suunnitelman kautta

Kun uusi käsite luodaan suunnitelmaan, uusi olio ilmestyy suunnitelmaoliona. Kun suunnitelma valmistuu, siitä luodaan
luonnosolio kopiona ja sitä voidaan muokata vielä edelleen. Kun suunnitelman valmistuminen julkaistaan, suunnitelmaolio
muutetaan viralliseksi ja siihen kopioidaan lopulliset tiedot luonnosoliosta. Suunnitelmaolion ID on virallinen ID ja se
säilyy, koska sama olio päivitetään lopulta viralliseksi.

```mermaid
graph TD
    subgraph planState [Suunnitelma: uudet versiot suunnitelman kontekstissa]
        subgraph planTracks [Muut oliot viittaavat suunnitelmaan]
            track1InPlan(Raide X)
        end
        subgraph planTrackNumber [&nbsp]
            planTnInPlan("Ratanumero 001\n❖ id=1, version=1")
        end
        track1InPlan --> planTnInPlan
    end
    subgraph draftState [Muutos: uudet versiot luonnosolioissa]
        subgraph draftTracks [Muut oliot viittaavat viralliseen]
            track1InDraft(Raide X)
            track2InDraft(Raide Y)
        end
        subgraph draftTrackNumber [Luonnos viittaa viralliseen]
            draftTnInDraft(Ratanumero 001\nid=123, version=1)
            planTnInDraft("Ratanumero 001\n❖ id=1, version=1")
        end
        track1InDraft --> planTnInDraft
        track2InDraft --> planTnInDraft
        draftTnInDraft -- draft_of --> planTnInDraft
    end
    subgraph endState [Lopputila: virallinen paikannuspohja muuttunut]
        subgraph endTracks [Muiden viitteet säilyy koska ID ei muutu]
            track1InEnd(Raide X)
            track2InEnd(Raide Y)
        end
        subgraph endTrackNumber [&nbsp]
            officialTnInEnd("Ratanumero 001\n❖ id=1, version=2")
        end
        track1InEnd --> officialTnInEnd
        track2InEnd --> officialTnInEnd
    end
    planState == Luonnos muutos:\nSuunnitelman oliot ei muutu ==> draftState == Julkaisu:\nSuunnitelmaolio muuttuu viralliseksi\nLuonnos poistuu ==> endState
    planTnInPlan ====> planTnInDraft ====> officialTnInEnd
    planTnInPlan -. Suunnitelmadata kopioidaan\nluonnosolioon .-> draftTnInDraft
    draftTnInDraft -. Luonnosdata kopioidaan\nsuunnitelmaolioon .-> officialTnInEnd
    classDef draftObject fill: lightyellow, stroke: black, color: black;
    classDef officialObject fill: lightgreen, stroke: black, color: black;
    classDef planObject fill: pink, stroke: black, color: black;
    class draftTnInDraft,track2InDraft,track2InEnd draftObject
    class officialTnInEnd officialObject
    class planTnInPlan,planTnInDraft,track1InPlan,track1InDraft,track1InEnd planObject
    linkStyle 8,9 stroke: grey, stroke-dasharray: 5 5
    classDef phaseCategory font-size: 18px, font-weight: bold
    class startState,planState,draftState,endState phaseCategory
    classDef objectCategory stroke: transparent
    class startTracks,planTracks,draftTracks,endTracks,startTrackNumber,planTrackNumber,draftTrackNumber,endTrackNumber objectCategory
```

### Kaavio: Olemassaolevan käsitteen muokkaus suunnitelman kautta

Suunniteltaessa muutos olemassaolevaan käsitteeseen, suunnitelmaan luodaan kopio johon muutokset tehdään. Kun
suunnitelma valmistuu, sen tiedot kopioidaan luonnosolioon, jota voidaan muokata edelleen. Kun suunnitelman
valmistuminen julkaistaan, alkuperäiselle oliolle kopioidaan data luonnosoliolta ja luonnos sekä suunnitelman olio
poistetaan. Alkuperäisen virallisen olion ID on virallinen ID ja se säilyy koska oliota vain päivitetään.

```mermaid
graph TD
    subgraph startState [Alkutila: virallinen paikannuspohja]
        subgraph startTracks [Muut oliot viittaavat viralliseen]
            track1InStart(Raide X)
        end
        subgraph startTrackNumber [&nbsp]
            officialTnInStart("Ratanumero 001\n❖ id=1, version=1")
        end
        track1InStart --> officialTnInStart
    end
    subgraph planState [Suunnitelma: uudet versiot suunnitelman kontekstissa]
        subgraph planTracks [Muut oliot viittaavat viralliseen]
            track1InPlan(Raide X)
            track2InPlan(Raide Y)
        end
        subgraph planTrackNumber [Suunnitelma viittaa viralliseen]
            planTnInPlan(Ratanumero 001\nid=123, version=1)
            officialTnInPlan("Ratanumero 001\n❖ id=1, version=1")
        end
        track1InPlan --> officialTnInPlan
        track2InPlan --> officialTnInPlan
        planTnInPlan -- plan_of --> officialTnInPlan
    end
    subgraph draftState [Muutos: uudet versiot luonnosolioissa]
        subgraph draftTracks [Muut oliot viittaavat viralliseen]
            track1InDraft(Raide X)
            track2InDraft(Raide Y)
            track3InDraft(Raide Z)
        end
        subgraph draftTrackNumber [Luonnos viittaa viralliseen]
            draftTnInDraft(Ratanumero 001\nid=456, version=1)
            planTnInDraft(Ratanumero 001\nid=123, version=1)
            officialTnInDraft("Ratanumero 001\n❖ id=1, version=1")
        end
        track1InDraft --> officialTnInDraft
        track2InDraft --> officialTnInDraft
        track3InDraft --> officialTnInDraft
        planTnInDraft -- plan_of --> officialTnInDraft
        draftTnInDraft -- draft_of --> officialTnInDraft
    end
    subgraph endState [Lopputila: virallinen paikannuspohja muuttunut]
        subgraph endTracks [Muiden viitteet säilyy koska ID ei muutu]
            track1InEnd(Raide X)
            track2InEnd(Raide Y)
            track3InEnd(Raide Z)
        end
        subgraph endTrackNumber [&nbsp]
            officialTnInEnd("Ratanumero 001\n❖ id=1, version=2")
        end
        track1InEnd --> officialTnInEnd
        track2InEnd --> officialTnInEnd
        track3InEnd --> officialTnInEnd
    end
    startState == Suunniteltu muutos:\nViralliset oliot ei muutu ==> planState == Luonnos muutos:\nSuunnitelman oliot ei muutu ==> draftState == Julkaisu:\nVirallinen olio päivittyy\nLuonnos ja suunnitelmaolio poistuu ==> endState
    planTnInPlan ====> planTnInDraft
    officialTnInStart ====> officialTnInPlan ====> officialTnInDraft ====> officialTnInEnd
    planTnInPlan -. Suunnitelmadata kopioidaan\nluonnosolioon .-> draftTnInDraft
    draftTnInDraft -. Luonnosdata kopioidaan\nalkuperäiseen olioon .-> officialTnInEnd
    classDef draftObject fill: lightyellow, stroke: black, color: black;
    classDef officialObject fill: lightgreen, stroke: black, color: black;
    classDef planObject fill: pink, stroke: black, color: black;
    class draftTnInDraft,track3InDraft,track3InEnd draftObject
    class officialTnInStart,officialTnInPlan,officialTnInDraft,officialTnInEnd,track1InStart,track1InPlan,track1InDraft,track1InEnd officialObject
    class planTnInPlan,planTnInDraft,track2InPlan,track2InDraft,track2InEnd planObject
    linkStyle 15,17,18 stroke: grey, stroke-dasharray: 5 5
    classDef phaseCategory font-size: 18px, font-weight: bold
    class startState,planState,draftState,endState phaseCategory
    classDef objectCategory stroke: transparent
    class startTracks,planTracks,draftTracks,endTracks,startTrackNumber,planTrackNumber,draftTrackNumber,endTrackNumber objectCategory
```
