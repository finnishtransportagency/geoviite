package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LocationTrackDescriptionBase
import fi.fta.geoviite.infra.common.ParsedSwitchName
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

private const val MISSING_PART_PLACEHOLDER = "???"

private fun getShortNumber(switch: LayoutSwitch?): String =
    switch?.parsedName?.shortNumberPart?.toString() ?: MISSING_PART_PLACEHOLDER

private fun getShortName(switch: LayoutSwitch?): String =
    switch?.parsedName?.let { "${it.prefix} ${it.shortNumberPart}" } ?: MISSING_PART_PLACEHOLDER

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
                LocationTrackNamingScheme.FREE_TEXT ->
                    FreeTextTrackNameStructure(
                        requireNotNull(nameFreeText) {
                            "Naming scheme of type $namingScheme must have a free text part"
                        }
                    )
                LocationTrackNamingScheme.WITHIN_OPERATING_POINT ->
                    WithinOperatingPointTrackNameStructure(
                        requireNotNull(nameFreeText) {
                            "Naming scheme of type $namingScheme must have a free text part"
                        }
                    )
                LocationTrackNamingScheme.BETWEEN_OPERATING_POINTS ->
                    BetweenOperatingPointsTrackNameStructure(
                        requireNotNull(nameSpecifier) {
                            "Naming scheme of type $namingScheme must have a name specifier part"
                        }
                    )
                LocationTrackNamingScheme.TRACK_NUMBER_TRACK ->
                    TrackNumberTrackNameStructure(
                        requireNotNull(nameFreeText) {
                            "Naming scheme of type $namingScheme must have a free text part"
                        },
                        requireNotNull(nameSpecifier) {
                            "Naming scheme of type $namingScheme must have a name specifier part"
                        },
                    )
                LocationTrackNamingScheme.CHORD -> ChordTrackNameStructure
            }
    }

    /** This logic is duplicated in frontend track-layout-model.tsx#formatTrackName */
    fun reify(trackNumber: LayoutTrackNumber, startSwitch: LayoutSwitch?, endSwitch: LayoutSwitch?): AlignmentName =
        when (this) {
            is FreeTextTrackNameStructure -> nameFreeText
            is WithinOperatingPointTrackNameStructure -> nameFreeText
            is BetweenOperatingPointsTrackNameStructure -> format(startSwitch, endSwitch)
            is TrackNumberTrackNameStructure -> format(trackNumber.number)
            is ChordTrackNameStructure -> format(startSwitch, endSwitch)
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

    fun format(startSwitch: LayoutSwitch?, endSwitch: LayoutSwitch?): AlignmentName =
        AlignmentName("${nameSpecifier.properForm} ${getShortName(startSwitch)}-${getShortName(endSwitch)}")
}

data object ChordTrackNameStructure : TrackNameStructure() {
    override val namingScheme: LocationTrackNamingScheme = LocationTrackNamingScheme.CHORD

    fun format(startSwitch: LayoutSwitch?, endSwitch: LayoutSwitch?): AlignmentName {
        val sharedPrefix = startSwitch?.parsedName?.prefix?.takeIf { it == endSwitch?.parsedName?.prefix }
        val formatted =
            sharedPrefix?.let { "$sharedPrefix ${getShortNumber(startSwitch)}-${getShortNumber(endSwitch)}" }
                ?: "${getShortName(startSwitch)}-${getShortName(endSwitch)}"
        return AlignmentName(formatted)
    }
}

data class TrackDescriptionStructure(
    val descriptionBase: LocationTrackDescriptionBase,
    val descriptionSuffix: LocationTrackDescriptionSuffix,
) {
    companion object {
        const val BUFFER_LOCALIZATION_KEY = "location-track-dialog.buffer"
        const val OWNERSHIP_BOUNDARY_LOCALIZATION_KEY = "location-track-dialog.ownership-boundary"
    }

    init {
        require(descriptionBase.length in locationTrackDescriptionLength) {
            "LocationTrack descriptionBase length invalid  not in range 4-256: " +
                "length=${descriptionBase.length} " +
                "allowed=$locationTrackDescriptionLength"
        }
    }

    /** This logic is duplicated in frontend track-layout-model.tsx#formatTrackDescription */
    fun reify(translation: Translation, startSwitch: LayoutSwitch?, endSwitch: LayoutSwitch?): FreeText =
        FreeText(
            when (descriptionSuffix) {
                LocationTrackDescriptionSuffix.NONE -> descriptionBase.toString()

                LocationTrackDescriptionSuffix.SWITCH_TO_BUFFER ->
                    "$descriptionBase ${getShortName(startSwitch ?: endSwitch)} - ${translation.t(BUFFER_LOCALIZATION_KEY)}"

                LocationTrackDescriptionSuffix.SWITCH_TO_OWNERSHIP_BOUNDARY ->
                    "$descriptionBase ${getShortName(startSwitch ?: endSwitch)} - ${translation.t(OWNERSHIP_BOUNDARY_LOCALIZATION_KEY)}"

                LocationTrackDescriptionSuffix.SWITCH_TO_SWITCH ->
                    "$descriptionBase ${getShortName(startSwitch)} - ${getShortName(endSwitch)}"
            }
        )
}

data class LocationTrack(
    val nameStructure: TrackNameStructure,
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
    /** Comes as calculated from LocationTrackGeometry. Anything set here will be overridden on save. */
    val boundingBox: BoundingBox?,
    /** Comes as calculated from LocationTrackGeometry. Anything set here will be overridden on save. */
    val length: Double,
    /** Comes as calculated from LocationTrackGeometry. Anything set here will be overridden on save. */
    val segmentCount: Int,
    /** Comes as calculated from LocationTrackGeometry. Anything set here will be overridden on save. */
    val startSwitchId: IntId<LayoutSwitch>?,
    /** Comes as calculated from LocationTrackGeometry. Anything set here will be overridden on save. */
    val endSwitchId: IntId<LayoutSwitch>?,
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
    val partOfUnfinishedSplit: Boolean?,
    val startSplitPoint: SplitPoint?,
    val endSplitPoint: SplitPoint?,
)

data class LocationTrackDuplicate(
    val id: IntId<LocationTrack>,
    val trackNumberId: IntId<LayoutTrackNumber>,
    val nameStructure: TrackNameStructure,
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
    val parsedName: ParsedSwitchName?,
    val name: SwitchName,
    val address: TrackMeter?,
    val location: Point?,
    val distance: Double?,
    val nearestOperatingPoint: RatkoOperatingPoint?,
)

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
