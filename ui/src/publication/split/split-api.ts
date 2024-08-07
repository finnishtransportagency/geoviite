import { SplitRequest } from 'tool-panel/location-track/split-store';
import { API_URI, getNullable, postNonNull, putNonNull } from 'api/api-fetch';
import { Split } from 'publication/publication-model';
import { LayoutBranch } from 'common/common-model';

const SPLIT_URI = `${API_URI}/location-track-split`;

export const postSplitLocationTrack = async (
    request: SplitRequest,
    branch: LayoutBranch,
): Promise<string> => {
    return postNonNull<SplitRequest, string>(`${SPLIT_URI}/${branch.toLowerCase()}`, request);
};

export const getSplit = async (id: string): Promise<Split | undefined> =>
    getNullable<Split>(`${SPLIT_URI}/${id}`);

export const putBulkTransferState = async (id: string, state: string): Promise<string> => {
    return putNonNull<string, string>(`${SPLIT_URI}/${id}/bulk-transfer-state`, state);
};
