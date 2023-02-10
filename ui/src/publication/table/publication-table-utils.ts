import { Operation } from 'publication/publication-model';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { KmNumber, TrackNumber } from 'common/common-model';
import i18n from 'i18next';

export enum SortProps {
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

export type SortInformation = {
    propName: SortProps;
    direction: SortDirection;
};

export const operationPriority = (operation: Operation | null) => {
    if (operation === 'CREATE') return 4;
    if (operation === 'MODIFY') return 3;
    if (operation === 'DELETE') return 2;
    if (operation === 'RESTORE') return 1;
    return 0;
};

export const nextSortDirection = {
    ASCENDING: SortDirection.DESCENDING,
    DESCENDING: SortDirection.UNSORTED,
    UNSORTED: SortDirection.ASCENDING,
};

export const InitiallyUnsorted = {
    propName: SortProps.NAME,
    direction: SortDirection.UNSORTED,
};

export const getSortInfoForProp = (
    oldSortDirection: SortDirection,
    oldSortPropName: SortProps,
    newSortPropName: SortProps,
) => ({
    propName: newSortPropName,
    direction:
        oldSortPropName === newSortPropName
            ? nextSortDirection[oldSortDirection]
            : SortDirection.ASCENDING,
});

export const sortDirectionIcon = (direction: SortDirection) => {
    switch (direction) {
        case SortDirection.ASCENDING:
            return Icons.Ascending;
        case SortDirection.DESCENDING:
            return Icons.Descending;
        case SortDirection.UNSORTED:
            return undefined;
    }
};

export function getTrackNumberUiName(trackNumber: TrackNumber | undefined) {
    return `${i18n.t('publication-table.track-number-long')} ${trackNumber}`;
}

export function getReferenceLineUiName(trackNumber: TrackNumber | undefined) {
    return `${i18n.t('publication-table.reference-line')} ${trackNumber}`;
}

export function getLocationTrackUiName(name: string) {
    return `${i18n.t('publication-table.location-track')} ${name}`;
}

export function getSwitchUiName(name: string) {
    return `${i18n.t('publication-table.switch')} ${name}`;
}

export function getKmPostUiName(kmNumber: KmNumber) {
    return `${i18n.t('publication-table.km-post')} ${kmNumber}`;
}
