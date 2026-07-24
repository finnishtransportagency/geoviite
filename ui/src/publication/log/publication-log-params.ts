import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { SearchItemType, SearchItemValue } from 'asset-search/search-dropdown';
import { LayoutContext } from 'common/common-model';
import { PublishableObjectIdAndType } from 'publication/publication-api';
import { brand } from 'common/brand';
import { getLocationTrack } from 'track-layout/layout-location-track-api';
import { getSwitch } from 'track-layout/layout-switch-api';
import { getTrackNumber } from 'track-layout/layout-track-number-api';
import { getKmPost } from 'track-layout/layout-km-post-api';
import { getOperationalPoint } from 'track-layout/layout-operational-point-api';
import { subMonths } from 'date-fns';
import { currentDay } from 'utils/date-utils';

export const PARAM_START_DATE = 'startDate';
export const PARAM_END_DATE = 'endDate';
export const PARAM_SPECIFIC_TYPE = 'specificType';
export const PARAM_SPECIFIC_ID = 'specificId';
export const PARAM_SPECIFIC_START_DATE = 'specificStartDate';
export const PARAM_SPECIFIC_END_DATE = 'specificEndDate';

export const defaultGlobalStartDate: Date = subMonths(currentDay, 1);
export const defaultGlobalEndDate: Date = currentDay;

type SpecificItemRef = { type: SearchItemType; id: string };

export function readSpecificItemParams(params: URLSearchParams): SpecificItemRef | undefined {
    const rawType = params.get(PARAM_SPECIFIC_TYPE);
    const id = params.get(PARAM_SPECIFIC_ID);
    if (!rawType || !id) return undefined;
    const allTypes: string[] = Object.values(SearchItemType);
    if (!allTypes.includes(rawType)) return undefined;
    return { type: rawType as SearchItemType, id };
}

export function searchableItemIdAndType(
    item: SearchItemValue<SearchItemType>,
): PublishableObjectIdAndType {
    switch (item.type) {
        case SearchItemType.LOCATION_TRACK:
            return { type: item.type, id: item.locationTrack.id };
        case SearchItemType.TRACK_NUMBER:
            return { type: item.type, id: item.trackNumber.id };
        case SearchItemType.SWITCH:
            return { type: item.type, id: item.layoutSwitch.id };
        case SearchItemType.KM_POST:
            return { type: item.type, id: item.kmPost.id };
        case SearchItemType.OPERATIONAL_POINT:
            return { type: item.type, id: item.operationalPoint.id };
        default:
            return exhaustiveMatchingGuard(item);
    }
}

function itemToRef(item: SearchItemValue<SearchItemType>): SpecificItemRef {
    return { type: item.type, id: String(searchableItemIdAndType(item).id) };
}

export function itemToSpecificItemParams(item: SearchItemValue<SearchItemType>): URLSearchParams {
    const { type, id } = itemToRef(item);
    const params = new URLSearchParams();
    params.set(PARAM_SPECIFIC_TYPE, type);
    params.set(PARAM_SPECIFIC_ID, id);
    return params;
}

export function publicationLogUrlForItem(item: SearchItemValue<SearchItemType>): string {
    return `/publications?${itemToSpecificItemParams(item).toString()}`;
}

export async function fetchSpecificItem(
    type: SearchItemType,
    id: string,
    layoutContext: LayoutContext,
): Promise<SearchItemValue<SearchItemType> | undefined> {
    switch (type) {
        case SearchItemType.LOCATION_TRACK: {
            const locationTrack = await getLocationTrack(brand(id), layoutContext);
            return locationTrack ? { type, locationTrack } : undefined;
        }
        case SearchItemType.SWITCH: {
            const layoutSwitch = await getSwitch(brand(id), layoutContext);
            return layoutSwitch ? { type, layoutSwitch } : undefined;
        }
        case SearchItemType.TRACK_NUMBER: {
            const trackNumber = await getTrackNumber(brand(id), layoutContext);
            return trackNumber ? { type, trackNumber } : undefined;
        }
        case SearchItemType.KM_POST: {
            const kmPost = await getKmPost(brand(id), layoutContext);
            return kmPost ? { type, kmPost } : undefined;
        }
        case SearchItemType.OPERATIONAL_POINT: {
            const operationalPoint = await getOperationalPoint(brand(id), layoutContext);
            return operationalPoint ? { type, operationalPoint } : undefined;
        }
        default:
            return exhaustiveMatchingGuard(type);
    }
}
