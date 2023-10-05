import { nextSortDirection, SortDirection } from 'utils/table-utils';

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
