package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import fi.fta.geoviite.infra.TestApi
import fi.fta.geoviite.infra.common.Oid
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc
import kotlin.reflect.KClass

const val LOCATION_TRACK_COLLECTION_URL = "/geoviite/paikannuspohja/v1/sijaintiraiteet"
const val MODIFIED_LOCATION_TRACK_COLLECTION_URL = "/geoviite/paikannuspohja/v1/sijaintiraiteet/muutokset"

class ExtTrackLayoutTestApiService(mockMvc: MockMvc) {
    val testApiMapper = jacksonObjectMapper().apply { setSerializationInclusion(JsonInclude.Include.NON_NULL) }
    val testApiConnection = TestApi(testApiMapper, mockMvc)

    //    val locationTracks = LocationTracks()

    //    inner class LocationTracks {
    //        private fun locationTrackUrl(oid: String): String {
    //            return "/geoviite/paikannuspohja/v1/sijaintiraiteet/${oid}"
    //        }
    //
    //        private fun modifiedLocationTrackUrl(oid: String): String {
    //            return "/geoviite/paikannuspohja/v1/sijaintiraiteet/${oid}/muutokset"
    //        }
    //
    //        private fun locationTrackGeometryUrl(oid: String): String {
    //            return "/geoviite/paikannuspohja/v1/sijaintiraiteet/${oid}/geometria"
    //        }
    //
    //        fun get(
    //            oid: Oid<LocationTrack>,
    //            vararg params: Pair<String, String>,
    //        ): ExtTestLocationTrackResponseV1 {
    //            return internalGet<ExtTestLocationTrackResponseV1>(locationTrackUrl(oid.toString()), params.toMap())
    //        }
    //
    //        fun getWithExpectedError(
    //            oid: String,
    //            vararg params: Pair<String, String>,
    //            httpStatus: HttpStatus,
    //        ): ExtTestErrorResponseV1 {
    //            return internalGet<ExtTestErrorResponseV1>(locationTrackUrl(oid), params.toMap(), httpStatus)
    //        }
    //
    //        fun getWithEmptyBody(
    //            oid: Oid<LocationTrack>,
    //            vararg params: Pair<String, String>,
    //            httpStatus: HttpStatus,
    //        ) {
    //            return internalGetWithoutBody(modifiedLocationTrackUrl(oid.toString()), params.toMap(), httpStatus)
    //        }
    //
    //        fun getModified(
    //            oid: Oid<LocationTrack>,
    //            vararg params: Pair<String, String>,
    //        ): ExtTestModifiedLocationTrackResponseV1 {
    //            return internalGet<ExtTestModifiedLocationTrackResponseV1>(
    //                modifiedLocationTrackUrl(oid.toString()),
    //                params.toMap(),
    //            )
    //        }
    //
    //        fun getModifiedWithExpectedError(
    //            oid: String,
    //            vararg params: Pair<String, String>,
    //            httpStatus: HttpStatus,
    //        ): ExtTestErrorResponseV1 {
    //            return internalGet<ExtTestErrorResponseV1>(
    //                modifiedLocationTrackUrl(oid),
    //                params.toMap(),
    //                httpStatus,
    //            )
    //        }
    //
    //        fun getModifiedWithEmptyBody(
    //            oid: Oid<LocationTrack>,
    //            vararg params: Pair<String, String>,
    //            httpStatus: HttpStatus,
    //        ) {
    //            return internalGetWithoutBody(modifiedLocationTrackUrl(oid.toString()), params.toMap(), httpStatus)
    //        }
    //
    //        fun getGeometry(
    //            oid: Oid<LocationTrack>,
    //            vararg params: Pair<String, String>,
    //        ): ExtTestLocationTrackGeometryResponseV1 {
    //            return internalGet<ExtTestLocationTrackGeometryResponseV1>(
    //                locationTrackGeometryUrl(oid.toString()),
    //                params.toMap(),
    //            )
    //        }
    //
    //        fun getGeometryWithExpectedError(
    //            oid: String,
    //            vararg params: Pair<String, String>,
    //            httpStatus: HttpStatus,
    //        ): ExtTestErrorResponseV1 {
    //            return internalGet<ExtTestErrorResponseV1>(
    //                locationTrackGeometryUrl(oid),
    //                params.toMap(),
    //                httpStatus,
    //            )
    //        }
    //
    //        fun getGeometryWithEmptyBody(
    //            oid: Oid<LocationTrack>,
    //            vararg params: Pair<String, String>,
    //            httpStatus: HttpStatus,
    //        ) {
    //            return internalGetWithoutBody(locationTrackGeometryUrl(oid.toString()), params.toMap(), httpStatus)
    //        }
    //    }

    //    fun getLocationTrackCollection(
    //        vararg params: Pair<String, String>,
    //    ): ExtTestLocationTrackCollectionResponseV1 {
    //        return internalGet<ExtTestLocationTrackCollectionResponseV1>(LOCATION_TRACK_COLLECTION_URL,
    // params.toMap())
    //    }
    //
    //    fun getLocationTrackCollectionWithExpectedError(
    //        vararg params: Pair<String, String>,
    //        httpStatus: HttpStatus,
    //    ): ExtTestErrorResponseV1 {
    //        return internalGet<ExtTestErrorResponseV1>(LOCATION_TRACK_COLLECTION_URL, params.toMap(), httpStatus)
    //    }
    //
    //    fun getModifiedLocationTrackCollection(
    //        vararg params: Pair<String, String>,
    //    ): ExtTestModifiedLocationTrackCollectionResponseV1 {
    //        return internalGet<ExtTestModifiedLocationTrackCollectionResponseV1>(
    //            MODIFIED_LOCATION_TRACK_COLLECTION_URL,
    //            params.toMap(),
    //        )
    //    }
    //
    //    fun getModifiedLocationTrackCollectionWithExpectedError(
    //        vararg params: Pair<String, String>,
    //        httpStatus: HttpStatus,
    //    ): ExtTestErrorResponseV1 {
    //        return internalGet<ExtTestErrorResponseV1>(MODIFIED_LOCATION_TRACK_COLLECTION_URL, params.toMap(),
    // httpStatus)
    //    }
    //
    //    fun getModifiedLocationTrackCollectionWithoutBody(
    //        vararg params: Pair<String, String>,
    //        httpStatus: HttpStatus,
    //    ) {
    //        return internalGetWithoutBody(MODIFIED_LOCATION_TRACK_COLLECTION_URL, params.toMap(), httpStatus)
    //    }
    //
    inner class AssetApi<
        AssetResponse : Any,
        AssetModificationResponse : Any,
        AssetGeometryResponse : Any,
    >(
        private val assetClazz: KClass<AssetResponse>,
        private val modifiedClazz: KClass<AssetModificationResponse>? = null,
        private val geometryClazz: KClass<AssetGeometryResponse>? = null,
        private val assetUrl: (String) -> String,
        private val modifiedUrl: ((String) -> String)? = null,
        private val geometryUrl: ((String) -> String)? = null,
    ) {
        fun get(oid: Oid<*>, vararg params: Pair<String, String>): AssetResponse {
            return internalGet(assetClazz, assetUrl(oid.toString()), params.toMap())
        }

        fun getWithExpectedError(
            oid: String,
            vararg params: Pair<String, String>,
            httpStatus: HttpStatus,
        ): ExtTestErrorResponseV1 {
            return internalGet(ExtTestErrorResponseV1::class, assetUrl(oid), params.toMap(), httpStatus)
        }

        fun getWithEmptyBody(oid: Oid<*>, vararg params: Pair<String, String>, httpStatus: HttpStatus) {
            internalGetWithoutBody(assetUrl(oid.toString()), params.toMap(), httpStatus)
        }

        fun getModified(oid: Oid<*>, vararg params: Pair<String, String>): AssetModificationResponse {
            require(modifiedUrl != null) { "Modifications not supported for this asset" }
            return internalGet(requireNotNull(modifiedClazz), modifiedUrl(oid.toString()), params.toMap())
        }

        fun getModifiedWithExpectedError(
            oid: String,
            vararg params: Pair<String, String>,
            httpStatus: HttpStatus,
        ): ExtTestErrorResponseV1 {
            require(modifiedUrl != null) { "Modifications not supported for this asset type" }
            return internalGet(ExtTestErrorResponseV1::class, modifiedUrl(oid), params.toMap(), httpStatus)
        }

        fun getModifiedWithEmptyBody(oid: Oid<*>, vararg params: Pair<String, String>, httpStatus: HttpStatus) {
            require(modifiedUrl != null) { "Modifications not supported for this asset type" }
            internalGetWithoutBody(modifiedUrl(oid.toString()), params.toMap(), httpStatus)
        }

        fun getGeometry(oid: Oid<*>, vararg params: Pair<String, String>): AssetGeometryResponse {
            require(geometryUrl != null) { "Geometry not supported for this asset type" }
            return internalGet(requireNotNull(geometryClazz), geometryUrl(oid.toString()), params.toMap())
        }

        fun getGeometryWithExpectedError(
            oid: String,
            vararg params: Pair<String, String>,
            httpStatus: HttpStatus,
        ): ExtTestErrorResponseV1 {
            require(geometryUrl != null) { "Geometry not supported for this asset type" }
            return internalGet(ExtTestErrorResponseV1::class, geometryUrl(oid), params.toMap(), httpStatus)
        }

        fun getGeometryWithEmptyBody(oid: Oid<*>, vararg params: Pair<String, String>, httpStatus: HttpStatus) {
            require(modifiedUrl != null) { "Geometry not supported for this asset type" }
            internalGetWithoutBody(modifiedUrl(oid.toString()), params.toMap(), httpStatus)
        }
    }

    // --- DSL instances ---
    val locationTracks =
        AssetApi(
            ExtTestLocationTrackResponseV1::class,
            ExtTestModifiedLocationTrackResponseV1::class,
            ExtTestLocationTrackGeometryResponseV1::class,
            assetUrl = { oid -> "/geoviite/paikannuspohja/v1/sijaintiraiteet/${oid}" },
            modifiedUrl = { oid -> "/geoviite/paikannuspohja/v1/sijaintiraiteet/${oid}/muutokset" },
            geometryUrl = { oid -> "/geoviite/paikannuspohja/v1/sijaintiraiteet/${oid}/geometria" },
        )

    //
    //    inner class AssetApi<T : ExtTestApiAsset>(val asset: T) {
    //        inline fun <reified R> get(oid: String, vararg params: Pair<String, String>): R =
    //            exec(asset.assetUrl(oid), params)
    //
    //        fun getWithExpectedError(
    //            oid: String,
    //            vararg params: Pair<String, String>,
    //            httpStatus: HttpStatus,
    //        ): ExtTestErrorResponseV1 = exec<ExtTestErrorResponseV1>(asset.assetUrl(oid), params, httpStatus)
    //
    //        fun getWithEmptyBody(oid: String, vararg params: Pair<String, String>, httpStatus: HttpStatus) {
    //            exec<Any>(asset.assetUrl(oid), params, httpStatus, emptyBody = true)
    //        }
    //    }
    //
    //    inner class ModifiedAssetApi<T : ExtTestApiAssetWithModifications>(val asset: T) {
    //        inline fun <reified R> getModified(oid: String, vararg params: Pair<String, String>): R =
    //            exec(asset.modifiedUrl(oid), params)
    //
    //        fun getModifiedWithExpectedError(
    //            oid: String,
    //            vararg params: Pair<String, String>,
    //            httpStatus: HttpStatus,
    //        ): ExtTestErrorResponseV1 = exec(asset.modifiedUrl(oid), params, httpStatus)
    //
    //        fun getModifiedWithEmptyBody(oid: String, vararg params: Pair<String, String>, httpStatus: HttpStatus) {
    //            exec<Any>(asset.modifiedUrl(oid), params, httpStatus, emptyBody = true)
    //        }
    //    }
    //
    //    inner class GeometryAssetApi<T : ExtTestApiAssetWithGeometry>(val asset: T) {
    //        inline fun <reified R> getGeometry(oid: String, vararg params: Pair<String, String>): R =
    //            exec(asset.geometryUrl(oid), params)
    //
    //        fun <R> getGeometryWithExpectedError(
    //            oid: String,
    //            vararg params: Pair<String, String>,
    //            httpStatus: HttpStatus,
    //        ): ExtTestErrorResponseV1 = exec(asset.geometryUrl(oid), params, httpStatus)
    //
    //        fun getGeometryWithEmptyBody(oid: String, vararg params: Pair<String, String>, httpStatus: HttpStatus) {
    //            exec<Any>(asset.geometryUrl(oid), params, httpStatus, emptyBody = true)
    //        }
    //    }

    fun <T : Any> internalGet(
        responseClazz: KClass<T>,
        url: String,
        params: Map<String, String> = emptyMap(),
        httpStatus: HttpStatus = HttpStatus.OK,
    ): T {
        return testApiConnection.doGetWithParams(url, params, httpStatus).let { body ->
            testApiMapper.readValue(body, responseClazz.java)
        }
    }

    fun internalGetWithoutBody(
        url: String,
        params: Map<String, String> = emptyMap(),
        httpStatus: HttpStatus,
    ) {
        testApiConnection.doGetWithParamsWithoutBody(url, params, httpStatus)
    }
}

// interface ExtTestApiAsset {
//    fun assetUrl(oid: String): String
// }
//
// interface ExtTestApiAssetWithModifications : ExtTestApiAsset {
//    fun modifiedUrl(oid: String): String
// }
//
// interface ExtTestApiAssetWithGeometry : ExtTestApiAsset {
//    fun geometryUrl(oid: String): String
// }
//
// object ExtTestApiLocationTrack : ExtTestApiAssetWithModifications, ExtTestApiAssetWithGeometry {
//    override fun assetUrl(oid: String) = "/geoviite/paikannuspohja/v1/sijaintiraiteet/${oid}"
//
//    override fun modifiedUrl(oid: String) = "/geoviite/paikannuspohja/v1/sijaintiraiteet/${oid}/muutokset"
//
//    override fun geometryUrl(oid: String) = "/geoviite/paikannuspohja/v1/sijaintiraiteet/${oid}/geometria"
// }
