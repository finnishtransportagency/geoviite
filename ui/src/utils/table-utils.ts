import { Icons } from 'vayla-design-lib/icon/Icon';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

export enum SortDirection {
    ASCENDING = 'ASCENDING',
    DESCENDING = 'DESCENDING',
    UNSORTED = 'UNSORTED',
}

export const nextSortDirection = {
    ASCENDING: SortDirection.DESCENDING,
    DESCENDING: SortDirection.UNSORTED,
    UNSORTED: SortDirection.ASCENDING,
};

export const getSortDirectionIcon = (direction: SortDirection) => {
    switch (direction) {
        case SortDirection.ASCENDING:
            return Icons.Ascending;
        case SortDirection.DESCENDING:
            return Icons.Descending;
        case SortDirection.UNSORTED:
            return undefined;
        default:
            return exhaustiveMatchingGuard(direction);
    }
};
