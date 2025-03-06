import { DownloadablePlan } from 'map/plan-download/plan-download-store';
import { KmNumberRange, PlanApplicability } from 'geometry/geometry-model';
import { compareKmNumberStrings } from 'common/common-model';

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
    else {
        return compareKmNumberStrings(a.kmNumberRange.min, b.kmNumberRange.min);
    }
};

export const isKmNumberWithinAlignment = (
    kmNumber: string,
    kmNumberRange: KmNumberRange,
): boolean =>
    compareKmNumberStrings(kmNumber, kmNumberRange.min) >= 0 &&
    compareKmNumberStrings(kmNumber, kmNumberRange.max) <= 0;
