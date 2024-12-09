package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LocationAccuracy
import fi.fta.geoviite.infra.common.LocationTrackDescriptionBase
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.TrackNumberDescription
import fi.fta.geoviite.infra.geography.ETRS89_TM35FIN_SRID
import fi.fta.geoviite.infra.geography.GeometryPoint
import fi.fta.geoviite.infra.geography.transformNonKKJCoordinate
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryKmPost
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometrySwitch
import fi.fta.geoviite.infra.linking.SuggestedSwitch
import fi.fta.geoviite.infra.linking.switches.SamplingGridPoints
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.publication.ValidatedAsset
import fi.fta.geoviite.infra.ratko.model.RatkoOperatingPoint
import fi.fta.geoviite.infra.switchLibrary.SwitchOwner
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.util.FreeText
import java.math.BigDecimal
import java.time.Instant

val LAYOUT_SRID = ETRS89_TM35FIN_SRID

enum class LocationTrackState(val category: LayoutStateCategory) {
    BUILT(LayoutStateCategory.EXISTING),
    IN_USE(LayoutStateCategory.EXISTING),
    NOT_IN_USE(LayoutStateCategory.EXISTING),
    DELETED(LayoutStateCategory.NOT_EXISTING);

    fun isLinkable() = this == IN_USE || this == BUILT || this == NOT_IN_USE

    fun isRemoved() = this == DELETED
}

enum class LayoutState(val category: LayoutStateCategory) {
    IN_USE(LayoutStateCategory.EXISTING),
    NOT_IN_USE(LayoutStateCategory.EXISTING),
    DELETED(LayoutStateCategory.NOT_EXISTING);

    fun isLinkable() = this == IN_USE || this == NOT_IN_USE

    fun isRemoved() = this == DELETED
}

enum class LayoutStateCategory {
    EXISTING,
    NOT_EXISTING;

    fun isLinkable() = this == EXISTING

    fun isRemoved() = this == NOT_EXISTING
}

enum class TopologicalConnectivityType {
    NONE,
    START,
    END,
    START_AND_END;

    fun isStartConnected() = this == START || this == START_AND_END

    fun isEndConnected() = this == END || this == START_AND_END
}

fun topologicalConnectivityTypeOf(startConnected: Boolean, endConnected: Boolean): TopologicalConnectivityType =
    if (startConnected && endConnected) TopologicalConnectivityType.START_AND_END
    else if (startConnected) TopologicalConnectivityType.START
    else if (endConnected) TopologicalConnectivityType.END else TopologicalConnectivityType.NONE

data class LocationTrackDuplicate(
    val id: IntId<LocationTrack>,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val name: AlignmentName,
    val externalId: Oid<LocationTrack>?,
    val start: AlignmentPoint?,
    val end: AlignmentPoint?,
    val length: Double,
    val duplicateStatus: DuplicateStatus,
)

data class LocationTrackDescription(val id: IntId<LocationTrack>, val description: FreeText)

data class TrackLayoutTrackNumber(
    val number: TrackNumber,
    val description: TrackNumberDescription,
    val state: LayoutState,
    val externalId: Oid<TrackLayoutTrackNumber>?,
    @JsonIgnore override val contextData: LayoutContextData<TrackLayoutTrackNumber>,
    @JsonIgnore val referenceLineId: IntId<ReferenceLine>? = null,
) : LayoutAsset<TrackLayoutTrackNumber>(contextData) {
    @JsonIgnore val exists = !state.isRemoved()

    init {
        require(description.isNotBlank()) { "TrackNumber should have a non-blank description" }
        require(description.length < 100) { "TrackNumber description too long: ${description.length}>100" }
    }

    override fun toLog(): String =
        logFormat("id" to id, "version" to version, "context" to contextData::class.simpleName, "number" to number)

    override fun withContext(contextData: LayoutContextData<TrackLayoutTrackNumber>): TrackLayoutTrackNumber =
        copy(contextData = contextData)
}

enum class LocationTrackType {
    MAIN, // Pääraide
    SIDE, // Sivuraide
    TRAP, // Turvaraide: Turvaraide on raide, jonka tarkoitus on ohjata liikkuva kalusto riittävän
    // etäälle siitä raiteesta, jota turvaraide suojaa.
    // https://fi.wikipedia.org/wiki/Turvavaihde
    CHORD, // Kujaraide: Kujaraide on raide, joka ei ole sivu-, eikä pääraide.
}

sealed class PolyLineLayoutAsset<T : PolyLineLayoutAsset<T>>(contextData: LayoutContextData<T>) :
    LayoutAsset<T>(contextData) {
    @get:JsonIgnore abstract val alignmentVersion: RowVersion<LayoutAlignment>?

    fun getAlignmentVersionOrThrow(): RowVersion<LayoutAlignment> =
        requireNotNull(alignmentVersion) { "${this::class.simpleName} has no an alignment: id=$id" }
}

data class ReferenceLine(
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val startAddress: TrackMeter,
    val sourceId: IntId<GeometryAlignment>?,
    val boundingBox: BoundingBox? = null,
    val length: Double = 0.0,
    val segmentCount: Int = 0,
    @JsonIgnore override val contextData: LayoutContextData<ReferenceLine>,
    @JsonIgnore override val alignmentVersion: RowVersion<LayoutAlignment>? = null,
) : PolyLineLayoutAsset<ReferenceLine>(contextData) {

    init {
        require(dataType == DataType.TEMP || alignmentVersion != null) { "ReferenceLine in DB must have an alignment" }
    }

    override fun toLog(): String =
        logFormat(
            "id" to id,
            "version" to version,
            "context" to contextData::class.simpleName,
            "trackNumber" to trackNumberId,
            "alignment" to alignmentVersion,
        )

    override fun withContext(contextData: LayoutContextData<ReferenceLine>): ReferenceLine =
        copy(contextData = contextData)
}

data class TopologyLocationTrackSwitch(val switchId: IntId<TrackLayoutSwitch>, val jointNumber: JointNumber)

val locationTrackDescriptionLength = 4..256

enum class LocationTrackDescriptionSuffix {
    NONE,
    SWITCH_TO_SWITCH,
    SWITCH_TO_BUFFER,
    SWITCH_TO_OWNERSHIP_BOUNDARY,
}

data class LayoutSwitchIdAndName(val id: IntId<TrackLayoutSwitch>, val name: SwitchName)

data class LocationTrackInfoboxExtras(
    val duplicateOf: LocationTrackDuplicate?,
    val duplicates: List<LocationTrackDuplicate>,
    val switchAtStart: LayoutSwitchIdAndName?,
    val switchAtEnd: LayoutSwitchIdAndName?,
    val partOfUnfinishedSplit: Boolean?,
    val startSplitPoint: SplitPoint?,
    val endSplitPoint: SplitPoint?,
)

data class SwitchValidationWithSuggestedSwitch(
    val switchId: IntId<TrackLayoutSwitch>,
    val switchValidation: ValidatedAsset<TrackLayoutSwitch>,
    val switchSuggestion: SuggestedSwitch?,
)

data class SwitchOnLocationTrack(
    val switchId: IntId<TrackLayoutSwitch>,
    val name: SwitchName,
    val address: TrackMeter?,
    val location: Point?,
    val distance: Double?,
    val nearestOperatingPoint: RatkoOperatingPoint?,
)

const val EndpointSplitPointLocationToleranceInMeters = 2

enum class DuplicateMatch {
    FULL,
    PARTIAL,
    NONE,
}

enum class DuplicateEndPointType {
    START,
    END,
}

sealed class SplitPoint {
    abstract val location: AlignmentPoint
    abstract val address: TrackMeter?

    abstract fun isSame(other: SplitPoint): Boolean
}

data class SwitchSplitPoint(
    override val location: AlignmentPoint,
    override val address: TrackMeter?,
    val switchId: IntId<TrackLayoutSwitch>,
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
    val type = "ENDPOINT_SPLIT_POINT"

    override fun isSame(other: SplitPoint): Boolean {
        return other is EndpointSplitPoint &&
            endPointType == other.endPointType &&
            lineLength(location, other.location) <= EndpointSplitPointLocationToleranceInMeters
    }
}

data class DuplicateStatus(
    val match: DuplicateMatch,
    val duplicateOfId: IntId<LocationTrack>?,
    val startSplitPoint: SplitPoint?,
    val endSplitPoint: SplitPoint?,
    val overlappingLength: Double?,
)

data class SplitDuplicateTrack(
    val id: IntId<LocationTrack>,
    val name: AlignmentName,
    val length: Double,
    val status: DuplicateStatus,
)

data class SplittingInitializationParameters(
    val id: IntId<LocationTrack>,
    val switches: List<SwitchOnLocationTrack>,
    val duplicates: List<SplitDuplicateTrack>,
)

data class LocationTrack(
    val name: AlignmentName,
    val descriptionBase: LocationTrackDescriptionBase,
    val descriptionSuffix: LocationTrackDescriptionSuffix,
    val type: LocationTrackType,
    val state: LocationTrackState,
    val externalId: Oid<LocationTrack>?,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val sourceId: IntId<GeometryAlignment>?,
    val boundingBox: BoundingBox?,
    val length: Double,
    val segmentCount: Int,
    val duplicateOf: IntId<LocationTrack>?,
    val topologicalConnectivity: TopologicalConnectivityType,
    val topologyStartSwitch: TopologyLocationTrackSwitch?,
    val topologyEndSwitch: TopologyLocationTrackSwitch?,
    val ownerId: IntId<LocationTrackOwner>,
    @JsonIgnore override val contextData: LayoutContextData<LocationTrack>,
    @JsonIgnore override val alignmentVersion: RowVersion<LayoutAlignment>? = null,
    @JsonIgnore val segmentSwitchIds: List<IntId<TrackLayoutSwitch>> = listOf(),
) : PolyLineLayoutAsset<LocationTrack>(contextData) {

    @JsonIgnore val exists = !state.isRemoved()

    @get:JsonIgnore
    val switchIds: List<IntId<TrackLayoutSwitch>> by lazy {
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
        require(dataType == DataType.TEMP || alignmentVersion != null) {
            "LocationTrack in DB must have an alignment: id=$id"
        }
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

data class TrackLayoutSwitch(
    val name: SwitchName,
    val switchStructureId: IntId<SwitchStructure>,
    val stateCategory: LayoutStateCategory,
    val joints: List<TrackLayoutSwitchJoint>,
    val externalId: Oid<TrackLayoutSwitch>?,
    val sourceId: DomainId<GeometrySwitch>?,
    val trapPoint: Boolean?,
    val ownerId: IntId<SwitchOwner>?,
    val source: GeometrySource,
    @JsonIgnore override val contextData: LayoutContextData<TrackLayoutSwitch>,
) : LayoutAsset<TrackLayoutSwitch>(contextData) {
    @JsonIgnore val exists = !stateCategory.isRemoved()
    val shortName =
        name.split(" ").lastOrNull()?.let { last ->
            if (last.startsWith("V")) {
                last.substring(1).toIntOrNull(10)?.toString()?.padStart(3, '0')?.let { switchNumber ->
                    "V$switchNumber"
                }
            } else {
                null
            }
        }

    fun getJoint(location: AlignmentPoint, delta: Double): TrackLayoutSwitchJoint? =
        getJoint(Point(location.x, location.y), delta)

    fun getJoint(location: Point, delta: Double): TrackLayoutSwitchJoint? =
        joints.find { j -> j.location.isSame(location, delta) }

    fun getJoint(number: JointNumber): TrackLayoutSwitchJoint? = joints.find { j -> j.number == number }

    override fun toLog(): String =
        logFormat(
            "id" to id,
            "version" to version,
            "context" to contextData::class.simpleName,
            "source" to source,
            "name" to name,
            "joints" to joints.map { j -> j.number.intValue },
        )

    override fun withContext(contextData: LayoutContextData<TrackLayoutSwitch>): TrackLayoutSwitch =
        copy(contextData = contextData)
}

data class SwitchPlacingRequest(val points: SamplingGridPoints, val layoutSwitchId: IntId<TrackLayoutSwitch>)

data class TrackLayoutSwitchJoint(
    val number: JointNumber,
    val location: Point,
    val locationAccuracy: LocationAccuracy?,
)

enum class KmPostGkLocationSource {
    FROM_GEOMETRY,
    FROM_LAYOUT,
    MANUAL,
}

data class TrackLayoutKmPostGkLocation(
    val location: GeometryPoint,
    val source: KmPostGkLocationSource,
    val confirmed: Boolean,
)

data class TrackLayoutKmPost(
    val kmNumber: KmNumber,
    val gkLocation: TrackLayoutKmPostGkLocation?,
    val state: LayoutState,
    val trackNumberId: IntId<TrackLayoutTrackNumber>?,
    val sourceId: DomainId<GeometryKmPost>?,
    @JsonIgnore override val contextData: LayoutContextData<TrackLayoutKmPost>,
) : LayoutAsset<TrackLayoutKmPost>(contextData) {
    @JsonIgnore val exists = !state.isRemoved()

    val layoutLocation = gkLocation?.let { transformNonKKJCoordinate(it.location.srid, LAYOUT_SRID, it.location) }

    fun getAsIntegral(): IntegralTrackLayoutKmPost? =
        if (state != LayoutState.IN_USE || layoutLocation == null || trackNumberId == null) null
        else IntegralTrackLayoutKmPost(kmNumber, layoutLocation, trackNumberId)

    override fun toLog(): String =
        logFormat(
            "id" to id,
            "version" to version,
            "context" to contextData::class.simpleName,
            "kmNumber" to kmNumber,
            "trackNumber" to trackNumberId,
        )

    override fun withContext(contextData: LayoutContextData<TrackLayoutKmPost>): TrackLayoutKmPost =
        copy(contextData = contextData)
}

data class IntegralTrackLayoutKmPost(
    val kmNumber: KmNumber,
    val location: Point,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
)

data class TrackLayoutKmLengthDetails(
    val trackNumber: TrackNumber,
    val kmNumber: KmNumber,
    val startM: BigDecimal,
    val endM: BigDecimal,
    val layoutGeometrySource: GeometrySource,
    val layoutLocation: Point?,
    val gkLocation: TrackLayoutKmPostGkLocation?,
    val gkLocationLinkedFromGeometry: Boolean,
) {
    val length = endM - startM
}

data class TrackLayoutSwitchJointMatch(val locationTrackId: IntId<LocationTrack>, val location: Point)

data class TrackLayoutSwitchJointConnection(
    val number: JointNumber,
    val accurateMatches: List<TrackLayoutSwitchJointMatch>,
    val locationAccuracy: LocationAccuracy?,
) {
    fun merge(other: TrackLayoutSwitchJointConnection): TrackLayoutSwitchJointConnection {
        check(number == other.number) { "expected $number == $other.number in TrackLayoutSwitchJointConnection#merge" }
        // location accuracy comes from the joint and hence can't differ
        check(locationAccuracy == other.locationAccuracy) {
            "expected $locationAccuracy == ${other.locationAccuracy} in TrackLayoutSwitchJointConnection#merge"
        }
        return TrackLayoutSwitchJointConnection(number, accurateMatches + other.accurateMatches, locationAccuracy)
    }
}

data class LayoutAssetChangeInfo(val created: Instant, val changed: Instant?)

data class TrackNumberAndChangeTime(
    val id: IntId<TrackLayoutTrackNumber>,
    val number: TrackNumber,
    val changeTime: Instant,
)

data class KmPostInfoboxExtras(val kmLength: Double?, val sourceGeometryPlanId: IntId<GeometryPlan>?)
