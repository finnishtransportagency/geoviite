import { GeometryPlanHeader, PlanSource } from 'geometry/geometry-model';
import { isNullOrBlank } from 'utils/string-utils';
import { getGeometryPlanHeadersBySearchTerms } from 'geometry/geometry-api';
import { debounceAsync } from 'utils/async-utils';
import { ValidationError } from 'utils/validation-utils';

export const searchGeometryPlanHeaders = (
    source: PlanSource,
    searchTerm: string,
): Promise<GeometryPlanHeader[]> => {
    if (isNullOrBlank(searchTerm)) {
        return Promise.resolve([]);
    }

    return getGeometryPlanHeadersBySearchTerms(
        10,
        undefined,
        undefined,
        [source],
        [],
        searchTerm,
    ).then((t) => t.items);
};

export const getGeometryPlanOptions = (
    headers: GeometryPlanHeader[],
    selectedHeader: GeometryPlanHeader | undefined,
) =>
    headers
        .filter((plan) => !selectedHeader || plan.id !== selectedHeader.id)
        .map((plan) => ({ name: plan.fileName, value: plan }));

export const debouncedGetGeometryPlanHeaders = debounceAsync(searchGeometryPlanHeaders, 250);

export function getVisibleErrorsByProp<T>(
    committedFields: (keyof T)[],
    validationErrors: ValidationError<T>[],
    prop: keyof T,
): string[] {
    return committedFields.includes(prop)
        ? validationErrors.filter((error) => error.field == prop).map((error) => error.reason)
        : [];
}
