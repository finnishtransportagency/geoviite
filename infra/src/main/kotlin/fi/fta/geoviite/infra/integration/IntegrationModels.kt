package fi.fta.geoviite.infra.integration

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import java.time.Instant

enum class RatkoPushErrorType {
    PROPERTIES,
    LOCATION,
    GEOMETRY,
    STATE,
    INTERNAL,
}

enum class RatkoOperation {
    CREATE,
    UPDATE,
    DELETE,
    FETCH_EXISTING,
}

enum class RatkoAssetType {
    TRACK_NUMBER,
    LOCATION_TRACK,
    SWITCH,
}

data class RatkoPush(
    val id: IntId<RatkoPush>,
    val startTime: Instant,
    val endTime: Instant?,
    val status: RatkoPushStatus,
)

sealed interface RatkoPushError {
    val id: IntId<RatkoPushError>
    val ratkoPushId: IntId<RatkoPush>
    val errorType: RatkoPushErrorType
    val ratkoStatusCode: String?
    val technicalMessage: String
}

data class RatkoErrorData(
    override val id: IntId<RatkoPushError>,
    override val ratkoPushId: IntId<RatkoPush>,
    override val errorType: RatkoPushErrorType,
    override val ratkoStatusCode: String?,
    override val technicalMessage: String,
) : RatkoPushError

sealed interface RatkoAssetId<T : LayoutAsset<T>> {
    val id: IntId<T>
    val type: RatkoAssetType
}

data class RatkoSwitchId(override val id: IntId<LayoutSwitch>) : RatkoAssetId<LayoutSwitch> {
    override val type: RatkoAssetType = RatkoAssetType.SWITCH
}

data class RatkoLocationTrackId(override val id: IntId<LocationTrack>) : RatkoAssetId<LocationTrack> {
    override val type: RatkoAssetType = RatkoAssetType.LOCATION_TRACK
}

data class RatkoTrackNumberId(override val id: IntId<LayoutTrackNumber>) : RatkoAssetId<LayoutTrackNumber> {
    override val type: RatkoAssetType = RatkoAssetType.TRACK_NUMBER
}

data class RatkoPushGeneralError(private val data: RatkoErrorData) : RatkoPushError by data

data class RatkoPushAssetError<T : LayoutAsset<T>>(
    val operation: RatkoOperation,
    private val data: RatkoErrorData,
    @JsonIgnore val ratkoAssetId: RatkoAssetId<T>,
) : RatkoPushError by data {
    val assetId: IntId<T>
        get() = ratkoAssetId.id

    val assetType: RatkoAssetType
        get() = ratkoAssetId.type
}

data class RatkoPushErrorResponse(val error: RatkoPushError, val publicationId: IntId<Publication>)

enum class RatkoPushStatus {
    IN_PROGRESS,
    IN_PROGRESS_M_VALUES,
    SUCCESSFUL,
    FAILED,
    CONNECTION_ISSUE,
    MANUAL_RETRY,
}
