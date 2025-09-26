package fi.fta.geoviite.api.tracklayout.v1

data class ExtTestAddressPointV1(val x: Double, val y: Double, val rataosoite: String?)

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

data class ExtTestLocationTrackResponseV1(
    val rataverkon_versio: String,
    val koordinaatisto: String,
    val sijaintiraide: ExtTestLocationTrackV1,
)

data class ExtTestLocationTrackGeometryResponseV1(
    val rataverkon_versio: String,
    val sijaintiraide_oid: String,
    val koordinaatisto: String,
    val osoitevalit: List<ExtTestGeometryIntervalV1>,
)

data class ExtTestModifiedLocationTrackGeometryResponseV1(
    val alkuversio: String,
    val loppuversio: String,
    val koordinaatisto: String,
    val osoitevalit: List<ExtTestGeometryIntervalV1>,
)

data class ExtTestModifiedLocationTrackResponseV1(
    val alkuversio: String,
    val loppuversio: String,
    val koordinaatisto: String,
    val sijaintiraide: ExtTestLocationTrackV1,
)

data class ExtTestLocationTrackCollectionResponseV1(
    val rataverkon_versio: String,
    val koordinaatisto: String,
    val sijaintiraiteet: List<ExtTestLocationTrackV1>,
)

data class ExtTestModifiedLocationTrackCollectionResponseV1(
    val alkuversio: String,
    val loppuversio: String,
    val koordinaatisto: String,
    val sijaintiraiteet: List<ExtTestLocationTrackV1>,
)

data class ExtTestTrackNumberV1(
    val ratanumero_oid: String,
    val ratanumero: String,
    val kuvaus: String,
    val tila: String,
    val alkusijainti: ExtTestAddressPointV1?,
    val loppusijainti: ExtTestAddressPointV1?,
)

data class ExtTestTrackNumberResponseV1(
    val rataverkon_versio: String,
    val koordinaatisto: String,
    val ratanumero: ExtTestTrackNumberV1,
)

data class ExtTestModifiedTrackNumberResponseV1(
    val alkuversio: String,
    val loppuversio: String,
    val koordinaatisto: String,
    val ratanumero: ExtTestTrackNumberV1,
)

data class ExtTestTrackNumberGeometryResponseV1(
    val rataverkon_versio: String,
    val ratanumero_oid: String,
    val koordinaatisto: String,
    val osoitevalit: List<ExtTestGeometryIntervalV1>,
)

data class ExtTestTrackNumberCollectionResponseV1(
    val rataverkon_versio: String,
    val koordinaatisto: String,
    val ratanumerot: List<ExtTestTrackNumberV1>,
)

data class ExtTestModifiedTrackNumberCollectionResponseV1(
    val alkuversio: String,
    val loppuversio: String,
    val koordinaatisto: String,
    val ratanumerot: List<ExtTestTrackNumberV1>,
)

data class ExtTestErrorResponseV1(val virheviesti: String, val korrelaatiotunnus: String, val aikaleima: String)

data class ExtTestGeometryIntervalV1(val alku: String, val loppu: String, val pisteet: List<ExtTestAddressPointV1>)
