package fi.fta.geoviite.api.tracklayout.v1

data class ExtTestAddressPointV1(
    val x: Double,
    val y: Double,
    val rataosoite: String?,
)

data class ExtTestLocationTrackV1(
    val sijaintiraide_oid: String,
    val sijaintiraidetunnus: String,
    val ratanumero: String,
    val ratanumero_oid: String,
    val tyyppi: String,
    val tila: String,
    val kuvaus: String,
    val omistaja: String,
    val alkusijainti: ExtTestAddressPointV1?,
    val loppusijainti: ExtTestAddressPointV1?,
)

data class ExtTestLocationTrackCollectionV1(
    val rataverkon_versio: String,
    val koordinaatisto: String,
    val sijaintiraiteet: List<ExtTestLocationTrackV1>,
)

data class ExtTestModifiedLocationTrackCollectionV1(
    val alkuversio: String,
    val loppuversio: String,
    val koordinaatisto: String,
    val sijaintiraiteet: List<ExtTestLocationTrackV1>,
)

data class ExtTestErrorResponseV1(
    val virheviesti: String,
    val korrelaatiotunnus: String,
    val aikaleima: String,
)
