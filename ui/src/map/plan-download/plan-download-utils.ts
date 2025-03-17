import { AreaSelection, DownloadablePlan } from 'map/plan-download/plan-download-store';
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
import {
    AlignmentStartAndEnd,
    LayoutLocationTrack,
    LayoutTrackNumber,
    LayoutTrackNumberId,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import {
    getReferenceLineStartAndEnd,
    getTrackNumberReferenceLine,
} from 'track-layout/layout-reference-line-api';
import { getChangeTimes } from 'common/change-time-api';

export const filterPlans = (
    plans: DownloadablePlan[],
    selectedApplicabilities: PlanApplicability[],
): DownloadablePlan[] =>
    plans.filter(
        (plan) => !plan.applicability || selectedApplicabilities.includes(plan.applicability),
    );

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

    if (areaSelection.locationTrack)
        return await getPlansLinkedToLocationTrack(
            layoutContext,
            areaSelection.locationTrack,
            startKm,
            endKm,
        ).then((plans) => plans.map(toDownloadablePlan));
    else if (areaSelection.trackNumber)
        return await getPlansLinkedToTrackNumber(
            layoutContext,
            areaSelection.trackNumber,
            startKm,
            endKm,
        ).then((plans) => plans.map(toDownloadablePlan));
    else return [];
}

export async function fetchTrackNumberAndExtremities(
    trackNumberId: LayoutTrackNumberId | undefined,
    layoutContext: LayoutContext,
): Promise<{
    trackNumber: LayoutTrackNumber | undefined;
    startAndEnd: AlignmentStartAndEnd | undefined;
}> {
    const trackNumber = trackNumberId
        ? await getTrackNumberById(trackNumberId, layoutContext)
        : undefined;
    const referenceLine = trackNumber
        ? await getTrackNumberReferenceLine(trackNumber.id, layoutContext)
        : undefined;
    const startAndEnd = referenceLine
        ? await getReferenceLineStartAndEnd(referenceLine.id, layoutContext)
        : undefined;
    return { trackNumber, startAndEnd };
}

export async function fetchLocationTrackAndExtremities(
    locationTrackId: LocationTrackId | undefined,
    layoutContext: LayoutContext,
): Promise<{
    startAndEnd: AlignmentStartAndEnd | undefined;
    locationTrack: LayoutLocationTrack | undefined;
}> {
    const locationTrack = locationTrackId
        ? await getLocationTrack(locationTrackId, layoutContext)
        : undefined;
    const startAndEnd = locationTrack
        ? await getLocationTrackStartAndEnd(
              locationTrack.id,
              layoutContext,
              getChangeTimes().layoutLocationTrack,
          )
        : undefined;
    return { locationTrack, startAndEnd };
}
