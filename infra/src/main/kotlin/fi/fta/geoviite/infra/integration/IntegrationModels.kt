package fi.fta.geoviite.infra.integration

import fi.fta.geoviite.infra.common.IntId
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

sealed class RatkoPushError(
    open val id: IntId<RatkoPushError>,
    open val ratkoPushId: IntId<RatkoPush>,
    open val errorType: RatkoPushErrorType,
    open val operation: RatkoOperation,
) {
    abstract val assetType: RatkoAssetType
    abstract val assetId: IntId<*>

    data class TrackNumber(
        override val id: IntId<RatkoPushError>,
        override val ratkoPushId: IntId<RatkoPush>,
        override val errorType: RatkoPushErrorType,
        override val operation: RatkoOperation,
        override val assetId: IntId<LayoutTrackNumber>,
    ) : RatkoPushError(id, ratkoPushId, errorType, operation) {
        override val assetType = RatkoAssetType.TRACK_NUMBER
    }

    data class LocationTrack(
        override val id: IntId<RatkoPushError>,
        override val ratkoPushId: IntId<RatkoPush>,
        override val errorType: RatkoPushErrorType,
        override val operation: RatkoOperation,
        override val assetId: IntId<fi.fta.geoviite.infra.tracklayout.LocationTrack>,
    ) : RatkoPushError(id, ratkoPushId, errorType, operation) {
        override val assetType = RatkoAssetType.LOCATION_TRACK
    }

    data class Switch(
        override val id: IntId<RatkoPushError>,
        override val ratkoPushId: IntId<RatkoPush>,
        override val errorType: RatkoPushErrorType,
        override val operation: RatkoOperation,
        override val assetId: IntId<LayoutSwitch>,
    ) : RatkoPushError(id, ratkoPushId, errorType, operation) {
        override val assetType = RatkoAssetType.SWITCH
    }
}

sealed class RatkoPushErrorWithAsset(
    open val id: IntId<RatkoPushError>,
    open val ratkoPushId: IntId<RatkoPush>,
    open val errorType: RatkoPushErrorType,
    open val operation: RatkoOperation,
) {
    abstract val assetType: RatkoAssetType
    abstract val asset: LayoutAsset<*>

    data class TrackNumber(
        override val id: IntId<RatkoPushError>,
        override val ratkoPushId: IntId<RatkoPush>,
        override val errorType: RatkoPushErrorType,
        override val operation: RatkoOperation,
        override val asset: LayoutTrackNumber,
    ) : RatkoPushErrorWithAsset(id, ratkoPushId, errorType, operation) {
        override val assetType = RatkoAssetType.TRACK_NUMBER
    }

    data class LocationTrack(
        override val id: IntId<RatkoPushError>,
        override val ratkoPushId: IntId<RatkoPush>,
        override val errorType: RatkoPushErrorType,
        override val operation: RatkoOperation,
        override val asset: fi.fta.geoviite.infra.tracklayout.LocationTrack,
    ) : RatkoPushErrorWithAsset(id, ratkoPushId, errorType, operation) {
        override val assetType = RatkoAssetType.LOCATION_TRACK
    }

    data class Switch(
        override val id: IntId<RatkoPushError>,
        override val ratkoPushId: IntId<RatkoPush>,
        override val errorType: RatkoPushErrorType,
        override val operation: RatkoOperation,
        override val asset: LayoutSwitch,
    ) : RatkoPushErrorWithAsset(id, ratkoPushId, errorType, operation) {
        override val assetType = RatkoAssetType.SWITCH
    }
}

enum class RatkoPushStatus {
    IN_PROGRESS,
    IN_PROGRESS_M_VALUES,
    SUCCESSFUL,
    FAILED,
    CONNECTION_ISSUE,
    MANUAL_RETRY,
}
