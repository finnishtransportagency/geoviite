package fi.fta.geoviite.infra.ratko.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.dataImport.RATKO_SRID
import fi.fta.geoviite.infra.geography.transformCoordinate
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import java.math.BigDecimal
import java.math.RoundingMode

const val GEOVIITE_NAME = "GEOVIITE"

data class RatkoOid<T>(val id: String) {
    constructor(oid: Oid<*>) : this(oid.stringValue)

    override fun toString() = id
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RatkoMetadata(
    val sourceName: String = GEOVIITE_NAME,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RatkoNodes(
    val nodes: List<RatkoNode> = listOf(),
    val type: RatkoNodesType,
) {
    @JsonIgnore
    fun getStartNode() = nodes.find { it.nodeType == RatkoNodeType.START_POINT }

    @JsonIgnore
    fun getEndNode() = nodes.find { it.nodeType == RatkoNodeType.END_POINT }

    fun withoutGeometries() = this.copy(nodes = nodes.map { it.withoutGeometry() })
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RatkoNode(
    val nodeType: RatkoNodeType,
    val point: RatkoPoint,
) {
    fun withoutGeometry() = this.copy(point = point.withoutGeometry())
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RatkoPoint(
    val kmM: RatkoTrackMeter,
    val geometry: RatkoGeometry?,
    val state: RatkoPointState?,
    val rowMetadata: RatkoMetadata? = null,
    val locationtrack: RatkoOid<RatkoLocationTrack>? = null,
    val routenumber: RatkoOid<RatkoRouteNumber>? = null,
) {
    fun withoutGeometry() = this.copy(geometry = null)
}

//^[0-9]{4}\+[0-9]{4}(\.[0-9]{1,15})?$
data class RatkoTrackMeter(
    override val kmNumber: KmNumber,
    override val meters: BigDecimal = BigDecimal.ZERO,
) : ITrackMeter {

    companion object {
        @JvmStatic
        @JsonCreator
        fun create(kmM: String): RatkoTrackMeter {
            val trackMeter = TrackMeter.create(kmM)
            return RatkoTrackMeter(
                kmNumber = trackMeter.kmNumber,
                meters = trackMeter.meters.let { m ->
                    if (m.scale() == 0) m
                    else m.setScale(3, RoundingMode.DOWN)
                }
            )
        }
    }

    constructor(trackMeter: TrackMeter) : this(trackMeter.kmNumber, trackMeter.meters)

    init {
        require(meters.scale() == 0 || meters.scale() == 3) {
            "Scale for ratko track meter can only be 0 or 3"
        }
    }

    @JsonValue
    override fun toString(): String {
        return meters.stripTrailingZeros().let { m ->
            formatTrackMeter(
                kmNumber = kmNumber,
                meters = if (m.scale() < 0) m.setScale(0, RoundingMode.DOWN) else m
            )
        }
    }
}

class RatkoGeometry(val type: RatkoGeometryType, coordinates: List<Double>, crs: RatkoCrs) {
    val coordinates: List<Double> =
        if (crs.properties.name != RATKO_SRID)
            transformCoordinate(crs.properties.name, RATKO_SRID, Point(coordinates[0], coordinates[1]))
                .let { listOf(it.x, it.y) }
        else coordinates

    val crs: RatkoCrs = RatkoCrs()

    constructor(point: IPoint) : this(
        type = RatkoGeometryType.POINT,
        coordinates = transformCoordinate(LAYOUT_SRID, RATKO_SRID, Point(point.x, point.y)).let { listOf(it.x, it.y) },
        crs = RatkoCrs()
    )
}

data class RatkoPointState(val name: RatkoPointStates)

data class RatkoCrsProperties(val name: Srid = RATKO_SRID)

data class RatkoCrs(val properties: RatkoCrsProperties = RatkoCrsProperties())

enum class RatkoNodeType(@get:JsonValue val value: String) {
    START_POINT("start_point"),
    END_POINT("end_point"),
    JOINT_A("joint_point_A"),
    JOINT_B("joint_point_B"),
    JOINT_C("joint_point_C"),
    JOINT_D("joint_point_D"),

    @Suppress("unused")
    MIDDLE_POINT("middle_point"),

    @Suppress("unused")
    SOLO_POINT("solo_point"),
}

enum class RatkoPointStates(@get:JsonValue val state: String) {
    VALID("VALID"),
    NOT_IN_USE("NOT IN USE"),
}

enum class RatkoGeometryType(@get:JsonValue val value: String) {
    POINT("Point")
}

enum class RatkoNodesType(@get:JsonValue val value: String) {
    JOINTS("joint_points"),
    START_AND_END("start_and_end"),
    END("end"),

    @Suppress("unused")
    START("start"),

    @Suppress("unused")
    START_MIDDLE_END("start_middle_end"),

    @Suppress("unused")
    POINT("point"),
}

enum class RatkoMeasurementMethod(@get:JsonValue val value: String) {
    VERIFIED_DESIGNED_GEOMETRY("VERIFIED DESIGNED GEOMETRY"),
    OFFICIALLY_MEASURED_GEODETICALLY("OFFICIALLY MEASURED GEODETICALLY"),
    TRACK_INSPECTION("TRACK INSPECTION"),
    DIGITALIZED_AERIAL_IMAGE("DIGITIZED AERIAL IMAGE"),
    UNVERIFIED_DESIGNED_GEOMETRY("UNVERIFIED DESIGNED GEOMETRY"),
    UNKNOWN("UNKNOWN")
}

enum class RatkoAssetGeomAccuracyType(@get:JsonValue val value: String) {
    DESIGNED_GEOLOCATION("DESIGNED GEOLOCATION"),
    OFFICIALLY_MEASURED_GEODETICALLY("OFFICIALLY MEASURED GEODETICALLY"),
    MEASURED_GEODETICALLY("MEASURED GEODETICALLY"),
    DIGITALIZED_AERIAL_IMAGE("DIGITIZED AERIAL IMAGE"),
    UNKNOWN("UNKNOWN"),
    GEOMETRY_CALCULATED("GEOMETRY CALCULATED"),
}

enum class RatkoAccuracyType(@get:JsonValue val value: String) {
    GEOMETRY_CALCULATED("GEOMETRY CALCULATED"),

    @Suppress("unused")
    DESIGNED_TRACK_ADDRESS("DESIGNED TRACKADDRESS"),

    @Suppress("unused")
    MEASURED_TRACK_ADDRESS("MEASURED TRACKADDRESS"),

    @Suppress("unused")
    OFFICIALLY_MEASURED_GEODETICALLY("OFFICIALLY MEASURED GEODETICALLY"),

    @Suppress("unused")
    MEASURED_GEODETICALLY("MEASURED GEODETICALLY"),

    @Suppress("unused")
    DIGITALIZED_AERIAL_IMAGE("DIGITIZED AERIAL IMAGE"),

    @Suppress("unused")
    ESTIMATED_TRACK_ADDRESS("ESTIMATED TRACKADDRESS"),
}
