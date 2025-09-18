package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import fi.fta.geoviite.infra.TestApi
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc

const val LOCATION_TRACK_COLLECTION_URL = "/geoviite/paikannuspohja/v1/sijaintiraiteet"
const val MODIFIED_LOCATION_TRACK_COLLECTION_URL = "/geoviite/paikannuspohja/v1/sijaintiraiteet/muutokset"

class ExtTrackLayoutTestApiService(mockMvc: MockMvc) {
    val testApiMapper = jacksonObjectMapper().apply { setSerializationInclusion(JsonInclude.Include.NON_NULL) }
    val testApiConnection = TestApi(testApiMapper, mockMvc)

    val locationTracks = LocationTracks()

    inner class LocationTracks {
        private fun locationTrackUrl(oid: String): String {
            return "/geoviite/paikannuspohja/v1/sijaintiraiteet/${oid}"
        }

        private fun modifiedLocationTrackUrl(oid: String): String {
            return "/geoviite/paikannuspohja/v1/sijaintiraiteet/${oid}/muutokset"
        }

        private fun locationTrackGeometryUrl(oid: String): String {
            return "/geoviite/paikannuspohja/v1/sijaintiraiteet/${oid}/geometria"
        }

        fun get(
            oid: Oid<LocationTrack>,
            vararg params: Pair<String, String>,
        ): ExtTestLocationTrackResponseV1 {
            return internalGet<ExtTestLocationTrackResponseV1>(locationTrackUrl(oid.toString()), params.toMap())
        }

        fun getWithExpectedError(
            oid: String,
            vararg params: Pair<String, String>,
            httpStatus: HttpStatus,
        ): ExtTestErrorResponseV1 {
            return internalGet<ExtTestErrorResponseV1>(locationTrackUrl(oid), params.toMap(), httpStatus)
        }

        fun getWithEmptyBody(
            oid: Oid<LocationTrack>,
            vararg params: Pair<String, String>,
            httpStatus: HttpStatus,
        ) {
            return internalGetWithoutBody(modifiedLocationTrackUrl(oid.toString()), params.toMap(), httpStatus)
        }

        fun getModified(
            oid: Oid<LocationTrack>,
            vararg params: Pair<String, String>,
        ): ExtTestModifiedLocationTrackResponseV1 {
            return internalGet<ExtTestModifiedLocationTrackResponseV1>(
                modifiedLocationTrackUrl(oid.toString()),
                params.toMap(),
            )
        }

        fun getModifiedWithExpectedError(
            oid: String,
            vararg params: Pair<String, String>,
            httpStatus: HttpStatus,
        ): ExtTestErrorResponseV1 {
            return internalGet<ExtTestErrorResponseV1>(
                modifiedLocationTrackUrl(oid),
                params.toMap(),
                httpStatus,
            )
        }

        fun getModifiedWithEmptyBody(
            oid: Oid<LocationTrack>,
            vararg params: Pair<String, String>,
            httpStatus: HttpStatus,
        ) {
            return internalGetWithoutBody(modifiedLocationTrackUrl(oid.toString()), params.toMap(), httpStatus)
        }

        fun getGeometry(
            oid: Oid<LocationTrack>,
            vararg params: Pair<String, String>,
        ): ExtTestLocationTrackGeometryResponseV1 {
            return internalGet<ExtTestLocationTrackGeometryResponseV1>(
                locationTrackGeometryUrl(oid.toString()),
                params.toMap(),
            )
        }

        fun getGeometryWithExpectedError(
            oid: String,
            vararg params: Pair<String, String>,
            httpStatus: HttpStatus,
        ): ExtTestErrorResponseV1 {
            return internalGet<ExtTestErrorResponseV1>(
                locationTrackGeometryUrl(oid),
                params.toMap(),
                httpStatus,
            )
        }

        fun getGeometryWithEmptyBody(
            oid: Oid<LocationTrack>,
            vararg params: Pair<String, String>,
            httpStatus: HttpStatus,
        ) {
            return internalGetWithoutBody(locationTrackGeometryUrl(oid.toString()), params.toMap(), httpStatus)
        }
    }

    fun getLocationTrackCollection(
        vararg params: Pair<String, String>,
    ): ExtTestLocationTrackCollectionResponseV1 {
        return internalGet<ExtTestLocationTrackCollectionResponseV1>(LOCATION_TRACK_COLLECTION_URL, params.toMap())
    }

    fun getLocationTrackCollectionWithExpectedError(
        vararg params: Pair<String, String>,
        httpStatus: HttpStatus,
    ): ExtTestErrorResponseV1 {
        return internalGet<ExtTestErrorResponseV1>(LOCATION_TRACK_COLLECTION_URL, params.toMap(), httpStatus)
    }

    fun getModifiedLocationTrackCollection(
        vararg params: Pair<String, String>,
    ): ExtTestModifiedLocationTrackCollectionResponseV1 {
        return internalGet<ExtTestModifiedLocationTrackCollectionResponseV1>(
            MODIFIED_LOCATION_TRACK_COLLECTION_URL,
            params.toMap(),
        )
    }

    fun getModifiedLocationTrackCollectionWithExpectedError(
        vararg params: Pair<String, String>,
        httpStatus: HttpStatus,
    ): ExtTestErrorResponseV1 {
        return internalGet<ExtTestErrorResponseV1>(MODIFIED_LOCATION_TRACK_COLLECTION_URL, params.toMap(), httpStatus)
    }

    fun getModifiedLocationTrackCollectionWithoutBody(
        vararg params: Pair<String, String>,
        httpStatus: HttpStatus,
    ) {
        return internalGetWithoutBody(MODIFIED_LOCATION_TRACK_COLLECTION_URL, params.toMap(), httpStatus)
    }

    inline fun <reified T> internalGet(
        url: String,
        params: Map<String, String> = emptyMap(),
        httpStatus: HttpStatus = HttpStatus.OK,
    ): T {
        return testApiConnection.doGetWithParams(url, params, httpStatus).let { body ->
            testApiMapper.readValue(body, T::class.java)
        }
    }

    private fun internalGetWithoutBody(
        url: String,
        params: Map<String, String> = emptyMap(),
        httpStatus: HttpStatus,
    ) {
        testApiConnection.doGetWithParamsWithoutBody(url, params, httpStatus)
    }
}
