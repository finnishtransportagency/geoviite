import { SplitRequest } from 'tool-panel/location-track/split-store';
import { API_URI, postNonNull, putNonNull } from 'api/api-fetch';
import { LayoutBranch } from 'common/common-model';

const SPLIT_URI = `${API_URI}/location-track-split`;

export const postSplitLocationTrack = async (
    request: SplitRequest,
    branch: LayoutBranch,
): Promise<string> => {
    return postNonNull<SplitRequest, string>(`${SPLIT_URI}/${branch.toLowerCase()}`, request);
};

export const putBulkTransferState = async (id: string, state: string): Promise<string> => {
    return putNonNull<string, string>(`${SPLIT_URI}/${id}/bulk-transfer-state`, state);
};

export const putBulkTransferExpeditedStart = async (
    id: string,
    expeditedStart: boolean,
): Promise<string> => {
    return putNonNull<string, string>(
        `${SPLIT_URI}/${id}/bulk-transfer/expedited-start`,
        expeditedStart.toString(),
    );
};
