import { nextSortDirection, SortDirection, TableSorting } from 'utils/table-utils';
import { PublicationTableItem } from 'publication/publication-model';
import { publicationOperationCompare } from 'sorting/publication-sorting';
import { TimeStamp } from 'common/common-model';
import {
    Comparator,
    fieldComparator,
    multiFieldComparator,
    timeStampComparator,
} from 'utils/array-utils';

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
