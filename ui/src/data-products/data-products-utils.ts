import { GeometryPlanHeader, PlanSource } from 'geometry/geometry-model';
import { isNilOrBlank } from 'utils/string-utils';
import { getGeometryPlanHeadersBySearchTerms } from 'geometry/geometry-api';
import { debounceAsync } from 'utils/async-utils';
import { getLocationTracksBySearchTerm } from 'track-layout/layout-location-track-api';
import { LayoutLocationTrack } from 'track-layout/track-layout-model';
import { CoordinateSystem, Srid } from 'common/common-model';

export const searchGeometryPlanHeaders = async (
    source: PlanSource,
    searchTerm: string,
): Promise<GeometryPlanHeader[]> => {
    if (isNilOrBlank(searchTerm)) {
        return Promise.resolve([]);
    }

    const t = await getGeometryPlanHeadersBySearchTerms(
        100,
        undefined,
        undefined,
        [source],
        [],
        searchTerm,
    );
    return t.planHeaders.items;
};

export function getPlanFullName(plan: GeometryPlanHeader): string {
    return `${plan.name} (${plan.fileName})`;
}

export const getGeometryPlanOptions = (
    headers: GeometryPlanHeader[],
    selectedHeader: GeometryPlanHeader | undefined,
) =>
    headers
        .filter((plan) => !selectedHeader || plan.id !== selectedHeader.id)
        .map((plan) => ({
            name: getPlanFullName(plan),
            value: plan,
            qaId: `plan-${plan.fileName}`,
        }));

export const debouncedGetGeometryPlanHeaders = debounceAsync(searchGeometryPlanHeaders, 250);

export const debouncedSearchTracks = debounceAsync(getLocationTracksBySearchTerm, 250);

export const getLocationTrackOptions = (tracks: LayoutLocationTrack[]) =>
    tracks.map((lt) => ({
        name: `${lt.name}, ${lt.description}`,
        value: lt,
        qaId: `location-track-${lt.id}`,
    }));

export type ElementHeading = {
    name: string;
    numeric: boolean;
    hasSeparator: boolean;
    qaId?: string;
};

export function numericHeading(
    name: string,
    qaId?: string,
): { name: string; numeric: true; hasSeparator: false; qaId?: string } {
    return { name, numeric: true, hasSeparator: false, qaId };
}

export function nonNumericHeading(
    name: string,
    qaId?: string,
): { name: string; numeric: false; hasSeparator: false; qaId?: string } {
    return { name, numeric: false, hasSeparator: false, qaId };
}

export const withSeparator = (heading: ElementHeading) => ({ ...heading, hasSeparator: true });

export type ElementHeadingWithClassName = ElementHeading & { className: string };
export const withClassName = (
    heading: ElementHeading,
    className: string,
): ElementHeadingWithClassName => ({ ...heading, className });

export const findCoordinateSystem = (srid: Srid, coordinateSystems: CoordinateSystem[]) =>
    coordinateSystems.find((crs) => crs.srid === srid);
