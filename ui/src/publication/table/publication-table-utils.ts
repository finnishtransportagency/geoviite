import { Icons } from 'vayla-design-lib/icon/Icon';

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

export enum SortDirection {
    ASCENDING = 'ASCENDING',
    DESCENDING = 'DESCENDING',
    UNSORTED = 'UNSORTED',
}

export type PublicationDetailsTableSortInformation = {
    propName: PublicationDetailsTableSortField;
    direction: SortDirection;
};

export const nextSortDirection = {
    ASCENDING: SortDirection.DESCENDING,
    DESCENDING: SortDirection.UNSORTED,
    UNSORTED: SortDirection.ASCENDING,
};

export const InitiallyUnsorted = {
    propName: PublicationDetailsTableSortField.NAME,
    direction: SortDirection.UNSORTED,
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
});

export const getSortDirectionIcon = (direction: SortDirection) => {
    switch (direction) {
        case SortDirection.ASCENDING:
            return Icons.Ascending;
        case SortDirection.DESCENDING:
            return Icons.Descending;
        case SortDirection.UNSORTED:
            return undefined;
    }
};
