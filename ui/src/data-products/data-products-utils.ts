import { GeometryPlanHeader, PlanSource } from 'geometry/geometry-model';
import { isNullOrBlank } from 'utils/string-utils';
import { getGeometryPlanHeadersBySearchTerms } from 'geometry/geometry-api';
import { debounceAsync } from 'utils/async-utils';
import { ValidationError } from 'utils/validation-utils';
import { getLocationTracksBySearchTerm } from 'track-layout/layout-location-track-api';
import { LayoutLocationTrack } from 'track-layout/track-layout-model';

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

export const debouncedSearchTracks = debounceAsync(getLocationTracksBySearchTerm, 250);

export const getLocationTrackOptions = (
    tracks: LayoutLocationTrack[],
    selectedTrack: LayoutLocationTrack | undefined,
) =>
    tracks
        .filter((lt) => !selectedTrack || lt.id !== selectedTrack.id)
        .map((lt) => ({ name: `${lt.name}, ${lt.description}`, value: lt }));

export function getVisibleErrorsByProp<T>(
    committedFields: (keyof T)[],
    validationErrors: ValidationError<T>[],
    prop: keyof T,
): string[] {
    return committedFields.includes(prop)
        ? validationErrors.filter((error) => error.field == prop).map((error) => error.reason)
        : [];
}

export const hasErrors = <T>(
    committedFields: (keyof T)[],
    validationErrors: ValidationError<T>[],
    prop: keyof T,
) => getVisibleErrorsByProp(committedFields, validationErrors, prop).length > 0;

export type ElementHeading = {
    name: string;
    numeric: boolean;
    hasSeparator: boolean;
};

export const numericHeading = (name: string) => ({
    name,
    numeric: true,
    hasSeparator: false,
});

export const nonNumericHeading = (name: string) => ({
    name,
    numeric: false,
    hasSeparator: false,
});

export const withSeparator = (heading: ElementHeading) => ({ ...heading, hasSeparator: true });
