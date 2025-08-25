import { TRACK_LAYOUT_URI } from 'track-layout/track-layout-api';
import { getNonNull, postNonNull, putNonNull, queryParams } from 'api/api-fetch';
import { designBranch, DesignBranch, LayoutDesignId, TimeStamp } from 'common/common-model';
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

export const getLayoutDesigns = async (
    includeDeleted: boolean,
    includeCompleted: boolean,
    changeTime: TimeStamp,
) =>
    designCache.get(changeTime, `${includeDeleted}_${includeCompleted}`, () =>
        getNonNull<LayoutDesign[]>(
            `${baseUri}/${queryParams({
                includeDeleted,
                includeCompleted,
            })}`,
        ),
    );

export async function getLayoutDesign(changeTime: TimeStamp, id: LayoutDesignId) {
    return getLayoutDesignOrUndefined(changeTime, id).then((design) => {
        if (!design) {
            throw new Error(`Design does not exists by id: ${id}!`);
        }
        return design;
    });
}

export async function getLayoutDesignByBranch(
    changeTime: TimeStamp,
    branch: DesignBranch,
): Promise<LayoutDesign | undefined> {
    const designs = await getLayoutDesigns(true, true, changeTime);
    return designs.find((design) => designBranch(design.id) === branch);
}

export const getLayoutDesignOrUndefined = async (changeTime: TimeStamp, id: LayoutDesignId) =>
    getLayoutDesigns(true, true, changeTime).then((designs) =>
        designs.find((design) => design.id === id),
    );

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
