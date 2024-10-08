import { nextSortDirection, SortDirection } from 'utils/table-utils';
import { PublicationTableItem } from 'publication/publication-model';
import { publicationOperationCompare } from 'sorting/publication-sorting';
import { Range, TimeStamp, TrackNumber } from 'common/common-model';
import { fieldComparator, timeStampComparator } from 'utils/array-utils';

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

const changedKmNumbersCompare = (a: PublicationTableItem, b: PublicationTableItem): number => {
    const emptyArraySortDirection = sortEmptyArraysAsLast(a.changedKmNumbers, b.changedKmNumbers);

    return emptyArraySortDirection
        ? emptyArraySortDirection
        : fieldComparator(
              (entry: { changedKmNumbers: Range<string>[] }) =>
                  entry.changedKmNumbers.map((e) => `${e.min}-${e.max}`).join(',') || '',
          )(a, b);
};

const trackNumbersCompare = (a: PublicationTableItem, b: PublicationTableItem): number => {
    const emptyArraySortDirection = sortEmptyArraysAsLast(a.trackNumbers, b.trackNumbers);

    return emptyArraySortDirection
        ? emptyArraySortDirection
        : fieldComparator(
              (entry: { trackNumbers: TrackNumber[] }) => entry.trackNumbers.join(',') || '',
          )(a, b);
};

const publicationLogSortFunctions: Record<
    PublicationDetailsTableSortField,
    (a: PublicationTableItem, b: PublicationTableItem) => number
> = {
    NAME: fieldComparator((entry: { name: string }) => entry.name),
    TRACK_NUMBERS: trackNumbersCompare,
    CHANGED_KM_NUMBERS: changedKmNumbersCompare,
    OPERATION: publicationOperationCompare,
    PUBLICATION_TIME: timeStampComparator(
        (entry: { publicationTime: TimeStamp }) => entry.publicationTime,
    ),
    PUBLICATION_USER: fieldComparator(
        (entry: { publicationUser: string }) => entry.publicationUser,
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
