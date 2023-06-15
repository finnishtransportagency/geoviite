import {
    AlignmentStartAndEnd,
    LayoutKmPost,
    LayoutKmPostId,
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
import { LoaderStatus, useLoader, useLoaderWithStatus, useNullableLoader } from 'utils/react-utils';
import {
    ChangeTimes,
    CoordinateSystem,
    PublishType,
    Srid,
    SwitchStructure,
    SwitchStructureId,
    TimeStamp,
} from 'common/common-model';
import { getCoordinateSystem, getSwitchStructure } from 'common/common-api';
import { GeometryPlanHeader, GeometryPlanId } from 'geometry/geometry-model';
import { getGeometryPlanHeader } from 'geometry/geometry-api';
import {
    getReferenceLine,
    getReferenceLineChangeTimes,
    getReferenceLines,
    getReferenceLineStartAndEnd,
    getTrackNumberReferenceLine,
} from 'track-layout/layout-reference-line-api';
import {
    getLocationTrack,
    getLocationTrackChangeTimes,
    getLocationTrackDuplicates,
    getLocationTracks,
    getLocationTrackStartAndEnd,
} from 'track-layout/layout-location-track-api';
import { getSwitch, getSwitches } from 'track-layout/layout-switch-api';
import { getTrackNumberById, getTrackNumbers } from 'track-layout/layout-track-number-api';
import { getKmPost, getKmPosts } from 'track-layout/layout-km-post-api';
import { PVDocumentHeader, PVDocumentId } from 'infra-model/projektivelho/pv-model';
import { getPVDocument } from 'infra-model/infra-model-api';

export function useTrackNumberReferenceLine(
    trackNumberId: LayoutTrackNumberId | undefined,
    publishType: PublishType,
    changeTime?: TimeStamp,
): LayoutReferenceLine | undefined {
    return useNullableLoader(
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
    return useNullableLoader(
        () => (id ? getReferenceLine(id, publishType, changeTime) : undefined),
        [id, publishType, changeTime],
    );
}

export function useReferenceLines(
    ids: ReferenceLineId[],
    publishType: PublishType,
    changeTime?: TimeStamp,
): LayoutReferenceLine[] {
    return (
        useLoader(
            () => (ids ? getReferenceLines(ids, publishType, changeTime) : undefined),
            [ids, publishType, changeTime],
        ) || []
    );
}

export function useLocationTrackDuplicates(
    id: LocationTrackId | undefined,
    publishType: PublishType,
): LayoutLocationTrackDuplicate[] | undefined {
    return useLoader(
        () => (id ? getLocationTrackDuplicates(publishType, id) : undefined),
        [id, publishType],
    );
}

export function useLocationTrack(
    id: LocationTrackId | undefined,
    publishType: PublishType,
    changeTime?: TimeStamp,
): LayoutLocationTrack | undefined {
    return useNullableLoader(
        () => (id ? getLocationTrack(id, publishType, changeTime) : undefined),
        [id, publishType, changeTime],
    );
}

export function useLocationTracks(
    ids: LocationTrackId[],
    publishType: PublishType,
    changeTime?: TimeStamp,
): LayoutLocationTrack[] {
    return (
        useLoader(
            () => (ids ? getLocationTracks(ids, publishType, changeTime) : undefined),
            [ids, publishType, changeTime],
        ) || []
    );
}

export function useSwitch(
    id: LayoutSwitchId | undefined,
    publishType: PublishType,
    changeTime?: TimeStamp,
): LayoutSwitch | undefined {
    return useLoader(
        () => (id ? getSwitch(id, publishType, changeTime) : undefined),
        [id, publishType, changeTime],
    );
}

export function useSwitches(
    ids: LayoutSwitchId[] | undefined,
    publishType: PublishType,
    changeTime?: TimeStamp,
): LayoutSwitch[] {
    return (
        useLoader(
            () => (ids ? getSwitches(ids, publishType, changeTime) : undefined),
            [ids, publishType, changeTime],
        ) || []
    );
}

export function useSwitchStructure(id: SwitchStructureId | undefined): SwitchStructure | undefined {
    return useLoader(() => (id ? getSwitchStructure(id) : undefined), [id]);
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

export function useTrackNumberWithStatus(
    publishType: PublishType,
    id: LayoutTrackNumberId | undefined | null,
    changeTime: TimeStamp,
): [LayoutTrackNumber | undefined, LoaderStatus] {
    return useLoaderWithStatus(
        () => (id ? getTrackNumberById(id, publishType, changeTime) : undefined),
        [id, publishType, changeTime],
    );
}

export function useTrackNumbers(
    publishType: PublishType,
    changeTime?: TimeStamp,
): LayoutTrackNumber[] | undefined {
    return useLoader(() => getTrackNumbers(publishType, changeTime), [publishType, changeTime]);
}

export function useTrackNumbersIncludingDeleted(
    publishType: PublishType,
    changeTime?: TimeStamp,
): LayoutTrackNumber[] | undefined {
    return useLoader(
        () => getTrackNumbers(publishType, changeTime, true),
        [publishType, changeTime],
    );
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
    changeTime: TimeStamp,
): AlignmentStartAndEnd | undefined {
    return useLoader(
        () => (id && publishType ? getLocationTrackStartAndEnd(id, publishType) : undefined),
        [id, publishType, changeTime],
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
    return useNullableLoader(() => (id ? getReferenceLineChangeTimes(id) : undefined), [id]);
}

export function useLocationTrackChangeTimes(
    id: LocationTrackId | undefined,
): ChangeTimes | undefined {
    return useNullableLoader(() => (id ? getLocationTrackChangeTimes(id) : undefined), [id]);
}

export function useCoordinateSystem(srid: Srid): CoordinateSystem | undefined {
    return useLoader(() => getCoordinateSystem(srid), [srid]);
}

export function useKmPost(
    id: LayoutKmPostId | undefined,
    publishType: PublishType,
    changeTime?: TimeStamp,
): LayoutKmPost | undefined {
    return useNullableLoader(
        () => (id ? getKmPost(id, publishType, changeTime) : undefined),
        [id, publishType, changeTime],
    );
}

export function useKmPosts(
    ids: LayoutKmPostId[] | undefined,
    publishType: PublishType,
    changeTime?: TimeStamp,
): LayoutKmPost[] {
    return (
        useLoader(
            () => (ids ? getKmPosts(ids, publishType, changeTime) : undefined),
            [ids, publishType, changeTime],
        ) || []
    );
}

export function usePvDocumentHeader(
    id: PVDocumentId | undefined | null,
    changeTime?: TimeStamp,
): PVDocumentHeader | undefined {
    return useLoader(
        () => (id ? getPVDocument(changeTime, id).then((v) => v || undefined) : undefined),
        [id, changeTime],
    );
}
