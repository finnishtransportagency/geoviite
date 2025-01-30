package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LocationTrackDescriptionBase
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.ratko.model.RatkoOperatingPoint
import fi.fta.geoviite.infra.util.FreeText

enum class LocationTrackType {
    MAIN, // Pääraide
    SIDE, // Sivuraide
    TRAP, // Turvaraide: Turvaraide on raide, jonka tarkoitus on ohjata liikkuva kalusto riittävän
    // etäälle siitä raiteesta, jota turvaraide suojaa.
    // https://fi.wikipedia.org/wiki/Turvavaihde
    CHORD, // Kujaraide: Kujaraide on raide, joka ei ole sivu-, eikä pääraide.
}

val locationTrackDescriptionLength = 4..256

enum class LocationTrackDescriptionSuffix {
    NONE,
    SWITCH_TO_SWITCH,
    SWITCH_TO_BUFFER,
    SWITCH_TO_OWNERSHIP_BOUNDARY,
}

enum class TopologicalConnectivityType {
    NONE,
    START,
    END,
    START_AND_END;

    fun isStartConnected() = this == START || this == START_AND_END

    fun isEndConnected() = this == END || this == START_AND_END
}

enum class LocationTrackState(val category: LayoutStateCategory) {
    BUILT(LayoutStateCategory.EXISTING),
    IN_USE(LayoutStateCategory.EXISTING),
    NOT_IN_USE(LayoutStateCategory.EXISTING),
    DELETED(LayoutStateCategory.NOT_EXISTING);

    fun isLinkable() = this == IN_USE || this == BUILT || this == NOT_IN_USE

    fun isRemoved() = this == DELETED
}

data class LocationTrack(
    val name: AlignmentName,
    val descriptionBase: LocationTrackDescriptionBase,
    val descriptionSuffix: LocationTrackDescriptionSuffix,
    val type: LocationTrackType,
    val state: LocationTrackState,
    val trackNumberId: IntId<LayoutTrackNumber>,
    val sourceId: IntId<GeometryAlignment>?,
    val boundingBox: BoundingBox?,
    val length: Double,
    val segmentCount: Int,
    val duplicateOf: IntId<LocationTrack>?,
    val topologicalConnectivity: TopologicalConnectivityType,
    @Deprecated("Topology links are now just the start/end nodes of the geometry")
    val topologyStartSwitch: TopologyLocationTrackSwitch?,
    @Deprecated("Topology links are now just the start/end nodes of the geometry")
    val topologyEndSwitch: TopologyLocationTrackSwitch?,
    val ownerId: IntId<LocationTrackOwner>,
    // TODO: GVT-2926 Remove this field entirely - maybe need to alter the baseclass?
    @JsonIgnore val alignmentVersion: RowVersion<LayoutAlignment>? = null,
    @JsonIgnore override val contextData: LayoutContextData<LocationTrack>,
    // TODO: GVT-2926 Replace this field with simple switchIds from topology model
    @JsonIgnore val segmentSwitchIds: List<IntId<LayoutSwitch>> = listOf(),
) : PolyLineLayoutAsset<LocationTrack>(contextData) {

    @JsonIgnore val exists = !state.isRemoved()

    @get:JsonIgnore
    val switchIds: List<IntId<LayoutSwitch>> by lazy {
        (listOfNotNull(topologyStartSwitch?.switchId) + segmentSwitchIds + listOfNotNull(topologyEndSwitch?.switchId))
            .distinct()
    }

    init {
        require(descriptionBase.length in locationTrackDescriptionLength) {
            "LocationTrack descriptionBase length invalid  not in range 4-256: " +
                "id=$id " +
                "length=${descriptionBase.length} " +
                "allowed=$locationTrackDescriptionLength"
        }
        //        require(dataType == DataType.TEMP || alignmentVersion != null) {
        //            "LocationTrack in DB must have an alignment: id=$id"
        //        }
        require(topologyStartSwitch?.switchId == null || topologyStartSwitch.switchId != topologyEndSwitch?.switchId) {
            "LocationTrack cannot topologically connect to the same switch at both ends: " +
                "trackId=$id " +
                "switchId=${topologyStartSwitch?.switchId} " +
                "startJoint=${topologyStartSwitch?.jointNumber} " +
                "endJoint=${topologyEndSwitch?.jointNumber}"
        }
    }

    override fun toLog(): String =
        logFormat(
            "id" to id,
            "version" to version,
            "context" to contextData::class.simpleName,
            "name" to name,
            "trackNumber" to trackNumberId,
            "alignment" to alignmentVersion,
        )

    override fun withContext(contextData: LayoutContextData<LocationTrack>): LocationTrack =
        copy(contextData = contextData)
}

data class LocationTrackDescription(val id: IntId<LocationTrack>, val description: FreeText)

data class LocationTrackInfoboxExtras(
    val duplicateOf: LocationTrackDuplicate?,
    val duplicates: List<LocationTrackDuplicate>,
    val switchAtStart: LayoutSwitchIdAndName?,
    val switchAtEnd: LayoutSwitchIdAndName?,
    val partOfUnfinishedSplit: Boolean?,
    val startSplitPoint: SplitPoint?,
    val endSplitPoint: SplitPoint?,
)

data class LocationTrackDuplicate(
    val id: IntId<LocationTrack>,
    val trackNumberId: IntId<LayoutTrackNumber>,
    val name: AlignmentName,
    val start: AlignmentPoint?,
    val end: AlignmentPoint?,
    val length: Double,
    val duplicateStatus: DuplicateStatus,
)

fun topologicalConnectivityTypeOf(startConnected: Boolean, endConnected: Boolean): TopologicalConnectivityType =
    if (startConnected && endConnected) TopologicalConnectivityType.START_AND_END
    else if (startConnected) TopologicalConnectivityType.START
    else if (endConnected) TopologicalConnectivityType.END else TopologicalConnectivityType.NONE

data class TopologyLocationTrackSwitch(val switchId: IntId<LayoutSwitch>, val jointNumber: JointNumber)

data class SwitchOnLocationTrack(
    val switchId: IntId<LayoutSwitch>,
    val name: SwitchName,
    val address: TrackMeter?,
    val location: Point?,
    val distance: Double?,
    val nearestOperatingPoint: RatkoOperatingPoint?,
)

data class LayoutSwitchIdAndName(val id: IntId<LayoutSwitch>, val name: SwitchName)

enum class DuplicateEndPointType {
    START,
    END,
}

enum class DuplicateMatch {
    FULL,
    PARTIAL,
    NONE,
}

data class DuplicateStatus(
    val match: DuplicateMatch,
    val duplicateOfId: IntId<LocationTrack>?,
    val startSplitPoint: SplitPoint?,
    val endSplitPoint: SplitPoint?,
    val overlappingLength: Double?,
)

sealed class SplitPoint {
    abstract val location: AlignmentPoint
    abstract val address: TrackMeter?

    abstract fun isSame(other: SplitPoint): Boolean
}

data class SwitchSplitPoint(
    override val location: AlignmentPoint,
    override val address: TrackMeter?,
    val switchId: IntId<LayoutSwitch>,
    val jointNumber: JointNumber,
) : SplitPoint() {
    val type = "SWITCH_SPLIT_POINT"

    override fun isSame(other: SplitPoint): Boolean {
        return other is SwitchSplitPoint && switchId == other.switchId && jointNumber == other.jointNumber
    }
}

data class EndpointSplitPoint(
    override val location: AlignmentPoint,
    override val address: TrackMeter?,
    val endPointType: DuplicateEndPointType,
) : SplitPoint() {
    companion object {
        private const val LOCATION_TOLERANCE_METERS = 2
    }

    val type = "ENDPOINT_SPLIT_POINT"

    override fun isSame(other: SplitPoint): Boolean {
        return other is EndpointSplitPoint &&
            endPointType == other.endPointType &&
            lineLength(location, other.location) <= LOCATION_TOLERANCE_METERS
    }
}
