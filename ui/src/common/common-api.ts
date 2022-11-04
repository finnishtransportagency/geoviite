import { API_URI, getThrowError } from 'api/api-fetch';
import {
    CoordinateSystem,
    Srid,
    SwitchOwner,
    SwitchStructure,
    SwitchStructureId,
} from 'common/common-model';
import { asyncCache } from 'cache/cache';
import { BoundingBox, Point } from 'model/geometry';

const GEOGRAPHY_URI = `${API_URI}/geography`;
const SWITCH_LIBRARY_URI = `${API_URI}/switch-library`;
const coordinateSystemCache = asyncCache<Srid, CoordinateSystem>();
const sridModelCache = asyncCache<undefined, CoordinateSystem[]>();
const switchStructureCache = asyncCache<undefined, SwitchStructure[]>();
const switchOwnerCache = asyncCache<undefined, SwitchOwner[]>();

export function bboxString(bbox: BoundingBox): string {
    return `${bbox.x.min}_${bbox.x.max}_${bbox.y.min}_${bbox.y.max}`;
}

export function pointString(point: Point): string {
    return `${point.x}_${point.y}`;
}

export async function getCoordinateSystem(srid: Srid): Promise<CoordinateSystem> {
    return coordinateSystemCache.getImmutable(srid, () =>
        getThrowError<CoordinateSystem>(`${GEOGRAPHY_URI}/coordinate-systems/${srid}`),
    );
}

export async function getSridList(): Promise<CoordinateSystem[]> {
    return sridModelCache.getImmutable(undefined, () =>
        getThrowError<CoordinateSystem[]>(`${GEOGRAPHY_URI}/coordinate-systems`),
    );
}

export async function getSwitchStructures(): Promise<SwitchStructure[]> {
    return switchStructureCache.getImmutable(undefined, () =>
        getThrowError<SwitchStructure[]>(`${SWITCH_LIBRARY_URI}/switch-structures`).then(
            (switchStructures) => switchStructures.sort((s1, s2) => s1.type.localeCompare(s2.type)),
        ),
    );
}

export async function getSwitchOwners(): Promise<SwitchOwner[]> {
    return switchOwnerCache.getImmutable(undefined, () =>
        getThrowError<SwitchOwner[]>(`${SWITCH_LIBRARY_URI}/switch-owners`).catch(() =>
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
