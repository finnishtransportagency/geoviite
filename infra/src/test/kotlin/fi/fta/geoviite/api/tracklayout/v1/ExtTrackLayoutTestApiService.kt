package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import fi.fta.geoviite.infra.TestApi
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import kotlin.reflect.KClass
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc

class ExtTrackLayoutTestApiService(mockMvc: MockMvc) {
    val testApiMapper = jacksonObjectMapper().apply { setSerializationInclusion(JsonInclude.Include.NON_NULL) }
    val testApiConnection = TestApi(testApiMapper, mockMvc)

    val trackLayoutVersion =
        AssetApi<Uuid<Publication>, ExtTestTrackLayoutVersionV1, Nothing, Nothing>(
            assetUrl = { uuid -> "/geoviite/paikannuspohja/v1/versiot/${uuid}" },
            assetClazz = ExtTestTrackLayoutVersionV1::class,
        )

    val trackLayoutVersionLatest =
        AssetCollectionApi<ExtTestTrackLayoutVersionV1, Nothing>(
            assetCollectionUrl = { "/geoviite/paikannuspohja/v1/versiot/uusin" },
            assetCollectionClazz = ExtTestTrackLayoutVersionV1::class,
        )

    val trackLayoutVersionCollection =
        AssetCollectionApi(
            assetCollectionUrl = { "/geoviite/paikannuspohja/v1/versiot" },
            assetCollectionClazz = ExtTestTrackLayoutVersionCollectionResponseV1::class,
            modifiedAssetCollectionUrl = { "/geoviite/paikannuspohja/v1/versiot/muutokset" },
            modifiedAssetCollectionClazz = ExtTestTrackLayoutVersionCollectionResponseV1::class,
        )

    val locationTracks =
        AssetApi<
            Oid<*>,
            ExtTestLocationTrackResponseV1,
            ExtTestModifiedLocationTrackResponseV1,
            ExtTestLocationTrackGeometryResponseV1,
        >(
            assetUrl = { oid -> "/geoviite/paikannuspohja/v1/sijaintiraiteet/${oid}" },
            ExtTestLocationTrackResponseV1::class,
            modifiedUrl = { oid -> "/geoviite/paikannuspohja/v1/sijaintiraiteet/${oid}/muutokset" },
            ExtTestModifiedLocationTrackResponseV1::class,
            geometryUrl = { oid -> "/geoviite/paikannuspohja/v1/sijaintiraiteet/${oid}/geometria" },
            ExtTestLocationTrackGeometryResponseV1::class,
        )

    val trackNumbers =
        AssetApi<
            Oid<*>,
            ExtTestTrackNumberResponseV1,
            ExtTestModifiedTrackNumberResponseV1,
            ExtTestTrackNumberGeometryResponseV1,
        >(
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

    val trackNumberKms =
        AssetApi<Oid<LayoutTrackNumber>, ExtTestTrackKmsResponseV1, Nothing, Nothing>(
            assetUrl = { oid -> "/geoviite/paikannuspohja/v1/ratanumerot/${oid}/ratakilometrit" },
            ExtTestTrackKmsResponseV1::class,
        )

    val trackNumberKmsCollection =
        AssetCollectionApi<ExtTestTrackKmsCollectionResponseV1, Nothing>(
            assetCollectionUrl = { "/geoviite/paikannuspohja/v1/ratanumerot/ratakilometrit" },
            ExtTestTrackKmsCollectionResponseV1::class,
        )

    val switch =
        AssetApi<Oid<*>, ExtTestSwitchResponseV1, ExtTestModifiedSwitchResponseV1, Nothing>(
            assetUrl = { oid -> "/geoviite/paikannuspohja/v1/vaihteet/${oid}" },
            ExtTestSwitchResponseV1::class,
            modifiedUrl = { oid -> "/geoviite/paikannuspohja/v1/vaihteet/${oid}/muutokset" },
            ExtTestModifiedSwitchResponseV1::class,
        )

    val switchCollection =
        AssetCollectionApi(
            assetCollectionUrl = { "/geoviite/paikannuspohja/v1/vaihteet" },
            ExtTestSwitchCollectionResponseV1::class,
            modifiedAssetCollectionUrl = { "/geoviite/paikannuspohja/v1/vaihteet/muutokset" },
            ExtTestModifiedSwitchCollectionResponseV1::class,
        )

    inner class AssetApi<
        AssetId : Any,
        AssetResponse : Any,
        AssetModificationResponse : Any,
        AssetGeometryResponse : Any,
    >(
        private val assetUrl: (String) -> String,
        private val assetClazz: KClass<AssetResponse>,
        private val modifiedUrl: ((String) -> String)? = null,
        private val modifiedClazz: KClass<AssetModificationResponse>? = null,
        private val geometryUrl: ((String) -> String)? = null,
        private val geometryClazz: KClass<AssetGeometryResponse>? = null,
    ) {
        fun get(id: AssetId, vararg params: Pair<String, String>): AssetResponse {
            return internalGet(assetClazz, assetUrl(id.toString()), params.toMap())
        }

        fun getAtVersion(
            id: AssetId,
            layoutVersion: Uuid<Publication>,
            vararg params: Pair<String, String>,
        ): AssetResponse {
            return get(id, TRACK_LAYOUT_VERSION to layoutVersion.toString(), *params)
        }

        fun getWithExpectedError(
            id: String,
            vararg params: Pair<String, String>,
            httpStatus: HttpStatus,
        ): ExtTestErrorResponseV1 {
            return internalGet(ExtTestErrorResponseV1::class, assetUrl(id), params.toMap(), httpStatus)
        }

        fun getWithEmptyBody(id: AssetId, vararg params: Pair<String, String>, httpStatus: HttpStatus) {
            internalGetWithoutBody(assetUrl(id.toString()), params.toMap(), httpStatus)
        }

        fun assertDoesntExist(id: AssetId, vararg params: Pair<String, String>) {
            getWithEmptyBody(id, *params, httpStatus = HttpStatus.NO_CONTENT)
        }

        fun assertDoesntExistAtVersion(
            id: AssetId,
            layoutVersion: Uuid<Publication>,
            vararg params: Pair<String, String>,
        ) {
            getWithEmptyBody(
                id,
                TRACK_LAYOUT_VERSION to layoutVersion.toString(),
                *params,
                httpStatus = HttpStatus.NO_CONTENT,
            )
        }

        fun getModifiedSince(
            id: AssetId,
            fromVersion: Uuid<Publication>,
            vararg params: Pair<String, String>,
        ): AssetModificationResponse {
            return getModified(id, TRACK_LAYOUT_VERSION_FROM to fromVersion.toString(), *params)
        }

        fun getModifiedBetween(
            id: AssetId,
            fromVersion: Uuid<Publication>,
            toVersion: Uuid<Publication>,
            vararg params: Pair<String, String>,
        ): AssetModificationResponse {
            return getModified(
                id,
                TRACK_LAYOUT_VERSION_FROM to fromVersion.toString(),
                TRACK_LAYOUT_VERSION_TO to toVersion.toString(),
                *params,
            )
        }

        fun getModified(id: AssetId, vararg params: Pair<String, String>): AssetModificationResponse {
            require(modifiedUrl != null) { "Modifications not supported for this asset" }
            return internalGet(requireNotNull(modifiedClazz), modifiedUrl(id.toString()), params.toMap())
        }

        fun getModifiedWithExpectedError(
            id: String,
            vararg params: Pair<String, String>,
            httpStatus: HttpStatus,
        ): ExtTestErrorResponseV1 {
            require(modifiedUrl != null) { "Modifications not supported for this asset type" }
            return internalGet(ExtTestErrorResponseV1::class, modifiedUrl(id), params.toMap(), httpStatus)
        }

        fun assertNoModificationSince(
            id: AssetId,
            layoutVersion: Uuid<Publication>,
            vararg params: Pair<String, String>,
        ) {
            return getModifiedWithEmptyBody(
                id,
                TRACK_LAYOUT_VERSION_FROM to layoutVersion.toString(),
                *params,
                httpStatus = HttpStatus.NO_CONTENT,
            )
        }

        fun assertNoModificationBetween(
            id: AssetId,
            from: Uuid<Publication>,
            to: Uuid<Publication>,
            vararg params: Pair<String, String>,
        ) {
            return getModifiedWithEmptyBody(
                id,
                TRACK_LAYOUT_VERSION_FROM to from.toString(),
                TRACK_LAYOUT_VERSION_TO to to.toString(),
                *params,
                httpStatus = HttpStatus.NO_CONTENT,
            )
        }

        fun getModifiedWithEmptyBody(id: AssetId, vararg params: Pair<String, String>, httpStatus: HttpStatus) {
            require(modifiedUrl != null) { "Modifications not supported for this asset type" }
            internalGetWithoutBody(modifiedUrl(id.toString()), params.toMap(), httpStatus)
        }

        fun getGeometry(id: AssetId, vararg params: Pair<String, String>): AssetGeometryResponse {
            require(geometryUrl != null) { "Geometry not supported for this asset type" }
            return internalGet(requireNotNull(geometryClazz), geometryUrl(id.toString()), params.toMap())
        }

        fun getGeometryAt(
            id: AssetId,
            layoutVersion: Uuid<Publication>,
            vararg params: Pair<String, String>,
        ): AssetGeometryResponse {
            return getGeometry(id, TRACK_LAYOUT_VERSION to layoutVersion.toString(), *params)
        }

        fun getGeometryWithExpectedError(
            id: String,
            vararg params: Pair<String, String>,
            httpStatus: HttpStatus,
        ): ExtTestErrorResponseV1 {
            require(geometryUrl != null) { "Geometry not supported for this asset type" }
            return internalGet(ExtTestErrorResponseV1::class, geometryUrl(id), params.toMap(), httpStatus)
        }

        fun getGeometryWithEmptyBody(id: AssetId, vararg params: Pair<String, String>, httpStatus: HttpStatus) {
            require(geometryUrl != null) { "Geometry not supported for this asset type" }
            internalGetWithoutBody(geometryUrl(id.toString()), params.toMap(), httpStatus)
        }

        fun getGeometryWithEmptyBodyAt(
            id: AssetId,
            layoutVersion: Uuid<Publication>,
            vararg params: Pair<String, String>,
            httpStatus: HttpStatus,
        ) {
            return getGeometryWithEmptyBody(
                id,
                TRACK_LAYOUT_VERSION to layoutVersion.toString(),
                *params,
                httpStatus = httpStatus,
            )
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

        fun getAtVersion(
            layoutVersion: Uuid<Publication>,
            vararg params: Pair<String, String>,
        ): AssetCollectionResponse {
            return get(TRACK_LAYOUT_VERSION to layoutVersion.toString(), *params)
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

        fun getModifiedSince(
            fromVersion: Uuid<Publication>,
            vararg params: Pair<String, String>,
        ): ModifiedAssetCollectionResponse {
            return getModified(TRACK_LAYOUT_VERSION_FROM to fromVersion.toString(), *params)
        }

        fun getModifiedBetween(
            fromVersion: Uuid<Publication>,
            toVersion: Uuid<Publication>,
            vararg params: Pair<String, String>,
        ): ModifiedAssetCollectionResponse {
            return getModified(
                TRACK_LAYOUT_VERSION_FROM to fromVersion.toString(),
                TRACK_LAYOUT_VERSION_TO to toVersion.toString(),
                *params,
            )
        }

        fun assertNoModificationSince(layoutVersion: Uuid<Publication>, vararg params: Pair<String, String>) {
            return getModifiedWithEmptyBody(
                TRACK_LAYOUT_VERSION_FROM to layoutVersion.toString(),
                *params,
                httpStatus = HttpStatus.NO_CONTENT,
            )
        }

        fun assertNoModificationBetween(
            from: Uuid<Publication>,
            to: Uuid<Publication>,
            vararg params: Pair<String, String>,
        ) {
            return getModifiedWithEmptyBody(
                TRACK_LAYOUT_VERSION_FROM to from.toString(),
                TRACK_LAYOUT_VERSION_TO to to.toString(),
                *params,
                httpStatus = HttpStatus.NO_CONTENT,
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
