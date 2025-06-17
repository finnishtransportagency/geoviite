package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LocationTrackDescriptionBase
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.localization.Translation
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

enum class LocationTrackNameSpecifier(val properForm: String) {
    PR("PR"),
    ER("ER"),
    IR("IR"),
    KR("KR"),
    LR("LR"),
    PSR("PsR"),
    ESR("EsR"),
    ISR("IsR"),
    LSR("LsR"),
    PKR("PKR"),
    EKR("EKR"),
    IKR("IKR"),
    LKR("LKR"),
    ITHR("ItHR"),
    LANHR("LänHR"),
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

sealed class TrackNameStructure {
    abstract val namingScheme: LocationTrackNamingScheme
    open val nameFreeText: AlignmentName? = null
    open val nameSpecifier: LocationTrackNameSpecifier? = null

    companion object {
        fun of(
            namingScheme: LocationTrackNamingScheme,
            nameFreeText: AlignmentName? = null,
            nameSpecifier: LocationTrackNameSpecifier? = null,
        ): TrackNameStructure =
            when (namingScheme) {
                LocationTrackNamingScheme.FREE_TEXT -> FreeTextTrackNameStructure(requireNotNull(nameFreeText))
                LocationTrackNamingScheme.WITHIN_OPERATING_POINT ->
                    WithinOperatingPointTrackNameStructure(requireNotNull(nameFreeText))
                LocationTrackNamingScheme.BETWEEN_OPERATING_POINTS ->
                    BetweenOperatingPointsTrackNameStructure(requireNotNull(nameSpecifier))
                LocationTrackNamingScheme.TRACK_NUMBER_TRACK ->
                    TrackNumberTrackNameStructure(requireNotNull(nameFreeText), requireNotNull(nameSpecifier))
                LocationTrackNamingScheme.CHORD -> ChordTrackNameStructure
            }
    }

    fun reify(trackNumber: LayoutTrackNumber, startSwitch: LayoutSwitch?, endSwitch: LayoutSwitch?): AlignmentName =
        when (this) {
            is FreeTextTrackNameStructure -> nameFreeText
            is WithinOperatingPointTrackNameStructure -> nameFreeText
            is BetweenOperatingPointsTrackNameStructure -> format(startSwitch?.name, endSwitch?.name)
            is TrackNumberTrackNameStructure -> format(trackNumber.number)
            is ChordTrackNameStructure -> format(startSwitch?.name, endSwitch?.name)
        }
}

data class FreeTextTrackNameStructure(override val nameFreeText: AlignmentName) : TrackNameStructure() {
    override val namingScheme: LocationTrackNamingScheme = LocationTrackNamingScheme.FREE_TEXT
}

data class WithinOperatingPointTrackNameStructure(override val nameFreeText: AlignmentName) : TrackNameStructure() {
    override val namingScheme: LocationTrackNamingScheme = LocationTrackNamingScheme.WITHIN_OPERATING_POINT
}

data class TrackNumberTrackNameStructure(
    override val nameFreeText: AlignmentName,
    override val nameSpecifier: LocationTrackNameSpecifier,
) : TrackNameStructure() {
    override val namingScheme: LocationTrackNamingScheme = LocationTrackNamingScheme.TRACK_NUMBER_TRACK

    fun format(trackNumber: TrackNumber): AlignmentName =
        AlignmentName("$trackNumber ${nameSpecifier.properForm} $nameFreeText".trim())
}

data class BetweenOperatingPointsTrackNameStructure(override val nameSpecifier: LocationTrackNameSpecifier) :
    TrackNameStructure() {
    override val namingScheme: LocationTrackNamingScheme = LocationTrackNamingScheme.BETWEEN_OPERATING_POINTS

    fun format(startSwitchName: SwitchName?, endSwitchName: SwitchName?): AlignmentName =
        AlignmentName("${nameSpecifier.properForm} ${startSwitchName ?: "???"}-${endSwitchName ?: "???"}")
}

data object ChordTrackNameStructure : TrackNameStructure() {
    override val namingScheme: LocationTrackNamingScheme = LocationTrackNamingScheme.CHORD

    fun format(startSwitchName: SwitchName?, endSwitchName: SwitchName?): AlignmentName {
        val startNameParts = startSwitchName?.split(" ")
        val endNameParts = endSwitchName?.split(" ")
        val startPrefix = startNameParts?.firstOrNull()
        val endPrefix = endNameParts?.firstOrNull()
        return if (startPrefix != null && startPrefix == endPrefix) {
            val restOfStartName = startNameParts.subList(1, startNameParts.lastIndex).joinToString(" ")
            val restOfEndName = endNameParts.subList(1, endNameParts.lastIndex).joinToString(" ")
            AlignmentName("$startPrefix $restOfStartName-$restOfEndName")
        } else {
            AlignmentName("${startSwitchName ?: "???"}-${endSwitchName ?: "???"}")
        }
    }
}

data class TrackDescriptionStructure(
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

    fun reify(translation: Translation, startSwitch: LayoutSwitch?, endSwitch: LayoutSwitch?): FreeText =
        when (descriptionSuffix) {
            LocationTrackDescriptionSuffix.NONE -> FreeText(descriptionBase.toString())

            LocationTrackDescriptionSuffix.SWITCH_TO_BUFFER ->
                FreeText(
                    "$descriptionBase ${startSwitch?.name ?: endSwitch?.name ?: "???"} - ${translation.t("location-track-dialog.buffer")}"
                )

            LocationTrackDescriptionSuffix.SWITCH_TO_SWITCH ->
                FreeText("$descriptionBase ${startSwitch?.name ?: "???"} - ${endSwitch?.name ?: "???"}")

            LocationTrackDescriptionSuffix.SWITCH_TO_OWNERSHIP_BOUNDARY ->
                FreeText(
                    "$descriptionBase ${startSwitch?.name ?: endSwitch?.name ?: "???"} - ${translation.t("location-track-dialog.ownership-boundary")}"
                )
        }
}

data class LocationTrack(
    val naming: TrackNameStructure,
    /**
     * Reified name from the structured fields, using dependencies (end switches & track numbers). Should not be edited
     * directly, only via the method [TrackNameStructure.reify]
     */
    val name: AlignmentName,
    val descriptionStructure: TrackDescriptionStructure,
    /**
     * Reified description from the structured fields, using dependencies (end switches). Should not be edited directly,
     * only, only via the method [TrackDescriptionStructure.reify]
     */
    val description: FreeText,
    val type: LocationTrackType,
    val state: LocationTrackState,
    val trackNumberId: IntId<LayoutTrackNumber>,
    val sourceId: IntId<GeometryAlignment>?,
    val boundingBox: BoundingBox?,
    val length: Double,
    val segmentCount: Int,
    val duplicateOf: IntId<LocationTrack>?,
    val topologicalConnectivity: TopologicalConnectivityType,
    val ownerId: IntId<LocationTrackOwner>,
    @JsonIgnore override val contextData: LayoutContextData<LocationTrack>,
    @JsonIgnore val switchIds: List<IntId<LayoutSwitch>> = listOf(),
) : PolyLineLayoutAsset<LocationTrack>(contextData) {

    @JsonIgnore val exists = !state.isRemoved()

    override fun toLog(): String =
        logFormat(
            "id" to id,
            "version" to version,
            "context" to contextData::class.simpleName,
            "name" to name,
            "trackNumber" to trackNumberId,
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

data class SwitchOnLocationTrack(
    val switchId: IntId<LayoutSwitch>,
    val name: SwitchName,
    val address: TrackMeter?,
    val location: Point?,
    val distance: Double?,
    val nearestOperatingPoint: RatkoOperatingPoint?,
)

data class LayoutSwitchIdAndName(val id: IntId<LayoutSwitch>, val name: SwitchName, val shortName: String?)

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
