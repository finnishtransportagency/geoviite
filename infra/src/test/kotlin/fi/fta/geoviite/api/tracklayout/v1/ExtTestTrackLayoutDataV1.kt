package fi.fta.geoviite.api.tracklayout.v1

data class ExtTestTrackLayoutVersionV1(val rataverkon_versio: String, val aikaleima: String, val kuvaus: String)

data class ExtTestTrackLayoutVersionCollectionResponseV1(
    val alkuversio: String,
    val loppuversio: String,
    val rataverkon_versiot: List<ExtTestTrackLayoutVersionV1>,
)

data class ExtTestCoordinateV1(val x: Double, val y: Double)

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
    val osoitevali: ExtTestGeometryIntervalV1?,
)

data class ExtTestModifiedLocationTrackGeometryResponseV1(
    val alkuversio: String,
    val loppuversio: String,
    val sijaintiraide_oid: String,
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
    val osoitevali: ExtTestGeometryIntervalV1?,
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

data class ExtTestKmPostOfficialLocationV1(
    val x: Double,
    val y: Double,
    val koordinaatisto: String,
    val vahvistettu: String,
)

data class ExtTestTrackKmV1(
    val tyyppi: String,
    val km_tunnus: String,
    val alkupaalu: String,
    val loppupaalu: String,
    val ratakilometrin_pituus: String,
    val virallinen_sijainti: ExtTestKmPostOfficialLocationV1?,
    val sijainti: ExtTestCoordinateV1,
)

data class ExtTestTrackNumberKmsV1(
    val ratanumero: String,
    val ratanumero_oid: String,
    val ratakilometrit: List<ExtTestTrackKmV1>,
)

data class ExtTestTrackKmsResponseV1(
    val rataverkon_versio: String,
    val koordinaatisto: String,
    val ratanumeron_ratakilometrit: ExtTestTrackNumberKmsV1,
)

data class ExtTestTrackKmsCollectionResponseV1(
    val rataverkon_versio: String,
    val koordinaatisto: String,
    val ratanumeroiden_ratakilometrit: List<ExtTestTrackNumberKmsV1>,
)

data class ExtTestErrorResponseV1(val virheviesti: String, val korrelaatiotunnus: String, val aikaleima: String)

data class ExtTestGeometryIntervalV1(
    val alkuosoite: String,
    val loppuosoite: String,
    val pisteet: List<ExtTestAddressPointV1>,
)

data class ExtTestSwitchJointV1(val numero: Int, val sijainti: ExtTestCoordinateV1)

data class ExtTestSwitchTrackJointV1(val numero: Int, val sijainti: ExtTestAddressPointV1)

data class ExtTestSwitchTrackLinkV1(val sijaintiraide_oid: String, val pisteet: List<ExtTestSwitchTrackJointV1>)

data class ExtTestSwitchV1(
    val vaihde_oid: String,
    val vaihdetunnus: String,
    val tyyppi: String,
    val katisyys: String,
    val esityspisteen_numero: Int,
    val tilakategoria: String,
    val omistaja: String,
    val turvavaihde: String,
    val pisteet: List<ExtTestSwitchJointV1>,
    val raidelinkit: List<ExtTestSwitchTrackLinkV1>,
)

data class ExtTestSwitchResponseV1(
    val rataverkon_versio: String,
    val koordinaatisto: String,
    val vaihde: ExtTestSwitchV1,
)

data class ExtTestModifiedSwitchResponseV1(
    val alkuversio: String,
    val loppuversio: String,
    val koordinaatisto: String,
    val vaihde: ExtTestSwitchV1,
)

data class ExtTestSwitchCollectionResponseV1(
    val rataverkon_versio: String,
    val koordinaatisto: String,
    val vaihteet: List<ExtTestSwitchV1>,
)

data class ExtTestModifiedSwitchCollectionResponseV1(
    val alkuversio: String,
    val loppuversio: String,
    val koordinaatisto: String,
    val vaihteet: List<ExtTestSwitchV1>,
)
