package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.RatkoExternalId
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geography.transformNonKKJCoordinate
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.tracklayout.EdgeDirection
import fi.fta.geoviite.infra.tracklayout.LAYOUT_M_DELTA
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
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
) {
    fun getExtRoute(
        layoutVersion: ExtLayoutVersionV1?,
        extCoordinateSystem: ExtSridV1?,
        startX: Double,
        startY: Double,
        endX: Double,
        endY: Double,
    ): ExtRouteResponseV1? {
        val publication = publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, layoutVersion?.value)
        val branch = publication.layoutBranch.branch
        val moment = publication.publicationTime
        val srid = coordinateSystem(extCoordinateSystem)

        val startPoint = toLayoutPoint(startX, startY, srid)
        val endPoint = toLayoutPoint(endX, endY, srid)

        val routeResult =
            routingService.getRoute(branch.official, startPoint, endPoint, MAX_ROUTE_SEEK_DISTANCE) ?: return null

        val trackIds = routeResult.route.sections.map { it.trackId }.distinct()
        val tracksWithGeometry =
            locationTrackService.listOfficialWithGeometryAtMoment(branch, moment, includeDeleted = false).filter {
                (t, _) ->
                trackIds.contains(t.id as IntId)
            }
        val trackGeometryById = tracksWithGeometry.associate { (t, g) -> (t.id as IntId<LocationTrack>) to g }
        val trackById = tracksWithGeometry.associate { (t, _) -> (t.id as IntId<LocationTrack>) to t }

        val trackExtIds: Map<IntId<LocationTrack>, RatkoExternalId<LocationTrack>> =
            locationTrackDao.fetchExternalIds(branch, trackIds)
        val trackNumberExtIds: Map<IntId<LayoutTrackNumber>, RatkoExternalId<LayoutTrackNumber>> =
            trackNumberDao.fetchExternalIds(branch)
        val switchExtIds: Map<IntId<LayoutSwitch>, RatkoExternalId<LayoutSwitch>> = switchDao.fetchExternalIds(branch)

        val getGeocodingContext = geocodingService.getLazyGeocodingContextsAtMoment(branch, moment)

        val sections =
            routeResult.route.sections.mapNotNull { section ->
                val track = trackById[section.trackId] ?: return@mapNotNull null
                val geometry = trackGeometryById[section.trackId] ?: return@mapNotNull null
                val trackOid = trackExtIds[section.trackId]?.oid ?: return@mapNotNull null
                val trackNumberId = track.trackNumberId as IntId<LayoutTrackNumber>
                val trackNumberOid = trackNumberExtIds[trackNumberId]?.oid ?: return@mapNotNull null
                val geocodingContext = getGeocodingContext(trackNumberId)

                val (alkuM, loppuM) =
                    when (section.direction) {
                        EdgeDirection.UP -> section.mRange.min to section.mRange.max
                        EdgeDirection.DOWN -> section.mRange.max to section.mRange.min
                    }

                val alkuPoint = geometry.getPointAtM(alkuM) ?: return@mapNotNull null
                val loppuPoint = geometry.getPointAtM(loppuM) ?: return@mapNotNull null

                val alkuAddress = geocodingContext?.getAddress(alkuPoint)?.first
                val loppuAddress = geocodingContext?.getAddress(loppuPoint)?.first

                ExtRouteSectionV1(
                    sijaintiraideOid = ExtOidV1(trackOid),
                    ratanumeroOid = ExtOidV1(trackNumberOid),
                    alku =
                        buildEndpoint(
                            alkuM,
                            alkuPoint,
                            alkuAddress?.formatFixedDecimals(3),
                            geometry,
                            switchExtIds,
                            srid,
                        ),
                    loppu =
                        buildEndpoint(
                            loppuM,
                            loppuPoint,
                            loppuAddress?.formatFixedDecimals(3),
                            geometry,
                            switchExtIds,
                            srid,
                        ),
                    suunta =
                        when (section.direction) {
                            EdgeDirection.UP -> ExtRouteDirectionV1.NOUSEVA
                            EdgeDirection.DOWN -> ExtRouteDirectionV1.LASKEVA
                        },
                    pituus = section.length,
                )
            }

        return ExtRouteResponseV1(
            rataverkonVersio = ExtLayoutVersionV1(publication),
            koordinaatisto = ExtSridV1(srid),
            reitti = ExtRouteV1(pituus = sections.sumOf { it.pituus }, reitinOsat = sections),
        )
    }

    private fun buildEndpoint(
        m: LineM<LocationTrackM>,
        point: IPoint,
        rataosoite: String?,
        geometry: LocationTrackGeometry,
        switchExtIds: Map<IntId<LayoutSwitch>, RatkoExternalId<LayoutSwitch>>,
        srid: Srid,
    ): ExtRouteSectionEndpointV1 {
        val switchLink = geometry.trackSwitchLinks.firstOrNull { tsl -> isSameM(tsl.location.m, m) }
        val trackLength = geometry.length

        val (tyyppi, vaihdeOid) =
            when {
                switchLink != null ->
                    ExtRouteEndpointTypeV1.VAIHDE to switchExtIds[switchLink.switchId]?.oid?.let(::ExtOidV1)
                isTrackEnd(m, trackLength) -> ExtRouteEndpointTypeV1.RAITEEN_PAA to null
                else -> ExtRouteEndpointTypeV1.SIJAINTI_RAITEELLA to null
            }

        val outputPoint = if (srid == LAYOUT_SRID) point else transformNonKKJCoordinate(LAYOUT_SRID, srid, point)

        return ExtRouteSectionEndpointV1(
            tyyppi = tyyppi,
            vaihdeOid = vaihdeOid,
            rataosoite = rataosoite,
            x = outputPoint.x,
            y = outputPoint.y,
            mArvo = m.distance,
        )
    }

    private fun isSameM(a: LineM<LocationTrackM>, b: LineM<LocationTrackM>): Boolean =
        abs(a.distance - b.distance) < LAYOUT_M_DELTA

    private fun isTrackEnd(m: LineM<LocationTrackM>, trackLength: LineM<LocationTrackM>): Boolean =
        m.distance < LAYOUT_M_DELTA || abs(m.distance - trackLength.distance) < LAYOUT_M_DELTA

    private fun toLayoutPoint(x: Double, y: Double, srid: Srid): Point {
        val input = Point(x, y)
        return if (srid == LAYOUT_SRID) input
        else transformNonKKJCoordinate(srid, LAYOUT_SRID, input).let { Point(it.x, it.y) }
    }
}
