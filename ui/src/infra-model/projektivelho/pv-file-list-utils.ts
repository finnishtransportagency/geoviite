import { Icons } from 'vayla-design-lib/icon/Icon';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

export enum PVTableSortField {
    PROJECT_NAME = 'PROJECT_NAME',
    DOCUMENT_NAME = 'DOCUMENT_NAME',
    DOCUMENT_DESCRIPTION = 'DOCUMENT_DESCRIPTION',
    DOCUMENT_MODIFIED = 'DOCUMENT_MODIFIED',
}

export type PVTableSortInformation = {
    propName: PVTableSortField;
    direction: PVSortDirection;
};

export enum PVSortDirection {
    ASCENDING = 'ASCENDING',
    DESCENDING = 'DESCENDING',
    UNSORTED = 'UNSORTED',
}

export const PVInitiallyUnsorted = {
    propName: PVTableSortField.PROJECT_NAME,
    direction: PVSortDirection.UNSORTED,
};

export const nextPVSortDirection = {
    ASCENDING: PVSortDirection.DESCENDING,
    DESCENDING: PVSortDirection.UNSORTED,
    UNSORTED: PVSortDirection.ASCENDING,
};

export const getSortInfoForPVProp = (
    oldSortDirection: PVSortDirection,
    oldSortPropName: PVTableSortField,
    newSortPropName: PVTableSortField,
) => ({
    propName: newSortPropName,
    direction:
        oldSortPropName === newSortPropName
            ? nextPVSortDirection[oldSortDirection]
            : PVSortDirection.ASCENDING,
});

export const getPVSortDirectionIcon = (direction: PVSortDirection) => {
    switch (direction) {
        case PVSortDirection.ASCENDING:
            return Icons.Ascending;
        case PVSortDirection.DESCENDING:
            return Icons.Descending;
        case PVSortDirection.UNSORTED:
            return undefined;
        default:
            return exhaustiveMatchingGuard(direction);
    }
};
