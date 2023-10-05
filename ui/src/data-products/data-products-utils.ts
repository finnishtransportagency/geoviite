import { GeometryPlanHeader, PlanSource } from 'geometry/geometry-model';
import { isNilOrBlank } from 'utils/string-utils';
import { getGeometryPlanHeadersBySearchTerms } from 'geometry/geometry-api';
import { debounceAsync } from 'utils/async-utils';
import { ValidationError } from 'utils/validation-utils';
import { getLocationTracksBySearchTerm } from 'track-layout/layout-location-track-api';
import { LayoutLocationTrack, LocationTrackDescription } from 'track-layout/track-layout-model';
import { CoordinateSystem, Srid } from 'common/common-model';

export const searchGeometryPlanHeaders = async (
    source: PlanSource,
    searchTerm: string,
): Promise<GeometryPlanHeader[]> => {
    if (isNilOrBlank(searchTerm)) {
        return Promise.resolve([]);
    }

    const t = await getGeometryPlanHeadersBySearchTerms(
        10,
        undefined,
        undefined,
        [source],
        [],
        searchTerm,
    );
    return t.items;
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
    descriptions: LocationTrackDescription[],
    selectedTrack: LayoutLocationTrack | undefined,
) =>
    tracks
        .filter((lt) => !selectedTrack || lt.id !== selectedTrack.id)
        .map((lt) => ({
            name: `${lt.name}, ${
                descriptions.find((desc) => desc.id == lt.id)?.description ?? '-'
            }`,
            value: lt,
        }));

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

export const findCoordinateSystem = (srid: Srid, coordinateSystems: CoordinateSystem[]) =>
    coordinateSystems.find((crs) => crs.srid === srid);
