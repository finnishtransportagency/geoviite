package fi.fta.geoviite.infra.geocoding

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.IntersectType
import fi.fta.geoviite.infra.math.IntersectType.WITHIN
import fi.fta.geoviite.infra.publication.ValidationVersions
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import java.time.Instant
import java.util.*
import kotlin.jvm.optionals.getOrNull
import org.springframework.transaction.annotation.Transactional

@GeoviiteService
class GeocodingService(
    private val addressPointsCache: AddressPointsCache,
    private val geocodingDao: GeocodingDao,
    private val geocodingCacheService: GeocodingCacheService,
) {
    fun getAddressPoints(
        layoutContext: LayoutContext,
        locationTrackId: IntId<LocationTrack>,
        resolution: Resolution = Resolution.ONE_METER,
    ): AlignmentAddresses? {
        return addressPointsCache.getAddressPointCacheKey(layoutContext, locationTrackId, resolution)?.let { cacheKey ->
            addressPointsCache.getAddressPoints(cacheKey)
        }
    }

    // TODO Tänneppä varmaan tarvitaan getAddressPoints(layoutContext, alignmentVresion)

    fun getAddressPoints(
        contextKey: GeocodingContextCacheKey,
        alignmentVersion: RowVersion<LayoutAlignment>,
        resolution: Resolution = Resolution.ONE_METER,
    ): AlignmentAddresses? {
        return addressPointsCache.getAddressPoints(AddressPointCacheKey(alignmentVersion, contextKey, resolution))
    }

    fun getAddress(
        layoutContext: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>,
        location: IPoint,
    ): Pair<TrackMeter, IntersectType>? {
        return getGeocodingContext(layoutContext, trackNumberId)?.getAddress(location)
    }

    fun getAddress(layoutContext: LayoutContext, trackNumberId: IntId<LayoutTrackNumber>, meter: Double): TrackMeter? {
        return getGeocodingContext(layoutContext, trackNumberId)?.getAddress(meter)
    }

    fun getAddressIfWithin(
        layoutContext: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>,
        location: IPoint,
    ): TrackMeter? {
        return getGeocodingContext(layoutContext, trackNumberId)?.getAddress(location)?.let { (address, intersect) ->
            if (intersect != WITHIN) null else address
        }
    }

    fun getLazyGeocodingContexts(layoutContext: LayoutContext): (IntId<LayoutTrackNumber>) -> GeocodingContext? {
        val contexts: MutableMap<IntId<LayoutTrackNumber>, Optional<GeocodingContext>> = mutableMapOf()
        return { trackNumberId ->
            contexts
                .computeIfAbsent(trackNumberId) { Optional.ofNullable(getGeocodingContext(layoutContext, it)) }
                .getOrNull()
        }
    }

    fun getTrackLocation(
        layoutContext: LayoutContext,
        locationTrack: LocationTrack,
        alignment: LayoutAlignment,
        address: TrackMeter,
    ): AddressPoint? {
        return getGeocodingContext(layoutContext, locationTrack.trackNumberId)?.getTrackLocation(alignment, address)
    }

    @Transactional(readOnly = true)
    fun getGeocodingContexts(layoutContext: LayoutContext): Map<IntId<LayoutTrackNumber>, GeocodingContext?> =
        geocodingDao.listLayoutGeocodingContextCacheKeys(layoutContext).associate { key ->
            key.trackNumberId to geocodingCacheService.getGeocodingContext(key)
        }

    fun getGeocodingContext(
        layoutContext: LayoutContext,
        trackNumberId: DomainId<LayoutTrackNumber>?,
    ): GeocodingContext? = if (trackNumberId is IntId) getGeocodingContext(layoutContext, trackNumberId) else null

    fun getGeocodingContext(geocodingContextCacheKey: GeocodingContextCacheKey) =
        geocodingCacheService.getGeocodingContext(geocodingContextCacheKey)

    fun getGeocodingContext(layoutContext: LayoutContext, trackNumberId: IntId<LayoutTrackNumber>): GeocodingContext? =
        getGeocodingContextCreateResult(layoutContext, trackNumberId)?.geocodingContext

    fun getGeocodingContextCreateResult(
        layoutContext: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>,
    ): GeocodingContextCreateResult? =
        geocodingCacheService.getGeocodingContextCreateResult(layoutContext, trackNumberId)

    fun getGeocodingContextAtMoment(
        branch: LayoutBranch,
        trackNumberId: IntId<LayoutTrackNumber>,
        moment: Instant,
    ): GeocodingContext? = geocodingCacheService.getGeocodingContextAtMoment(branch, trackNumberId, moment)

    fun getGeocodingContext(trackNumber: TrackNumber, plan: RowVersion<GeometryPlan>): GeocodingContext? =
        geocodingCacheService.getGeocodingContext(trackNumber, plan)

    fun getGeocodingContextCacheKey(
        layoutContext: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>,
    ): LayoutGeocodingContextCacheKey? = geocodingDao.getLayoutGeocodingContextCacheKey(layoutContext, trackNumberId)

    fun getGeocodingContextCacheKey(
        trackNumberId: IntId<LayoutTrackNumber>,
        versions: ValidationVersions,
    ): GeocodingContextCacheKey? = geocodingDao.getLayoutGeocodingContextCacheKey(trackNumberId, versions)

    fun getGeocodingContextCacheKey(
        branch: LayoutBranch,
        trackNumberId: IntId<LayoutTrackNumber>,
        moment: Instant,
    ): GeocodingContextCacheKey? = geocodingDao.getLayoutGeocodingContextCacheKey(branch, trackNumberId, moment)
}
