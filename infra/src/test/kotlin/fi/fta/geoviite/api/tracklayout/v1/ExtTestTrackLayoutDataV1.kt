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
    val osoitevalit: List<ExtTestModifiedGeometryIntervalV1>,
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

data class ExtTestTrackBoundaryChangeV1(
    val geometrian_lahderaide_oid: String,
    val geometrian_lahderaide_tunnus: String,
    val geometrian_kohderaide_oid: String,
    val geometrian_kohderaide_tunnus: String,
    val alkuosoite: String,
    val loppuosoite: String,
    val geometrian_muutos: String,
)

data class ExtTestTrackBoundaryChangeOperationV1(
    val rataverkon_versio: String,
    val ratanumero: String,
    val ratanumero_oid: String,
    val tyyppi: String,
    val muutokset: List<ExtTestTrackBoundaryChangeV1>,
)

data class ExtTestTrackBoundaryChangeResponseV1(
    val alkuversio: String,
    val loppuversio: String,
    val rajojen_muutokset: List<ExtTestTrackBoundaryChangeOperationV1>,
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

data class ExtTestModifiedTrackNumberGeometryResponseV1(
    val alkuversio: String,
    val loppuversio: String,
    val ratanumero_oid: String,
    val koordinaatisto: String,
    val osoitevalit: List<ExtTestModifiedGeometryIntervalV1>,
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

data class ExtTestModifiedGeometryIntervalV1(
    val alkuosoite: String,
    val loppuosoite: String,
    val muutostyyppi: String,
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

data class ExtTestOperationalPointRatoTypeV1(val koodi: String, val selite: String)

data class ExtTestOperationalPointRinfTypeV1(val koodi: String, val selite_en: String)

data class ExtTestOperationalPointV1(
    val toiminnallinen_piste_oid: String,
    val rinf_id: String?,
    val nimi: String,
    val lyhenne: String?,
    val tila: String,
    val lähde: String,
    val tyyppi_rato: ExtTestOperationalPointRatoTypeV1?,
    val tyyppi_rinf: ExtTestOperationalPointRinfTypeV1?,
    val uic_koodi: String?,
    val sijainti: ExtTestCoordinateV1?,
    val raiteet: List<ExtTestOperationalPointTrackV1>,
    val vaihteet: List<ExtTestOperationalPointSwitchV1>,
    val alue: ExtTestPolygonV1?,
)

data class ExtTestPolygonV1(val tyyppi: String, val pisteet: List<ExtTestCoordinateV1>)

data class ExtTestOperationalPointTrackV1(val sijaintiraide_oid: String)

data class ExtTestOperationalPointSwitchV1(val vaihde_oid: String)

data class ExtTestOperationalPointResponseV1(
    val rataverkon_versio: String,
    val koordinaatisto: String,
    val toiminnallinen_piste: ExtTestOperationalPointV1,
)

data class ExtTestModifiedOperationalPointResponseV1(
    val alkuversio: String,
    val loppuversio: String,
    val koordinaatisto: String,
    val toiminnallinen_piste: ExtTestOperationalPointV1,
)

data class ExtTestOperationalPointCollectionResponseV1(
    val rataverkon_versio: String,
    val koordinaatisto: String,
    val toiminnalliset_pisteet: List<ExtTestOperationalPointV1>,
)

data class ExtTestModifiedOperationalPointCollectionResponseV1(
    val alkuversio: String,
    val loppuversio: String,
    val koordinaatisto: String,
    val toiminnalliset_pisteet: List<ExtTestOperationalPointV1>,
)
