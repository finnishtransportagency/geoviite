import {
    AlignmentStartAndEnd,
    LayoutKmPost,
    LayoutKmPostId,
    LayoutLocationTrack,
    LayoutReferenceLine,
    LayoutSwitch,
    LayoutSwitchId,
    LayoutTrackNumber,
    LayoutTrackNumberId,
    LocationTrackId,
    LocationTrackInfoboxExtras,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { LoaderStatus, useLoader, useLoaderWithStatus, useOptionalLoader } from 'utils/react-utils';
import {
    CoordinateSystem,
    DraftableChangeInfo,
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
    getLocationTrackInfoboxExtras,
    getLocationTracks,
    getLocationTracksByName,
    getLocationTrackStartAndEnd,
} from 'track-layout/layout-location-track-api';
import { getSwitch, getSwitchChangeTimes, getSwitches } from 'track-layout/layout-switch-api';
import {
    getTrackNumberById,
    getTrackNumberChangeTimes,
    getTrackNumbers,
} from 'track-layout/layout-track-number-api';
import { getKmPost, getKmPostChangeTimes, getKmPosts } from 'track-layout/layout-km-post-api';
import { PVDocumentHeader, PVDocumentId } from 'infra-model/projektivelho/pv-model';
import { getPVDocument } from 'infra-model/infra-model-api';
import { OnSelectFunction, OptionalUnselectableItemCollections } from 'selection/selection-model';
import {
    updateReferenceLineChangeTime,
    updateTrackNumberChangeTime,
    updateKmPostChangeTime,
    updateSwitchChangeTime,
    updateLocationTrackChangeTime,
} from 'common/change-time-api';
import { isNilOrBlank } from 'utils/string-utils';

export function useTrackNumberReferenceLine(
    trackNumberId: LayoutTrackNumberId | undefined,
    publishType: PublishType,
    changeTime?: TimeStamp,
): LayoutReferenceLine | undefined {
    return useOptionalLoader(
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
    return useOptionalLoader(
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

export function useLocationTrack(
    id: LocationTrackId | undefined,
    publishType: PublishType,
    changeTime?: TimeStamp,
): LayoutLocationTrack | undefined {
    return useOptionalLoader(
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
    return useOptionalLoader(
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
    id: LayoutTrackNumberId | undefined,
): LayoutTrackNumber | undefined {
    return useLoader(
        () => (id ? getTrackNumberById(id, publishType) : undefined),
        [id, publishType],
    );
}

export function useTrackNumberWithStatus(
    publishType: PublishType,
    id: LayoutTrackNumberId | undefined,
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
    changeTime: TimeStamp | undefined = undefined,
): AlignmentStartAndEnd | undefined {
    return useLoader(
        () => (id && publishType ? getReferenceLineStartAndEnd(id, publishType) : undefined),
        [id, publishType, changeTime],
    );
}

export function useLocationTrackStartAndEnd(
    id: LocationTrackId | undefined,
    publishType: PublishType | undefined,
    changeTime: TimeStamp,
): [AlignmentStartAndEnd | undefined, LoaderStatus] {
    return useLoaderWithStatus(
        () =>
            id && publishType
                ? getLocationTrackStartAndEnd(id, publishType, changeTime)
                : undefined,
        [id, publishType, changeTime],
    );
}

export function useLocationTrackInfoboxExtras(
    id: LocationTrackId | undefined,
    publishType: PublishType,
): [LocationTrackInfoboxExtras | undefined, LoaderStatus] {
    return useLoaderWithStatus(
        () => (id === undefined ? undefined : getLocationTrackInfoboxExtras(id, publishType)),
        [id, publishType],
    );
}
export function useConflictingTrack(
    trackNumberId: LayoutTrackNumberId | undefined,
    trackName: string | undefined,
    trackId: LocationTrackId | undefined,
    publishType: PublishType,
): LayoutLocationTrack | undefined {
    return useLoader(
        () =>
            trackNumberId === undefined || trackName === undefined || isNilOrBlank(trackName)
                ? undefined
                : getLocationTracksByName(trackNumberId, trackName, publishType).then((tracks) =>
                      tracks.find((t) => t.id !== trackId),
                  ),
        [trackNumberId, trackName, trackId, publishType],
    );
}

export function usePlanHeader(id: GeometryPlanId | undefined): GeometryPlanHeader | undefined {
    return useLoader(() => (id ? getGeometryPlanHeader(id) : undefined), [id]);
}

export function useTrackNumberChangeTimes(
    id: LayoutTrackNumberId | undefined,
): DraftableChangeInfo | undefined {
    return useOptionalLoader(() => (id ? getTrackNumberChangeTimes(id) : undefined), [id]);
}

export function useReferenceLineChangeTimes(
    id: ReferenceLineId | undefined,
): DraftableChangeInfo | undefined {
    return useOptionalLoader(() => (id ? getReferenceLineChangeTimes(id) : undefined), [id]);
}

export function useLocationTrackChangeTimes(
    id: LocationTrackId | undefined,
): DraftableChangeInfo | undefined {
    return useOptionalLoader(() => (id ? getLocationTrackChangeTimes(id) : undefined), [id]);
}

export function useSwitchChangeTimes(
    id: LayoutSwitchId | undefined,
): DraftableChangeInfo | undefined {
    return useOptionalLoader(() => (id ? getSwitchChangeTimes(id) : undefined), [id]);
}

export function useKmPostChangeTimes(
    id: LayoutKmPostId | undefined,
): DraftableChangeInfo | undefined {
    return useOptionalLoader(() => (id ? getKmPostChangeTimes(id) : undefined), [id]);
}

export function useCoordinateSystem(srid: Srid): CoordinateSystem | undefined {
    return useLoader(() => getCoordinateSystem(srid), [srid]);
}

export function useKmPost(
    id: LayoutKmPostId | undefined,
    publishType: PublishType,
    changeTime?: TimeStamp,
): LayoutKmPost | undefined {
    return useOptionalLoader(
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
    id: PVDocumentId | undefined,
    changeTime?: TimeStamp,
): PVDocumentHeader | undefined {
    return useLoader(
        () => (id ? getPVDocument(changeTime, id).then((v) => v || undefined) : undefined),
        [id, changeTime],
    );
}

export function refreshTrackNumberSelection(
    publicationState: PublishType,
    onSelect: OnSelectFunction,
    onUnselect: (items: OptionalUnselectableItemCollections) => void,
): (id: LayoutTrackNumberId) => void {
    return (id) => {
        Promise.all([updateTrackNumberChangeTime(), updateReferenceLineChangeTime()]).then(
            ([ts, _]) => {
                getTrackNumberById(id, publicationState, ts).then((tn) => {
                    if (tn) onSelect({ trackNumbers: [id] });
                    else onUnselect({ trackNumbers: [id] });
                });
            },
        );
    };
}

export function refreshLocationTrackSelection(
    publicationState: PublishType,
    onSelect: OnSelectFunction,
    onUnselect: (items: OptionalUnselectableItemCollections) => void,
): (id: LocationTrackId) => void {
    return (id) => {
        updateLocationTrackChangeTime().then((ts) => {
            getLocationTrack(id, publicationState, ts).then((lt) => {
                if (lt) onSelect({ locationTracks: [id] });
                else onUnselect({ locationTracks: [id] });
            });
        });
    };
}

export function refreshSwitchSelection(
    publicationState: PublishType,
    onSelect: OnSelectFunction,
    onUnselect: (items: OptionalUnselectableItemCollections) => void,
): (id: LayoutSwitchId) => void {
    return (id) => {
        updateSwitchChangeTime().then((ts) => {
            getSwitch(id, publicationState, ts).then((s) => {
                if (s) onSelect({ switches: [id] });
                else onUnselect({ switches: [id] });
            });
        });
    };
}

export function refereshKmPostSelection(
    publicationState: PublishType,
    onSelect: OnSelectFunction,
    onUnselect: (items: OptionalUnselectableItemCollections) => void,
): (id: LayoutKmPostId) => void {
    return (id) => {
        updateKmPostChangeTime().then((ts) => {
            getKmPost(id, publicationState, ts).then((kmp) => {
                if (kmp) onSelect({ kmPosts: [id] });
                else onUnselect({ kmPosts: [id] });
            });
        });
    };
}
