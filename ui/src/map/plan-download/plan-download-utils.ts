import { DownloadablePlan } from 'map/plan-download/plan-download-slice';
import { PlanApplicability } from 'geometry/geometry-model';
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
