package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geography.crs
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryKmPost
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometrySwitch
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.boundingBoxCombining
import fi.fta.geoviite.infra.switchLibrary.SwitchOwner
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.FreeText
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
    FUTURE_EXISTING,
    EXISTING,
    NOT_EXISTING;

    fun isPublishable() = this != FUTURE_EXISTING
    fun isLinkable() = this == EXISTING
    fun isRemoved() = this == NOT_EXISTING
}

enum class TopologicalConnectivityType {
    NONE,
    START,
    END,
    START_AND_END;
}

data class GeometryPlanLayout(
    val fileName: FileName,
    val alignments: List<MapAlignment<GeometryAlignment>>,
    val switches: List<TrackLayoutSwitch>,
    val kmPosts: List<TrackLayoutKmPost>,
    val boundingBox: BoundingBox? = boundingBoxCombining(alignments.mapNotNull { a -> a.boundingBox }),
    val planId: DomainId<GeometryPlan>,
    val planDataType: DataType,
)

data class LocationTrackDuplicate(
    val id: IntId<LocationTrack>,
    val name: AlignmentName,
    val externalId: Oid<LocationTrack>?,
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
    MAIN, // P????raide
    SIDE, // Sivuraide
    TRAP, // Turvaraide: Turvaraide on raide, jonka tarkoitus on ohjata liikkuva kalusto riitt??v??n et????lle siit?? raiteesta, jota turvaraide suojaa. https://fi.wikipedia.org/wiki/Turvavaihde
    CHORD, // Kujaraide: Kujaraide on raide, joka ei ole sivu-, eik?? p????raide.
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

    fun getAlignmentVersionOrThrow(): RowVersion<LayoutAlignment> = alignmentVersion
        ?: throw IllegalStateException("ReferenceLine has no an alignment")
}

data class TopologyLocationTrackSwitch(
    val switchId: IntId<TrackLayoutSwitch>,
    val jointNumber: JointNumber,
)

data class LocationTrack(
    val name: AlignmentName,
    val description: FreeText,
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
        require(description.length in 4..256) {
            "LocationTrack description length ${description.length} not in range 4-256"
        }
        require(dataType == DataType.TEMP || alignmentVersion != null) {
            "LocationTrack in DB must have an alignment"
        }
    }

    fun getAlignmentVersionOrThrow(): RowVersion<LayoutAlignment> = alignmentVersion
        ?: throw IllegalStateException("LocationTrack has no alignment")
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

    fun getJoint(location: LayoutPoint, delta: Double): TrackLayoutSwitchJoint? =
        getJoint(Point(location.x, location.y), delta)

    fun getJoint(location: Point, delta: Double): TrackLayoutSwitchJoint? =
        joints.find { j -> j.location.isSame(location, delta) }

    fun getJoint(number: JointNumber): TrackLayoutSwitchJoint? =
        joints.find { j -> j.number == number }
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

data class TrackLayoutSwitchJointMatch(
    val locationTrackId: IntId<LocationTrack>,
    val location: Point,
)

data class TrackLayoutSwitchJointConnection(
    val number: JointNumber,
    val accurateMatches: List<TrackLayoutSwitchJointMatch>,
    val locationAccuracy: LocationAccuracy?
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

data class ChangeTimes(
    val created: Instant,
    val changed: Instant,
    val officialChanged: Instant?,
    val draftChanged: Instant?,
)
