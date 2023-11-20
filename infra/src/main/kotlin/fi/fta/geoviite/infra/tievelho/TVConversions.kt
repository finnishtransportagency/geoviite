import fi.fta.geoviite.infra.tievelho.generated.model.*

fun getLiikenneMerkki() = TVKohdeluokkaVarusteetLiikennemerkitluonti(
    lahdejarjestelmanId = null,
    paattyen = null,
    lahdejarjestelma = TVNimikkeistoYleisetLahdejarjestelma.lj01, // should be nullable
    schemaversio = 1,
    alkaen = null,
    tiekohteenTila = TVNimikkeistoYleisetTiekohteenTila.tt01, // should be nullable
    menetelma = TVNimikkeistoYleisetMenetelma.m01, // should be nullable
    sijainti = TVKomponenttiYleisetPistesijaintiMuokkausSijainti(
        tie = 10,
        osa = 2,
        etaisyys = 4961,
    ),
    ominaisuudet = TVKohdeluokkaVarusteetLiikennemerkitSijainnitonAllOfOminaisuudet(
        infranimikkeisto = TVKomponenttiYleisetInfranimikkeistoluokitusInfranimikkeisto(
            rakenteellinenJarjestelmakokonaisuus = setOf(
                TVNimikkeistoYleisetRakenteellinenJarjestelmakokonaisuus.rjk03
            ),
            toiminnallinenJarjestelmakokonaisuus = setOf(),
        ),
        kuntoJaVauriotiedot = TVKomponenttiKuntoJaVauriotiedotYleinenKuntoluokkaKuntoJaVauriotiedot(
            arvioituJaljellaOlevaKayttoika = null,
            yleinenKuntoluokka = TVNimikkeistoKuntoJaVauriotiedotKuntoluokka.kl05,
        ),
        toiminnallisetOminaisuudet = TVKohdeluokkaVarusteetLiikennemerkitSijainnitonAllOfOminaisuudetAllOfToiminnallisetOminaisuudet(
            asetusnumero = TVNimikkeistoVarusteetLiikennemerkkiAsetusnumero.liiasnro140, // should be nullable
            voimassaoloAlkaa = null,
            lisatietoja = null,
            vaikutussuunta = TVNimikkeistoVarusteetLiikennemerkkiVaikutussuunta.liivasu01, // should be nullable
            lakinumero = TVNimikkeistoVarusteetLiikennemerkkiLakinumero.liilnro1, // should be nullable
            voimassaoloPaattyy = null,
        ),
        rakenteellisetOminaisuudet = TVKohdeluokkaVarusteetLiikennemerkitSijainnitonAllOfOminaisuudetAllOfRakenteellisetOminaisuudet(
            tekstikoko = TVNimikkeistoVarusteetLiikennemerkkiTekstikoko.liitekok01, // should be nullable
            kilvenValaistus = null,
            kalvotyyppi = TVNimikkeistoVarusteetLiikennemerkkiKalvotyyppi.liikalty01, // should be nullable
            paivaloistekalvo = null,
            arvo = null,
            kiinnitystapa = TVNimikkeistoVarusteetLiikennemerkkiKiinnitystapa.liikita01, // should be nullable
            tunnus = null,
            pintaAla = null,
            lisatyyppi = setOf(TVNimikkeistoVarusteetLiikennemerkkiLisatyyppi.liility01),
            materiaali = TVNimikkeistoVarusteetMateriaali.ma01, // should be nullable
            suunta = null,
            tyyppi = TVNimikkeistoVarusteetLiikennemerkkiTyyppi.liity07, // should be nullable
            koko = TVNimikkeistoVarusteetLiikennemerkkiKoko.liikok01, // should be nullable
            korkeusasema = null,
        ),
        toimenpiteet = listOf(),
        sijaintipoikkeus = TVNimikkeistoYleisetSijaintipoikkeus.sp01, // should be nullable
        // These have generated defaults (can be skipped in constructor)
        korjausvastuu = null,
        omistajatarkenne = null,
        omistaja = null,
        muuOmistaja = null,
        hoitovastuu = null,
        muuKorjausvastuu = null,
        muuHoitovastuu = null,
        talvikunnossapito = null,
    ),
    // These have generated defaults (can be skipped in constructor)
    edellinenOid = null,
    muutoksenLahdeOid = null,
    sijaintiTilannepaiva = null,
//    mitattugeometria = null,
    mitattugeometria = TVKomponenttiYleisetPistesijaintiMuokkausMitattugeometria(
        geometria = TVKomponenttiYleisetPistesijaintiGeometrycollectionGeometriesInner(
            coordinates = listOf(22.430364716361264, 60.49857702128488, 0),
            type = TVKomponenttiYleisetPistesijaintiGeometrycollectionGeometriesInner.Type.point,
        ),
    ),
//    sijaintitarkenne = null,
    sijaintitarkenne = TVKomponenttiYleisetSijaintitarkenneLuontiSijaintitarkenne(
        luiskat = null,
        kaistat = null,
        pientareet = null,
        puoli = null,
        reunaAlueet = null,
        tasanteet = null,
        erotusalueet = null,
        ojanPohjat = null,
        keskialue = null,
        ajoradat = null,
    ),
)
