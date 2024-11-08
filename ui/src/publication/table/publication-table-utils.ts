import { nextSortDirection, SortDirection } from 'utils/table-utils';
import { PublicationTableItem } from 'publication/publication-model';
import { publicationOperationCompare } from 'sorting/publication-sorting';
import { TimeStamp } from 'common/common-model';
import { fieldComparator, multiFieldComparator, timeStampComparator } from 'utils/array-utils';

export enum PublicationDetailsTableSortField {
    NAME = 'NAME',
    CHANGED_KM_NUMBERS = 'CHANGED_KM_NUMBERS',
    TRACK_NUMBERS = 'TRACK_NUMBERS',
    OPERATION = 'OPERATION',
    PUBLICATION_TIME = 'PUBLICATION_TIME',
    PUBLICATION_USER = 'PUBLICATION_USER',
    RATKO_PUSH_TIME = 'RATKO_PUSH_TIME',
    MESSAGE = 'MESSAGE',
}

export type PublicationDetailsTableSortInformation = {
    propName: PublicationDetailsTableSortField;
    direction: SortDirection;
    sortFunction: (item1: PublicationTableItem, item2: PublicationTableItem) => number;
};

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

const publicationLogSortFunctions: Record<
    PublicationDetailsTableSortField,
    (a: PublicationTableItem, b: PublicationTableItem) => number
> = {
    NAME: multiFieldComparator((entry: { name: string }) => entry.name, publicationTimeGetter),
    TRACK_NUMBERS: trackNumbersCompare,
    CHANGED_KM_NUMBERS: changedKmNumbersCompare,
    OPERATION: publicationLogOperationCompare,
    PUBLICATION_TIME: timeStampComparator(
        (entry: { publicationTime: string }) => entry.publicationTime,
    ),
    PUBLICATION_USER: multiFieldComparator(
        (entry: { publicationUser: string }) => entry.publicationUser,
        publicationTimeGetter,
    ),
    RATKO_PUSH_TIME: timeStampComparator(
        (entry: { ratkoPushTime: TimeStamp }) => entry.ratkoPushTime,
    ),
    MESSAGE: fieldComparator((entry: { message: string }) => entry.message),
};

export const InitiallyUnsorted: PublicationDetailsTableSortInformation = {
    propName: PublicationDetailsTableSortField.NAME,
    direction: SortDirection.UNSORTED,
    sortFunction: publicationLogSortFunctions['NAME'],
};

export const getSortInfoForProp = (
    oldSortDirection: SortDirection,
    oldSortPropName: PublicationDetailsTableSortField,
    newSortPropName: PublicationDetailsTableSortField,
) => ({
    propName: newSortPropName,
    direction:
        oldSortPropName === newSortPropName
            ? nextSortDirection[oldSortDirection]
            : SortDirection.ASCENDING,
    sortFunction: publicationLogSortFunctions[newSortPropName],
});
