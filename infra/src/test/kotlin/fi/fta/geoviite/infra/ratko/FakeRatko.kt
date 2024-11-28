package fi.fta.geoviite.infra.ratko

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.treeToValue
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.ratko.model.IncomingRatkoAssetLocation
import fi.fta.geoviite.infra.ratko.model.IncomingRatkoGeometry
import fi.fta.geoviite.infra.ratko.model.IncomingRatkoNode
import fi.fta.geoviite.infra.ratko.model.IncomingRatkoNodes
import fi.fta.geoviite.infra.ratko.model.IncomingRatkoPoint
import fi.fta.geoviite.infra.ratko.model.RatkoAssetGeometry
import fi.fta.geoviite.infra.ratko.model.RatkoAssetLocation
import fi.fta.geoviite.infra.ratko.model.RatkoAssetProperty
import fi.fta.geoviite.infra.ratko.model.RatkoAssetType
import fi.fta.geoviite.infra.ratko.model.RatkoBulkTransferPollResponse
import fi.fta.geoviite.infra.ratko.model.RatkoBulkTransferStartResponse
import fi.fta.geoviite.infra.ratko.model.RatkoCrs
import fi.fta.geoviite.infra.ratko.model.RatkoGeometryType
import fi.fta.geoviite.infra.ratko.model.RatkoLocationTrack
import fi.fta.geoviite.infra.ratko.model.RatkoMetadataAsset
import fi.fta.geoviite.infra.ratko.model.RatkoNodeType
import fi.fta.geoviite.infra.ratko.model.RatkoOid
import fi.fta.geoviite.infra.ratko.model.RatkoOperatingPointAsset
import fi.fta.geoviite.infra.ratko.model.RatkoOperatingPointAssetsResponse
import fi.fta.geoviite.infra.ratko.model.RatkoOperatingPointParse
import fi.fta.geoviite.infra.ratko.model.RatkoPlan
import fi.fta.geoviite.infra.ratko.model.RatkoPoint
import fi.fta.geoviite.infra.ratko.model.RatkoRouteNumber
import fi.fta.geoviite.infra.split.BulkTransfer
import fi.fta.geoviite.infra.split.BulkTransferState
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import java.time.Duration
import org.mockserver.client.ForwardChainExpectation
import org.mockserver.configuration.Configuration
import org.mockserver.integration.ClientAndServer
import org.mockserver.matchers.MatchType
import org.mockserver.matchers.Times
import org.mockserver.model.Delay
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse
import org.mockserver.model.JsonBody
import org.mockserver.model.MediaType
import org.slf4j.event.Level
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

@ConditionalOnProperty("geoviite.ratko.test-port")
@GeoviiteService
class FakeRatkoService @Autowired constructor(@Value("\${geoviite.ratko.test-port:}") private val testRatkoPort: Int) {
    fun start(): FakeRatko = FakeRatko(testRatkoPort)
}

class FakeRatko(port: Int) {
    private val mockServer: ClientAndServer =
        ClientAndServer.startClientAndServer(Configuration.configuration().logLevel(Level.ERROR), port)

    private val jsonMapper =
        jsonMapper {
                addModule(kotlinModule { configure(KotlinFeature.NullIsSameAsDefault, true) })
                addModule(JavaTimeModule())

                // Serialize track meters as AAAA+BBBB instead of
                // {"kmNumber": "AAAA", "meters": BBBB}
                SimpleModule()
                    .addSerializer(TrackMeter::class.java, ToStringSerializer(TrackMeter::class.java))
                    .let(::addModule)
            }
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

            // Serialize Instants as timestamp strings (such as 2024-11-15T22:00Z) instead of UNIX
            // integer timestamps.
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    fun stop() {
        mockServer.stop()
    }

    fun isOnline() {
        get("/api/versions/v1.0/version").withId("version-check").respond(ok())
    }

    fun isOffline() {
        get("/api/versions/v1.0/version").withId("version-check").respond(HttpResponse.response().withStatusCode(500))
    }

    fun acceptsNewRouteNumbersGivingThemOids(oids: List<String>) {
        patch("/api/infra/v1.0/routenumbers/geom", oids).respond(ok())

        oids.forEach { oid ->
            post("/api/infra/v1.0/routenumber/points/$oid").respond(ok())
            post("/api/infra/v1.0/routenumbers", mapOf("id" to oid)).respond(okJson(mapOf("id" to oid)))
            post("/api/infra/v1.0/routenumbers", mapOf<String, String>(), MatchType.STRICT, times = Times.once())
                .respond(okJson(mapOf("id" to oid)))
        }
    }

    fun acceptsNewRouteNumbersWithoutPointsGivingThemOids(oids: List<String>) {
        patch("/api/infra/v1.0/routenumbers/geom", oids).respond(ok())

        oids.forEach { oid ->
            post("/api/infra/v1.0/routenumber/points/$oid", times = Times.exactly(0)).respond(ok())
            post("/api/infra/v1.0/routenumbers", mapOf("id" to oid)).respond(okJson(mapOf("id" to oid)))
            post("/api/infra/v1.0/routenumbers", times = Times.once()).respond(okJson(mapOf("id" to oid)))
        }
    }

    fun acceptsNewLocationTrackGivingItOid(oid: String) {
        post("/api/infra/v1.0/locationtracks", mapOf<String, String>(), MatchType.STRICT, Times.once())
            .respond(okJson(mapOf("id" to oid)))
        put("/api/infra/v1.0/locationtracks", mapOf("id" to oid)).respond(ok())
        get("/api/locations/v1.1/locationtracks/${oid}", Times.once()).respond(okJson(listOf<Unit>()))
        post("/api/infra/v1.0/points/${oid}").respond(ok())
        patch("/api/infra/v1.0/points/${oid}").respond(ok())
        post("/api/infra/v1.0/locationtracks", mapOf("id" to oid)).respond(okJson(mapOf("id" to oid)))
        post("/api/assets/v1.2", mapOf("type" to RatkoAssetType.METADATA.value))
            .respond(okJson(listOf(mapOf("id" to oid))))
    }

    fun acceptsNewLocationTrackWithoutPointsGivingItOid(oid: String) {
        post("/api/infra/v1.0/locationtracks", mapOf<String, String>(), MatchType.STRICT, Times.once())
            .respond(okJson(mapOf("id" to oid)))
        put("/api/infra/v1.0/locationtracks", mapOf("id" to oid)).respond(ok())
        get("/api/locations/v1.1/locationtracks/${oid}", Times.once()).respond(okJson(listOf<Unit>()))
        post("/api/infra/v1.0/points/${oid}", times = Times.exactly(0)).respond(ok())
        patch("/api/infra/v1.0/points/${oid}", times = Times.exactly(0)).respond(ok())
        post("/api/infra/v1.0/locationtracks", mapOf("id" to oid)).respond(okJson(mapOf("id" to oid)))
        post("/api/assets/v1.2", mapOf("type" to RatkoAssetType.METADATA.value))
            .respond(okJson(listOf(mapOf("id" to oid))))
    }

    fun acceptsNewSwitchGivingItOid(oid: String) {
        post("/api/assets/v1.2", mapOf("type" to "turnout", "id" to oid)).respond(okJson(listOf(mapOf("id" to oid))))
        put("/api/assets/v1.2/${oid}/locations").respond(ok())
        put("/api/assets/v1.2/${oid}/geoms").respond(ok())
        put("/api/assets/v1.2/${oid}/properties").respond(ok())
        post("/api/assets/v1.2", mapOf("type" to "turnout"), MatchType.STRICT, times = Times.once())
            .respond(okJson(listOf(mapOf("id" to oid))))
    }

    fun acceptsNewSwitchWithoutDataGivingItOid(oid: String) {
        post("/api/assets/v1.2", mapOf("type" to "turnout", "id" to oid)).respond(okJson(listOf(mapOf("id" to oid))))
        put("/api/assets/v1.2/${oid}/locations", Times.exactly(0)).respond(ok())
        put("/api/assets/v1.2/${oid}/geoms", times = Times.exactly(0)).respond(ok())
        put("/api/assets/v1.2/${oid}/properties", times = Times.exactly(0)).respond(ok())
        post("/api/assets/v1.2", mapOf("type" to "turnout"), MatchType.STRICT)
            .respond(okJson(listOf(mapOf("id" to oid))))
    }

    fun hasRouteNumber(routeNumberAsset: InterfaceRatkoRouteNumber) {
        put("/api/infra/v1.0/routenumbers", mapOf("id" to routeNumberAsset.id)).respond(ok())
        get("/api/locations/v1.1/routenumber/${routeNumberAsset.id}").respond(okJson(routeNumberAsset))
        patch("/api/infra/v1.0/routenumber/points/${routeNumberAsset.id}").respond(ok())
    }

    fun hasLocationTrack(locationTrackAsset: InterfaceRatkoLocationTrack) {
        put("/api/infra/v1.0/locationtracks", mapOf("id" to locationTrackAsset.id)).respond(ok())
        get("/api/locations/v1.1/locationtracks/${locationTrackAsset.id}").respond(okJson(listOf(locationTrackAsset)))
        patch("/api/infra/v1.0/points/${locationTrackAsset.id}").respond(ok())
        locationTrackAsset.nodecollection.nodes
            .map { node -> node.point.km }
            .distinct()
            .forEach { km -> delete("/api/infra/v1.0/points/${locationTrackAsset.id}/${km}").respond(ok()) }
    }

    fun hasSwitch(switchAsset: InterfaceRatkoSwitch) {
        get("/api/assets/v1.2/${switchAsset.id}").respond(okJson(switchAsset))
        put("/api/assets/v1.2/${switchAsset.id}/properties").respond(ok())
    }

    fun getPushedRouteNumber(oid: Oid<LayoutTrackNumber>): List<RatkoRouteNumber> =
        mockServer
            .retrieveRecordedRequests(request("/api/infra/v1.0/routenumbers").withMethod("POST|PUT"))
            .map { request -> request.bodyAsString }
            .filter { body -> body.length > 3 }
            .mapNotNull { body ->
                val json = jsonMapper.readValue(body, RatkoRouteNumber::class.java)
                if (json.id == oid.toString()) json else null
            }

    fun acceptsNewBulkTransferGivingItId(bulkTransferId: IntId<BulkTransfer>) {
        val responseStarted = RatkoBulkTransferStartResponse(locationTrackChangeId = bulkTransferId)
        //        val responseFinished = bulkTransferPollResponseInProgress(bulkTransferId =
        // bulkTransferId)

        post(BULK_TRANSFER_CREATE_PATH, times = Times.once()).respond(okJson(responseStarted))
        //        get(bulkTransferPollPath(bulkTransferId)).respond(okJson(responseFinished))
    }

    fun allowsBulkTransferStatePollingAndAnswersWithState(
        bulkTransferId: IntId<BulkTransfer>,
        bulkTransferState: BulkTransferState,
        times: Times = Times.once(),
    ) {
        val response =
            when (bulkTransferState) {
                BulkTransferState.IN_PROGRESS -> bulkTransferPollResponseInProgress(bulkTransferId)
                BulkTransferState.CREATED -> bulkTransferPollResponseCreated(bulkTransferId)
                BulkTransferState.DONE -> bulkTransferPollResponseFinished(bulkTransferId)

                else -> error { "This FakeRatko api does not support bulkTransferState=${bulkTransferState}" }
            }

        get(bulkTransferPollPath(bulkTransferId), times).respond(okJson(response))
    }

    fun acceptsBulkTransferExpeditedStart(bulkTransferId: IntId<BulkTransfer>, times: Times = Times.once()) {
        put(bulkTransferExpeditedStartPath(bulkTransferId), times = times).respond(statusCodeResponse(200))
    }

    fun respondsToBulkTransferPoll(
        bulkTransferId: IntId<BulkTransfer>,
        response: RatkoBulkTransferPollResponse,
        times: Times = Times.once(),
    ) {
        get(bulkTransferPollPath(bulkTransferId), times = times).respond(okJson(response))
    }

    fun respondsToBulkTransferCreateWithHttpStatus(httpStatusCode: Int, times: Times = Times.once()) {
        post(BULK_TRANSFER_CREATE_PATH, times = times).respond(statusCodeResponse(httpStatusCode))
    }

    fun respondsToBulkTransferPollWithHttpStatus(
        bulkTransferId: IntId<BulkTransfer>,
        httpStatusCode: Int,
        times: Times = Times.once(),
    ) {
        get(bulkTransferPollPath(bulkTransferId), times = times).respond(statusCodeResponse(httpStatusCode))
    }

    fun acceptsNewDesignGivingItId(id: Int) {
        post("/api/plan/v1.0/plans", times = Times.once()).respond(respondWithSameDesign(id))
        put("/api/plan/v1.0/plans/$id").respond(respondWithSameDesign(id))
    }

    fun providesPlanItemIdsInDesign(id: Int) {
        var planItemIdSeq = 0
        post("/api/plan/v1.0/plans/$id/plan_items").respond(okJson(mapOf("id" to planItemIdSeq++, "planId" to id)))
    }

    fun getUpdatesToDesign(id: Int): List<RatkoPlan> =
        mockServer.retrieveRecordedRequests(request().withPath("/api/plan/v1.0/plans/$id").withMethod("PUT")).map { req
            ->
            jsonMapper.readValue(req.bodyAsString)
        }

    // return deleted route number kms, or an empty string if all of a route number points were
    // deleted
    fun getRouteNumberPointDeletions(oid: String): List<String> =
        getPointDeletions(oid, "infra/v1.0/routenumber/points")

    fun getLocationTrackPointDeletions(oid: String): List<String> = getPointDeletions(oid, "infra/v1.0/points")

    fun getCreatedRouteNumberPoints(oid: String) = getPointUpdates(oid, "infra/v1.0/routenumber/points", "POST")

    fun getUpdatedRouteNumberPoints(oid: String) = getPointUpdates(oid, "infra/v1.0/routenumber/points", "PATCH")

    fun getCreatedLocationTrackPoints(oid: String) = getPointUpdates(oid, "infra/v1.0/points", "POST")

    fun getUpdatedLocationTrackPoints(oid: String) = getPointUpdates(oid, "infra/v1.0/points", "PATCH")

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

    fun getPushedMetadata(locationTrackOid: String? = null, routeNumberOid: String? = null): List<RatkoMetadataAsset> =
        mockServer
            .retrieveRecordedRequests(
                request()
                    .withPath("/api/assets/v1.2")
                    .withMethod("POST")
                    .withBody(
                        JsonBody.json(
                            mapOf("type" to RatkoAssetType.METADATA.value) +
                                (locationTrackOid?.let { oid -> metadataFilterOn("locationtrack", oid) } ?: mapOf()) +
                                (routeNumberOid?.let { oid -> metadataFilterOn("routenumber", oid) } ?: mapOf())
                        )
                    )
            )
            .map { req -> jsonMapper.readValue(req.bodyAsString) }

    private fun putKmMs(nodeCollection: JsonNode) =
        nodeCollection.get("nodes").forEach { node ->
            val point = node.get("point") as ObjectNode
            val kmM = point.get("kmM").textValue()
            point.put("km", kmM.substring(0, 4))
            point.put("m", kmM.substring(5))
        }

    fun lastPushedSwitchBody(oid: String): String =
        mockServer
            .retrieveRecordedRequests(
                request()
                    .withPath("/api/assets/v1.2")
                    .withMethod("POST")
                    .withBody(JsonBody.json(mapOf("type" to "turnout", "id" to oid)))
            )
            .last()
            .bodyAsString

    fun hostPushedSwitch(oid: String) = hasSwitch(getLastPushedSwitch(oid))

    fun getLastPushedSwitch(oid: String): InterfaceRatkoSwitch = jsonMapper.readValue(lastPushedSwitchBody(oid))

    private fun lastPushedLocationTrackBody(oid: String): String =
        mockServer
            .retrieveRecordedRequests(
                request("/api/infra/v1.0/locationtracks")
                    .withMethod("POST|PUT")
                    .withBody(JsonBody.json(mapOf("id" to oid), MatchType.ONLY_MATCHING_FIELDS))
            )
            .last()
            .bodyAsString!!

    fun hostLocationTrackOid(oid: String) {
        put("/api/infra/v1.0/locationtracks", mapOf("id" to oid)).respond(ok())
        get("/api/locations/v1.1/locationtracks/${oid}").respond(ok())
    }

    fun hostPushedLocationTrack(oid: String) {
        val tree = jsonMapper.readTree(lastPushedLocationTrackBody(oid))
        putKmMs(tree.get("nodecollection"))
        hasLocationTrack(jsonMapper.treeToValue(tree))
    }

    fun getLastPushedLocationTrack(oid: String): RatkoLocationTrack =
        jsonMapper.readValue(lastPushedLocationTrackBody(oid))

    fun getPushedSwitchLocations(oid: String): List<List<RatkoAssetLocation>> =
        mockServer
            .retrieveRecordedRequests(request().withPath("/api/assets/v1.2/${oid}/locations").withMethod("PUT"))
            .map { request -> jsonMapper.readValue(request.bodyAsString) }

    fun getPushedSwitchGeometries(oid: String): List<List<RatkoAssetGeometry>> =
        mockServer
            .retrieveRecordedRequests(request().withPath("/api/assets/v1.2/${oid}/geoms").withMethod("PUT"))
            .map { request -> jsonMapper.readValue(request.bodyAsString) }

    fun hasOperatingPoints(points: List<RatkoOperatingPointParse>) =
        post("/api/assets/v1.2/search", mapOf("assetType" to "railway_traffic_operating_point"), times = Times.once())
            .respond(okJson(RatkoOperatingPointAssetsResponse(points.map(::marshallOperatingPoint))))

    fun delayedOkGetResponse(url: String, delay: Duration, times: Times = Times.once()) {
        get(url, times = times).respond(HttpResponse().withDelay(Delay.milliseconds(delay.toMillis())))
    }

    fun delayedOkPostResponse(url: String, delay: Duration, times: Times = Times.once()) {
        post(url, times = times).respond(HttpResponse().withDelay(Delay.milliseconds(delay.toMillis())))
    }

    private fun getPointUpdates(oid: String, urlInfix: String, method: String): List<List<RatkoPoint>> =
        mockServer
            .retrieveRecordedRequests(request("/api/$urlInfix/$oid").withMethod(method))
            .map { request -> request.bodyAsString }
            .filter { body -> body.length > 3 }
            .map(jsonMapper::readValue)

    private fun getPointDeletions(oid: String, urlInfix: String): List<String> =
        mockServer.retrieveRecordedRequests(request().withPath("/api/$urlInfix/$oid.*").withMethod("DELETE")).map {
            request ->
            request.path.toString().substring("/api/$urlInfix/$oid".length).dropWhile { it == '/' }
        }

    private fun respondWithSameDesign(id: Int): (request: HttpRequest) -> HttpResponse = { request: HttpRequest ->
        val planJson = jsonMapper.readTree(request.bodyAsString) as ObjectNode
        planJson.put("id", id)
        okJson(planJson)
    }

    private fun get(url: String, times: Times? = null): ForwardChainExpectation =
        expectation(url, "GET", null, null, times)

    private fun put(
        url: String,
        body: Any? = null,
        bodyMatchType: MatchType? = null,
        times: Times? = null,
    ): ForwardChainExpectation = expectation(url, "PUT", body, bodyMatchType, times)

    private fun post(
        url: String,
        body: Any? = null,
        bodyMatchType: MatchType? = null,
        times: Times? = null,
    ): ForwardChainExpectation = expectation(url, "POST", body, bodyMatchType, times)

    private fun patch(
        url: String,
        body: Any? = null,
        bodyMatchType: MatchType? = null,
        times: Times? = null,
    ): ForwardChainExpectation = expectation(url, "PATCH", body, bodyMatchType, times)

    private fun delete(
        url: String,
        body: Any? = null,
        bodyMatchType: MatchType? = null,
        times: Times? = null,
    ): ForwardChainExpectation = expectation(url, "DELETE", body, bodyMatchType, times)

    private fun expectation(
        url: String,
        method: String,
        body: Any?,
        bodyMatchType: MatchType?,
        times: Times?,
    ): ForwardChainExpectation =
        mockServer.`when`(
            request(url).withMethod(method).apply {
                if (body != null) {
                    this.withBody(JsonBody.json(body, bodyMatchType ?: MatchType.ONLY_MATCHING_FIELDS))
                }
            },
            times ?: Times.unlimited(),
        )

    private fun ok() = HttpResponse.response().withStatusCode(200)

    private fun statusCodeResponse(httpStatusCode: Int) = HttpResponse.response().withStatusCode(httpStatusCode)

    private fun okJson(body: Any) =
        HttpResponse.response(jsonMapper.writeValueAsString(body))
            .withStatusCode(200)
            .withContentType(MediaType.APPLICATION_JSON)
}

private fun marshallOperatingPoint(point: RatkoOperatingPointParse): RatkoOperatingPointAsset =
    RatkoOperatingPointAsset(
        id = point.externalId.toString(),
        properties =
            listOf(
                RatkoAssetProperty("operational_point_type", enumValue = point.type.name),
                RatkoAssetProperty("name", stringValue = point.name),
                RatkoAssetProperty("operational_point_abbreviation", stringValue = point.abbreviation),
                RatkoAssetProperty("operational_point_code", stringValue = point.uicCode),
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

fun bulkTransferPollPath(bulkTransferId: IntId<BulkTransfer>) = "$BULK_TRANSFER_POLL_PATH/${bulkTransferId.intValue}"

fun bulkTransferExpeditedStartPath(bulkTransferId: IntId<BulkTransfer>) =
    "${BULK_TRANSFER_EXPEDITED_START_PATH}/${bulkTransferId.intValue}"
