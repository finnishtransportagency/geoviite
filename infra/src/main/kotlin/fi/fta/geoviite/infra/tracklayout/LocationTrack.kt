package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.ALLOWED_ALIGNMENT_NAME_CHARACTERS
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LocationTrackDescriptionBase
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.SwitchNameParts
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.localization.Translation
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.ratko.model.RatkoOperationalPoint
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.StringSanitizer

data class LocationTrackNameFreeTextPart @JsonCreator(mode = DELEGATING) constructor(private val value: String) :
    Comparable<LocationTrackNameFreeTextPart>, CharSequence by value {

    companion object {
        val allowedLength = 1..50
        val sanitizer =
            StringSanitizer(LocationTrackNameFreeTextPart::class, ALLOWED_ALIGNMENT_NAME_CHARACTERS, allowedLength)
    }

    init {
        sanitizer.assertSanitized(value)
        sanitizer.assertTrimmed(value)
    }

    @JsonValue override fun toString(): String = value

    override fun compareTo(other: LocationTrackNameFreeTextPart): Int = value.compareTo(other.value)
}

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
    switch?.nameParts?.shortNumberPart?.toString() ?: MISSING_PART_PLACEHOLDER

private fun getShortName(switch: LayoutSwitch?): String =
    switch?.nameParts?.let { "${it.prefix} ${it.shortNumberPart}" } ?: MISSING_PART_PLACEHOLDER

sealed class LocationTrackNameStructure {
    abstract val scheme: LocationTrackNamingScheme
    open val freeText: LocationTrackNameFreeTextPart? = null
    open val specifier: LocationTrackNameSpecifier? = null

    companion object {
        fun of(
            scheme: LocationTrackNamingScheme,
            freeText: LocationTrackNameFreeTextPart? = null,
            specifier: LocationTrackNameSpecifier? = null,
        ): LocationTrackNameStructure =
            when (scheme) {
                LocationTrackNamingScheme.FREE_TEXT ->
                    LocationTrackNameFreeText(
                        requireNotNull(freeText) { "Naming scheme of type $scheme must have a free text part" }
                    )
                LocationTrackNamingScheme.WITHIN_OPERATING_POINT ->
                    LocationTrackNameWithinOperatingPoint(
                        requireNotNull(freeText) { "Naming scheme of type $scheme must have a free text part" }
                    )
                LocationTrackNamingScheme.BETWEEN_OPERATING_POINTS -> LocationTrackNameBetweenOperatingPoints(specifier)
                LocationTrackNamingScheme.TRACK_NUMBER_TRACK ->
                    LocationTrackNameByTrackNumber(
                        freeText,
                        requireNotNull(specifier) { "Naming scheme of type $scheme must have a name specifier part" },
                    )
                LocationTrackNamingScheme.CHORD -> LocationTrackNameChord
            }
    }

    /** This logic is duplicated in frontend track-layout-model.tsx#formatTrackName */
    fun reify(trackNumber: LayoutTrackNumber, startSwitch: LayoutSwitch?, endSwitch: LayoutSwitch?): AlignmentName =
        when (this) {
            is LocationTrackNameFreeText -> AlignmentName(freeText.toString())
            is LocationTrackNameWithinOperatingPoint -> AlignmentName(freeText.toString())
            is LocationTrackNameBetweenOperatingPoints -> format(startSwitch, endSwitch)
            is LocationTrackNameByTrackNumber -> format(trackNumber.number)
            is LocationTrackNameChord -> format(startSwitch, endSwitch)
        }
}

data class LocationTrackNameFreeText(override val freeText: LocationTrackNameFreeTextPart) :
    LocationTrackNameStructure() {
    override val scheme: LocationTrackNamingScheme = LocationTrackNamingScheme.FREE_TEXT
}

data class LocationTrackNameWithinOperatingPoint(override val freeText: LocationTrackNameFreeTextPart) :
    LocationTrackNameStructure() {
    override val scheme: LocationTrackNamingScheme = LocationTrackNamingScheme.WITHIN_OPERATING_POINT
}

data class LocationTrackNameByTrackNumber(
    override val freeText: LocationTrackNameFreeTextPart?,
    override val specifier: LocationTrackNameSpecifier,
) : LocationTrackNameStructure() {
    override val scheme: LocationTrackNamingScheme = LocationTrackNamingScheme.TRACK_NUMBER_TRACK

    fun format(trackNumber: TrackNumber): AlignmentName {
        val nameString =
            if (freeText != null) "$trackNumber ${specifier.properForm} $freeText"
            else "$trackNumber ${specifier.properForm}"

        return AlignmentName(nameString.trim())
    }
}

data class LocationTrackNameBetweenOperatingPoints(override val specifier: LocationTrackNameSpecifier?) :
    LocationTrackNameStructure() {
    override val scheme: LocationTrackNamingScheme = LocationTrackNamingScheme.BETWEEN_OPERATING_POINTS

    fun format(startSwitch: LayoutSwitch?, endSwitch: LayoutSwitch?): AlignmentName {
        val nameString =
            if (specifier != null) "${specifier.properForm} ${getShortName(startSwitch)}-${getShortName(endSwitch)}"
            else "${getShortName(startSwitch)}-${getShortName(endSwitch)}"

        return AlignmentName(nameString.trim())
    }
}

data object LocationTrackNameChord : LocationTrackNameStructure() {
    override val scheme: LocationTrackNamingScheme = LocationTrackNamingScheme.CHORD

    fun format(startSwitch: LayoutSwitch?, endSwitch: LayoutSwitch?): AlignmentName {
        val sharedPrefix = startSwitch?.nameParts?.prefix?.takeIf { it == endSwitch?.nameParts?.prefix }
        val formatted =
            sharedPrefix?.let { "$sharedPrefix ${getShortNumber(startSwitch)}-${getShortNumber(endSwitch)}" }
                ?: "${getShortName(startSwitch)}-${getShortName(endSwitch)}"
        return AlignmentName(formatted)
    }
}

data class LocationTrackDescriptionStructure(
    val base: LocationTrackDescriptionBase,
    val suffix: LocationTrackDescriptionSuffix,
) {
    companion object {
        const val BUFFER_LOCALIZATION_KEY = "location-track-dialog.buffer"
        const val OWNERSHIP_BOUNDARY_LOCALIZATION_KEY = "location-track-dialog.ownership-boundary"
    }

    init {
        require(base.length in locationTrackDescriptionLength) {
            "LocationTrack description base length invalid: expected=${locationTrackDescriptionLength} actual=${base.length}" +
                "length=${base.length} " +
                "allowed=$locationTrackDescriptionLength"
        }
    }

    /** This logic is duplicated in frontend track-layout-model.tsx#formatTrackDescription */
    fun reify(translation: Translation, startSwitch: LayoutSwitch?, endSwitch: LayoutSwitch?): FreeText =
        FreeText(
            when (suffix) {
                LocationTrackDescriptionSuffix.NONE -> base.toString()

                LocationTrackDescriptionSuffix.SWITCH_TO_BUFFER ->
                    "$base ${getShortNumber(startSwitch ?: endSwitch)} - ${translation.t(BUFFER_LOCALIZATION_KEY)}"

                LocationTrackDescriptionSuffix.SWITCH_TO_OWNERSHIP_BOUNDARY ->
                    "$base ${getShortNumber(startSwitch ?: endSwitch)} - ${translation.t(OWNERSHIP_BOUNDARY_LOCALIZATION_KEY)}"

                LocationTrackDescriptionSuffix.SWITCH_TO_SWITCH ->
                    "$base ${getShortNumber(startSwitch)} - ${getShortNumber(endSwitch)}"
            }
        )
}

data class LocationTrack(
    val nameStructure: LocationTrackNameStructure,
    /**
     * Reified name from the structured fields, using dependencies (end switches & track numbers). Should not be edited
     * directly, only via the method [LocationTrackNameStructure.reify]
     */
    val name: AlignmentName,
    val descriptionStructure: LocationTrackDescriptionStructure,
    /**
     * Reified description from the structured fields, using dependencies (end switches). Should not be edited directly,
     * only via the method [LocationTrackDescriptionStructure.reify]
     */
    val description: FreeText,
    val type: LocationTrackType,
    val state: LocationTrackState,
    val trackNumberId: IntId<LayoutTrackNumber>,
    val sourceId: IntId<GeometryAlignment>?,
    /** Comes as calculated from LocationTrackGeometry. Anything set here will be overridden on save. */
    val boundingBox: BoundingBox?,
    /** Comes as calculated from LocationTrackGeometry. Anything set here will be overridden on save. */
    val length: LineM<LocationTrackM>,
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
    val nameStructure: LocationTrackNameStructure,
    val descriptionStructure: LocationTrackDescriptionStructure,
    val name: AlignmentName,
    val start: AlignmentPoint<LocationTrackM>?,
    val end: AlignmentPoint<LocationTrackM>?,
    val length: LineM<LocationTrackM>,
    val duplicateStatus: DuplicateStatus,
)

fun topologicalConnectivityTypeOf(startConnected: Boolean, endConnected: Boolean): TopologicalConnectivityType =
    if (startConnected && endConnected) TopologicalConnectivityType.START_AND_END
    else if (startConnected) TopologicalConnectivityType.START
    else if (endConnected) TopologicalConnectivityType.END else TopologicalConnectivityType.NONE

data class SwitchOnLocationTrack(
    val switchId: IntId<LayoutSwitch>,
    val nameParts: SwitchNameParts?,
    val name: SwitchName,
    val address: TrackMeter?,
    val location: Point?,
    val distance: LineM<LocationTrackM>?,
    val nearestOperatingPoint: RatkoOperationalPoint?,
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
    abstract val location: AlignmentPoint<LocationTrackM>
    abstract val address: TrackMeter?

    abstract fun isSame(other: SplitPoint): Boolean
}

data class SwitchSplitPoint(
    override val location: AlignmentPoint<LocationTrackM>,
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
    override val location: AlignmentPoint<LocationTrackM>,
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
