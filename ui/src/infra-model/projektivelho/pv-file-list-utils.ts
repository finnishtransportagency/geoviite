import { Icons } from 'vayla-design-lib/icon/Icon';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { PVDocumentHeader } from 'infra-model/projektivelho/pv-model';

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

export const PVNextSortDirection = {
    ASCENDING: PVSortDirection.DESCENDING,
    DESCENDING: PVSortDirection.UNSORTED,
    UNSORTED: PVSortDirection.ASCENDING,
};

export const getPVSortInfoForProp = (
    oldSortDirection: PVSortDirection,
    oldSortPropName: PVTableSortField,
    newSortPropName: PVTableSortField,
) => ({
    propName: newSortPropName,
    direction:
        oldSortPropName === newSortPropName
            ? PVNextSortDirection[oldSortDirection]
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

export function sortPVTableColumns(
    sortInfo: PVTableSortInformation,
    sortableDocumentHeaders: PVDocumentHeader[],
): PVDocumentHeader[] {
    if (sortInfo) {
        if (sortInfo.propName === PVTableSortField.PROJECT_NAME) {
            sortableDocumentHeaders.sort((a, b) => {
                if (
                    (a.project?.name && b.project === null) ||
                    (a.project?.name &&
                        b.project?.name &&
                        a.project?.name.trim().toUpperCase() > b.project?.name.trim().toUpperCase())
                ) {
                    return getSortDirection(sortInfo.direction, true);
                }

                if (
                    (a.project === null && b.project?.name) ||
                    (a.project?.name &&
                        b.project?.name &&
                        a.project?.name.trim().toUpperCase() < b.project?.name.trim().toUpperCase())
                ) {
                    return getSortDirection(sortInfo.direction, false);
                }
                return 0;
            });
        }

        if (sortInfo.propName === PVTableSortField.DOCUMENT_NAME) {
            sortableDocumentHeaders.sort((a, b) => {
                if (a.document.name.toUpperCase() > b.document.name.trim().toUpperCase()) {
                    return getSortDirection(sortInfo.direction, true);
                }
                if (a.document.name.toUpperCase() < b.document.name.trim().toUpperCase()) {
                    return getSortDirection(sortInfo.direction, false);
                }
                return 0;
            });
        }

        if (sortInfo.propName === PVTableSortField.DOCUMENT_DESCRIPTION) {
            sortableDocumentHeaders.sort((a, b) => {
                if (
                    (a.document.description && b.document.description === null) ||
                    (a.document.description &&
                        b.document.description &&
                        a.document.description.trim().toUpperCase() >
                            b.document.description.trim().toUpperCase())
                ) {
                    return getSortDirection(sortInfo.direction, true);
                }
                if (
                    (a.document.description === null && b.document.description) ||
                    (a.document.description &&
                        b.document.description &&
                        a.document.description.trim().toUpperCase() <
                            b.document.description.trim().toUpperCase())
                ) {
                    return getSortDirection(sortInfo.direction, false);
                }
                return 0;
            });
        }

        if (sortInfo.propName === PVTableSortField.DOCUMENT_MODIFIED) {
            sortableDocumentHeaders.sort((a, b) => {
                if (a.document.modified > b.document.modified) {
                    return getSortDirection(sortInfo.direction, true);
                }
                if (a.document.modified > b.document.modified) {
                    return getSortDirection(sortInfo.direction, false);
                }
                return 0;
            });
        }
    }
    return sortableDocumentHeaders;
}

const getSortDirection = (direction: PVSortDirection, firstParamHasPriority: boolean) => {
    if (firstParamHasPriority) {
        if (direction === PVSortDirection.ASCENDING) return 1;
        if (direction === PVSortDirection.DESCENDING) return -1;
    } else if (!firstParamHasPriority) {
        if (direction === PVSortDirection.ASCENDING) return -1;
        if (direction === PVSortDirection.DESCENDING) return 1;
    }
    return 0;
};
