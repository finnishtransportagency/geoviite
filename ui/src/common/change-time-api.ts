import { appStore } from 'store/store';

import { API_URI, getNonNull, getNullable } from 'api/api-fetch';
import { createDelegates } from 'store/store-utils';
import { TimeStamp } from 'common/common-model';
import { ChangeTimes, commonActionCreators } from 'common/common-slice';

const CHANGES_API = `${API_URI}/change-times`;

const delegates = createDelegates(commonActionCreators);

const _intervalHandle = window.setInterval(() => {
    updateAllChangeTimes();
}, 15000);

export function getChangeTimes(): ChangeTimes {
    return appStore.getState().common.changeTimes;
}

export function updateAllChangeTimes(): Promise<ChangeTimes> {
    return getNullable<ChangeTimes>(`${CHANGES_API}/collected`).then((newTimes) => {
        if (newTimes) {
            delegates.setChangeTimes(newTimes);
            return newTimes;
        } else {
            return getChangeTimes();
        }
    });
}

export function updateTrackNumberChangeTime(): Promise<TimeStamp> {
    return updateChangeTime(
        `${CHANGES_API}/track-numbers`,
        delegates.setLayoutTrackNumberChangeTime,
        getChangeTimes().layoutTrackNumber,
    );
}

export function updateLocationTrackChangeTime(): Promise<TimeStamp> {
    return updateChangeTime(
        `${CHANGES_API}/location-tracks`,
        delegates.setLayoutLocationTrackChangeTime,
        getChangeTimes().layoutLocationTrack,
    );
}

export function updateReferenceLineChangeTime(): Promise<TimeStamp> {
    return updateChangeTime(
        `${CHANGES_API}/reference-lines`,
        delegates.setLayoutReferenceLineChangeTime,
        getChangeTimes().layoutReferenceLine,
    );
}

export function updateSwitchChangeTime(): Promise<TimeStamp> {
    return updateChangeTime(
        `${CHANGES_API}/switches`,
        delegates.setLayoutSwitchChangeTime,
        getChangeTimes().layoutSwitch,
    );
}

export function updateKmPostChangeTime(): Promise<TimeStamp> {
    return updateChangeTime(
        `${CHANGES_API}/km-posts`,
        delegates.setLayoutKmPostChangeTime,
        getChangeTimes().layoutKmPost,
    );
}

export function updatePlanChangeTime(): Promise<TimeStamp> {
    return updateChangeTime(
        `${CHANGES_API}/plans`,
        delegates.setGeometryPlanChangeTime,
        getChangeTimes().geometryPlan,
    );
}

export function updateProjectChangeTime(): Promise<TimeStamp> {
    return updateChangeTime(
        `${CHANGES_API}/projects`,
        delegates.setProjectChangeTime,
        getChangeTimes().project,
    );
}

export function updatePublicationChangeTime(): Promise<TimeStamp> {
    return updateChangeTime(
        `${CHANGES_API}/publications`,
        delegates.setPublicationChangeTime,
        getChangeTimes().publication,
    );
}

export function updatePVDocumentsChangeTime(): Promise<TimeStamp> {
    return updateChangeTime(
        `${CHANGES_API}/projektivelho-documents`,
        delegates.setPVDocumentChangeTime,
        getChangeTimes().pvDocument,
    );
}

export function updateSplitChangeTime(): Promise<TimeStamp> {
    return updateChangeTime(
        `${CHANGES_API}/splits`,
        delegates.setSplitChangeTime,
        getChangeTimes().split,
    );
}

export function updateLayoutDesignChangeTime(): Promise<TimeStamp> {
    return updateChangeTime(
        `${CHANGES_API}/layout-designs`,
        delegates.setLayoutDesignChangeTime,
        getChangeTimes().layoutDesign,
    );
}

export function updateOperationalPointsChangeTime(): Promise<TimeStamp> {
    return updateChangeTime(
        `${CHANGES_API}/operational-points`,
        delegates.setOperationalPointsChangeTime,
        getChangeTimes().operatingPoints,
    );
}

function updateChangeTime(
    url: string,
    storeUpdate: (ts: TimeStamp) => void,
    defaultValue: TimeStamp,
): Promise<TimeStamp> {
    return getNonNull<TimeStamp>(url).then(
        (ts) => {
            storeUpdate(ts);
            return ts;
        },
        () => {
            storeUpdate(defaultValue);
            return defaultValue;
        },
    );
}
