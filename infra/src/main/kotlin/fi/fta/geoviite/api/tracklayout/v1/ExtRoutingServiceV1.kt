package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geography.transformNonKKJCoordinate
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.tracklayout.EdgeDirection
import fi.fta.geoviite.infra.tracklayout.LAYOUT_M_DELTA
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutDesign
import fi.fta.geoviite.infra.tracklayout.LayoutDesignService
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LineM
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackM
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.RoutingService
import java.math.RoundingMode
import kotlin.math.abs
import org.springframework.beans.factory.annotation.Autowired

const val MAX_ROUTE_SEEK_DISTANCE = 100.0

@GeoviiteService
class ExtRoutingServiceV1
@Autowired
constructor(
    private val publicationService: PublicationService,
    private val routingService: RoutingService,
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val switchDao: LayoutSwitchDao,
    private val geocodingService: GeocodingService,
    private val layoutDesignService: LayoutDesignService,
) {
    fun getExtRoute(
        designOid: ExtOidV1<LayoutDesign>?,
        layoutVersion: ExtLayoutVersionV1?,
        extCoordinateSystem: ExtSridV1?,
        startX: Double,
        startY: Double,
        endX: Double,
        endY: Double,
    ): ExtRouteResponseV1? {
        val branch = branchByDesignOid(layoutDesignService, designOid)
        val publication = publicationService.getPublicationByUuidOrLatest(branch, layoutVersion?.value)
        val moment = publication.publicationTime
        val srid = coordinateSystem(extCoordinateSystem)

        val startPoint = toLayoutCoordinate(startX, startY, srid)
        val endPoint = toLayoutCoordinate(endX, endY, srid)

        val routeResult =
            routingService.getRoute(branch, moment, startPoint, endPoint, MAX_ROUTE_SEEK_DISTANCE) ?: return null

        val trackIds = routeResult.route.sections.map { it.trackId }.distinct()
        val tracksWithGeometry =
            locationTrackService.listOfficialWithGeometryAtMoment(branch, moment, includeDeleted = false).filter {
                (t, _) ->
                trackIds.contains(t.id as IntId)
            }
        val trackGeometryById = tracksWithGeometry.associate { (t, g) -> (t.id as IntId<LocationTrack>) to g }
        val trackById = tracksWithGeometry.associate { (t, _) -> (t.id as IntId<LocationTrack>) to t }

        val trackOidRefs = oidReferences(locationTrackDao, branch, trackIds)
        val trackNumberOidRefs = oidReferences(trackNumberDao, branch)
        val switchOidRefs = oidReferences(switchDao, branch)

        val getGeocodingContext = geocodingService.getLazyGeocodingContextsAtMoment(branch, moment)

        val sections =
            routeResult.route.sections.map { section ->
                val track = trackById[section.trackId] ?: throwRouteTrackNotFound(section.trackId)
                val geometry = trackGeometryById[section.trackId] ?: throwRouteTrackGeometryNotFound(section.trackId)
                val trackOid = trackOidRefs.get(section.trackId)
                val trackNumberId = track.trackNumberId as IntId<LayoutTrackNumber>
                val trackNumberOid = trackNumberOidRefs.get(trackNumberId)
                val geocodingContext = getGeocodingContext(trackNumberId)

                val (startM, endM) =
                    when (section.direction) {
                        EdgeDirection.UP -> section.mRange.min to section.mRange.max
                        EdgeDirection.DOWN -> section.mRange.max to section.mRange.min
                    }

                val startPoint = geometry.getPointAtM(startM) ?: throwRoutePointAtMNotFound(startM, section.trackId)
                val endPoint = geometry.getPointAtM(endM) ?: throwRoutePointAtMNotFound(endM, section.trackId)

                val startAddress = geocodingContext?.getAddress(startPoint)?.first
                val endAddress = geocodingContext?.getAddress(endPoint)?.first

                ExtRouteSectionV1(
                    locationTrackOid = ExtOidV1(trackOid),
                    trackNumberOid = ExtOidV1(trackNumberOid),
                    start =
                        buildEndpoint(
                            startM,
                            startPoint,
                            startAddress?.formatFixedDecimals(3),
                            geometry,
                            switchOidRefs,
                            srid,
                        ),
                    end =
                        buildEndpoint(
                            endM,
                            endPoint,
                            endAddress?.formatFixedDecimals(3),
                            geometry,
                            switchOidRefs,
                            srid,
                        ),
                    direction =
                        when (section.direction) {
                            EdgeDirection.UP -> ExtRouteDirectionV1.ASCENDING
                            EdgeDirection.DOWN -> ExtRouteDirectionV1.DESCENDING
                        },
                    length = section.length.toBigDecimal().setScale(3, RoundingMode.HALF_UP),
                )
            }

        return ExtRouteResponseV1(
            layoutVersion = ExtLayoutVersionV1(publication),
            coordinateSystem = ExtSridV1(srid),
            route =
                ExtRouteV1(
                    totalLength = sections.sumOf { it.length },
                    sections = sections,
                ),
        )
    }

    private fun buildEndpoint(
        m: LineM<LocationTrackM>,
        point: IPoint,
        trackAddress: String?,
        geometry: LocationTrackGeometry,
        switchOidRefs: ExtOidReferencesV1<LayoutSwitch>,
        srid: Srid,
    ): ExtRouteSectionEndpointV1 {
        val switchLink = geometry.trackSwitchLinks.firstOrNull { tsl -> isSameM(tsl.location.m, m) }
        val trackLength = geometry.length

        val (type, switchOid) =
            when {
                switchLink != null -> ExtRouteEndpointTypeV1.SWITCH to ExtOidV1(switchOidRefs.get(switchLink.switchId))
                isTrackEnd(m, trackLength) -> ExtRouteEndpointTypeV1.TRACK_END to null
                else -> ExtRouteEndpointTypeV1.TRACK_POSITION to null
            }

        val outputPoint = if (srid == LAYOUT_SRID) point else transformNonKKJCoordinate(LAYOUT_SRID, srid, point)

        return ExtRouteSectionEndpointV1(
            type = type,
            switchOid = switchOid,
            trackAddress = trackAddress,
            x = outputPoint.x,
            y = outputPoint.y,
            mValue = m.distance.toBigDecimal().setScale(3, RoundingMode.HALF_UP),
        )
    }

    private fun isSameM(a: LineM<LocationTrackM>, b: LineM<LocationTrackM>): Boolean =
        abs(a.distance - b.distance) < LAYOUT_M_DELTA

    private fun isTrackEnd(m: LineM<LocationTrackM>, trackLength: LineM<LocationTrackM>): Boolean =
        m.distance < LAYOUT_M_DELTA || abs(m.distance - trackLength.distance) < LAYOUT_M_DELTA

    private fun toLayoutCoordinate(x: Double, y: Double, srid: Srid): Point {
        val input = Point(x, y)
        return if (srid == LAYOUT_SRID) input
        else transformNonKKJCoordinate(srid, LAYOUT_SRID, input).let { Point(it.x, it.y) }
    }
}
