import {
    AlignmentStartAndEnd,
    LayoutLocationTrack,
    LayoutLocationTrackDuplicate,
    LayoutReferenceLine,
    LayoutSwitch,
    LayoutSwitchId,
    LayoutTrackNumber,
    LayoutTrackNumberId,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { useLoader } from 'utils/react-utils';
import {
    getLocationTrack,
    getLocationTrackChangeTimes,
    getLocationTrackDuplicates,
    getLocationTrackStartAndEnd,
    getReferenceLine,
    getReferenceLineChangeTimes,
    getReferenceLineStartAndEnd,
    getSwitch,
    getTrackNumberById,
    getTrackNumberReferenceLine,
    getTrackNumbers,
} from 'track-layout/track-layout-api';
import { ChangeTimes, CoordinateSystem, PublishType, Srid, TimeStamp } from 'common/common-model';
import { getCoordinateSystem } from 'common/common-api';
import { GeometryPlanHeader, GeometryPlanId } from 'geometry/geometry-model';
import { getGeometryPlanHeader } from 'geometry/geometry-api';

export function useTrackNumberReferenceLine(
    trackNumberId: LayoutTrackNumberId | undefined,
    publishType: PublishType,
    changeTime?: TimeStamp,
): LayoutReferenceLine | undefined {
    return useLoader(
        () =>
            trackNumberId
                ? getTrackNumberReferenceLine(trackNumberId, publishType, changeTime)
                : undefined,
        [trackNumberId, publishType, changeTime],
    );
}

export function useReferenceLine(
    id: ReferenceLineId | undefined,
    publishType: PublishType,
    changeTime?: TimeStamp,
): LayoutReferenceLine | undefined {
    return useLoader(
        () => (id ? getReferenceLine(id, publishType, changeTime) : undefined),
        [id, publishType, changeTime],
    );
}

export function useLocationTrackDuplicates(
    id: LocationTrackId | undefined,
    publishType: PublishType,
): LayoutLocationTrackDuplicate[] | undefined {
    return useLoader(() => (id ? getLocationTrackDuplicates(publishType, id) : undefined), [id]);
}

export function useLocationTrack(
    id: LocationTrackId | undefined,
    publishType: PublishType,
    changeTime?: TimeStamp,
): LayoutLocationTrack | undefined {
    return useLoader(
        () => (id ? getLocationTrack(id, publishType, changeTime) : undefined),
        [id, publishType, changeTime],
    );
}

export function useSwitch(
    id: LayoutSwitchId | undefined,
    publishType: PublishType,
): LayoutSwitch | undefined {
    return useLoader(() => (id ? getSwitch(id, publishType) : undefined), [id, publishType]);
}

export function useTrackNumber(
    publishType: PublishType,
    id: LayoutTrackNumberId | undefined | null,
): LayoutTrackNumber | undefined {
    return useLoader(
        () => (id ? getTrackNumberById(id, publishType) : undefined),
        [id, publishType],
    );
}

export function useTrackNumbers(
    publishType: PublishType,
    changeTime?: TimeStamp,
): LayoutTrackNumber[] | undefined {
    return useLoader(() => getTrackNumbers(publishType, changeTime), [publishType, changeTime]);
}

export function useReferenceLineStartAndEnd(
    id: ReferenceLineId | undefined,
    publishType: PublishType | undefined,
): AlignmentStartAndEnd | undefined {
    return useLoader(
        () => (id && publishType ? getReferenceLineStartAndEnd(id, publishType) : undefined),
        [id, publishType],
    );
}

export function useLocationTrackStartAndEnd(
    id: LocationTrackId | undefined,
    publishType: PublishType | undefined,
): AlignmentStartAndEnd | undefined {
    return useLoader(
        () => (id && publishType ? getLocationTrackStartAndEnd(id, publishType) : undefined),
        [id, publishType],
    );
}

export function usePlanHeader(
    id: GeometryPlanId | null | undefined,
): GeometryPlanHeader | undefined {
    return useLoader(() => (id ? getGeometryPlanHeader(id) : undefined), [id]);
}

export function useReferenceLineChangeTimes(
    id: ReferenceLineId | undefined,
): ChangeTimes | undefined {
    return useLoader(() => (id ? getReferenceLineChangeTimes(id) : undefined), [id]);
}

export function useLocationTrackChangeTimes(
    id: LocationTrackId | undefined,
): ChangeTimes | undefined {
    return useLoader(() => (id ? getLocationTrackChangeTimes(id) : undefined), [id]);
}

export function useCoordinateSystem(srid: Srid): CoordinateSystem | undefined {
    return useLoader(() => getCoordinateSystem(srid), [srid]);
}
