import { TRACK_LAYOUT_URI } from 'track-layout/track-layout-api';
import { getNonNull, postNonNull, putNonNull } from 'api/api-fetch';
import { LayoutDesignId, TimeStamp } from 'common/common-model';
import { asyncCache } from 'cache/cache';

const designCache = asyncCache<string, LayoutDesign[]>();

const baseUri = `${TRACK_LAYOUT_URI}/layout-design`;

export type LayoutDesignSaveRequest = {
    name: string;
    estimatedCompletion: TimeStamp;
    designState: 'ACTIVE' | 'DELETED' | 'COMPLETED';
};
export type LayoutDesign = {
    id: LayoutDesignId;
} & LayoutDesignSaveRequest;

export const getLayoutDesigns = async (changeTime: TimeStamp) =>
    designCache.get(changeTime, '', () => getNonNull<LayoutDesign[]>(`${baseUri}/`));

export const getLayoutDesign = async (changeTime: TimeStamp, id: LayoutDesignId) =>
    getLayoutDesigns(changeTime).then((designs) => designs.find((design) => design.id === id));

export const updateLayoutDesign = async (
    layoutDesignId: string,
    saveRequest: LayoutDesignSaveRequest,
) =>
    putNonNull<LayoutDesignSaveRequest, LayoutDesignId>(
        `${baseUri}/${layoutDesignId}`,
        saveRequest,
    );

export const insertLayoutDesign = async (saveRequest: LayoutDesignSaveRequest) =>
    postNonNull<LayoutDesignSaveRequest, LayoutDesignId>(`${baseUri}/`, saveRequest);
