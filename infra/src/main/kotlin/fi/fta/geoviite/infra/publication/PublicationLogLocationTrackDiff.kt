package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.geocoding.AddressAndM
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.localization.Translation
import fi.fta.geoviite.infra.math.roundTo1Decimal
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackOwner
import fi.fta.geoviite.infra.tracklayout.OperationalPoint
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import fi.fta.geoviite.infra.tracklayout.TrackNumberAndChangeTime
import java.time.Instant

private data class LocationTrackEndpointAddresses(
    val oldStart: AddressAndM?,
    val oldEnd: AddressAndM?,
    val newStart: AddressAndM?,
    val newEnd: AddressAndM?,
)

private fun resolveEndpointAddresses(
    locationTrackChanges: LocationTrackChanges,
    previousPublicationTime: Instant,
    publicationTime: Instant,
    getGeocodingContext: (IntId<LayoutTrackNumber>, Instant) -> GeocodingContext<ReferenceLineM>?,
): LocationTrackEndpointAddresses =
    LocationTrackEndpointAddresses(
        oldStart =
            locationTrackChanges.startPoint.old?.let { pt ->
                locationTrackChanges.trackNumberId.old?.let {
                    getGeocodingContext(it, previousPublicationTime)?.getAddressAndM(pt)
                }
            },
        oldEnd =
            locationTrackChanges.endPoint.old?.let { pt ->
                locationTrackChanges.trackNumberId.old?.let {
                    getGeocodingContext(it, previousPublicationTime)?.getAddressAndM(pt)
                }
            },
        newStart =
            locationTrackChanges.startPoint.new?.let { pt ->
                getGeocodingContext(locationTrackChanges.trackNumberId.new, publicationTime)?.getAddressAndM(pt)
            },
        newEnd =
            locationTrackChanges.endPoint.new?.let { pt ->
                getGeocodingContext(locationTrackChanges.trackNumberId.new, publicationTime)?.getAddressAndM(pt)
            },
    )

fun diffLocationTrack(
    translation: Translation,
    locationTrackChanges: LocationTrackChanges,
    referenceChanges: PublicationReferencedAssetSetChanges,
    switchVersionLookup: (LayoutRowVersion<LayoutSwitch>) -> LayoutSwitch,
    operationalPointVersionLookup: (LayoutRowVersion<OperationalPoint>) -> OperationalPoint,
    branch: LayoutBranch,
    publicationTime: Instant,
    previousPublicationTime: Instant,
    trackNumberCache: List<TrackNumberAndChangeTime>,
    changedKmNumbers: Set<KmNumber>,
    switchOids: Map<IntId<LayoutSwitch>, Oid<LayoutSwitch>>,
    operationalPointOids: Map<IntId<OperationalPoint>, Oid<OperationalPoint>>,
    getGeocodingContext: (IntId<LayoutTrackNumber>, Instant) -> GeocodingContext<ReferenceLineM>?,
    getOwners: () -> List<LocationTrackOwner>,
    getOfficialAtMoment: (LayoutBranch, IntId<LocationTrack>, Instant) -> LocationTrack?,
): List<PublicationChange<*>> {
    val oldAndTime = locationTrackChanges.duplicateOf.old to previousPublicationTime
    val newAndTime = locationTrackChanges.duplicateOf.new to publicationTime
    val addresses =
        resolveEndpointAddresses(locationTrackChanges, oldAndTime.second, newAndTime.second, getGeocodingContext)

    val switchLinkChanges = referenceChanges.locationTrackSwitches[locationTrackChanges.id]
    val operationalPointLinkChanges = referenceChanges.locationTrackOperationalPoints[locationTrackChanges.id]

    return listOfNotNull(
        compareChangeValues(locationTrackChanges.oid, { it }, PropKey("oid")),
        compareChangeValues(
            locationTrackChanges.trackNumberId,
            { tnIdFromChange ->
                trackNumberCache.findLast { tn -> tn.id == tnIdFromChange && tn.changeTime <= publicationTime }?.number
            },
            PropKey("track-number"),
        ),
        compareChangeValues(locationTrackChanges.name, { it }, PropKey("location-track")),
        compareChangeValues(
            locationTrackChanges.namingScheme,
            { it },
            PropKey("location-track-naming-scheme"),
            null,
            "LocationTrackNamingScheme",
        ),
        compareChangeValues(locationTrackChanges.state, { it }, PropKey("state"), null, "LocationTrackState"),
        compareChangeValues(
            locationTrackChanges.type,
            { it },
            PropKey("location-track-type"),
            null,
            "LocationTrackType",
        ),
        compareChangeValues(locationTrackChanges.description, { it }, PropKey("description")),
        compareChangeValues(locationTrackChanges.descriptionBase, { it }, PropKey("description-base")),
        compareChangeValues(
            locationTrackChanges.descriptionSuffix,
            { it },
            PropKey("description-suffix"),
            enumLocalizationKey = "LocationTrackDescriptionSuffix",
        ),
        compareChangeValues(
            locationTrackChanges.owner,
            { getOwners().find { owner -> owner.id == it }?.name },
            PropKey("owner"),
        ),
        compareChange(
            { oldAndTime.first != newAndTime.first },
            oldAndTime,
            newAndTime,
            { (duplicateOf, timestamp) -> duplicateOf?.let { getOfficialAtMoment(branch, it, timestamp)?.name } },
            PropKey("duplicate-of"),
        ),
        compareLength(
            locationTrackChanges.length.old,
            locationTrackChanges.length.new,
            DISTANCE_CHANGE_THRESHOLD,
            ::roundTo1Decimal,
            PropKey("length"),
            getLengthChangedRemarkOrNull(translation, locationTrackChanges.length.old, locationTrackChanges.length.new),
        ),
        compareChange(
            { !pointsAreSame(locationTrackChanges.startPoint.old, locationTrackChanges.startPoint.new) },
            locationTrackChanges.startPoint.old,
            locationTrackChanges.startPoint.new,
            ::formatLocation,
            PropKey("start-location"),
            getPointMovedRemarkOrNull(
                translation,
                locationTrackChanges.startPoint.old,
                locationTrackChanges.startPoint.new,
            ),
        ),
        compareChange(
            { addresses.oldStart?.address != addresses.newStart?.address },
            addresses.oldStart?.address,
            addresses.newStart?.address,
            { it.toString() },
            PropKey("start-address"),
            null,
        ),
        compareChange(
            { !pointsAreSame(locationTrackChanges.endPoint.old, locationTrackChanges.endPoint.new) },
            locationTrackChanges.endPoint.old,
            locationTrackChanges.endPoint.new,
            ::formatLocation,
            PropKey("end-location"),
            getPointMovedRemarkOrNull(translation, locationTrackChanges.endPoint.old, locationTrackChanges.endPoint.new),
        ),
        compareChange(
            { addresses.oldEnd?.address != addresses.newEnd?.address },
            addresses.oldEnd?.address,
            addresses.newEnd?.address,
            { it.toString() },
            PropKey("end-address"),
            null,
        ),
        if (changedKmNumbers.isNotEmpty()) {
            PublicationChange(
                PropKey("geometry"),
                ChangeValue(null, null),
                getKmNumbersChangedRemarkOrNull(
                    translation,
                    changedKmNumbers,
                    locationTrackChanges.geometryChangeSummaries,
                ),
            )
        } else {
            null
        },
        if (switchLinkChanges == null) {
            null
        } else {
            compareChange(
                { switchLinkChanges[ChangeSide.OLD] != switchLinkChanges[ChangeSide.NEW] },
                null,
                null,
                { it },
                PropKey("linked-switches"),
                getSwitchLinksChangedRemark(translation, switchLinkChanges, switchVersionLookup, switchOids::getValue),
            )
        },
        if (operationalPointLinkChanges == null) {
            null
        } else {
            compareChange(
                { operationalPointLinkChanges[ChangeSide.OLD] != operationalPointLinkChanges[ChangeSide.NEW] },
                null,
                null,
                { it },
                PropKey("linked-operational-points"),
                getOperationalPointLinksChangedRemark(
                    translation,
                    operationalPointLinkChanges,
                    operationalPointVersionLookup,
                    operationalPointOids::getValue,
                ),
            )
        },
    )
}
