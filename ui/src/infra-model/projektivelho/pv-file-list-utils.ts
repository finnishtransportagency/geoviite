import { PVDocumentHeader } from 'infra-model/projektivelho/pv-model';
import { nextSortDirection, SortDirection } from 'utils/table-utils';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

export enum PVTableSortField {
    PROJECT_NAME = 'PROJECT_NAME',
    DOCUMENT_NAME = 'DOCUMENT_NAME',
    DOCUMENT_DESCRIPTION = 'DOCUMENT_DESCRIPTION',
    DOCUMENT_MODIFIED = 'DOCUMENT_MODIFIED',
}

export type PVTableSortInformation = {
    propName: PVTableSortField;
    direction: SortDirection;
};

export const PVInitiallyUnsorted = {
    propName: PVTableSortField.PROJECT_NAME,
    direction: SortDirection.UNSORTED,
};

export const getPVSortInfoForProp = (
    oldSortDirection: SortDirection,
    oldSortPropName: PVTableSortField,
    newSortPropName: PVTableSortField,
) => ({
    propName: newSortPropName,
    direction:
        oldSortPropName === newSortPropName
            ? nextSortDirection[oldSortDirection]
            : SortDirection.ASCENDING,
});

export function sortPVTableColumns(
    sortInfo: PVTableSortInformation,
    sortableDocumentHeaders: PVDocumentHeader[],
): PVDocumentHeader[] {
    switch (sortInfo.propName) {
        case PVTableSortField.PROJECT_NAME:
            sortableDocumentHeaders.sort((a, b) => {
                if (
                    (a.project?.name && b.project === null) ||
                    (a.project?.name &&
                        b.project?.name &&
                        a.project.name.trim().toUpperCase() > b.project.name.trim().toUpperCase())
                ) {
                    return getSortDirection(sortInfo.direction, true);
                }
                if (
                    (a.project === null && b.project?.name) ||
                    (a.project?.name &&
                        b.project?.name &&
                        a.project.name.trim().toUpperCase() < b.project.name.trim().toUpperCase())
                ) {
                    return getSortDirection(sortInfo.direction, false);
                }
                return 0;
            });
            return sortableDocumentHeaders;
        case PVTableSortField.DOCUMENT_NAME:
            sortableDocumentHeaders.sort((a, b) => {
                if (a.document.name.toUpperCase() > b.document.name.trim().toUpperCase()) {
                    return getSortDirection(sortInfo.direction, true);
                }
                if (a.document.name.toUpperCase() < b.document.name.trim().toUpperCase()) {
                    return getSortDirection(sortInfo.direction, false);
                }
                return 0;
            });
            return sortableDocumentHeaders;
        case PVTableSortField.DOCUMENT_DESCRIPTION:
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
            return sortableDocumentHeaders;
        case PVTableSortField.DOCUMENT_MODIFIED:
            sortableDocumentHeaders.sort((a, b) => {
                if (a.document.modified > b.document.modified) {
                    return getSortDirection(sortInfo.direction, true);
                }
                if (a.document.modified < b.document.modified) {
                    return getSortDirection(sortInfo.direction, false);
                }
                return 0;
            });
            return sortableDocumentHeaders;
        default:
            return exhaustiveMatchingGuard(sortInfo.propName);
    }
}
const getSortDirection = (
    direction: SortDirection,
    aIsGreaterThanBByTheOrderingCriterion: boolean,
) => {
    if (aIsGreaterThanBByTheOrderingCriterion) {
        if (direction === SortDirection.ASCENDING) return 1;
        if (direction === SortDirection.DESCENDING) return -1;
    } else if (!aIsGreaterThanBByTheOrderingCriterion) {
        if (direction === SortDirection.ASCENDING) return -1;
        if (direction === SortDirection.DESCENDING) return 1;
    }
    return 0;
};
