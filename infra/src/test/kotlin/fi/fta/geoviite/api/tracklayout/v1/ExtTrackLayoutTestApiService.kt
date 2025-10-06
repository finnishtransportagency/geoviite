package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import fi.fta.geoviite.infra.TestApi
import fi.fta.geoviite.infra.common.Oid
import kotlin.reflect.KClass
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc

class ExtTrackLayoutTestApiService(mockMvc: MockMvc) {
    val testApiMapper = jacksonObjectMapper().apply { setSerializationInclusion(JsonInclude.Include.NON_NULL) }
    val testApiConnection = TestApi(testApiMapper, mockMvc)

    val locationTracks =
        AssetApi(
            assetUrl = { oid -> "/geoviite/paikannuspohja/v1/sijaintiraiteet/${oid}" },
            ExtTestLocationTrackResponseV1::class,
            modifiedUrl = { oid -> "/geoviite/paikannuspohja/v1/sijaintiraiteet/${oid}/muutokset" },
            ExtTestModifiedLocationTrackResponseV1::class,
            geometryUrl = { oid -> "/geoviite/paikannuspohja/v1/sijaintiraiteet/${oid}/geometria" },
            ExtTestLocationTrackGeometryResponseV1::class,
        )

    val trackNumbers =
        AssetApi(
            assetUrl = { oid -> "/geoviite/paikannuspohja/v1/ratanumerot/${oid}" },
            ExtTestTrackNumberResponseV1::class,
            modifiedUrl = { oid -> "/geoviite/paikannuspohja/v1/ratanumerot/${oid}/muutokset" },
            ExtTestModifiedTrackNumberResponseV1::class,
            geometryUrl = { oid -> "/geoviite/paikannuspohja/v1/ratanumerot/${oid}/geometria" },
            ExtTestTrackNumberGeometryResponseV1::class,
        )

    val locationTrackCollection =
        AssetCollectionApi(
            assetCollectionUrl = { "/geoviite/paikannuspohja/v1/sijaintiraiteet" },
            ExtTestLocationTrackCollectionResponseV1::class,
            modifiedAssetCollectionUrl = { "/geoviite/paikannuspohja/v1/sijaintiraiteet/muutokset" },
            ExtTestModifiedLocationTrackCollectionResponseV1::class,
        )

    val trackNumberCollection =
        AssetCollectionApi(
            assetCollectionUrl = { "/geoviite/paikannuspohja/v1/ratanumerot" },
            ExtTestTrackNumberCollectionResponseV1::class,
            modifiedAssetCollectionUrl = { "/geoviite/paikannuspohja/v1/ratanumerot/muutokset" },
            ExtTestModifiedTrackNumberCollectionResponseV1::class,
        )

    inner class AssetApi<AssetResponse : Any, AssetModificationResponse : Any, AssetGeometryResponse : Any>(
        private val assetUrl: (String) -> String,
        private val assetClazz: KClass<AssetResponse>,
        private val modifiedUrl: ((String) -> String)? = null,
        private val modifiedClazz: KClass<AssetModificationResponse>? = null,
        private val geometryUrl: ((String) -> String)? = null,
        private val geometryClazz: KClass<AssetGeometryResponse>? = null,
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
            require(geometryUrl != null) { "Geometry not supported for this asset type" }
            internalGetWithoutBody(geometryUrl(oid.toString()), params.toMap(), httpStatus)
        }
    }

    inner class AssetCollectionApi<AssetCollectionResponse : Any, ModifiedAssetCollectionResponse : Any>(
        private val assetCollectionUrl: () -> String,
        private val assetCollectionClazz: KClass<AssetCollectionResponse>,
        private val modifiedAssetCollectionUrl: (() -> String)? = null,
        private val modifiedAssetCollectionClazz: KClass<ModifiedAssetCollectionResponse>? = null,
    ) {

        fun get(vararg params: Pair<String, String>): AssetCollectionResponse {
            return internalGet(assetCollectionClazz, assetCollectionUrl(), params.toMap())
        }

        fun getWithExpectedError(vararg params: Pair<String, String>, httpStatus: HttpStatus): ExtTestErrorResponseV1 {
            return internalGet(ExtTestErrorResponseV1::class, assetCollectionUrl(), params.toMap(), httpStatus)
        }

        fun getWithEmptyBody(vararg params: Pair<String, String>, httpStatus: HttpStatus) {
            internalGetWithoutBody(assetCollectionUrl(), params.toMap(), httpStatus)
        }

        fun getModified(vararg params: Pair<String, String>): ModifiedAssetCollectionResponse {
            require(modifiedAssetCollectionUrl != null) { "Modifications not supported for this asset collection type" }
            return internalGet(
                requireNotNull(modifiedAssetCollectionClazz),
                modifiedAssetCollectionUrl(),
                params.toMap(),
            )
        }

        fun getModifiedWithExpectedError(
            vararg params: Pair<String, String>,
            httpStatus: HttpStatus,
        ): ExtTestErrorResponseV1 {
            require(modifiedAssetCollectionUrl != null) { "Modifications not supported for this asset collection type" }
            return internalGet(ExtTestErrorResponseV1::class, modifiedAssetCollectionUrl(), params.toMap(), httpStatus)
        }

        fun getModifiedWithEmptyBody(vararg params: Pair<String, String>, httpStatus: HttpStatus) {
            require(modifiedAssetCollectionUrl != null) { "Modifications not supported for this asset collection type" }
            internalGetWithoutBody(modifiedAssetCollectionUrl(), params.toMap(), httpStatus)
        }
    }

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

    fun internalGetWithoutBody(url: String, params: Map<String, String> = emptyMap(), httpStatus: HttpStatus) {
        testApiConnection.doGetWithParamsWithoutBody(url, params, httpStatus)
    }
}
