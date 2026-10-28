package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.RatkoExternalId
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geocoding.Resolution
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.ratko.IExternalIdDao
import fi.fta.geoviite.infra.tracklayout.GeocodingAlignmentM
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutAssetReader
import fi.fta.geoviite.infra.tracklayout.LayoutDesign
import fi.fta.geoviite.infra.tracklayout.LayoutDesignService
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import java.time.Instant
import kotlin.reflect.KClass

inline fun <reified T : LayoutAsset<T>> idLookup(dao: IExternalIdDao<T>, oid: Oid<T>): IntId<T> =
    dao.lookupByExternalId(oid)?.id ?: throwOidTargetNotFound(oid)

fun branchByDesignOid(layoutDesignService: LayoutDesignService, oid: ExtOidV1<LayoutDesign>?): LayoutBranch =
    if (oid == null) LayoutBranch.main
    else {
        LayoutBranch.design((layoutDesignService.getByOid(oid.value) ?: throwOidTargetNotFound(oid.value)).id as IntId)
    }

/**
 * The versions of the assets an ext API response lists for the branch. The main branch lists its official assets; a
 * design branch lists only the design's own rows, in any design asset state, with no fallback to main.
 */
fun <T : LayoutAsset<T>> fetchExtVersionsAtMoment(
    dao: LayoutAssetReader<T>,
    branch: LayoutBranch,
    moment: Instant,
): List<LayoutRowVersion<T>> =
    when (branch) {
        is MainBranch -> dao.fetchAllOfficialVersionsAtMoment(branch, moment)
        is DesignBranch -> dao.fetchDesignInternalVersionsAtMoment(branch, moment)
    }

/** Single-asset variant of [fetchExtVersionsAtMoment]. */
fun <T : LayoutAsset<T>> fetchExtVersionAtMoment(
    dao: LayoutAssetReader<T>,
    branch: LayoutBranch,
    id: IntId<T>,
    moment: Instant,
): LayoutRowVersion<T>? =
    when (branch) {
        is MainBranch -> dao.fetchOfficialVersionAtMoment(branch, id, moment)
        is DesignBranch -> dao.fetchDesignInternalVersionAtMoment(branch, id, moment)
    }

/** Variant of [fetchExtVersionsAtMoment] limited to the given assets. */
fun <T : LayoutAsset<T>> fetchManyExtVersionsAtMoment(
    dao: LayoutAssetReader<T>,
    branch: LayoutBranch,
    ids: List<IntId<T>>,
    moment: Instant,
): List<LayoutRowVersion<T>> =
    when (branch) {
        is MainBranch -> dao.fetchManyOfficialVersionsAtMoment(branch, ids, moment)
        is DesignBranch -> dao.fetchManyDesignInternalVersionsAtMoment(branch, ids, moment)
    }

/**
 * The geocoding context for a track number version from [fetchExtVersionAtMoment]. Km posts come from the branch's
 * context at the moment, and the track number's own row gives the reference line, so a design row that the design
 * context hides (cancelled or completed) still geocodes with its own geometry. Null when the branch's context has no
 * geocoding context for the track number.
 */
fun getExtGeocodingContextAtMoment(
    geocodingService: GeocodingService,
    branch: LayoutBranch,
    trackNumberVersion: LayoutRowVersion<LayoutTrackNumber>,
    moment: Instant,
): GeocodingContext<ReferenceLineM>? =
    geocodingService
        .getGeocodingContextCacheKey(branch, trackNumberVersion.id, moment)
        ?.copy(trackNumberVersion = trackNumberVersion)
        ?.let(geocodingService::getGeocodingContext)

/**
 * The OIDs to report for an asset: its OID in the requested branch, plus its main-branch OID separately when the
 * requested branch is a design branch (null when the requested branch is the main branch itself).
 */
data class BranchOidsV1<T : LayoutAsset<T>>(val oid: Oid<T>, val officialOid: Oid<T>?)

/**
 * Resolves the OIDs to report for an asset looked up by [requestOid]. An asset with no OID in a design branch is not
 * part of the design's externally published state, and is reported as not found.
 */
inline fun <reified T : LayoutAsset<T>> branchOids(
    dao: IExternalIdDao<T>,
    branch: LayoutBranch,
    requestOid: Oid<T>,
    id: IntId<T>,
): BranchOidsV1<T> {
    val oids = dao.fetchExternalIdsByBranch(id)
    val officialOid = oids[LayoutBranch.main]?.oid
    return if (branch == LayoutBranch.main)
        BranchOidsV1(officialOid ?: throwOidNotFoundInBranch(requestOid, branch), null)
    else BranchOidsV1(oids[branch]?.oid ?: throwOidNotFoundInBranch(requestOid, branch), officialOid)
}

/** OID references from an ext API response to other assets (e.g. a location track's track number reference). */
class ExtOidReferencesV1<T : LayoutAsset<T>>(
    val assetType: KClass<T>,
    val branch: LayoutBranch,
    private val extIds: Map<IntId<T>, RatkoExternalId<T>>,
) {
    fun getOrNull(id: DomainId<T>): Oid<T>? = extIds[id]?.oid

    fun get(id: DomainId<T>): Oid<T> =
        getOrNull(id) ?: error("${assetType.simpleName} OID not found: branch=$branch id=$id")
}

/**
 * Resolves the OIDs by which to refer to other assets in an ext API response. Pass null [ids] to resolve references to
 * all assets of the type.
 */
inline fun <reified T : LayoutAsset<T>> oidReferences(
    dao: IExternalIdDao<T>,
    branch: LayoutBranch,
    ids: Collection<IntId<T>>? = null,
): ExtOidReferencesV1<T> = ExtOidReferencesV1(T::class, branch, dao.fetchExternalIdsWithInheritance(branch, ids))

/** Resolves the OID by which to refer to a single other asset in an ext API response. */
inline fun <reified T : LayoutAsset<T>> oidReference(
    dao: IExternalIdDao<T>,
    branch: LayoutBranch,
    id: IntId<T>,
): Oid<T> = oidReferences(dao, branch, listOf(id)).get(id)

fun coordinateSystem(extCoordinateSystem: ExtSridV1?): Srid = extCoordinateSystem?.value ?: LAYOUT_SRID

fun resolution(extResolution: ExtResolutionV1?): Resolution = extResolution?.toResolution() ?: Resolution.ONE_METER

inline fun <reified T : GeocodingAlignmentM<T>> IPoint?.toAddress(geocodingContext: GeocodingContext<T>): TrackMeter? =
    this?.let { point -> geocodingContext.getAddress(point, lenientExtrapolation = true)?.first }
