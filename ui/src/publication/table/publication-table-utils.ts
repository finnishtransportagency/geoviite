import {
    Operation,
    PublicationDetails,
    PublishedLocationTrack,
    PublishedSwitch,
    PublishedTrackNumber,
} from 'publication/publication-model';
import {
    fieldComparator,
    filterNotEmpty,
    nonEmptyArray,
    timeStampComparator,
} from 'utils/array-utils';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { KmNumber, TrackNumber } from 'common/common-model';
import { LayoutTrackNumber, LayoutTrackNumberId } from 'track-layout/track-layout-model';
import { PublicationTableRowProps } from 'publication/table/publication-table-row';
import { RatkoPushStatus } from 'ratko/ratko-model';
import i18n from 'i18next';

export enum SortProps {
    NAME = 'NAME',
    CHANGED_KM_NUMBERS = 'CHANGED_KM_NUMBERS',
    TRACK_NUMBERS = 'TRACK_NUMBERS',
    OPERATION = 'OPERATION',
    PUBLICATION_TIME = 'PUBLICATION_TIME',
    PUBLICATION_USER = 'PUBLICATION_USER',
    RATKO_PUSH_TIME = 'RATKO_PUSH_TIME',
    DEFINITION = 'DEFINITION',
}

export enum SortDirection {
    ASCENDING = 'ASCENDING',
    DESCENDING = 'DESCENDING',
    UNSORTED = 'UNSORTED',
}

export type SortInformation = {
    propName: SortProps;
    direction: SortDirection;
    function: (v1: unknown, v2: unknown) => number;
};

export const operationPriority = (operation: Operation | null) => {
    if (operation === 'CREATE') return 4;
    if (operation === 'MODIFY') return 3;
    if (operation === 'DELETE') return 2;
    if (operation === 'RESTORE') return 1;
    return 0;
};

const nameCompare = fieldComparator((entry: PublicationTableRowProps) =>
    entry.name.toLocaleLowerCase(),
);

const definitionCompare = fieldComparator((entry: PublicationTableRowProps) =>
    entry.message.toLocaleLowerCase(),
);

const compareStringArrays = (a: string[] | undefined, b: string[] | undefined) => {
    const aIsEmpty = !a || a.length === 0;
    const bIsEmpty = !b || b.length === 0;

    if (aIsEmpty && bIsEmpty) return 0;
    if (aIsEmpty) return 1;
    if (bIsEmpty) return -1;

    const minA = a.sort()[0];
    const minB = b.sort()[0];

    return minA < minB ? -1 : minA == minB ? 0 : 1;
};

const changedKmNumbersCompare = (a: PublicationTableRowProps, b: PublicationTableRowProps) =>
    compareStringArrays(a.changedKmNumbers, b.changedKmNumbers);

const trackNumberCompare = (a: PublicationTableRowProps, b: PublicationTableRowProps) =>
    compareStringArrays(a.trackNumbers, b.trackNumbers);

const publicationUserCompare = fieldComparator(
    (entry: PublicationTableRowProps) => entry.publicationUser,
);

const publicationTimeCompare = fieldComparator(
    (entry: PublicationTableRowProps) => entry.publicationTime,
);

const ratkoPushTimeCompare = timeStampComparator(
    (entry: PublicationTableRowProps) => entry.ratkoPushTime,
);

const operationCompare = (a: PublicationTableRowProps, b: PublicationTableRowProps) =>
    operationPriority(b.operation) - operationPriority(a.operation);

const sortFunctionsByPropName = {
    NAME: nameCompare,
    TRACK_NUMBERS: trackNumberCompare,
    OPERATION: operationCompare,
    PUBLICATION_TIME: publicationTimeCompare,
    PUBLICATION_USER: publicationUserCompare,
    RATKO_PUSH_TIME: ratkoPushTimeCompare,
    DEFINITION: definitionCompare,
    CHANGED_KM_NUMBERS: changedKmNumbersCompare,
};

export const nextSortDirection = {
    ASCENDING: SortDirection.DESCENDING,
    DESCENDING: SortDirection.UNSORTED,
    UNSORTED: SortDirection.ASCENDING,
};

export const InitiallyUnsorted = {
    propName: SortProps.NAME,
    direction: SortDirection.UNSORTED,
    function: (_a: unknown, _b: unknown) => 0,
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
    function: sortFunctionsByPropName[newSortPropName],
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

function findTrackNumber(trackNumbers: LayoutTrackNumber[], id: LayoutTrackNumberId) {
    return trackNumbers.find((t) => t.id == id)?.number;
}

export const toPublicationTableRows = (
    publication: PublicationDetails,
    trackNumbers: LayoutTrackNumber[],
): PublicationTableRowProps[] => {
    return [
        ...publishedChangesToTableRows(publication, trackNumbers),
        ...calculatedChangesToPublicationTableRows(publication, trackNumbers),
    ];
};

const publishedChangesToTableRows = (
    publication: PublicationDetails,
    trackNumbers: LayoutTrackNumber[],
) => {
    const publicationInfo = {
        publicationTime: publication.publicationTime,
        publicationUser: publication.publicationUser,
        ratkoPushTime:
            publication.ratkoPushStatus === RatkoPushStatus.SUCCESSFUL
                ? publication.ratkoPushTime
                : null,
        message: publication.message ?? '',
    };

    const trackNumberItems = publishedTrackNumbersToTableRows(publication.trackNumbers);

    const referenceLines = publication.referenceLines.map((referenceLine) => ({
        name: getReferenceLineUiName(findTrackNumber(trackNumbers, referenceLine.trackNumberId)),
        trackNumbers: nonEmptyArray(findTrackNumber(trackNumbers, referenceLine.trackNumberId)),
        operation: referenceLine.operation,
        changedKmNumbers: referenceLine.changedKmNumbers,
    }));

    const locationTracks = publishedLocationTracksToTableRows(
        publication.locationTracks,
        trackNumbers,
    );

    const switches = publishedSwitchesToTableRows(publication.switches, trackNumbers);

    const kmPosts = publication.kmPosts.map((kmPost) => ({
        name: getKmPostUiName(kmPost.kmNumber),
        trackNumbers: nonEmptyArray(findTrackNumber(trackNumbers, kmPost.trackNumberId)),
        operation: kmPost.operation,
    }));

    return [...trackNumberItems, ...referenceLines, ...locationTracks, ...switches, ...kmPosts].map(
        (c) => ({
            ...publicationInfo,
            ...c,
        }),
    );
};

const calculatedChangesToPublicationTableRows = (
    publication: PublicationDetails,
    trackNumbers: LayoutTrackNumber[],
): PublicationTableRowProps[] => {
    const calculatedPublicationInfo = {
        publicationTime: publication.publicationTime,
        publicationUser: publication.publicationUser,
        ratkoPushTime:
            publication.ratkoPushStatus === RatkoPushStatus.SUCCESSFUL
                ? publication.ratkoPushTime
                : null,
        message: i18n.t('publication-table.calculated-change'),
    };

    const calculatedTrackNumbers = publishedTrackNumbersToTableRows(
        publication.calculatedChanges.trackNumbers,
    );

    const calculatedLocationTracks = publishedLocationTracksToTableRows(
        publication.calculatedChanges.locationTracks,
        trackNumbers,
    );

    const calculatedSwitches = publishedSwitchesToTableRows(
        publication.calculatedChanges.switches,
        trackNumbers,
    );

    return [...calculatedTrackNumbers, ...calculatedLocationTracks, ...calculatedSwitches].map(
        (c) => ({
            ...calculatedPublicationInfo,
            ...c,
        }),
    );
};

const publishedTrackNumbersToTableRows = (publishedTrackNumbers: PublishedTrackNumber[]) => {
    return publishedTrackNumbers.map((trackNumber) => ({
        name: getTrackNumberUiName(trackNumber.number),
        trackNumbers: [trackNumber.number],
        operation: trackNumber.operation,
    }));
};

const publishedLocationTracksToTableRows = (
    locationTracks: PublishedLocationTrack[],
    trackNumbers: LayoutTrackNumber[],
) => {
    return locationTracks.map((locationTrack) => ({
        name: getLocationTrackUiName(locationTrack.name),
        trackNumbers: nonEmptyArray(findTrackNumber(trackNumbers, locationTrack.trackNumberId)),
        operation: locationTrack.operation,
        changedKmNumbers: locationTrack.changedKmNumbers,
    }));
};

const publishedSwitchesToTableRows = (
    switches: PublishedSwitch[],
    trackNumbers: LayoutTrackNumber[],
) => {
    return switches.map((s) => ({
        name: getSwitchUiName(s.name),
        trackNumbers: s.trackNumberIds
            .map((id) => findTrackNumber(trackNumbers, id))
            .filter(filterNotEmpty),
        operation: s.operation,
    }));
};
