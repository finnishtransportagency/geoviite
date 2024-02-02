import { SplitRequest } from 'tool-panel/location-track/split-store';
import { API_URI, getNullable, postNonNull, putNullable } from 'api/api-fetch';
import { Split } from 'publication/publication-model';

const SPLIT_URI = `${API_URI}/location-track-split`;

export const postSplitLocationTrack = async (request: SplitRequest): Promise<string> => {
    return postNonNull<SplitRequest, string>(SPLIT_URI, request);
};

export const getSplit = async (id: string): Promise<Split | undefined> =>
    getNullable<Split>(`${SPLIT_URI}/${id}`);

export const putBulkTransferState = async (id: string, state: string): Promise<void> => {
    await putNullable<string, void>(`${SPLIT_URI}/${id}/bulk-transfer-state`, state);
};
