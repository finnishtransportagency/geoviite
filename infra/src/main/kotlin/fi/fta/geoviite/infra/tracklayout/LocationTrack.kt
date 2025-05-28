package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LocationTrackDescriptionBase
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.localization.Translation
import fi.fta.geoviite.infra.logging.Loggable
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

enum class LocationTrackNamingScheme {
    FREE_TEXT,
    WITHIN_OPERATING_POINT,
    BETWEEN_OPERATING_POINTS,
    TRACK_NUMBER_TRACK,
    CHORD,
}

enum class LocationTrackNameSpecifier {
    PR,
    ER,
    IR,
    KR,
    LR,
    PSR,
    ESR,
    ISR,
    LSR,
    PKR,
    EKR,
    IKR,
    LKR,
    ITHR,
    LANHR,
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

sealed class DbLocationTrackNaming {
    abstract val namingScheme: LocationTrackNamingScheme
    open val nameFreeText: AlignmentName? = null
    open val nameSpecifier: LocationTrackNameSpecifier? = null
}

data class DbFreeTextTrackNaming(override val nameFreeText: AlignmentName) : DbLocationTrackNaming() {
    override val namingScheme: LocationTrackNamingScheme = LocationTrackNamingScheme.FREE_TEXT
}

data class DbTrackNumberTrackNaming(
    override val nameFreeText: AlignmentName,
    override val nameSpecifier: LocationTrackNameSpecifier,
) : DbLocationTrackNaming() {
    override val namingScheme: LocationTrackNamingScheme = LocationTrackNamingScheme.TRACK_NUMBER_TRACK
}

data class DbWithinOperatingPointTrackNaming(override val nameFreeText: AlignmentName) : DbLocationTrackNaming() {
    override val namingScheme: LocationTrackNamingScheme = LocationTrackNamingScheme.WITHIN_OPERATING_POINT
}

data class DbBetweenOperatingPointsTrackNaming(override val nameSpecifier: LocationTrackNameSpecifier) :
    DbLocationTrackNaming() {
    override val namingScheme: LocationTrackNamingScheme = LocationTrackNamingScheme.BETWEEN_OPERATING_POINTS
}

data object DbChordTrackNaming : DbLocationTrackNaming() {
    override val namingScheme: LocationTrackNamingScheme = LocationTrackNamingScheme.CHORD
}

sealed class ReifiedTrackNaming() {
    abstract val namingScheme: LocationTrackNamingScheme
    val name: AlignmentName by lazy { getName() }

    abstract fun getName(): AlignmentName
}

data class ReifiedFreeTextTrackNaming(val nameFreeText: AlignmentName) : ReifiedTrackNaming() {
    override val namingScheme: LocationTrackNamingScheme = LocationTrackNamingScheme.FREE_TEXT

    override fun getName(): AlignmentName = nameFreeText
}

data class ReifiedBetweenOperatingPointsTrackNaming(val startSwitchName: SwitchName?, val endSwitchName: SwitchName?) :
    ReifiedTrackNaming() {
    override val namingScheme: LocationTrackNamingScheme = LocationTrackNamingScheme.BETWEEN_OPERATING_POINTS

    override fun getName(): AlignmentName {
        val startName = startSwitchName?.let { "$it" } ?: "???"
        val endName = endSwitchName?.let { "$it" } ?: "???"
        return AlignmentName("$startName-$endName")
    }
}

data class ReifiedChordTrackNaming(val startSwitchName: SwitchName?, val endSwitchName: SwitchName?) :
    ReifiedTrackNaming() {
    override val namingScheme: LocationTrackNamingScheme = LocationTrackNamingScheme.CHORD

    override fun getName(): AlignmentName {
        val startName = startSwitchName?.let { "$it" } ?: "???"
        val endName = endSwitchName?.let { "$it" } ?: "???"
        return AlignmentName("$startName-$endName")
    }
}

data class ReifiedWithinOperatingPointTrackNaming(val nameFreeText: AlignmentName) : ReifiedTrackNaming() {
    override val namingScheme: LocationTrackNamingScheme = LocationTrackNamingScheme.WITHIN_OPERATING_POINT

    override fun getName(): AlignmentName = nameFreeText
}

data class ReifiedTrackNumberTrackNaming(
    val trackNumber: TrackNumber,
    val nameFreeText: AlignmentName,
    val nameSpecifier: LocationTrackNameSpecifier,
) : ReifiedTrackNaming() {
    override val namingScheme: LocationTrackNamingScheme = LocationTrackNamingScheme.TRACK_NUMBER_TRACK

    override fun getName(): AlignmentName = AlignmentName("$trackNumber $nameSpecifier $nameFreeText")
}

data class LocationTrackName(val id: IntId<LocationTrack>, val name: AlignmentName)

data class DbLocationTrackDescription(
    val descriptionBase: LocationTrackDescriptionBase,
    val descriptionSuffix: LocationTrackDescriptionSuffix,
) {
    init {
        require(descriptionBase.length in locationTrackDescriptionLength) {
            "LocationTrack descriptionBase length invalid  not in range 4-256: " +
                "length=${descriptionBase.length} " +
                "allowed=$locationTrackDescriptionLength"
        }
    }
}

data class ReifiedTrackDescription(
    val dbDescription: DbLocationTrackDescription,
    val startSwitchName: SwitchName,
    val endSwitchName: SwitchName,
) {
    fun getDescription(translation: Translation): FreeText {
        val base = dbDescription.descriptionBase.toString()
        val end =
            when (dbDescription.descriptionSuffix) {
                LocationTrackDescriptionSuffix.NONE -> dbDescription.descriptionBase.toString()

                LocationTrackDescriptionSuffix.SWITCH_TO_BUFFER ->
                    " ${startSwitchName ?: endSwitchName ?: "???"} - ${translation.t("location-track-dialog.buffer")}"

                LocationTrackDescriptionSuffix.SWITCH_TO_SWITCH ->
                    " ${startSwitchName ?: "???"} - ${endSwitchName ?: "???"}"

                LocationTrackDescriptionSuffix.SWITCH_TO_OWNERSHIP_BOUNDARY ->
                    " ${startSwitchName ?: endSwitchName ?: "???"} - ${translation.t("location-track-dialog.ownership-boundary")}"
            }
        return FreeText(base + end)
    }
}

data class AugLocationTrackCacheKey(
    val trackVersion: LayoutRowVersion<LocationTrack>,
    val trackNumberVersion: LayoutRowVersion<LayoutTrackNumber>,
    val startSwitchVersion: LayoutRowVersion<LayoutSwitch>?,
    val endSwitchVersion: LayoutRowVersion<LayoutSwitch>?,
)

interface ILocationTrack : Loggable {
    val version: LayoutRowVersion<LocationTrack>?
    val id: DomainId<LocationTrack>
    val dbName: DbLocationTrackNaming
    val dbDescription: DbLocationTrackDescription
    val type: LocationTrackType
    val state: LocationTrackState
    val trackNumberId: IntId<LayoutTrackNumber>
    val sourceId: IntId<GeometryAlignment>?
    val boundingBox: BoundingBox?
    val length: Double
    val segmentCount: Int
    val duplicateOf: IntId<LocationTrack>?
    val topologicalConnectivity: TopologicalConnectivityType
    val topologyStartSwitch: TopologyLocationTrackSwitch?
    val topologyEndSwitch: TopologyLocationTrackSwitch?
    val ownerId: IntId<LocationTrackOwner>
    val contextData: LayoutContextData<LocationTrack>
    val alignmentVersion: RowVersion<LayoutAlignment>?
    val segmentSwitchIds: List<IntId<LayoutSwitch>>

    @get:JsonIgnore
    val exists
        get() = !state.isRemoved()

    @get:JsonIgnore
    val switchIds: List<IntId<LayoutSwitch>>
        get() =
            (listOfNotNull(topologyStartSwitch?.switchId) +
                    segmentSwitchIds +
                    listOfNotNull(topologyEndSwitch?.switchId))
                .distinct()

    fun getAlignmentVersionOrThrow(): RowVersion<LayoutAlignment>
}

data class AugLocationTrack(
    private val translation: Translation,
    private val dbTrack: LocationTrack,
    val reifiedNaming: ReifiedTrackNaming,
    val reifiedDescription: ReifiedTrackDescription,
) : ILocationTrack by dbTrack {
    val name: AlignmentName by lazy { reifiedNaming.getName() }
    val description: FreeText by lazy { reifiedDescription.getDescription(translation) }

    override fun toLog(): String =
        logFormat(
            "id" to id,
            "version" to version,
            "context" to contextData::class.simpleName,
            "name" to name,
            "trackNumber" to trackNumberId,
            "alignment" to alignmentVersion,
        )
}

data class LocationTrack(
    override val dbName: DbLocationTrackNaming,
    override val dbDescription: DbLocationTrackDescription,
    override val type: LocationTrackType,
    override val state: LocationTrackState,
    override val trackNumberId: IntId<LayoutTrackNumber>,
    override val sourceId: IntId<GeometryAlignment>?,
    override val boundingBox: BoundingBox?,
    override val length: Double,
    override val segmentCount: Int,
    override val duplicateOf: IntId<LocationTrack>?,
    override val topologicalConnectivity: TopologicalConnectivityType,
    override val topologyStartSwitch: TopologyLocationTrackSwitch?,
    override val topologyEndSwitch: TopologyLocationTrackSwitch?,
    override val ownerId: IntId<LocationTrackOwner>,
    @JsonIgnore override val contextData: LayoutContextData<LocationTrack>,
    @JsonIgnore override val alignmentVersion: RowVersion<LayoutAlignment>? = null,
    @JsonIgnore override val segmentSwitchIds: List<IntId<LayoutSwitch>> = listOf(),
) : ILocationTrack, PolyLineLayoutAsset<LocationTrack>(contextData) {
    override fun toLog(): String =
        logFormat(
            "id" to id,
            "version" to version,
            "context" to contextData::class.simpleName,
            "dbName" to dbName,
            "trackNumber" to trackNumberId,
            "alignment" to alignmentVersion,
        )

    override fun withContext(contextData: LayoutContextData<LocationTrack>): LocationTrack =
        copy(contextData = contextData)
}

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
    val namingScheme: LocationTrackNamingScheme,
    val nameFreeText: AlignmentName?,
    val nameSpecifier: LocationTrackNameSpecifier?,
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
