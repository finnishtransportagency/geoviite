package fi.fta.geoviite.infra.ratko

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.jsonMapper
import tools.jackson.module.kotlin.kotlinModule
import tools.jackson.module.kotlin.readValue
import tools.jackson.module.kotlin.treeToValue
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.patch
import com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2
import com.github.tomakehurst.wiremock.http.Response
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.ratko.model.IncomingRatkoAssetLocation
import fi.fta.geoviite.infra.ratko.model.IncomingRatkoGeometry
import fi.fta.geoviite.infra.ratko.model.IncomingRatkoNode
import fi.fta.geoviite.infra.ratko.model.IncomingRatkoNodes
import fi.fta.geoviite.infra.ratko.model.IncomingRatkoPoint
import fi.fta.geoviite.infra.ratko.model.RatkoAssetApiType
import fi.fta.geoviite.infra.ratko.model.RatkoAssetGeometry
import fi.fta.geoviite.infra.ratko.model.RatkoAssetLocation
import fi.fta.geoviite.infra.ratko.model.RatkoAssetProperty
import fi.fta.geoviite.infra.ratko.model.RatkoCrs
import fi.fta.geoviite.infra.ratko.model.RatkoGeometryType
import fi.fta.geoviite.infra.ratko.model.RatkoLocationTrack
import fi.fta.geoviite.infra.ratko.model.RatkoLocationTrackState
import fi.fta.geoviite.infra.ratko.model.RatkoMetadataAsset
import fi.fta.geoviite.infra.ratko.model.RatkoNodeType
import fi.fta.geoviite.infra.ratko.model.RatkoOid
import fi.fta.geoviite.infra.ratko.model.RatkoOperationalPointAsset
import fi.fta.geoviite.infra.ratko.model.RatkoOperationalPointAssetsResponse
import fi.fta.geoviite.infra.ratko.model.RatkoOperationalPointParse
import fi.fta.geoviite.infra.ratko.model.RatkoPlan
import fi.fta.geoviite.infra.ratko.model.RatkoPlanItem
import fi.fta.geoviite.infra.ratko.model.RatkoPoint
import fi.fta.geoviite.infra.ratko.model.RatkoRouteNumber
import fi.fta.geoviite.infra.split.BulkTransfer
import fi.fta.geoviite.infra.split.BulkTransferState
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

@ConditionalOnProperty("geoviite.ratko.test-port")
@GeoviiteService
class FakeRatkoService @Autowired constructor(@Value("\${geoviite.ratko.test-port:}") private val testRatkoPort: Int) {
    fun start(): FakeRatko = FakeRatko(testRatkoPort)
}

class FakeRatko(port: Int) {

    private val jsonMapper = jsonMapper {
        addModule(kotlinModule { configure(KotlinFeature.NullIsSameAsDefault, true) })
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    private val planItemIdTransformer = PlanItemIdTransformer()

    private val wireMock: WireMockServer = WireMockServer(options().port(port).extensions(planItemIdTransformer))

    init {
        wireMock.start()
    }

    fun stop() {
        wireMock.stop()
    }

    private var versionCheckStub: StubMapping? = null

    private var routeNumberNewOidSeq = 0
    private var locationTrackNewOidSeq = 0
    private var switchNewOidSeq = 0

    fun isOnline() {
        versionCheckStub?.let { wireMock.removeStub(it) }
        versionCheckStub =
            wireMock.stubFor(get(urlEqualTo("/api/versions/v1.0/version")).willReturn(aResponse().withStatus(200)))
    }

    fun isOffline() {
        versionCheckStub?.let { wireMock.removeStub(it) }
        versionCheckStub =
            wireMock.stubFor(get(urlEqualTo("/api/versions/v1.0/version")).willReturn(aResponse().withStatus(500)))
    }

    fun acceptsNewRouteNumbersGivingThemOids(oids: List<String>) {
        wireMock.stubFor(
            patch(urlEqualTo("/api/infra/v1.0/routenumbers/geom"))
                .withRequestBody(equalToJson(jsonMapper.writeValueAsString(oids), true, true))
                .willReturn(ok())
        )
        oids.forEach { oid ->
            wireMock.stubFor(post(urlEqualTo("/api/infra/v1.0/routenumber/points/$oid")).willReturn(ok()))
            wireMock.stubFor(
                post(urlEqualTo("/api/infra/v1.0/routenumbers"))
                    .withRequestBody(equalToJson("""{"id":"$oid"}""", true, true))
                    .willReturn(okJson(mapOf("id" to oid)))
            )
            stubNewRouteNumberOid(oid)
        }
    }

    fun acceptsNewRouteNumbersWithoutPointsGivingThemOids(oids: List<String>) {
        wireMock.stubFor(
            patch(urlEqualTo("/api/infra/v1.0/routenumbers/geom"))
                .withRequestBody(equalToJson(jsonMapper.writeValueAsString(oids), true, true))
                .willReturn(ok())
        )
        oids.forEach { oid ->
            // Times.exactly(0) for routenumber/points: omit — 404 if called
            wireMock.stubFor(
                post(urlEqualTo("/api/infra/v1.0/routenumbers"))
                    .withRequestBody(equalToJson("""{"id":"$oid"}""", true, true))
                    .willReturn(okJson(mapOf("id" to oid)))
            )
            stubNewRouteNumberOid(oid)
        }
    }

    fun acceptsNewLocationTrackGivingItOid(
        oid: String,
        ratkoLocationTrackAfterCreation: InterfaceRatkoLocationTrack? = null,
    ) {
        stubNewLocationTrackOid(oid)
        wireMock.stubFor(
            patch(urlEqualTo("/api/infra/v1.1/locationtracks/$oid"))
                .withRequestBody(equalToJson("""{"id":"$oid"}""", true, true))
                .willReturn(ok())
        )
        if (ratkoLocationTrackAfterCreation != null) {
            wireMock.stubFor(
                get(urlEqualTo("/api/locations/v1.1/locationtracks/$oid"))
                    .inScenario("lt-get-$oid")
                    .whenScenarioStateIs(STARTED)
                    .willSetStateTo("exists")
                    .willReturn(okJson(listOf<Unit>()))
            )
            wireMock.stubFor(
                get(urlEqualTo("/api/locations/v1.1/locationtracks/$oid"))
                    .inScenario("lt-get-$oid")
                    .whenScenarioStateIs("exists")
                    .willReturn(okJson(listOf(ratkoLocationTrackAfterCreation)))
            )
        } else {
            wireMock.stubFor(
                get(urlEqualTo("/api/locations/v1.1/locationtracks/$oid")).willReturn(okJson(listOf<Unit>()))
            )
        }
        wireMock.stubFor(post(urlEqualTo("/api/infra/v1.0/points/$oid")).willReturn(ok()))
        wireMock.stubFor(patch(urlEqualTo("/api/infra/v1.1/points/$oid?updateKmMvalues=true")).willReturn(ok()))
        wireMock.stubFor(
            post(urlEqualTo("/api/infra/v1.0/locationtracks"))
                .withRequestBody(equalToJson("""{"id":"$oid"}""", true, true))
                .willReturn(okJson(mapOf("id" to oid)))
        )
        wireMock.stubFor(
            post(urlEqualTo("/api/assets/v1.2"))
                .withRequestBody(equalToJson("""{"type":"${RatkoAssetApiType.METADATA.value}"}""", true, true))
                .willReturn(okJson(listOf(mapOf("id" to oid))))
        )
    }

    fun acceptsMultipleNewLocationTracksWithReferencedGeometry(oids: List<String>, locationTrackOidOfGeometry: String) {
        oids.forEach { oid -> acceptsNewLocationTrackWithReferencedGeometry(oid, locationTrackOidOfGeometry) }
    }

    fun acceptsNewLocationTrackWithReferencedGeometry(oid: String, locationTrackOidOfGeometry: String) {
        stubNewLocationTrackOid(oid)
        wireMock.stubFor(get(urlEqualTo("/api/locations/v1.1/locationtracks/$oid")).willReturn(okJson(listOf<Unit>())))
        wireMock.stubFor(
            post(urlPathEqualTo("/api/infra/v1.0/locationtracks"))
                .withRequestBody(equalToJson("""{"id":"$oid"}""", true, true))
                .withQueryParam("locationtrackOidOfGeometry", equalTo(locationTrackOidOfGeometry))
                .willReturn(okJson(mapOf("id" to oid)))
        )
        // Times.exactly(0) for points: omit — 404 if called
        wireMock.stubFor(
            post(urlEqualTo("/api/assets/v1.2"))
                .withRequestBody(equalToJson("""{"type":"${RatkoAssetApiType.METADATA.value}"}""", true, true))
                .willReturn(okJson(listOf(mapOf("id" to oid))))
        )
    }

    fun acceptsUpdatingLocationTrackWithReferencedGeometry(
        existingRatkoLocationTrack: InterfaceRatkoLocationTrack,
        locationTrackOidOfGeometry: String,
    ) {
        val oid = existingRatkoLocationTrack.id
        wireMock.stubFor(
            get(urlEqualTo("/api/locations/v1.1/locationtracks/$oid"))
                .willReturn(okJson(listOf(existingRatkoLocationTrack)))
        )
        wireMock.stubFor(
            patch(urlEqualTo("/api/infra/v1.1/locationtracks/$oid"))
                .withRequestBody(equalToJson("""{"id":"$oid"}""", true, true))
                .willReturn(ok())
        )
        wireMock.stubFor(
            patch(urlPathEqualTo("/api/infra/v1.1/locationtracks/$oid"))
                .withRequestBody(equalToJson("{}", true, true))
                .withQueryParam("locationtrackOIDOfGeometry", equalTo(locationTrackOidOfGeometry))
                .willReturn(ok())
        )
        // Times.exactly(0) for points: omit — 404 if called
        wireMock.stubFor(
            post(urlEqualTo("/api/assets/v1.2"))
                .withRequestBody(equalToJson("""{"type":"${RatkoAssetApiType.METADATA.value}"}""", true, true))
                .willReturn(okJson(listOf(mapOf("id" to oid))))
        )
    }

    fun acceptsUpdatingLocationTrackPartiallyWithReferencedGeometry(
        existingRatkoLocationTrack: InterfaceRatkoLocationTrack,
        locationTrackOidOfGeometry: String,
    ) {
        val oid = existingRatkoLocationTrack.id
        wireMock.stubFor(
            get(urlEqualTo("/api/locations/v1.1/locationtracks/$oid"))
                .willReturn(okJson(listOf(existingRatkoLocationTrack)))
        )
        wireMock.stubFor(
            patch(urlEqualTo("/api/infra/v1.1/locationtracks/$oid"))
                .withRequestBody(equalToJson("""{"id":"$oid"}""", true, true))
                .willReturn(ok())
        )
        wireMock.stubFor(
            post(urlPathEqualTo("/api/infra/v1.0/locationtracks"))
                .withRequestBody(equalToJson("""{"id":"$oid"}""", true, true))
                .withQueryParam("locationtrackOidOfGeometry", equalTo(locationTrackOidOfGeometry))
                .willReturn(okJson(mapOf("id" to oid)))
        )
        wireMock.stubFor(
            patch(urlPathEqualTo("/api/infra/v1.1/locationtracks/$oid"))
                .withRequestBody(equalToJson("{}", true, true))
                .withQueryParam("locationtrackOIDOfGeometry", equalTo(locationTrackOidOfGeometry))
                .willReturn(ok())
        )
        wireMock.stubFor(patch(urlEqualTo("/api/infra/v1.1/points/$oid?updateKmMvalues=true")).willReturn(ok()))
        wireMock.stubFor(
            post(urlEqualTo("/api/assets/v1.2"))
                .withRequestBody(equalToJson("""{"type":"${RatkoAssetApiType.METADATA.value}"}""", true, true))
                .willReturn(okJson(listOf(mapOf("id" to oid))))
        )
    }

    fun expectsLocationTrackStateTransforms(
        locationTrackAsset: InterfaceRatkoLocationTrack,
        states: List<RatkoLocationTrackState>,
    ) {
        wireMock.stubFor(
            get(urlEqualTo("/api/locations/v1.1/locationtracks/${locationTrackAsset.id}"))
                .willReturn(okJson(listOf(locationTrackAsset)))
        )
        wireMock.stubFor(
            patch(urlEqualTo("/api/infra/v1.1/points/${locationTrackAsset.id}?updateKmMvalues=true")).willReturn(ok())
        )
        locationTrackAsset.nodecollection.nodes
            .map { node -> node.point.km }
            .distinct()
            .forEach { km ->
                wireMock.stubFor(
                    delete(urlEqualTo("/api/infra/v1.0/points/${locationTrackAsset.id}/${km}")).willReturn(ok())
                )
            }
        states.forEach { state ->
            wireMock.stubFor(
                patch(urlEqualTo("/api/infra/v1.1/locationtracks/${locationTrackAsset.id}"))
                    .withRequestBody(
                        equalToJson(
                            jsonMapper.writeValueAsString(mapOf("id" to locationTrackAsset.id, "state" to state.value)),
                            true,
                            true,
                        )
                    )
                    .willReturn(ok())
            )
        }
    }

    fun acceptsNewLocationTrackWithoutPointsGivingItOid(oid: String) {
        stubNewLocationTrackOid(oid)
        wireMock.stubFor(
            patch(urlEqualTo("/api/infra/v1.1/locationtracks/$oid"))
                .withRequestBody(equalToJson("""{"id":"$oid"}""", true, true))
                .willReturn(ok())
        )
        wireMock.stubFor(get(urlEqualTo("/api/locations/v1.1/locationtracks/$oid")).willReturn(okJson(listOf<Unit>())))
        // Times.exactly(0) for points: omit — 404 if called
        wireMock.stubFor(
            post(urlEqualTo("/api/infra/v1.0/locationtracks"))
                .withRequestBody(equalToJson("""{"id":"$oid"}""", true, true))
                .willReturn(okJson(mapOf("id" to oid)))
        )
        wireMock.stubFor(
            post(urlEqualTo("/api/assets/v1.2"))
                .withRequestBody(equalToJson("""{"type":"${RatkoAssetApiType.METADATA.value}"}""", true, true))
                .willReturn(okJson(listOf(mapOf("id" to oid))))
        )
    }

    fun acceptsNewSwitchGivingItOid(oid: String) {
        wireMock.stubFor(
            post(urlEqualTo("/api/assets/v1.2"))
                .withRequestBody(equalToJson("""{"type":"turnout","id":"$oid"}""", true, true))
                .willReturn(okJson(listOf(mapOf("id" to oid))))
        )
        wireMock.stubFor(put(urlEqualTo("/api/assets/v1.2/$oid/locations")).willReturn(ok()))
        wireMock.stubFor(put(urlEqualTo("/api/assets/v1.2/$oid/geoms")).willReturn(ok()))
        wireMock.stubFor(put(urlEqualTo("/api/assets/v1.2/$oid/properties")).willReturn(ok()))
        stubNewSwitchOid(oid)
    }

    fun acceptsNewSwitchWithoutDataGivingItOid(oid: String) {
        wireMock.stubFor(
            post(urlEqualTo("/api/assets/v1.2"))
                .withRequestBody(equalToJson("""{"type":"turnout","id":"$oid"}""", true, true))
                .willReturn(okJson(listOf(mapOf("id" to oid))))
        )
        stubNewSwitchOid(oid)
    }

    fun hasRouteNumber(routeNumberAsset: InterfaceRatkoRouteNumber) {
        wireMock.stubFor(
            put(urlEqualTo("/api/infra/v1.0/routenumbers"))
                .withRequestBody(equalToJson("""{"id":"${routeNumberAsset.id}"}""", true, true))
                .willReturn(ok())
        )
        wireMock.stubFor(
            get(urlEqualTo("/api/locations/v1.1/routenumber/${routeNumberAsset.id}"))
                .willReturn(okJson(routeNumberAsset))
        )
        wireMock.stubFor(
            patch(urlEqualTo("/api/infra/v1.0/routenumber/points/${routeNumberAsset.id}")).willReturn(ok())
        )
    }

    fun hasLocationTrack(locationTrackAsset: InterfaceRatkoLocationTrack) {
        wireMock.stubFor(
            patch(urlEqualTo("/api/infra/v1.1/locationtracks/${locationTrackAsset.id}"))
                .withRequestBody(equalToJson("""{"id":"${locationTrackAsset.id}"}""", true, true))
                .willReturn(ok())
        )
        wireMock.stubFor(
            get(urlEqualTo("/api/locations/v1.1/locationtracks/${locationTrackAsset.id}"))
                .willReturn(okJson(listOf(locationTrackAsset)))
        )
        wireMock.stubFor(
            patch(urlEqualTo("/api/infra/v1.1/points/${locationTrackAsset.id}?updateKmMvalues=true")).willReturn(ok())
        )
        locationTrackAsset.nodecollection.nodes
            .map { node -> node.point.km }
            .distinct()
            .forEach { km ->
                wireMock.stubFor(
                    delete(urlEqualTo("/api/infra/v1.0/points/${locationTrackAsset.id}/${km}")).willReturn(ok())
                )
            }
    }

    fun hasSwitch(switchAsset: InterfaceRatkoSwitch, locations: List<RatkoAssetLocation>? = null) {
        val pushedSwitch = if (locations == null) switchAsset else switchAsset.copy(locations = locations)
        wireMock.stubFor(get(urlEqualTo("/api/assets/v1.2/${switchAsset.id}")).willReturn(okJson(pushedSwitch)))
        wireMock.stubFor(put(urlEqualTo("/api/assets/v1.2/${switchAsset.id}/properties")).willReturn(ok()))
        wireMock.stubFor(put(urlEqualTo("/api/assets/v1.2/${switchAsset.id}/locations")).willReturn(ok()))
        wireMock.stubFor(put(urlEqualTo("/api/assets/v1.2/${switchAsset.id}/geoms")).willReturn(ok()))
    }

    fun getPushedRouteNumber(oid: Oid<LayoutTrackNumber>): List<RatkoRouteNumber> =
        (findAll(postRequestedFor(urlEqualTo("/api/infra/v1.0/routenumbers"))) +
                findAll(putRequestedFor(urlEqualTo("/api/infra/v1.0/routenumbers"))))
            .map { it.bodyAsString }
            .filter { it.length > 3 }
            .mapNotNull { body ->
                val json = jsonMapper.readValue(body, RatkoRouteNumber::class.java)
                if (json.id == oid) json else null
            }

    fun acceptsNewBulkTransferGivingItId(bulkTransferId: IntId<BulkTransfer>) {
        val responseStarted = BulkTransferResponse(id = bulkTransferId, state = BulkTransferState.IN_PROGRESS)
        val responseFinished = BulkTransferResponse(id = bulkTransferId, state = BulkTransferState.DONE)

        wireMock.stubFor(post(urlEqualTo("/api/split/bulk-transfer/start")).willReturn(okJson(responseStarted)))
        wireMock.stubFor(
            get(urlEqualTo("/api/split/bulk-transfer/$bulkTransferId/state")).willReturn(okJson(responseFinished))
        )
    }

    fun allowsBulkTransferStatePollingAndAnswersWithState(
        bulkTransferId: IntId<BulkTransfer>,
        bulkTransferState: BulkTransferState,
    ) {
        val response = BulkTransferResponse(id = bulkTransferId, state = bulkTransferState)
        wireMock.stubFor(get(urlEqualTo("/api/split/bulk-transfer/$bulkTransferId/state")).willReturn(okJson(response)))
    }

    fun acceptsNewDesignGivingItId(id: Int) {
        // Ratko explicitly only sends back an ID when we create a plan
        wireMock.stubFor(post(urlEqualTo("/api/plan/v1.0/plans")).willReturn(okJson(mapOf("id" to id))))
        wireMock.stubFor(put(urlEqualTo("/api/plan/v1.0/plans/$id")).willReturn(okJson(mapOf("id" to id))))
    }

    fun providesPlanItemIdsInDesign(id: Int) {
        wireMock.stubFor(
            post(urlEqualTo("/api/plan/v1.0/plans/$id/plan_items"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withTransformers("plan-item-id-transformer")
                )
        )
        wireMock.stubFor(put(urlMatching("/api/plan/v1.0/plan_items/.*")).willReturn(ok()))
    }

    fun getUpdatesToDesign(id: Int): List<RatkoPlan> =
        findAll(putRequestedFor(urlEqualTo("/api/plan/v1.0/plans/$id"))).map { jsonMapper.readValue(it.bodyAsString) }

    fun getUpdatesToPlanItem(id: Int): List<RatkoPlanItem> =
        findAll(putRequestedFor(urlEqualTo("/api/plan/v1.0/plan_items/$id"))).map {
            jsonMapper.readValue(it.bodyAsString)
        }

    // Returns deleted km suffixes, or empty string if all points were deleted
    fun getRouteNumberPointDeletions(oid: String): List<String> =
        getPointDeletions(oid, "infra/v1.0/routenumber/points")

    fun getLocationTrackPointDeletions(oid: String): List<String> = getPointDeletions(oid, "infra/v1.0/points")

    fun getCreatedRouteNumberPoints(oid: String) = getPointUpdates(oid, "infra/v1.0/routenumber/points", "POST")

    fun getUpdatedRouteNumberPoints(oid: String) = getPointUpdates(oid, "infra/v1.0/routenumber/points", "PATCH")

    fun getCreatedLocationTrackPoints(oid: String) = getPointUpdates(oid, "infra/v1.0/points", "POST")

    fun getUpdatedLocationTrackPoints(oid: String) = getPointUpdates(oid, "infra/v1.1/points", "PATCH")

    private fun metadataFilterOn(pointField: String, oid: String) =
        mapOf(
            "locations" to
                listOf(
                    mapOf(
                        "nodecollection" to
                            mapOf(
                                "nodes" to
                                    listOf(
                                        mapOf("point" to mapOf(pointField to (mapOf("id" to oid)))),
                                        mapOf("point" to mapOf(pointField to (mapOf("id" to oid)))),
                                    )
                            )
                    )
                )
        )

    fun getPushedMetadata(locationTrackOid: String? = null, routeNumberOid: String? = null): List<RatkoMetadataAsset> {
        val filter =
            mapOf("type" to RatkoAssetApiType.METADATA.value) +
                (locationTrackOid?.let { oid -> metadataFilterOn("locationtrack", oid) } ?: mapOf()) +
                (routeNumberOid?.let { oid -> metadataFilterOn("routenumber", oid) } ?: mapOf())
        return findAll(
                postRequestedFor(urlEqualTo("/api/assets/v1.2"))
                    .withRequestBody(equalToJson(jsonMapper.writeValueAsString(filter), true, true))
            )
            .map { jsonMapper.readValue(it.bodyAsString) }
    }

    private fun putKmMs(nodeCollection: JsonNode) =
        nodeCollection.get("nodes").forEach { node ->
            val point = node.get("point") as ObjectNode
            val kmM = point.get("kmM").textValue()
            point.put("km", kmM.substring(0, 4))
            point.put("m", kmM.substring(5))
        }

    fun hostCreatedSwitch(oid: String) =
        hasSwitch(getLastCreatedSwitch(oid)!!, getPushedSwitchLocations(oid).lastOrNull())

    fun getLastCreatedSwitch(oid: String): InterfaceRatkoSwitch? =
        findAll(
                postRequestedFor(urlEqualTo("/api/assets/v1.2"))
                    .withRequestBody(equalToJson("""{"type":"turnout","id":"$oid"}""", true, true))
            )
            .lastOrNull()
            ?.bodyAsString
            ?.let(jsonMapper::readValue)

    private fun lastCreatedLocationTrackBody(oid: String): String? =
        findAll(
                postRequestedFor(urlEqualTo("/api/infra/v1.0/locationtracks"))
                    .withRequestBody(equalToJson("""{"id":"$oid"}""", true, true))
            )
            .lastOrNull()
            ?.bodyAsString

    private fun lastUpdatedLocationTrackBody(oid: String): String? =
        findAll(patchRequestedFor(urlEqualTo("/api/infra/v1.1/locationtracks/$oid"))).lastOrNull()?.bodyAsString

    fun getLastCreatedLocationTrack(oid: String): RatkoLocationTrack? =
        lastCreatedLocationTrackBody(oid)?.let(jsonMapper::readValue)

    fun getLastUpdatedLocationTrack(oid: String): RatkoLocationTrack? =
        lastUpdatedLocationTrackBody(oid)?.let(jsonMapper::readValue)

    private fun lastCreatedRouteNumberBody(oid: String): String? =
        findAll(
                postRequestedFor(urlEqualTo("/api/infra/v1.0/routenumbers"))
                    .withRequestBody(equalToJson("""{"id":"$oid"}""", true, true))
            )
            .lastOrNull()
            ?.bodyAsString

    private fun lastUpdatedRouteNumberBody(oid: String): String? =
        findAll(
                putRequestedFor(urlEqualTo("/api/infra/v1.0/routenumbers"))
                    .withRequestBody(equalToJson("""{"id":"$oid"}""", true, true))
            )
            .lastOrNull()
            ?.bodyAsString

    fun getLastCreatedRouteNumber(oid: String): InterfaceRatkoRouteNumber? =
        lastCreatedRouteNumberBody(oid)?.let(jsonMapper::readValue)

    fun getLastUpdatedRouteNumber(oid: String): InterfaceRatkoRouteNumber? =
        lastUpdatedRouteNumberBody(oid)?.let(jsonMapper::readValue)

    fun hostLocationTrackOid(oid: String) {
        wireMock.stubFor(
            patch(urlEqualTo("/api/infra/v1.1/locationtracks/$oid"))
                .withRequestBody(equalToJson("""{"id":"$oid"}""", true, true))
                .willReturn(ok())
        )
        wireMock.stubFor(get(urlEqualTo("/api/locations/v1.1/locationtracks/$oid")).willReturn(ok()))
    }

    fun hostCreatedRouteNumber(oid: String) {
        val tree = jsonMapper.readTree(lastCreatedRouteNumberBody(oid))
        putKmMs(tree.get("nodecollection"))
        hasRouteNumber(jsonMapper.treeToValue(tree))
    }

    fun hostCreatedLocationTrack(oid: String) {
        val tree = jsonMapper.readTree(lastCreatedLocationTrackBody(oid))
        putKmMs(tree.get("nodecollection"))
        hasLocationTrack(jsonMapper.treeToValue(tree))
    }

    fun getPushedSwitchLocations(oid: String): List<List<RatkoAssetLocation>> =
        findAll(putRequestedFor(urlEqualTo("/api/assets/v1.2/$oid/locations"))).map {
            jsonMapper.readValue(it.bodyAsString)
        }

    fun getPushedSwitchGeometries(oid: String): List<List<RatkoAssetGeometry>> =
        findAll(putRequestedFor(urlEqualTo("/api/assets/v1.2/$oid/geoms"))).map {
            jsonMapper.readValue(it.bodyAsString)
        }

    fun hasOperationalPoints(points: List<RatkoOperationalPointParse>) {
        wireMock.stubFor(
            post(urlPathEqualTo("/api/assets/v1.2/search"))
                .withRequestBody(equalToJson("""{"assetType":"railway_traffic_operating_point"}""", true, true))
                .willReturn(okJson(RatkoOperationalPointAssetsResponse(points.map(::marshallOperationalPoint))))
        )
    }

    private fun getPointUpdates(oid: String, urlInfix: String, method: String): List<List<RatkoPoint>> {
        val requestedFor =
            when (method) {
                "POST" -> postRequestedFor(urlEqualTo("/api/$urlInfix/$oid"))
                "PATCH" -> patchRequestedFor(urlPathEqualTo("/api/$urlInfix/$oid"))
                else -> error("Unsupported method: $method")
            }
        return findAll(requestedFor).map { it.bodyAsString }.filter { it.length > 3 }.map(jsonMapper::readValue)
    }

    private fun getPointDeletions(oid: String, urlInfix: String): List<String> =
        findAll(deleteRequestedFor(urlMatching("/api/$urlInfix/$oid.*"))).map {
            it.url.substringAfter("/api/$urlInfix/$oid").dropWhile { c -> c == '/' }
        }

    fun doesNotHaveRouteNumber(oid: String) {
        wireMock.stubFor(
            get(urlEqualTo("/api/locations/v1.1/routenumber/$oid"))
                .willReturn(
                    notFoundJson(
                        mapOf(
                            "code" to "NOT_FOUND",
                            "message" to "Route number couldn't be found with the external id [$oid]",
                        )
                    )
                )
        )
    }

    fun doesNotHaveLocationTrack(oid: String) {
        wireMock.stubFor(
            get(urlEqualTo("/api/locations/v1.1/locationtracks/$oid"))
                .willReturn(
                    notFoundJson(
                        mapOf(
                            "code" to "NOT_FOUND",
                            "message" to "Location track couldn't be found with the external id [$oid]",
                        )
                    )
                )
        )
    }

    fun doesNotHaveSwitch(oid: String) {
        wireMock.stubFor(
            get(urlEqualTo("/api/assets/v1.2/$oid"))
                .willReturn(
                    notFoundJson(
                        mapOf(
                            "code" to "NOT_FOUND",
                            "message" to "Switch couldn't be found with the external id [$oid]",
                        )
                    )
                )
        )
    }

    fun doesNotHaveLocationTrackPoints(oid: String, km: String) {
        wireMock.stubFor(
            delete(urlEqualTo("/api/infra/v1.0/points/$oid/$km"))
                .willReturn(
                    notFoundJson(
                        mapOf(
                            "code" to "NOT_FOUND",
                            "message" to "Points couldn't be found for location track [$oid] at km [$km]",
                        )
                    )
                )
        )
    }

    fun doesNotHaveRouteNumberPoints(oid: String, km: String) {
        wireMock.stubFor(
            delete(urlEqualTo("/api/infra/v1.0/routenumber/points/$oid/$km"))
                .willReturn(
                    notFoundJson(
                        mapOf(
                            "code" to "NOT_FOUND",
                            "message" to "Points couldn't be found for route number [$oid] at km [$km]",
                        )
                    )
                )
        )
    }

    fun rejectsRouteNumberCreationWithError(oid: String, code: String, message: String) {
        doesNotHaveRouteNumber(oid)
        wireMock.stubFor(
            post(urlEqualTo("/api/infra/v1.0/routenumbers"))
                .withRequestBody(equalToJson("""{"id":"$oid"}""", true, true))
                .willReturn(errorJson(code, message))
        )
    }

    fun rejectsLocationTrackCreationWithError(oid: String, code: String, message: String) {
        doesNotHaveLocationTrack(oid)
        wireMock.stubFor(
            post(urlEqualTo("/api/infra/v1.0/locationtracks"))
                .withRequestBody(equalToJson("""{"id":"$oid"}""", true, true))
                .willReturn(errorJson(code, message))
        )
    }

    fun rejectsSwitchCreationWithError(oid: String, code: String, message: String) {
        doesNotHaveSwitch(oid)
        wireMock.stubFor(
            post(urlEqualTo("/api/assets/v1.2"))
                .withRequestBody(equalToJson("""{"type":"turnout","id":"$oid"}""", true, true))
                .willReturn(errorJson(code, message))
        )
    }

    fun rejectsPlanCreationWithError(code: String, message: String) {
        wireMock.stubFor(post(urlEqualTo("/api/plan/v1.0/plans")).willReturn(errorJson(code, message)))
    }

    fun respondsWithMalformedBodyForRouteNumberCreation(oid: String) {
        doesNotHaveRouteNumber(oid)
        wireMock.stubFor(
            post(urlEqualTo("/api/infra/v1.0/routenumbers"))
                .withRequestBody(equalToJson("""{"id":"$oid"}""", true, true))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{not valid json}")
                )
        )
    }

    private fun ok() = aResponse().withStatus(200)

    private fun errorJson(code: String, message: String) =
        aResponse()
            .withStatus(400)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(mapOf("code" to code, "message" to message)))

    private fun notFoundJson(body: Any) =
        aResponse()
            .withStatus(404)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(body))

    private fun okJson(body: Any) =
        aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(body))

    private fun findAll(requestedFor: RequestPatternBuilder) = wireMock.findAll(requestedFor)

    private fun stubNewRouteNumberOid(oid: String) {
        val seq = routeNumberNewOidSeq++
        val currentState = if (seq == 0) STARTED else "rn-new-oid-$seq"
        wireMock.stubFor(
            post(urlEqualTo("/api/infra/v1.0/routenumbers"))
                .withRequestBody(equalToJson("{}", true, false))
                .inScenario("route-number-new-oid")
                .whenScenarioStateIs(currentState)
                .willSetStateTo("rn-new-oid-${seq + 1}")
                .willReturn(okJson(mapOf("id" to oid)))
        )
    }

    private fun stubNewLocationTrackOid(oid: String) {
        val seq = locationTrackNewOidSeq++
        val currentState = if (seq == 0) STARTED else "lt-new-oid-$seq"
        wireMock.stubFor(
            post(urlEqualTo("/api/infra/v1.0/locationtracks"))
                .withRequestBody(equalToJson("{}", true, false))
                .inScenario("location-track-new-oid")
                .whenScenarioStateIs(currentState)
                .willSetStateTo("lt-new-oid-${seq + 1}")
                .willReturn(okJson(mapOf("id" to oid)))
        )
    }

    private fun stubNewSwitchOid(oid: String) {
        val seq = switchNewOidSeq++
        val currentState = if (seq == 0) STARTED else "sw-new-oid-$seq"
        wireMock.stubFor(
            post(urlEqualTo("/api/assets/v1.2"))
                .withRequestBody(equalToJson("""{"type":"turnout"}""", true, false))
                .inScenario("switch-new-oid")
                .whenScenarioStateIs(currentState)
                .willSetStateTo("sw-new-oid-${seq + 1}")
                .willReturn(okJson(listOf(mapOf("id" to oid))))
        )
    }

    inner class PlanItemIdTransformer : ResponseTransformerV2 {
        private var seq = 1

        override fun transform(response: Response, serveEvent: ServeEvent): Response {
            val planJson = jsonMapper.readTree(serveEvent.request.bodyAsString) as ObjectNode
            planJson.put("id", seq++)
            return Response.Builder.like(response).but().body(jsonMapper.writeValueAsString(planJson)).build()
        }

        override fun getName() = "plan-item-id-transformer"

        override fun applyGlobally() = false
    }
}

private fun marshallOperationalPoint(point: RatkoOperationalPointParse): RatkoOperationalPointAsset =
    RatkoOperationalPointAsset(
        id = point.externalId.toString(),
        properties =
            listOf(
                RatkoAssetProperty("operational_point_type", enumValue = point.type.name),
                RatkoAssetProperty("name", stringValue = point.name.toString()),
                RatkoAssetProperty("operational_point_abbreviation", stringValue = point.abbreviation.toString()),
                RatkoAssetProperty("operational_point_code", integerValue = point.uicCode?.toInt()),
            ),
        locations =
            listOf(
                IncomingRatkoAssetLocation(
                    nodecollection =
                        IncomingRatkoNodes(
                            nodes =
                                listOf(
                                    IncomingRatkoNode(
                                        nodeType = RatkoNodeType.SOLO_POINT,
                                        point =
                                            IncomingRatkoPoint(
                                                geometry =
                                                    IncomingRatkoGeometry(
                                                        RatkoGeometryType.POINT,
                                                        point.location.let { listOf(it.x, it.y) },
                                                        RatkoCrs(),
                                                    ),
                                                routenumber = RatkoOid(point.trackNumberExternalId),
                                            ),
                                    )
                                )
                        )
                )
            ),
    )

data class BulkTransferResponse(val id: IntId<BulkTransfer>, val state: BulkTransferState)
