import { API_URI, getNonNull } from 'api/api-fetch';
import {
    CoordinateSystem, LocationTrackOwner,
    Srid,
    SwitchOwner,
    SwitchStructure,
    SwitchStructureId,
} from 'common/common-model';
import { asyncCache } from 'cache/cache';
import { BoundingBox, Point } from 'model/geometry';

const GEOGRAPHY_URI = `${API_URI}/geography`;
const SWITCH_LIBRARY_URI = `${API_URI}/switch-library`;
const LOCATION_TRACK_URI = `${API_URI}//track-layout/location-tracks`;
const coordinateSystemCache = asyncCache<Srid, CoordinateSystem>();
const sridModelCache = asyncCache<undefined, CoordinateSystem[]>();
const switchStructureCache = asyncCache<undefined, SwitchStructure[]>();
const switchOwnerCache = asyncCache<undefined, SwitchOwner[]>();
const locationTrackOwnerCache = asyncCache<undefined, LocationTrackOwner[]>();

export function bboxString(bbox: BoundingBox): string {
    return `${bbox.x.min}_${bbox.x.max}_${bbox.y.min}_${bbox.y.max}`;
}

export function pointString(point: Point): string {
    return `${point.x}_${point.y}`;
}

export async function getCoordinateSystem(srid: Srid): Promise<CoordinateSystem> {
    return coordinateSystemCache.getImmutable(srid, () =>
        getNonNull<CoordinateSystem>(`${GEOGRAPHY_URI}/coordinate-systems/${srid}`),
    );
}

export async function getSridList(): Promise<CoordinateSystem[]> {
    return sridModelCache.getImmutable(undefined, () =>
        getNonNull<CoordinateSystem[]>(`${GEOGRAPHY_URI}/coordinate-systems`),
    );
}

export async function getSwitchStructures(): Promise<SwitchStructure[]> {
    return switchStructureCache.getImmutable(undefined, () =>
        getNonNull<SwitchStructure[]>(`${SWITCH_LIBRARY_URI}/switch-structures`).then(
            (switchStructures) => switchStructures.sort((s1, s2) => s1.type.localeCompare(s2.type)),
        ),
    );
}

export async function getSwitchOwners(): Promise<SwitchOwner[]> {
    return switchOwnerCache.getImmutable(undefined, () =>
        getNonNull<SwitchOwner[]>(`${SWITCH_LIBRARY_URI}/switch-owners`).catch(() =>
            Promise.reject('failed to fetch switch owners'),
        ),
    );
}

export async function getSwitchStructure(
    id: SwitchStructureId,
): Promise<SwitchStructure | undefined> {
    return getSwitchStructures().then((switchStructures) =>
        switchStructures.find((s) => s.id === id),
    );
}
export async function getLocationTrackOwners(): Promise<SwitchOwner[]> {
    return locationTrackOwnerCache.getImmutable(undefined, () =>
        getNonNull<LocationTrackOwner[]>(`${LOCATION_TRACK_URI}/location-track-owners`).catch(() =>
            Promise.reject('failed to fetch location track owners'),
        ),
    );
}
