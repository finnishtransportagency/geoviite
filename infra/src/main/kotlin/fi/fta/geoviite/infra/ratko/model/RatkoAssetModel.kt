package fi.fta.geoviite.infra.ratko.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory

@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class RatkoAsset(
    open val state: RatkoAssetState,
    open val properties: List<RatkoAssetProperty>,
    open val rowMetadata: RatkoMetadata,
    open val locations: List<RatkoAssetLocation>?,
    val type: RatkoAssetType
) {
    abstract fun withoutGeometries(): RatkoAsset
}

data class RatkoMetadataAsset(
    override val properties: List<RatkoAssetProperty>,
    override val rowMetadata: RatkoMetadata = RatkoMetadata(),
    override val locations: List<RatkoAssetLocation>,
) : RatkoAsset(
    state = RatkoAssetState.IN_USE,
    type = RatkoAssetType.METADATA,
    rowMetadata = rowMetadata,
    properties = properties,
    locations = locations
) {
    override fun withoutGeometries() = this.copy(locations = locations.map { it.withoutGeometries() })
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RatkoSwitchAsset(
    val id: String?,
    override val state: RatkoAssetState,
    override val properties: List<RatkoAssetProperty>,
    override val rowMetadata: RatkoMetadata = RatkoMetadata(),
    override val locations: List<RatkoAssetLocation>?,
    val assetGeoms: List<RatkoAssetGeometry>?
) : RatkoAsset(
    state = state,
    type = RatkoAssetType.TURNOUT,
    rowMetadata = rowMetadata,
    properties = properties,
    locations = locations
) {
    override fun withoutGeometries() = this.copy(locations = locations?.map { it.withoutGeometries() })
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RatkoAssetProperty(
    val name: String,
    val enumValue: String? = null,
    val integerValue: Int? = null,
    val stringValue: String? = null,
)

data class RatkoAssetLocation(
    val nodecollection: RatkoNodes,
    val priority: Int,
    val accuracyType: RatkoAccuracyType,
) {
    fun withoutGeometries() = this.copy(nodecollection = nodecollection.withoutGeometries())
}

data class RatkoAssetGeometry(
    val geometry: RatkoGeometry,
    val geomType: RatkoAssetGeometryType,
    val assetGeomAccuracyType: RatkoAssetGeomAccuracyType,
)

enum class RatkoAssetGeometryType(@get:JsonValue val value: String) {
    JOINT_A("JOINT_POINT_A"),
    JOINT_B("JOINT_POINT_B"),
    JOINT_C("JOINT_POINT_C"),
    JOINT_D("JOINT_POINT_D"),
    MATH_POINT("MATH_POINT"),
    MATH_POINT_AB("MATH_POINT_AB"),
    MATH_POINT_AC("MATH_POINT_AC"),
    MATH_POINT_AD("MATH_POINT_AD"),
    CACHE_POINT("CACHE_POINT")
}

enum class RatkoAssetState(@get:JsonValue val value: String, val category: LayoutStateCategory? = null) {
    SUGGESTED("SUGGESTED", LayoutStateCategory.FUTURE_EXISTING),
    PLANNED("PLANNED", LayoutStateCategory.FUTURE_EXISTING), // RATAVAGE -prosessin kautta suunniteltu
    BUILT("BUILT", LayoutStateCategory.EXISTING), // rakennettu
    IN_TRAFFIC(
        "IN TRAFFIC",
        LayoutStateCategory.EXISTING
    ), // liikennöinti voi alkaa rajoituksin, kunnossapitäjä rakennusurakoitsija
    IN_USE(
        "IN USE",
        LayoutStateCategory.EXISTING
    ), // liikennöinti ilman rajoituksia, kunnossapitäjä kunnossapitourakoitsija
    REPLACED("REPLACED", LayoutStateCategory.NOT_EXISTING),
    NOT_IN_USE("NOT IN USE", LayoutStateCategory.EXISTING), // olemassa, mutta ei käytössä
    REMOVED("REMOVED", LayoutStateCategory.NOT_EXISTING), // olemassa, mutta irti radasta
    DELETED("DELETED", LayoutStateCategory.NOT_EXISTING), // purettu maastossa

    TRANSITION("TRANSITION"),
    CANCELED("CANCELED"), // muutospyyntö peruttu
    COMPLETED("COMPLETED"), // muutospyyntö käsitelty
    REJECTED("REJECTED"), // Hylätyn RYHTI toimenpide-ehdotuksen tai RATKO UI:n kautta tehty muutosilmoitus
    OLD("OLD"),
}

enum class RatkoAssetType(@get:JsonValue val value: String) {
    TURNOUT("turnout"),
    METADATA("metadata_location_accuracy")
}
