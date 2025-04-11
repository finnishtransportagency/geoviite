import {
    AreaSelection,
    DownloadablePlan,
    PlanDownloadAssetAndExtremities,
    PlanDownloadAssetId,
    PlanDownloadAssetType,
    TrackNumberAssetAndExtremities,
} from 'map/plan-download/plan-download-store';
import { GeometryPlanHeader, PlanApplicability } from 'geometry/geometry-model';
import { compareKmNumberStrings, kmNumberIsValid, LayoutContext } from 'common/common-model';
import { expectDefined } from 'utils/type-utils';
import {
    getLocationTrack,
    getLocationTrackStartAndEnd,
    getPlansLinkedToLocationTrack,
} from 'track-layout/layout-location-track-api';
import {
    getPlansLinkedToTrackNumber,
    getTrackNumberById,
} from 'track-layout/layout-track-number-api';
import { LayoutTrackNumberId, LocationTrackId } from 'track-layout/track-layout-model';
import {
    getReferenceLineStartAndEnd,
    getTrackNumberReferenceLine,
} from 'track-layout/layout-reference-line-api';
import { getChangeTimes } from 'common/change-time-api';

export const filterPlans = (
    plans: DownloadablePlan[],
    selectedApplicabilities: PlanApplicability[],
    includePaikannuspalvelu: boolean,
): DownloadablePlan[] =>
    plans
        .filter(
            (plan) => !plan.applicability || selectedApplicabilities.includes(plan.applicability),
        )
        .filter((plan) => includePaikannuspalvelu || plan.source !== 'PAIKANNUSPALVELU');

export const comparePlans = (a: DownloadablePlan, b: DownloadablePlan): number => {
    if (!a.applicability && b.applicability) return -1;
    else if (a.applicability && !b.applicability) return 1;
    else if (!a.kmNumberRange && b.kmNumberRange) return -1;
    else if (a.kmNumberRange && !b.kmNumberRange) return 1;
    else if (!a.kmNumberRange && !b.kmNumberRange) return 0;
    else {
        return compareKmNumberStrings(
            expectDefined(a.kmNumberRange).min,
            expectDefined(b.kmNumberRange).min,
        );
    }
};

export const toDownloadablePlan = (planHeader: GeometryPlanHeader): DownloadablePlan => ({
    id: planHeader.id,
    name: planHeader.fileName,
    applicability: planHeader.planApplicability,
    source: planHeader.source,
    kmNumberRange: planHeader.kmNumberRange,
});

export async function fetchDownloadablePlans(
    areaSelection: AreaSelection,
    layoutContext: LayoutContext,
): Promise<DownloadablePlan[]> {
    const startKm = kmNumberIsValid(areaSelection.startTrackMeter)
        ? areaSelection.startTrackMeter
        : undefined;
    const endKm = kmNumberIsValid(areaSelection.endTrackMeter)
        ? areaSelection.endTrackMeter
        : undefined;

    if (areaSelection.asset?.type === 'LOCATION_TRACK') {
        return await getPlansLinkedToLocationTrack(
            layoutContext,
            areaSelection.asset.id,
            startKm,
            endKm,
        ).then((plans) => plans.map(toDownloadablePlan));
    } else if (areaSelection.asset?.type === 'TRACK_NUMBER') {
        return await getPlansLinkedToTrackNumber(
            layoutContext,
            areaSelection.asset.id,
            startKm,
            endKm,
        ).then((plans) => plans.map(toDownloadablePlan));
    } else {
        return [];
    }
}

const fetchTrackNumberAndExtremities = async (
    trackNumberId: LayoutTrackNumberId,
    layoutContext: LayoutContext,
): Promise<TrackNumberAssetAndExtremities | undefined> => {
    const trackNumber = await getTrackNumberById(trackNumberId, layoutContext);
    const referenceLine = trackNumber
        ? await getTrackNumberReferenceLine(trackNumber.id, layoutContext)
        : undefined;
    const startAndEnd = referenceLine
        ? await getReferenceLineStartAndEnd(referenceLine.id, layoutContext)
        : undefined;
    return trackNumber && startAndEnd
        ? { type: PlanDownloadAssetType.TRACK_NUMBER, asset: trackNumber, startAndEnd }
        : undefined;
};

const fetchLocationTrackAndExtremities = async (
    locationTrackId: LocationTrackId,
    layoutContext: LayoutContext,
): Promise<PlanDownloadAssetAndExtremities | undefined> => {
    const locationTrack = await getLocationTrack(locationTrackId, layoutContext);
    const startAndEnd = locationTrack
        ? await getLocationTrackStartAndEnd(
              locationTrack.id,
              layoutContext,
              getChangeTimes().layoutLocationTrack,
          )
        : undefined;
    return locationTrack && startAndEnd
        ? { type: PlanDownloadAssetType.LOCATION_TRACK, asset: locationTrack, startAndEnd }
        : undefined;
};

export const fetchAssetAndExtremities = async (
    asset: PlanDownloadAssetId,
    layoutContext: LayoutContext,
): Promise<PlanDownloadAssetAndExtremities | undefined> =>
    asset.type === 'TRACK_NUMBER'
        ? fetchTrackNumberAndExtremities(asset.id, layoutContext)
        : fetchLocationTrackAndExtremities(asset.id, layoutContext);
