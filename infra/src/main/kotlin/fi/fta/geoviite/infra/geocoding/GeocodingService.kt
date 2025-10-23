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
import fi.fta.geoviite.infra.tracklayout.AlignmentM
import fi.fta.geoviite.infra.tracklayout.IAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LineM
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackM
import fi.fta.geoviite.infra.tracklayout.PlanLayoutAlignmentM
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
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
    ): AlignmentAddresses<LocationTrackM>? {
        return addressPointsCache.getAddressPointCacheKey(layoutContext, locationTrackId, resolution)?.let { cacheKey ->
            addressPointsCache.getAddressPoints(cacheKey)
        }
    }

    fun getAddressPoints(
        contextKey: LayoutGeocodingContextCacheKey,
        trackVersion: LayoutRowVersion<LocationTrack>,
        resolution: Resolution = Resolution.ONE_METER,
    ): AlignmentAddresses<LocationTrackM>? {
        return addressPointsCache.getAddressPoints(AddressPointCacheKey(trackVersion, contextKey, resolution))
    }

    fun getAddress(
        layoutContext: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>,
        location: IPoint,
    ): Pair<TrackMeter, IntersectType>? {
        return getGeocodingContext(layoutContext, trackNumberId)?.getAddress(location)
    }

    fun getAddress(
        layoutContext: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>,
        meter: LineM<ReferenceLineM>,
    ): TrackMeter? {
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

    fun getLazyGeocodingContexts(
        layoutContext: LayoutContext
    ): (IntId<LayoutTrackNumber>) -> GeocodingContext<ReferenceLineM>? {
        val contexts: MutableMap<IntId<LayoutTrackNumber>, Optional<GeocodingContext<ReferenceLineM>>> = mutableMapOf()
        return { trackNumberId ->
            contexts
                .computeIfAbsent(trackNumberId) { Optional.ofNullable(getGeocodingContext(layoutContext, it)) }
                .getOrNull()
        }
    }

    fun getLazyGeocodingContextsAtMoment(
        branch: LayoutBranch,
        moment: Instant,
    ): (IntId<LayoutTrackNumber>) -> GeocodingContext<ReferenceLineM>? {
        val contexts: MutableMap<IntId<LayoutTrackNumber>, Optional<GeocodingContext<ReferenceLineM>>> = mutableMapOf()
        return { trackNumberId ->
            contexts
                .computeIfAbsent(trackNumberId) { Optional.ofNullable(getGeocodingContextAtMoment(branch, it, moment)) }
                .getOrNull()
        }
    }

    fun <M : AlignmentM<M>> getTrackLocation(
        layoutContext: LayoutContext,
        locationTrack: LocationTrack,
        alignment: IAlignment<M>,
        address: TrackMeter,
    ): AddressPoint<M>? {
        return getGeocodingContext(layoutContext, locationTrack.trackNumberId)?.getTrackLocation(alignment, address)
    }

    @Transactional(readOnly = true)
    fun getGeocodingContexts(
        layoutContext: LayoutContext
    ): Map<IntId<LayoutTrackNumber>, GeocodingContext<ReferenceLineM>?> =
        geocodingDao.listLayoutGeocodingContextCacheKeys(layoutContext).associate { key ->
            key.trackNumberId to geocodingCacheService.getGeocodingContext(key)
        }

    fun getGeocodingContext(
        layoutContext: LayoutContext,
        trackNumberId: DomainId<LayoutTrackNumber>?,
    ): GeocodingContext<ReferenceLineM>? =
        if (trackNumberId is IntId) getGeocodingContext(layoutContext, trackNumberId) else null

    fun getGeocodingContext(geocodingContextCacheKey: LayoutGeocodingContextCacheKey) =
        geocodingCacheService.getGeocodingContext(geocodingContextCacheKey)

    fun getGeocodingContext(
        layoutContext: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>,
    ): GeocodingContext<ReferenceLineM>? =
        getGeocodingContextCreateResult(layoutContext, trackNumberId)?.geocodingContext

    fun getGeocodingContextCreateResult(
        layoutContext: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>,
    ): ValidatedGeocodingContext<ReferenceLineM>? =
        geocodingCacheService.getGeocodingContextCreateResult(layoutContext, trackNumberId)

    fun getGeocodingContextAtMoment(
        branch: LayoutBranch,
        trackNumberId: IntId<LayoutTrackNumber>,
        moment: Instant,
    ): GeocodingContext<ReferenceLineM>? =
        geocodingCacheService.getGeocodingContextAtMoment(branch, trackNumberId, moment)

    fun getGeocodingContext(
        trackNumber: TrackNumber,
        plan: RowVersion<GeometryPlan>,
    ): GeocodingContext<PlanLayoutAlignmentM>? = geocodingCacheService.getGeocodingContext(trackNumber, plan)

    fun getGeocodingContextCacheKey(
        layoutContext: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>,
    ): LayoutGeocodingContextCacheKey? = geocodingDao.getLayoutGeocodingContextCacheKey(layoutContext, trackNumberId)

    fun getGeocodingContextCacheKey(
        trackNumberId: IntId<LayoutTrackNumber>,
        versions: ValidationVersions,
    ): LayoutGeocodingContextCacheKey? = geocodingDao.getLayoutGeocodingContextCacheKey(trackNumberId, versions)

    fun getGeocodingContextCacheKey(
        branch: LayoutBranch,
        trackNumberId: IntId<LayoutTrackNumber>,
        moment: Instant,
    ): LayoutGeocodingContextCacheKey? = geocodingDao.getLayoutGeocodingContextCacheKey(branch, trackNumberId, moment)
}
