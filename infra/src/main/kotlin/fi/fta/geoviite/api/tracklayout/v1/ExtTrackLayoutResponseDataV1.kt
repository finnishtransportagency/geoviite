package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.api.tracklayout.v1.ExtKmPostLocationConfirmedV1.CONFIRMED
import fi.fta.geoviite.api.tracklayout.v1.ExtKmPostLocationConfirmedV1.NOT_CONFIRMED
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.geography.GeometryPoint
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.switchLibrary.SwitchHand
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostGkLocation
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import io.swagger.v3.oas.annotations.media.Schema

const val FI_MAIN = "pääraide"
const val FI_SIDE = "sivuraide"
const val FI_TRAP = "turvaraide"
const val FI_CHORD = "kujaraide"

@Schema(name = "Sijaintiraiteen tyyppi", type = "string", allowableValues = [FI_MAIN, FI_SIDE, FI_TRAP, FI_CHORD])
enum class ExtLocationTrackTypeV1(val value: String) {
    MAIN(FI_MAIN),
    SIDE(FI_SIDE),
    TRAP(FI_TRAP),
    CHORD(FI_CHORD);

    @JsonValue override fun toString() = value

    companion object {
        fun of(locationTrackType: LocationTrackType): ExtLocationTrackTypeV1 {
            return when (locationTrackType) {
                LocationTrackType.MAIN -> MAIN
                LocationTrackType.SIDE -> SIDE
                LocationTrackType.TRAP -> TRAP
                LocationTrackType.CHORD -> CHORD
            }
        }
    }
}

const val FI_BUILT = "rakennettu"
const val FI_IN_USE = "käytössä"
const val FI_NOT_IN_USE = "käytöstä poistettu"
const val FI_DELETED = "poistettu"

@Schema(
    name = "Sijaintiraiteen tila",
    type = "string",
    allowableValues = [FI_BUILT, FI_IN_USE, FI_NOT_IN_USE, FI_DELETED],
)
enum class ExtLocationTrackStateV1(val value: String) {
    BUILT(FI_BUILT),
    IN_USE(FI_IN_USE),
    NOT_IN_USE(FI_NOT_IN_USE),
    DELETED(FI_DELETED);

    @JsonValue fun jsonValue() = value

    companion object {
        fun of(locationTrackState: LocationTrackState): ExtLocationTrackStateV1 {
            return when (locationTrackState) {
                LocationTrackState.BUILT -> BUILT
                LocationTrackState.IN_USE -> IN_USE
                LocationTrackState.NOT_IN_USE -> NOT_IN_USE
                LocationTrackState.DELETED -> DELETED
            }
        }
    }
}

@Schema(name = "Ratanumeron tila", type = "string", allowableValues = [FI_IN_USE, FI_NOT_IN_USE, FI_DELETED])
enum class ExtTrackNumberStateV1(val value: String) {
    IN_USE(FI_IN_USE),
    NOT_IN_USE(FI_NOT_IN_USE),
    DELETED(FI_DELETED);

    @JsonValue fun jsonValue() = value

    companion object {
        fun of(trackNumberState: LayoutState): ExtTrackNumberStateV1 {
            return when (trackNumberState) {
                LayoutState.IN_USE -> IN_USE
                LayoutState.NOT_IN_USE -> NOT_IN_USE
                LayoutState.DELETED -> DELETED
            }
        }
    }
}

const val FI_EXISTING = "olemassaoleva kohde"
const val FI_NOT_EXISTING = "poistunut kohde"

@Schema(name = "Vaihteen tilakategoria", type = "string", allowableValues = [FI_EXISTING, FI_NOT_EXISTING])
enum class ExtSwitchStateV1(val value: String) {
    EXISTING(FI_EXISTING),
    NOT_EXISTING(FI_NOT_EXISTING);

    @JsonValue fun jsonValue() = value

    companion object {
        fun of(switchState: LayoutStateCategory): ExtSwitchStateV1 {
            return when (switchState) {
                LayoutStateCategory.EXISTING -> EXISTING
                LayoutStateCategory.NOT_EXISTING -> NOT_EXISTING
            }
        }
    }
}

const val FI_RIGHT = "oikea"
const val FI_LEFT = "vasen"
const val FI_NONE = "ei määritelty"

@Schema(name = "Vaihteen kätisyys", type = "string", allowableValues = [FI_RIGHT, FI_LEFT, FI_NONE])
enum class ExtSwitchHandV1(val value: String) {
    RIGHT(FI_RIGHT),
    LEFT(FI_LEFT),
    NONE(FI_NONE);

    @JsonValue override fun toString() = value

    companion object {
        fun of(hand: SwitchHand): ExtSwitchHandV1 {
            return when (hand) {
                SwitchHand.RIGHT -> RIGHT
                SwitchHand.LEFT -> LEFT
                SwitchHand.NONE -> NONE
            }
        }
    }
}

const val FI_YES = "kyllä"
const val FI_NO = "ei"
const val FI_UNKNOWN = "ei tiedossa"

@Schema(name = "Vaihteen turvavaihdestatus", type = "string", allowableValues = [FI_YES, FI_NO, FI_UNKNOWN])
enum class ExtSwitchTrapPointV1(val value: String) {
    YES(FI_YES),
    NO(FI_NO),
    UNKNOWN(FI_UNKNOWN);

    @JsonValue override fun toString() = value

    companion object {
        fun of(isTrapPoint: Boolean?): ExtSwitchTrapPointV1 {
            return when (isTrapPoint) {
                null -> UNKNOWN
                true -> YES
                false -> NO
            }
        }
    }
}

const val FI_TRACK_NUMBER_START = "ratanumeron alku"
const val FI_KM_POST = "tasakilometripiste"

@Schema(name = "Ratakilometrin tyyppi", type = "string", allowableValues = [FI_TRACK_NUMBER_START, FI_KM_POST])
enum class ExtTrackKmTypeV1(val value: String) {
    TRACK_NUMBER_START(FI_TRACK_NUMBER_START),
    KM_POST(FI_KM_POST);

    @JsonValue override fun toString() = value
}

const val FI_CONFIRMED = "vahvistettu"
const val FI_NOT_CONFIRMED = "ei vahvistettu"

@Schema(
    name = "Tasakilometripisteen virallisen sijainnin vahvistus",
    type = "string",
    allowableValues = [FI_CONFIRMED, FI_NOT_CONFIRMED],
)
enum class ExtKmPostLocationConfirmedV1(val value: String) {
    CONFIRMED(FI_CONFIRMED),
    NOT_CONFIRMED(FI_NOT_CONFIRMED);

    @JsonValue override fun toString() = value
}

const val FI_GEOMETRY = "geometria"
const val FI_ADDRESSING = "osoitteisto"

@Schema(
    name = "Tasakilometripisteen virallisen sijainnin vahvistus",
    type = "string",
    allowableValues = [FI_GEOMETRY, FI_ADDRESSING],
)
enum class ExtGeometryChangeTypeV1(val value: String) {
    GEOMETRY(FI_GEOMETRY),
    ADDRESSING(FI_ADDRESSING);

    @JsonValue override fun toString() = value
}

@Schema(name = "Koordinaattisijainti")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtCoordinateV1(val x: Double, val y: Double) {
    constructor(coordinate: IPoint) : this(coordinate.x, coordinate.y)
}

@Schema(name = "Tasakilometripisteen virallinen sijainti")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtKmPostOfficialLocationV1(
    val x: Double,
    val y: Double,
    @JsonProperty(COORDINATE_SYSTEM) val srid: ExtSridV1,
    @JsonProperty(OFFICIAL_LOCATION_CONFIRMED) val confirmed: ExtKmPostLocationConfirmedV1,
) {
    constructor(
        geometryPoint: GeometryPoint,
        confirmed: Boolean,
    ) : this(
        geometryPoint.x,
        geometryPoint.y,
        ExtSridV1(geometryPoint.srid),
        if (confirmed) CONFIRMED else NOT_CONFIRMED,
    )

    constructor(location: LayoutKmPostGkLocation) : this(location.location, location.confirmed)
}

@Schema(name = "Osoitepiste")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtAddressPointV1(
    val x: Double,
    val y: Double,
    @Schema(type = "string", example = "0012+0123.456") @JsonProperty(TRACK_ADDRESS) val trackAddress: String?,
)

@Schema(name = "Osoiteväli")
data class ExtCenterLineTrackIntervalV1(
    @Schema(type = "string", example = "0012+0123.456") @JsonProperty("alkuosoite") val startAddress: String,
    @Schema(type = "string", example = "0012+0123.456") @JsonProperty("loppuosoite") val endAddress: String,
    @JsonProperty("pisteet") val addressPoints: List<ExtAddressPointV1>,
)

@Schema(name = "Muuttunut osoiteväli")
data class ExtModifiedCenterLineTrackIntervalV1(
    @JsonProperty("alkuosoite") val startAddress: String,
    @JsonProperty("loppuosoite") val endAddress: String,
    @JsonProperty("muutostyyppi") val changeType: ExtGeometryChangeTypeV1,
    @JsonProperty("pisteet") val addressPoints: List<ExtAddressPointV1>,
)

@Schema(
    description = "Koordinaattijärjestelmän EPSG-koodi",
    type = "string",
    format = "epsg-code",
    pattern = "^EPSG:\\d{4}$",
    example = "EPSG:3067",
)
data class ExtSridV1(val value: Srid) {
    @JsonValue override fun toString() = value.toString()

    @JsonCreator(mode = DELEGATING) constructor(value: String) : this(Srid(value))
}

@Schema(description = "Rataverkon version yksilöivä tunniste", type = "string", format = "uuid")
data class ExtLayoutVersionV1(val value: Uuid<Publication>) {
    @JsonValue override fun toString() = value.toString()

    @JsonCreator(mode = DELEGATING) constructor(value: String) : this(Uuid<Publication>(value))

    constructor(publication: Publication) : this(publication.uuid)
}

@Schema(
    type = "string",
    format = "oid",
    // If we give an example value (for field descriptions), swagger fills it in as a default value in "try it out"
    // See Swagger issue: https://github.com/swagger-api/swagger-ui/issues/5776
    // example = "1.2.246.578.13.123456",
    // Workaround: this can also be assigned per-field in the json data classes
)
data class ExtOidV1<T>(val value: Oid<T>) {
    @JsonValue override fun toString() = value.toString()

    @JsonCreator(mode = DELEGATING) constructor(value: String) : this(Oid<T>(value))
}
