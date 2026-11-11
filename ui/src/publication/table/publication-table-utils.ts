import { nextSortDirection, SortDirection, TableSorting } from 'utils/table-utils';
import { PublicationTableItem } from 'publication/publication-model';
import { publicationOperationCompare } from 'sorting/publication-sorting';
import { TimeStamp, KmNumber, officialMainLayoutContext, Range } from 'common/common-model';
import { PublishedAsset } from 'publication/publication-api';
import { BoundingBox } from 'model/geometry';
import {
    calculateBoundingBoxToShowAroundLocation,
    calculateOperationalPointBoundingBox,
    calculateSwitchBoundingBox,
} from 'map/map-utils';
import { OnSelectOptions } from 'selection/selection-model';
import { LayoutTrackNumberId } from 'track-layout/track-layout-model';
import { getKmPostByNumber } from 'track-layout/layout-km-post-api';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import {
    Comparator,
    fieldComparator,
    multiFieldComparator,
    timeStampComparator,
} from 'utils/array-utils';

export type PublicationDisplayMode =
    'PUBLICATION_LOG' | 'SINGLE_PUBLICATION_DETAILS' | 'SINGLE_ASSET';

export type SortablePublicationTableProps = Pick<
    PublicationTableItem,
    | 'name'
    | 'changedKmNumbers'
    | 'trackNumbers'
    | 'operation'
    | 'publicationTime'
    | 'publicationUser'
    | 'ratkoPushTime'
    | 'message'
>;

const sortEmptyArraysAsLast = <T>(a: T[], b: T[]): number | undefined => {
    if (a.length === 0 && b.length !== 0) {
        return 1;
    } else if (a.length !== 0 && b.length === 0) {
        return -1;
    } else {
        return undefined;
    }
};

const changedKmNumbersGetter = (entry: PublicationTableItem) => {
    return entry.changedKmNumbers.map((e) => `${e.min}-${e.max}`).join(',') || '';
};

const trackNumbersGetter = (entry: PublicationTableItem) => entry.trackNumbers.join(',') || '';

const publicationTimeGetter = (entry: { publicationTime: TimeStamp }) => entry.publicationTime;

const changedKmNumbersCompare = (a: PublicationTableItem, b: PublicationTableItem): number => {
    const emptyArraySortDirection = sortEmptyArraysAsLast(a.changedKmNumbers, b.changedKmNumbers);

    return emptyArraySortDirection
        ? emptyArraySortDirection
        : multiFieldComparator(changedKmNumbersGetter, publicationTimeGetter)(a, b);
};

const trackNumbersCompare = (a: PublicationTableItem, b: PublicationTableItem): number => {
    const emptyArraySortDirection = sortEmptyArraysAsLast(a.trackNumbers, b.trackNumbers);

    return emptyArraySortDirection
        ? emptyArraySortDirection
        : multiFieldComparator(trackNumbersGetter, publicationTimeGetter)(a, b);
};

const publicationLogOperationCompare = (
    a: PublicationTableItem,
    b: PublicationTableItem,
): number => {
    const operationComparison = publicationOperationCompare(a, b);

    return operationComparison !== 0
        ? operationComparison
        : fieldComparator(publicationTimeGetter)(a, b);
};

const publicationLogSorting: {
    [key in keyof SortablePublicationTableProps]: Comparator<SortablePublicationTableProps>;
} = {
    name: multiFieldComparator((entry: { name: string }) => entry.name, publicationTimeGetter),
    trackNumbers: trackNumbersCompare,
    changedKmNumbers: changedKmNumbersCompare,
    operation: publicationLogOperationCompare,
    publicationTime: timeStampComparator(
        (entry: { publicationTime: string }) => entry.publicationTime,
    ),
    publicationUser: multiFieldComparator(
        (entry: { publicationUser: string }) => entry.publicationUser,
        publicationTimeGetter,
    ),
    ratkoPushTime: timeStampComparator(
        (entry: { ratkoPushTime: TimeStamp }) => entry.ratkoPushTime,
    ),
    message: fieldComparator((entry: { message: string }) => entry.message),
};

export const SortedByTimeDesc: TableSorting<SortablePublicationTableProps> = {
    propName: 'publicationTime',
    direction: SortDirection.DESCENDING,
    function: publicationLogSorting.publicationTime,
};

export const SortedByNameAsc: TableSorting<SortablePublicationTableProps> = {
    propName: 'name',
    direction: SortDirection.ASCENDING,
    function: publicationLogSorting.name,
};

export const getSortInfoForProp = (
    oldSortDirection: SortDirection,
    oldSortPropName: keyof SortablePublicationTableProps,
    newSortPropName: keyof SortablePublicationTableProps,
): TableSorting<SortablePublicationTableProps> => ({
    propName: newSortPropName,
    direction:
        oldSortPropName === newSortPropName
            ? nextSortDirection[oldSortDirection]
            : SortDirection.ASCENDING,
    function: publicationLogSorting[newSortPropName],
});

export const publishedAssetBoundingBox = (
    publishedAsset: PublishedAsset,
): BoundingBox | undefined => {
    switch (publishedAsset.type) {
        case 'TRACK_NUMBER':
        case 'LOCATION_TRACK':
            return publishedAsset.asset.boundingBox;
        case 'SWITCH':
            return calculateSwitchBoundingBox(
                undefined,
                publishedAsset.asset.joints.map((joint) => joint.location),
            );
        case 'KM_POST':
            return publishedAsset.asset.layoutLocation
                ? calculateBoundingBoxToShowAroundLocation(publishedAsset.asset.layoutLocation)
                : undefined;
        case 'OPERATIONAL_POINT':
            return calculateOperationalPointBoundingBox(
                publishedAsset.asset.location,
                publishedAsset.asset.polygon,
            );
        default:
            return exhaustiveMatchingGuard(publishedAsset);
    }
};

const publishedAssetSelectionItems = (publishedAsset: PublishedAsset): OnSelectOptions => {
    switch (publishedAsset.type) {
        case 'TRACK_NUMBER':
            return { trackNumbers: [publishedAsset.asset.id] };
        case 'LOCATION_TRACK':
            return { locationTracks: [publishedAsset.asset.id] };
        case 'SWITCH':
            return { switches: [publishedAsset.asset.id] };
        case 'KM_POST':
            return { kmPosts: [publishedAsset.asset.id] };
        case 'OPERATIONAL_POINT':
            return { operationalPoints: [publishedAsset.asset.id] };
        default:
            return exhaustiveMatchingGuard(publishedAsset);
    }
};

const publishedAssetTrackNumberId = (
    publishedAsset: PublishedAsset,
): LayoutTrackNumberId | undefined => {
    switch (publishedAsset.type) {
        case 'TRACK_NUMBER':
            return publishedAsset.asset.id;
        case 'LOCATION_TRACK':
        case 'KM_POST':
            return publishedAsset.asset.trackNumberId;
        case 'SWITCH':
        case 'OPERATIONAL_POINT':
            return undefined;
        default:
            return exhaustiveMatchingGuard(publishedAsset);
    }
};

/**
 * The selection for showing a published asset on the map. If the row has a changed km number, the
 * km post of the first changed km is selected as well, and becomes the active tool panel tab.
 */
export const publishedAssetSelection = async (
    publishedAsset: PublishedAsset,
    changedKmNumbers: Range<KmNumber>[],
): Promise<OnSelectOptions> => {
    const assetSelection: OnSelectOptions = {
        ...publishedAssetSelectionItems(publishedAsset),
        selectedTab: { id: publishedAsset.asset.id, type: publishedAsset.type },
    };
    const kmNumber = changedKmNumbers[0]?.min;
    const trackNumberId = publishedAssetTrackNumberId(publishedAsset);
    if (
        publishedAsset.type === 'KM_POST' ||
        kmNumber === undefined ||
        trackNumberId === undefined
    ) {
        return assetSelection;
    }

    // The km post is a bonus selection: if fetching it fails, still show the asset itself
    const kmPost = await getKmPostByNumber(
        officialMainLayoutContext(),
        trackNumberId,
        kmNumber,
        true,
    ).catch(() => undefined);
    return kmPost
        ? {
              ...assetSelection,
              kmPosts: [kmPost.id],
              selectedTab: { id: kmPost.id, type: 'KM_POST' },
          }
        : assetSelection;
};
