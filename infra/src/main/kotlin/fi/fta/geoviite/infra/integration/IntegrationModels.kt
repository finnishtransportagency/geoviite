package fi.fta.geoviite.infra.integration

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
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

data class RatkoPushError<T>(
    val id: IntId<RatkoPushError<*>>,
    val ratkoPushId: IntId<RatkoPush>,
    val errorType: RatkoPushErrorType,
    val operation: RatkoOperation,
    val assetId: IntId<T>,
    val assetType: RatkoAssetType,
)

data class RatkoPushErrorWithAsset(
    val id: IntId<RatkoPushError<*>>,
    val ratkoPushId: IntId<RatkoPush>,
    val errorType: RatkoPushErrorType,
    val operation: RatkoOperation,
    val assetType: RatkoAssetType,
    val asset: LayoutAsset<*>,
)

enum class RatkoPushStatus {
    IN_PROGRESS,
    IN_PROGRESS_M_VALUES,
    SUCCESSFUL,
    FAILED,
    CONNECTION_ISSUE,
    MANUAL_RETRY,
}
