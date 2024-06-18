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

Jos luonnosmuutosta ei haluta viedä viralliseen paikannuspohjaan, se voidaan myös perua (Revert). Tämä tapahtuu
yksinkertaisesti poistamalla luonnosolio, jolloin voimaan jää sen virallinen versio, jos sellaista on. Jos luonnos on
uusi luonti, sen peruminen poistaa koko käsitteen, mikä tarkoittaa että myös siihen viittaavat käsitteet täytyy poistaa
tai muuttaa niin että viittaus poistuu.

Luonnosmuutokset voidaan viedä viralliseen paikannuspohjaan julkaisu-toiminnon (Publication) kautta. Luonnostilassa on
mahdollista olla tilapäistä / osittaista dataa, koska sitä ollaan vasta luomassa, mutta sellaista ei voi olla
virallisessa paikannuspohjassa. Tästä johtuen, julkaisuun sisältyy joukko validointeja (julkaisuvalidointi, publication
validation, kts. [Julkaisu](julkaisu.md)), jonka avulla varmistetaan että julkaistavat tiedot muodostavat eheän
kokonaisuuden. Tässä varmistetaan myös että virallisesta paikannuspohjasta ei voida viitata pelkästään luonnoksena
olevaan käsitteeseen.

## Suunniteltu paikannupohja (Planned Layout)

Suunniteltuja paikannuspohjia voi olla monta, sillä ne koostuvat erillisistä suunnitelmista jotka tarjoavat itsenäiset
joukot suunniteltuja muutoksia. Käyttäjän kannalta ne toimivat pitkälti kuten luonnos paikannuspohja, mutta ne
ovat kukin omia erillisiä kontekstejaan. Suunniteltu paikannuspohja rakentuu aina virallisen paikannuspohjan päälle,
eikä siis ole mitenkään tietoinen varsinaisen paikannupohjan luonnos kontekstista.

Suunnitelmilla on myös omat luonnosversionsa ja niistä tehdään omia julkaisujaan, jotka siis menevät julkaistuksi
suunnitelmaksi, ei varsinaiseen paikannuspohjaan. Vasta julkaistu suunnitelma viedään Ratkoon suunniltuina käsitteinä,
jolloin niihin voidaan suunnitella myös muita kohteita Ratkon puolella.

Kun suunnitelman käsitteet valmistuvat, ne viedään ensin varsinaisen paikannuspohjan luonnoksiksi. Luonnospuolella niitä
voidaan edelleen muokata ja täydentää ennen kun ne julkaistaan normaalin luonnoksen tapaan viralliseksi. Tässä kohtaa
myös itse suunnitelma päivitetään valmistuneeksi.

## Kontekstien väliset siirtymät

Sekä virallisen paikannuspohjan että suunnitelmien muutokset tehdään aina luonnosten kautta. Suunnitelmien
valmistuminenkin on virallisen paikannuspohjan muutos ja menee luonnoksen kautta. Mahdollisia siirtymiä kontekstien
välillä ovat siis:

-   Virallinen paikannuspohja -> Luonnos paikannuspohja (luonnos muutos)
-   Luonnos paikannuspohja -> Virallinen paikannuspohja (paikannuspohjan julkaisu)
-   Virallinen paikannuspohja -> Luonnos suunnitelma (suunniteltu muutos)
-   Luonnos suunnitelma -> Julkaistu suunnitelma (suunnitelman julkaisu)
-   Julkaistu suunnitelma -> Luonnos paikannuspohja (suunnitelman valmistuminen)

## ID käsittely ja viittaukset kontekstien välillä

Eri kontekstien versiot samasta käsitteestä ovat kannassa omia rivejään ja siten niillä on omat ID:nsä. Käyttöliittymän
kannalta käsittellä on kuitenkin aina vain yksi ID ja lisätietona konteksti, jossa sitä tarkastellaan. Tuo yksi ID on
käsitteenä nimeltään virallinen ID (official ID). Käytännössä se on käsitteen ensimmäisen ilmenemän ID, riippumatta
missä kontekstissa se ensimmäisen kerran syntyi. Koska tuo ID ei muutu käsitteen siirtyessä eri kontekstien välillä,
käyttöliittymä voi helposti siirtyä tarkastelemaan koko paikannuspohjaa eri konteksteissa.

Myös käsitteiden keskinäiset viittaukset tehdään aina tällä virallisella ID:llä, minkä johdosta noita viittauksia ei
tarvitse korjata silloin kun käsiteestä tehdään uusi versio toiseen kontekstiin -- virallinen ID säilyy aina samana.
Koska luonnos ja suunnitelmat rakentuvat virallisen paikannuspohjan päälle, tämä tarkoittaa että niiden sisältämät
käsitteet voivat viitata virallisilla ID:llä sekä oman kontekstin käsitteisiin että virallisiin käsitteisiin.
Vastaavasti suunnitelman luonnosolio voi viitata suunnitelman luonnoskäsitteisiin, sen julkaistuihin käsitteisiin tai
virallisen paikannuspohjan käsitteisiin, mutta ei virallisen paikannuspohjan luonnoksiin.

Lopullinen rivi jota viite tarkoittaa pitää siis aina tulkita huomioiden myös käsiteltävä konteksti. Jos esimerkiksi
viralliseen käsitteeseen tulee muutos luonnoskontekstiin, toisen luonnoksen viite kyseiseen olioon säilyy
muuttumattomana mutta tarkoittaa nyt luonnosversiota käsitteestä. Vastaavasti jos käsite poistuu luonnoskontekstista,
sama viite tarkoittaa nyt virallisesta paikannuspohjasta tulevaa oliota.

Käsitteiden elinkaaria uuden luonnissa tai olemassaolevan muutoksissa kuvataan alla tarkemmin eri skenaarioille
kontekstit huomioiden. Huomattavaa on, että ensimmäisenä syntynyt olio (tietokantarivi) säilyy ja siirtyy lopulta
viralliseen paikannuspohjaan, mutta muokattaessa muutokset tehdään kopioon, josta data kopioidaan alkuperäiselle
julkaisussa. Tämä varmistaa että virallinen ID säilyy muita viittaajia varten kaikissa ketjuissa.

### Kaavioiden merkinnät

```mermaid
graph TD
    subgraph legendColor [Värikoodit]
        legendDraft(Luonnos paikannuspohja)
        legendOfficial(Virallinen paikannuspohja)
legendDraftPlan(Luonnos suunnitelma)
        legendPlan(Julkaistu suunnitelma)
        legendDraft ~~~ legendOfficial ~~~ legendDraftPlan ~~~ legendPlan
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
    classDef planObject fill: cyan, stroke: black, color: black;
    classDef draftPlanObject fill: pink, stroke: black, color: black;
    class legendDraft draftObject
    class legendOfficial officialObject
    class legendPlan planObject
    class legendDraftPlan draftPlanObject
    linkStyle 4 stroke: grey, stroke-dasharray: 5 5
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
        draftTnInDraft -- official_row --> officialTnInDraft
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
    startState == Luonnos muutos:\nViralliset oliot eivät muutu ==> draftState == Julkaisu:\nVirallinen olio päivittyy\nLuonnosolio poistuu ==> endState
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

### Suunnitelmakäsitteiden luonti ja muokkaus

Suunnitelmakäsitteiden luonti, muokkaus ja julkaisu tapahtuu samoin kuin virallisessa paikannuspohjassa, luonnosten
kautta. Näistä ketjuista ei ole erillisiä kaavioita, koska prosessi on sama kuin yllä. On kuitenkin hyvä huomata
viittauksiin tulee yksi kerros lisää, sillä suunnitelmaluonnos voi viitata sekä julkaistuun suunnitelmaan että
viralliseen paikannuspohjaan. Virallisen paikannuspohjan luonnokset ja suunnitelmaluonnokset elävät kuitenkin täysin
erillään, eikä niiden välillä voi olla viittauksia.

### Kaavio: Uuden käsitteen tuonti viralliseen paikannuspohjaan suunnitelman kautta

Kun uusi käsite luodaan suunnitelmaan, se syntyy ensin luonnossuunnitelmaoliona. Se julkaistaan ensin viralliseksi
suunnitelmaksi, sillä vain virallisesta suunnitelmasta voidaan tuoda dataa viralliseen paikannuspohjaan.

Kun suunnitelma valmistuu, siitä luodaan luonnosolio kopiona ja sitä voidaan muokata vielä edelleen. Kun suunnitelman
valmistuminen julkaistaan, suunnitelmaolio muutetaan viralliseksi ja siihen kopioidaan lopulliset tiedot luonnosoliosta.
Suunnitelmaolion ID on virallinen ID ja se säilyy, koska sama olio päivitetään lopulta viralliseksi.

```mermaid
graph TD
    subgraph planDraftState [Luonnossuunnitelma: uusi suunnitelmakäsite syntyy luonnoksena]
        subgraph planDraftTracks [Muut oliot viittaavat luonnossuunnitelmaan]
            track1InPlanDraft(Raide X)
        end
        subgraph planDraftTrackNumber [&nbsp]
            planTnInPlanDraft("Ratanumero 001\n❖ id=1, version=1")
        end
        track1InPlanDraft --> planTnInPlanDraft
    end
    subgraph planState [Julkaistu suunnitelma: olio säilyy]
        subgraph planTracks [Muut oliot viittaavat julkaistuun suunnitelmaan]
            track1InPlan(Raide X)
            track2InPlan(Raide Y)
        end
        subgraph planTrackNumber [&nbsp]
            planTnInPlan("Ratanumero 001\n❖ id=1, version=2")
        end
        track1InPlan --> planTnInPlan
        track2InPlan --> planTnInPlan
    end
    subgraph draftState [Paikannuspohjan luonnokseen tuodut muutokset]
        subgraph draftTracks [Muut oliot viittaavat edelleen viralliseen suunnitelmaan]
            track1InDraft(Raide X)
            track2InDraft(Raide Y)
            track3InDraft(Raide Z)
        end
        subgraph draftTrackNumber [Luonnos viittaa suunnitelmaan]
            draftTnInDraft(Ratanumero 001\nid=123, version=1)
            planTnInDraft("Ratanumero 001\n❖ id=1, version=2")
        end
        track1InDraft --> planTnInDraft
        track2InDraft --> planTnInDraft
        track3InDraft --> planTnInDraft
        draftTnInDraft -- design_row --> planTnInDraft
    end
    subgraph endState [Lopputila: virallinen paikannuspohja muuttunut]
        subgraph endTracks [Muiden viitteet säilyy koska ID ei muutu]
            track1InEnd(Raide X)
            track2InEnd(Raide Y)
            track3InEnd(Raide Z)
        end
        subgraph endTrackNumber [&nbsp]
            officialTnInEnd("Ratanumero 001\n❖ id=1, version=3")
        end
        track1InEnd --> officialTnInEnd
        track2InEnd --> officialTnInEnd
        track3InEnd --> officialTnInEnd
    end
    planTnInPlanDraft ====> planTnInPlan ====> planTnInDraft ====> officialTnInEnd
    planDraftState == Suunnitelman julkaisu:\nLuonnosolio muuttuu viralliseksi ==> planState == Luonnos muutos:\nSuunnitelman oliot eivät muutu ==> draftState == Julkaisu:\nSuunnitelmaolio muuttuu viralliseksi\nLuonnos poistuu ==> endState
    planTnInPlan -. Suunnitelmadata kopioidaan\nluonnosolioon .-> draftTnInDraft
    draftTnInDraft -. Luonnosdata kopioidaan\nsuunnitelmaolioon .-> officialTnInEnd
    classDef draftObject fill: lightyellow, stroke: black, color: black;
    classDef officialObject fill: lightgreen, stroke: black, color: black;
    classDef planObject fill: cyan, stroke: black, color: black;
    classDef draftPlanObject fill: pink, stroke: black, color: black;
    class track3InDraft,track3InEnd,draftTnInDraft draftObject
    class officialTnInEnd officialObject
    class track2InPlan,track2InDraft,track2InEnd,planTnInPlan,planTnInDraft planObject
    class track1InPlanDraft,track1InPlan,track1InDraft,track1InEnd,planTnInPlanDraft draftPlanObject
    linkStyle 10,11,12 stroke: grey, stroke-dasharray: 5 5
    classDef phaseCategory font-size: 18px, font-weight: bold
    class startState,planDraftState,planState,draftState,endState phaseCategory
    classDef objectCategory stroke: transparent
    class startTracks,planDraftTracks,planTracks,draftTracks,endTracks,startTrackNumber,planTrackNumber,draftTrackNumber,endTrackNumber objectCategory
```

### Kaavio: Olemassaolevan käsitteen muokkaus suunnitelman kautta

Suunniteltaessa muutos olemassaolevaan käsitteeseen, suunnitelmaan luodaan ensin luonnoskopio johon muutokset tehdään.
Nämä muutokset on julkaistava viralliseksi suunnitelmaksi ennen kun muutosta voidaan viedä viralliseen paikannuspohjaan.

Kun suunnitelma valmistuu, sen tiedot kopioidaan virallisen paikannuspohjan luonnosolioon, jota voidaan muokata
edelleen. Kun suunnitelman valmistuminen julkaistaan, alkuperäiselle oliolle kopioidaan data luonnosoliolta ja luonnos
sekä suunnitelman olio poistetaan. Alkuperäisen virallisen olion ID on virallinen ID ja se säilyy koska oliota vain
päivitetään.

```mermaid
graph TD
    subgraph startState [Alkutila: virallinen paikannuspohja]
        subgraph startTrackNumber [&nbsp]
            officialTnInStart("Ratanumero 001\n❖ id=1, version=1")
        end
        subgraph startTracks [Muut oliot viittaavat viralliseen]
            track1InStart(Raide X)
        end
        track1InStart --> officialTnInStart
    end
    subgraph planDraftState [Suunnitelmaluonnos: uudet versiot suunnitelman kontekstissa]
        subgraph planDraftTrackNumber [Luonnossuunnitelma viittaa viralliseen]
            officialTnInPlanDraft("Ratanumero 001\n❖ id=1, version=1")
            planTnInPlanDraft(Ratanumero 001\nid=123, version=1)
        end
        subgraph planDraftTracks [Muut oliot viittaavat viralliseen]
            track1InPlanDraft(Raide X)
            track2InPlanDraft(Raide Y)
        end
        track1InPlanDraft --> officialTnInPlanDraft
        track2InPlanDraft --> officialTnInPlanDraft
        planTnInPlanDraft -- official_row --> officialTnInPlanDraft
    end
    subgraph planState [Julkaistu suunnitelma]
        subgraph planTrackNumber [Suunnitelma viittaa viralliseen]
            officialTnInPlan("Ratanumero 001\n❖ id=1, version=1")
            planTnInPlan(Ratanumero 001\nid=123, version=1)
        end
        subgraph planTracks [Muut oliot viittaavat viralliseen]
            track1InPlan(Raide X)
            track2InPlan(Raide Y)
            track3InPlan(Raide Z)
        end
        track1InPlan --> officialTnInPlan
        track2InPlan --> officialTnInPlan
        track3InPlan --> officialTnInPlan
        planTnInPlan -- official_row --> officialTnInPlan
    end
    subgraph draftState [Muutos: uudet versiot luonnosolioissa]
        subgraph draftTrackNumber [Luonnos viittaa viralliseen]
            planTnInDraft(Ratanumero 001\nid=123, version=1)
            draftTnInDraft(Ratanumero 001\nid=456, version=1)
            officialTnInDraft("Ratanumero 001\n❖ id=1, version=1")
        end
        subgraph draftTracks [Muut oliot viittaavat viralliseen]
            track1InDraft(Raide X)
            track2InDraft(Raide Y)
            track3InDraft(Raide Z)
            track4InDraft(Raide Å)
        end
        track1InDraft --> officialTnInDraft
        track2InDraft --> officialTnInDraft
        track3InDraft --> officialTnInDraft
        track4InDraft --> officialTnInDraft
        planTnInDraft -- official_row --> officialTnInDraft
        draftTnInDraft -- design_row --> planTnInDraft
        draftTnInDraft -- official_row --> officialTnInDraft
    end
    subgraph endState [Lopputila: virallinen paikannuspohja muuttunut]
        subgraph endTracks [Muiden viitteet säilyy koska ID ei muutu]
            track1InEnd(Raide X)
            track2InEnd(Raide Y)
            track3InEnd(Raide Z)
            track4InEnd(Raide Å)
        end
        subgraph endTrackNumber [&nbsp]
            officialTnInEnd("Ratanumero 001\n❖ id=1, version=2")
        end
        track1InEnd --> officialTnInEnd
        track2InEnd --> officialTnInEnd
        track3InEnd --> officialTnInEnd
        track4InEnd --> officialTnInEnd
    end
    startState == Suunniteltu luonnosmuutos:\nViralliset oliot eivät muutu ==> planDraftState == Suunnitelman julkaisu:\nViralliset oliot eivät muutu ==> planState == Luonnos muutos:\nSuunnitelman oliot eivät muutu ==> draftState == Julkaisu:\nVirallinen olio päivittyy\nLuonnos ja suunnitelmaolio poistuu ==> endState
    officialTnInStart ====> officialTnInPlanDraft ====> officialTnInPlan ====> officialTnInDraft ====> officialTnInEnd
    planTnInPlanDraft ====>  planTnInPlan ====> planTnInDraft
    planTnInPlan -. Suunnitelmadata kopioidaan\nluonnosolioon .-> draftTnInDraft
    draftTnInDraft -. Luonnosdata kopioidaan\nalkuperäiseen olioon .-> officialTnInEnd
    classDef draftObject fill: lightyellow, stroke: black, color: black;
    classDef officialObject fill: lightgreen, stroke: black, color: black;
    classDef planObject fill: cyan, stroke: black, color: black;
    classDef draftPlanObject fill: pink, stroke: black, color: black;
    class draftTnInDraft,track4InPlan,track4InDraft,track4InEnd draftObject
    class officialTnInStart,officialTnInPlanDraft,officialTnInPlan,officialTnInDraft,officialTnInEnd,track1InStart,track1InPlanDraft,track1InPlan,track1InDraft,track1InEnd officialObject
    class planTnInPlan,planTnInDraft,track3InPlan,track3InDraft,track3InEnd planObject
    class planTnInPlanDraft,track2InPlanDraft,track2InPlan,track2InDraft,track2InEnd draftPlanObject
    linkStyle 23,24,25,26 stroke: grey, stroke-dasharray: 5 5
    classDef phaseCategory font-size: 18px, font-weight: bold
    class startState,planDraftState,planState,draftState,endState phaseCategory
    classDef objectCategory stroke: transparent
    class startTracks,planDraftTracks,planTracks,draftTracks,endTracks,startTrackNumber,planDraftTrackNumber,planTrackNumber,draftTrackNumber,endTrackNumber objectCategory
```
