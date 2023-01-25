import { trackLayoutStore } from 'store/store';

import { API_URI, getIgnoreError, getWithDefault } from 'api/api-fetch';
import { actionCreators, ChangeTimes } from 'track-layout/track-layout-store';
import { createDelegates } from 'store/store-utils';
import { TimeStamp } from 'common/common-model';

const CHANGES_API = `${API_URI}/change-times`;

const delegates = createDelegates(trackLayoutStore.dispatch, actionCreators);

const _intervalHandle = window.setInterval(() => {
    updateAllChangeTimes();
}, 15000);

export function getChangeTimes(): ChangeTimes {
    return trackLayoutStore.getState().trackLayout.changeTimes;
}

export function updateAllChangeTimes(): Promise<ChangeTimes> {
    return getIgnoreError<ChangeTimes>(`${CHANGES_API}/collected`).then((newTimes) => {
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

export function updatePublicationChangeTime(): Promise<TimeStamp> {
    return updateChangeTime(
        `${CHANGES_API}/publications`,
        delegates.setPublicationChangeTime,
        getChangeTimes().publication,
    );
}

function updateChangeTime(
    url: string,
    storeUpdate: (ts: TimeStamp) => void,
    defaultValue: TimeStamp,
): Promise<TimeStamp> {
    return getWithDefault<TimeStamp>(url, defaultValue).then((ts) => {
        storeUpdate(ts);
        return ts;
    });
}
