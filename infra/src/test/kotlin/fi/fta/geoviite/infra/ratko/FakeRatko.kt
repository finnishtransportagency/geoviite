package fi.fta.geoviite.infra.ratko

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.*
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.ratko.model.*
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import org.mockserver.client.ForwardChainExpectation
import org.mockserver.configuration.Configuration
import org.mockserver.integration.ClientAndServer
import org.mockserver.matchers.MatchType
import org.mockserver.matchers.Times
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse
import org.mockserver.model.JsonBody
import org.mockserver.model.MediaType
import org.slf4j.event.Level
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@ConditionalOnProperty("geoviite.ratko.test-port")
@Service
class FakeRatkoService @Autowired constructor(@Value("\${geoviite.ratko.test-port:}") private val testRatkoPort: Int) {
    fun start(): FakeRatko = FakeRatko(testRatkoPort)
}

class FakeRatko(port: Int) {
    private val mockServer: ClientAndServer =
        ClientAndServer.startClientAndServer(Configuration.configuration().logLevel(Level.ERROR), port)

    private val jsonMapper =
        jsonMapper { addModule(kotlinModule { configure(KotlinFeature.NullIsSameAsDefault, true) }) }
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

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

    fun acceptsNewSwitchGivingItOid(oid: String) {
        post("/api/assets/v1.2", mapOf("type" to "turnout", "id" to oid)).respond(okJson(listOf(mapOf("id" to oid))))
        put("/api/assets/v1.2/${oid}/locations").respond(ok())
        put("/api/assets/v1.2/${oid}/geoms").respond(ok())
        put("/api/assets/v1.2/${oid}/properties").respond(ok())
        post("/api/assets/v1.2", mapOf("type" to "turnout"), MatchType.STRICT, times = Times.once())
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
    }

    fun hasSwitch(switchAsset: InterfaceRatkoSwitch) {
        get("/api/assets/v1.2/${switchAsset.id}").respond(okJson(switchAsset))
        put("/api/assets/v1.2/${switchAsset.id}/properties").respond(ok())
    }

    fun getPushedRouteNumber(oid: Oid<TrackLayoutTrackNumber>): List<RatkoRouteNumber> =
        mockServer.retrieveRecordedRequests(request("/api/infra/v1.0/routenumbers").withMethod("POST|PUT"))
            .map { request -> request.bodyAsString }
            .filter { body -> body.length > 3 }
            .mapNotNull { body ->
                val json = jsonMapper.readValue(body, RatkoRouteNumber::class.java)
                if (json.id == oid.toString()) json else null
            }

    // return deleted route number kms, or an empty string if all of a route number points were deleted
    fun getRouteNumberPointDeletions(oid: String): List<String> =
        getPointDeletions(oid, "infra/v1.0/routenumber/points")

    fun getLocationTrackPointDeletions(oid: String): List<String> = getPointDeletions(oid, "infra/v1.0/points")

    fun getCreatedRouteNumberPoints(oid: String) = getPointUpdates(oid, "infra/v1.0/routenumber/points", "POST")

    fun getUpdatedRouteNumberPoints(oid: String) = getPointUpdates(oid, "infra/v1.0/routenumber/points", "PATCH")

    fun getCreatedLocationTrackPoints(oid: String) = getPointUpdates(oid, "infra/v1.0/points", "POST")

    fun getUpdatedLocationTrackPoints(oid: String) = getPointUpdates(oid, "infra/v1.0/points", "PATCH")

    private fun metadataFilterOn(pointField: String, oid: String) =
        mapOf(
            "locations" to listOf(
                mapOf(
                    "nodecollection" to mapOf(
                        "nodes" to listOf(
                            mapOf("point" to mapOf(pointField to (mapOf("id" to oid)))),
                            mapOf("point" to mapOf(pointField to (mapOf("id" to oid)))),
                        )
                    )
                )
            )
        )

    fun getPushedMetadata(locationTrackOid: String? = null, routeNumberOid: String? = null): List<RatkoMetadataAsset> =
        mockServer.retrieveRecordedRequests(
            request().withPath("/api/assets/v1.2")
                .withMethod("POST")
                .withBody(
                    JsonBody.json(
                        mapOf("type" to RatkoAssetType.METADATA.value) +
                                (locationTrackOid?.let { oid -> metadataFilterOn("locationtrack", oid) } ?: mapOf()) +
                                (routeNumberOid?.let { oid -> metadataFilterOn("routenumber", oid) } ?: mapOf())
                    )
                )
        ).map { req ->
            jsonMapper.readValue(req.bodyAsString)
        }

    private fun putKmMs(nodeCollection: JsonNode) = nodeCollection.get("nodes").forEach { node ->
        val point = node.get("point") as ObjectNode
        val kmM = point.get("kmM").textValue()
        point.put("km", kmM.substring(0, 4))
        point.put("m", kmM.substring(5))
    }

    fun lastPushedSwitchBody(oid: String): String = mockServer.retrieveRecordedRequests(
        request().withPath("/api/assets/v1.2")
            .withMethod("POST")
            .withBody(JsonBody.json(mapOf("type" to "turnout", "id" to oid)))
    ).last().bodyAsString

    fun hostPushedSwitch(oid: String) = hasSwitch(getLastPushedSwitch(oid))

    fun getLastPushedSwitch(oid: String): InterfaceRatkoSwitch = jsonMapper.readValue(lastPushedSwitchBody(oid))

    private fun lastPushedLocationTrackBody(oid: String): String = mockServer.retrieveRecordedRequests(
        request("/api/infra/v1.0/locationtracks").withMethod("POST|PUT")
            .withBody(JsonBody.json(mapOf("id" to oid), MatchType.ONLY_MATCHING_FIELDS))
    ).last().bodyAsString!!

    fun hostPushedLocationTrack(oid: String) {
        val tree = jsonMapper.readTree(lastPushedLocationTrackBody(oid))
        putKmMs(tree.get("nodecollection"))
        hasLocationTrack(jsonMapper.treeToValue(tree))
    }

    fun getLastPushedLocationTrack(oid: String): RatkoLocationTrack =
        jsonMapper.readValue(lastPushedLocationTrackBody(oid))

    fun getPushedSwitchLocations(oid: String): List<List<RatkoAssetLocation>> =
        mockServer.retrieveRecordedRequests(
            request().withPath("/api/assets/v1.2/${oid}/locations").withMethod("PUT")
        ).map { request ->
            jsonMapper.readValue(request.bodyAsString)
        }

    fun getPushedSwitchGeometries(oid: String): List<List<RatkoAssetGeometry>> =
        mockServer.retrieveRecordedRequests(
            request().withPath("/api/assets/v1.2/${oid}/geoms").withMethod("PUT")
        ).map { request -> jsonMapper.readValue(request.bodyAsString) }

    fun hasOperatingPoints(points: List<RatkoOperatingPointParse>) = post(
        "/api/assets/v1.2/search",
        mapOf("assetType" to "railway_traffic_operating_point")
    ).respond { req ->
        val body = jsonMapper.readTree(req.bodyAsString)
        val size = body.get("size").intValue()
        val pageNumber = body.get("pageNumber").intValue()
        okJson(
            RatkoOperatingPointAssetsResponse(
                points.subList(
                    (size * pageNumber).coerceAtMost(points.size),
                    (size * (pageNumber + 1)).coerceAtMost(points.size),
                ).map(::marshallOperatingPoint)
            )
        )
    }

    private fun getPointUpdates(oid: String, urlInfix: String, method: String): List<List<RatkoPoint>> =
        mockServer.retrieveRecordedRequests(
            request("/api/$urlInfix/$oid").withMethod(method)
        )
            .map { request -> request.bodyAsString }
            .filter { body -> body.length > 3 }
            .map(jsonMapper::readValue)

    private fun getPointDeletions(oid: String, urlInfix: String): List<String> =
        mockServer.retrieveRecordedRequests(
            request().withPath("/api/$urlInfix/$oid.*").withMethod("DELETE")
        )
            .map { request ->
                request.path.toString()
                    .substring("/api/$urlInfix/$oid".length)
                    .dropWhile { it == '/' }
            }

    private fun get(url: String, times: Times? = null): ForwardChainExpectation =
        expectation(url, "GET", null, null, times)

    private fun put(
        url: String,
        body: Any? = null,
        bodyMatchType: MatchType? = null,
        times: Times? = null,
    ): ForwardChainExpectation =
        expectation(url, "PUT", body, bodyMatchType, times)

    private fun post(
        url: String,
        body: Any? = null,
        bodyMatchType: MatchType? = null,
        times: Times? = null,
    ): ForwardChainExpectation =
        expectation(url, "POST", body, bodyMatchType, times)

    private fun patch(
        url: String,
        body: Any? = null,
        bodyMatchType: MatchType? = null,
        times: Times? = null,
    ): ForwardChainExpectation =
        expectation(url, "PATCH", body, bodyMatchType, times)

    private fun expectation(
        url: String,
        method: String,
        body: Any?,
        bodyMatchType: MatchType?,
        times: Times?,
    ): ForwardChainExpectation =
        mockServer.`when`(request(url).withMethod(method).apply {
            if (body != null) {
                this.withBody(JsonBody.json(body, bodyMatchType ?: MatchType.ONLY_MATCHING_FIELDS))
            }
        }, times ?: Times.unlimited())

    private fun ok() =
        HttpResponse.response().withStatusCode(200)

    private fun okJson(body: Any) =
        HttpResponse.response(jsonMapper.writeValueAsString(body))
            .withStatusCode(200)
            .withContentType(MediaType.APPLICATION_JSON)
}

private fun marshallOperatingPoint(point: RatkoOperatingPointParse): RatkoOperatingPointAsset = RatkoOperatingPointAsset(
    id = point.externalId.toString(),
    properties = listOf(
        RatkoAssetProperty("operational_point_type", enumValue = point.type.name),
        RatkoAssetProperty("name", stringValue = point.name),
        RatkoAssetProperty("operational_point_abbreviation", stringValue = point.abbreviation),
        RatkoAssetProperty("operational_point_code", stringValue = point.uicCode),
    ),
    locations = listOf(
        IncomingRatkoAssetLocation(
            nodecollection = IncomingRatkoNodes(
                nodes = listOf(
                    IncomingRatkoNode(
                        nodeType = RatkoNodeType.MIDDLE_POINT,
                        point = IncomingRatkoPoint(
                            geometry = IncomingRatkoGeometry(
                                RatkoGeometryType.POINT,
                                point.location.let { listOf(it.x, it.y) },
                                RatkoCrs()
                            ),
                            routenumber = RatkoOid(point.trackNumberExternalId),
                        )
                    )
                )
            )
        )
    ),
)
