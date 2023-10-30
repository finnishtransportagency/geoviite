package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geography.crs
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryKmPost
import fi.fta.geoviite.infra.geometry.GeometrySwitch
import fi.fta.geoviite.infra.linking.SuggestedSwitch
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.ValidatedAsset
import fi.fta.geoviite.infra.switchLibrary.SwitchOwner
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.util.FreeText
import java.math.BigDecimal
import java.time.Instant

val LAYOUT_SRID = Srid(3067)
val LAYOUT_CRS = crs(LAYOUT_SRID)

enum class LayoutState(val category: LayoutStateCategory) {
    IN_USE(LayoutStateCategory.EXISTING),
    NOT_IN_USE(LayoutStateCategory.EXISTING),
    PLANNED(LayoutStateCategory.FUTURE_EXISTING),
    DELETED(LayoutStateCategory.NOT_EXISTING);

    fun isPublishable() = this != PLANNED
    fun isLinkable() = this == IN_USE || this == NOT_IN_USE
    fun isRemoved() = this == DELETED
}

enum class LayoutStateCategory {
    FUTURE_EXISTING, EXISTING, NOT_EXISTING;

    fun isPublishable() = this != FUTURE_EXISTING
    fun isLinkable() = this == EXISTING
    fun isRemoved() = this == NOT_EXISTING
}

enum class TopologicalConnectivityType {
    NONE, START, END, START_AND_END;
}

data class LocationTrackDuplicate(
    val id: IntId<LocationTrack>,
    val name: AlignmentName,
    val externalId: Oid<LocationTrack>?,
)

data class LocationTrackDescription(
    val id: IntId<LocationTrack>,
    val description: FreeText,
)

data class TrackLayoutTrackNumber(
    val number: TrackNumber,
    val description: FreeText,
    val state: LayoutState,
    val externalId: Oid<TrackLayoutTrackNumber>?,
    override val id: DomainId<TrackLayoutTrackNumber> = StringId(),
    override val dataType: DataType = DataType.TEMP,
    override val version: RowVersion<TrackLayoutTrackNumber>? = null,
    @JsonIgnore override val draft: Draft<TrackLayoutTrackNumber>? = null,
) : Draftable<TrackLayoutTrackNumber> {
    @JsonIgnore
    val exists = !state.isRemoved()

    init {
        require(description.isNotBlank()) { "TrackNumber should have a non-blank description" }
        require(description.length < 100) { "TrackNumber description too long: ${description.length}>100" }
    }
}

enum class LocationTrackType {
    MAIN, // Pääraide
    SIDE, // Sivuraide
    TRAP, // Turvaraide: Turvaraide on raide, jonka tarkoitus on ohjata liikkuva kalusto riittävän etäälle siitä raiteesta, jota turvaraide suojaa. https://fi.wikipedia.org/wiki/Turvavaihde
    CHORD, // Kujaraide: Kujaraide on raide, joka ei ole sivu-, eikä pääraide.
}

data class ReferenceLine(
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val startAddress: TrackMeter,
    val sourceId: IntId<GeometryAlignment>?,
    override val id: DomainId<ReferenceLine> = deriveFromSourceId("RL", sourceId),
    override val dataType: DataType = DataType.TEMP,
    override val version: RowVersion<ReferenceLine>? = null,
    val boundingBox: BoundingBox? = null,
    val length: Double = 0.0,
    val segmentCount: Int = 0,
    @JsonIgnore val alignmentVersion: RowVersion<LayoutAlignment>? = null,
    @JsonIgnore override val draft: Draft<ReferenceLine>? = null,
) : Draftable<ReferenceLine> {

    init {
        require(dataType == DataType.TEMP || alignmentVersion != null) {
            "ReferenceLine in DB must have an alignment"
        }
    }

    fun getAlignmentVersionOrThrow(): RowVersion<LayoutAlignment> =
        alignmentVersion ?: throw IllegalStateException("ReferenceLine has no an alignment")
}

data class TopologyLocationTrackSwitch(
    val switchId: IntId<TrackLayoutSwitch>,
    val jointNumber: JointNumber,
)

val locationTrackDescriptionLength = 4..256

enum class DescriptionSuffixType {
    NONE, SWITCH_TO_SWITCH, SWITCH_TO_BUFFER
}

data class LayoutSwitchIdAndName(val id: IntId<TrackLayoutSwitch>, val name: SwitchName)

data class LocationTrackInfoboxExtras(
    val duplicateOf: LocationTrackDuplicate?,
    val duplicates: List<LocationTrackDuplicate>,
    val switchAtStart: LayoutSwitchIdAndName?,
    val switchAtEnd: LayoutSwitchIdAndName?,
)

data class SwitchValidationWithSuggestedSwitch(
    val switchId: IntId<TrackLayoutSwitch>,
    val switchValidation: ValidatedAsset<TrackLayoutSwitch>,
    val suggestedSwitch: SuggestedSwitch?,
)

data class LocationTrack(
    val name: AlignmentName,
    val descriptionBase: FreeText,
    val descriptionSuffix: DescriptionSuffixType,
    val type: LocationTrackType,
    val state: LayoutState,
    val externalId: Oid<LocationTrack>?,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val sourceId: IntId<GeometryAlignment>?,
    override val id: DomainId<LocationTrack> = deriveFromSourceId("LT", sourceId),
    override val dataType: DataType = DataType.TEMP,
    override val version: RowVersion<LocationTrack>? = null,
    val boundingBox: BoundingBox?,
    val length: Double,
    val segmentCount: Int,
    val duplicateOf: IntId<LocationTrack>?,
    val topologicalConnectivity: TopologicalConnectivityType,
    val topologyStartSwitch: TopologyLocationTrackSwitch?,
    val topologyEndSwitch: TopologyLocationTrackSwitch?,
    @JsonIgnore val alignmentVersion: RowVersion<LayoutAlignment>? = null,
    @JsonIgnore override val draft: Draft<LocationTrack>? = null,
) : Draftable<LocationTrack> {

    @JsonIgnore
    val exists = !state.isRemoved()

    init {
        require(descriptionBase.length in locationTrackDescriptionLength) {
            "LocationTrack descriptionBase length invalid  not in range 4-256: " + "id=$id " + "length=${descriptionBase.length} " + "allowed=$locationTrackDescriptionLength"
        }
        require(dataType == DataType.TEMP || alignmentVersion != null) {
            "LocationTrack in DB must have an alignment: id=$id"
        }
        require(
            topologyStartSwitch?.switchId == null || topologyStartSwitch.switchId != topologyEndSwitch?.switchId
        ) {
            "LocationTrack cannot topologically connect to the same switch at both ends: " + "trackId=$id " + "switchId=${topologyStartSwitch?.switchId} " + "startJoint=${topologyStartSwitch?.jointNumber} " + "endJoint=${topologyEndSwitch?.jointNumber}"
        }
    }

    fun getAlignmentVersionOrThrow(): RowVersion<LayoutAlignment> =
        alignmentVersion ?: throw IllegalStateException("LocationTrack has no alignment")
}


data class TrackLayoutSwitch(
    val name: SwitchName,
    val switchStructureId: IntId<SwitchStructure>,
    val stateCategory: LayoutStateCategory,
    val joints: List<TrackLayoutSwitchJoint>,
    val externalId: Oid<TrackLayoutSwitch>?,
    val sourceId: DomainId<GeometrySwitch>?,
    override val id: DomainId<TrackLayoutSwitch> = deriveFromSourceId("S", sourceId),
    override val dataType: DataType = DataType.TEMP,
    val trapPoint: Boolean?,
    val ownerId: IntId<SwitchOwner>?,
    override val version: RowVersion<TrackLayoutSwitch>? = null,
    @JsonIgnore override val draft: Draft<TrackLayoutSwitch>? = null,
    val source: GeometrySource,
) : Draftable<TrackLayoutSwitch> {
    @JsonIgnore
    val exists = !stateCategory.isRemoved()
    val shortName = name.split(" ").lastOrNull()?.let { last ->
        if (last.startsWith("V")) {
            last.substring(1).toIntOrNull(10)?.toString()?.padStart(3, '0')?.let { switchNumber -> "V$switchNumber" }
        } else null
    }

    fun getJoint(location: LayoutPoint, delta: Double): TrackLayoutSwitchJoint? =
        getJoint(Point(location.x, location.y), delta)

    fun getJoint(location: Point, delta: Double): TrackLayoutSwitchJoint? =
        joints.find { j -> j.location.isSame(location, delta) }

    fun getJoint(number: JointNumber): TrackLayoutSwitchJoint? = joints.find { j -> j.number == number }
}

data class TrackLayoutSwitchJoint(val number: JointNumber, val location: Point, val locationAccuracy: LocationAccuracy?)

data class TrackLayoutKmPost(
    val kmNumber: KmNumber,
    val location: Point?,
    val state: LayoutState,
    val trackNumberId: IntId<TrackLayoutTrackNumber>?,
    val sourceId: DomainId<GeometryKmPost>?,
    override val id: DomainId<TrackLayoutKmPost> = deriveFromSourceId("K", sourceId),
    override val dataType: DataType = DataType.TEMP,
    override val version: RowVersion<TrackLayoutKmPost>? = null,
    @JsonIgnore override val draft: Draft<TrackLayoutKmPost>? = null,
) : Draftable<TrackLayoutKmPost> {
    @JsonIgnore
    val exists = !state.isRemoved()
}

enum class TrackLayoutKmPostTableColumn {
    TRACK_NUMBER, KILOMETER, START_M, END_M, LENGTH, LOCATION_E, LOCATION_N, WARNING
}

data class TrackLayoutKmLengthDetails(
    val trackNumber: TrackNumber,
    val kmNumber: KmNumber,
    val startM: BigDecimal,
    val endM: BigDecimal,
    val locationSource: GeometrySource,
    val location: Point?,
) {
    val length = endM - startM
}

data class TrackLayoutSwitchJointMatch(
    val locationTrackId: IntId<LocationTrack>,
    val location: Point,
)

data class TrackLayoutSwitchJointConnection(
    val number: JointNumber,
    val accurateMatches: List<TrackLayoutSwitchJointMatch>,
    val locationAccuracy: LocationAccuracy?,
) {
    val matches by lazy {
        accurateMatches.map { accurateMatch ->
            accurateMatch.locationTrackId
        }
    }

    fun merge(other: TrackLayoutSwitchJointConnection): TrackLayoutSwitchJointConnection {
        check(number == other.number) { "expected $number == $other.number in TrackLayoutSwitchJointConnection#merge" }
        // location accuracy comes from the joint and hence can't differ
        check(locationAccuracy == other.locationAccuracy) {
            "expected $locationAccuracy == ${other.locationAccuracy} in TrackLayoutSwitchJointConnection#merge"
        }
        return TrackLayoutSwitchJointConnection(
            number,
            accurateMatches + other.accurateMatches,
            locationAccuracy,
        )
    }
}

data class DraftableChangeInfo(
    val created: Instant,
    val changed: Instant,
    val officialChanged: Instant?,
    val draftChanged: Instant?,
)

data class TrackNumberAndChangeTime(
    val id: IntId<TrackLayoutTrackNumber>,
    val number: TrackNumber,
    val changeTime: Instant,
)

fun getTranslation(key: String) = kmLengthTranslations[key] ?: ""

private val kmLengthTranslations = mapOf(
    "projected-location-warning" to "Sijainti on raiteen keskilinjalle projisoitu sijainti.",
    "start-address-location-warning" to "Sijainti on pituusmittauslinjan alun sijainti.",
    "TRACK_NUMBER-header" to "Ratanumero",
    "KILOMETER-header" to "Ratakilometri",
    "START_M-header" to "Alkupaalu",
    "END_M-header" to "Loppupaalu",
    "LENGTH-header" to "Pituus (m)",
    "LOCATION_E-header" to "Koordinaatti E",
    "LOCATION_N-header" to "Koordinaatti N",
    "WARNING-header" to "Huomiot"
)
