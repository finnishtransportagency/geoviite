package fi.fta.geoviite.infra.geography

import fi.fta.geoviite.infra.common.Srid

val WGS_84_SRID = Srid(4326)

val ETRS89_SRID = Srid(4258)
val ETRS89_TM35FIN_SRID = Srid(3067)

val FIN_GK19_SRID = Srid(3873)
val FIN_GK20_SRID = Srid(3874)
val FIN_GK21_SRID = Srid(3875)
val FIN_GK22_SRID = Srid(3876)
val FIN_GK23_SRID = Srid(3877)
val FIN_GK24_SRID = Srid(3878)
val FIN_GK25_SRID = Srid(3879)
val FIN_GK26_SRID = Srid(3880)
val FIN_GK27_SRID = Srid(3881)
val FIN_GK28_SRID = Srid(3882)
val FIN_GK29_SRID = Srid(3883)
val FIN_GK30_SRID = Srid(3884)
val FIN_GK31_SRID = Srid(3885)
val gkFinSrids =
    setOf(
        FIN_GK19_SRID,
        FIN_GK20_SRID,
        FIN_GK21_SRID,
        FIN_GK22_SRID,
        FIN_GK23_SRID,
        FIN_GK24_SRID,
        FIN_GK25_SRID,
        FIN_GK26_SRID,
        FIN_GK27_SRID,
        FIN_GK28_SRID,
        FIN_GK29_SRID,
        FIN_GK30_SRID,
        FIN_GK31_SRID,
    )

val KKJ0_SRID = Srid(3386)
val KKJ1_SRID = Srid(2391)
val KKJ2_SRID = Srid(2392)
val KKJ3_YKJ_SRID = Srid(2393)
val KKJ4_SRID = Srid(2394)
val KKJ5_SRID = Srid(3387)
val kkjSrids = setOf(KKJ0_SRID, KKJ1_SRID, KKJ2_SRID, KKJ3_YKJ_SRID, KKJ4_SRID, KKJ5_SRID)

val geoviiteDefaultSrids by lazy {
    (listOf(WGS_84_SRID, ETRS89_SRID, ETRS89_TM35FIN_SRID) + gkFinSrids + kkjSrids).sortedBy(Srid::code)
}

fun isKKJ(srid: Srid) = kkjSrids.contains(srid)

fun isGkFinSrid(srid: Srid) = gkFinSrids.contains(srid)
