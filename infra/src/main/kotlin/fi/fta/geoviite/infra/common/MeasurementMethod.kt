package fi.fta.geoviite.infra.common

enum class MeasurementMethod {
    OFFICIALLY_MEASURED_GEODETICALLY,
    TRACK_INSPECTION,
    DIGITIZED_AERIAL_IMAGE,
    POINT_CLOUD_SIGNALED,
    POINT_CLOUD_UNSIGNALED,
    GNSS_IMU,
    RTK_GNSS,
    UNKNOWN,
}
