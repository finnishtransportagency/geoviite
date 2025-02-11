package fi.fta.geoviite.infra.ratko.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.split.BulkTransfer
import fi.fta.geoviite.infra.split.BulkTransferState
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory

@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class RatkoAsset(
    open val state: RatkoAssetState,
    open val properties: Collection<RatkoAssetProperty>,
    open val rowMetadata: RatkoMetadata,
    open val locations: List<RatkoAssetLocation>?,
    val type: RatkoAssetType,
    open val isPlanContext: Boolean,
    open val planItemIds: List<Int>?,
) {
    abstract fun withoutGeometries(): RatkoAsset
}

data class RatkoMetadataAsset(
    override val properties: Collection<RatkoAssetProperty>,
    override val rowMetadata: RatkoMetadata = RatkoMetadata(),
    override val locations: List<RatkoAssetLocation>,
    override val isPlanContext: Boolean,
    override val planItemIds: List<Int>?,
) :
    RatkoAsset(
        state = RatkoAssetState.IN_USE,
        type = RatkoAssetType.METADATA,
        rowMetadata = rowMetadata,
        properties = properties,
        locations = locations,
        isPlanContext = isPlanContext,
        planItemIds = planItemIds,
    ) {
    override fun withoutGeometries() = this.copy(locations = locations.map { it.withoutGeometries() })
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RatkoSwitchAsset(
    val id: String?,
    override val state: RatkoAssetState,
    override val properties: Collection<RatkoAssetProperty>,
    override val rowMetadata: RatkoMetadata = RatkoMetadata(),
    override val locations: List<RatkoAssetLocation>?,
    val assetGeoms: Collection<RatkoAssetGeometry>?,
    override val isPlanContext: Boolean,
    override val planItemIds: List<Int>?,
) :
    RatkoAsset(
        state = state,
        type = RatkoAssetType.TURNOUT,
        rowMetadata = rowMetadata,
        properties = properties,
        locations = locations,
        isPlanContext = isPlanContext,
        planItemIds = planItemIds,
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

data class RatkoAssetLocation(val nodecollection: RatkoNodes, val priority: Int, val accuracyType: RatkoAccuracyType) {
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
    @Suppress("unused") CACHE_POINT("CACHE_POINT"),
}

enum class RatkoAssetState(@get:JsonValue val value: String, val category: LayoutStateCategory? = null) {
    @Suppress("unused") PLANNED("PLANNED"), // RATAVAGE -prosessin kautta suunniteltu
    IN_USE(
        "IN USE",
        LayoutStateCategory.EXISTING,
    ), // liikennöinti ilman rajoituksia, kunnossapitäjä kunnossapitourakoitsija
    @Suppress("unused") SUGGESTED("SUGGESTED"),
    BUILT("BUILT", LayoutStateCategory.EXISTING), // rakennettu
    @Suppress("unused")
    IN_TRAFFIC(
        "IN TRAFFIC",
        LayoutStateCategory.EXISTING,
    ), // liikennöinti voi alkaa rajoituksin, kunnossapitäjä rakennusurakoitsija
    @Suppress("unused") REPLACED("REPLACED", LayoutStateCategory.NOT_EXISTING),
    NOT_IN_USE("NOT IN USE", LayoutStateCategory.EXISTING), // olemassa, mutta ei käytössä
    @Suppress("unused") REMOVED("REMOVED", LayoutStateCategory.NOT_EXISTING), // olemassa, mutta irti radasta
    DELETED("DELETED", LayoutStateCategory.NOT_EXISTING), // purettu maastossa
    @Suppress("unused") TRANSITION("TRANSITION"),
    @Suppress("unused") CANCELED("CANCELED"), // muutospyyntö peruttu
    @Suppress("unused") COMPLETED("COMPLETED"), // muutospyyntö käsitelty
    @Suppress("unused")
    REJECTED("REJECTED"), // Hylätyn RYHTI toimenpide-ehdotuksen tai RATKO UI:n kautta tehty muutosilmoitus
    @Suppress("unused") OLD("OLD"),
}

enum class RatkoAssetType(@get:JsonValue val value: String) {
    TURNOUT("turnout"),
    METADATA("metadata_location_accuracy"),
    RAILWAY_TRAFFIC_OPERATING_POINT("railway_traffic_operating_point"),
}

data class RatkoOperatingPointAssetsResponse(val assets: List<RatkoOperatingPointAsset>)

data class RatkoOperatingPointAsset(
    val id: String,
    val properties: Collection<RatkoAssetProperty>,
    val locations: List<IncomingRatkoAssetLocation>?,
) {
    fun getIntProperty(name: String): Int? = properties.find { it.name == name }?.integerValue

    fun getStringProperty(name: String): String? = properties.find { it.name == name }?.stringValue

    inline fun <reified T : Enum<T>> getEnumProperty(name: String): T? =
        properties
            .find { it.name == name }
            ?.enumValue
            ?.let { v -> T::class.java.enumConstants.find { c -> c.name == v } }
}

data class IncomingRatkoAssetLocation(val nodecollection: IncomingRatkoNodes)

data class IncomingRatkoNodes(val nodes: Collection<IncomingRatkoNode> = listOf())

data class IncomingRatkoNode(val point: IncomingRatkoPoint, val nodeType: RatkoNodeType)

data class IncomingRatkoPoint(val geometry: IncomingRatkoGeometry, val routenumber: RatkoOid<RatkoRouteNumber>)

data class IncomingRatkoGeometry(val type: RatkoGeometryType, val coordinates: List<Double>, val crs: RatkoCrs)

data class RatkoBulkTransferResponse(val id: IntId<BulkTransfer>, val state: BulkTransferState)
