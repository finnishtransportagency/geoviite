import { Operation, PublishValidationError } from 'publication/publication-model';
import { fieldComparator } from 'utils/array-utils';
import { Icons } from 'vayla-design-lib/icon/Icon';

export enum SortProps {
    NAME = 'NAME',
    TRACK_NUMBER = 'TRACK_NUMBER',
    OPERATION = 'OPERATION',
    CHANGE_TIME = 'CHANGE_TIME',
    USER_NAME = 'USER_NAME',
    ERRORS = 'ERRORS',
    PUSHED_TO_RATKO = 'PUSHED_TO_RATKO',
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

const includesErrors = (errors: PublishValidationError[]) =>
    errors.some((err) => err.type == 'ERROR');
const includesWarnings = (errors: PublishValidationError[]) =>
    errors.some((err) => err.type == 'WARNING');
const errorSeverityPriority = (errors: PublishValidationError[]) => {
    let priority = 0;
    if (includesErrors(errors)) priority += 2;
    if (includesWarnings(errors)) priority += 1;
    return priority;
};

const operationPriority = (operation: Operation) => {
    if (operation === 'CREATE') return 4;
    if (operation === 'MODIFY') return 3;
    if (operation === 'DELETE') return 2;
    if (operation === 'RESTORE') return 1;
    return 0;
};

const nameCompare = fieldComparator((entry: { name: string }) => entry.name.toLocaleLowerCase());
const trackNumberCompare = fieldComparator((entry: { trackNumber: string }) => entry.trackNumber);
const userNameCompare = fieldComparator((entry: { userName: string }) => entry.userName);
const changeTimeCompare = fieldComparator((entry: { changeTime: string }) => entry.changeTime);
const pushedToRatkoCompare = fieldComparator(
    (entry: { ratkoPushDate: string | undefined }) => entry.ratkoPushDate,
);
const errorListCompare = (
    a: { errors: PublishValidationError[] },
    b: { errors: PublishValidationError[] },
) => {
    const priorityBySeverity = errorSeverityPriority(b.errors) - errorSeverityPriority(a.errors);
    return priorityBySeverity !== 0 ? priorityBySeverity : b.errors.length - a.errors.length;
};
const operationCompare = (a: { operation: Operation }, b: { operation: Operation }) =>
    operationPriority(b.operation) - operationPriority(a.operation);

const sortFunctionsByPropName = {
    NAME: nameCompare,
    TRACK_NUMBER: trackNumberCompare,
    OPERATION: operationCompare,
    CHANGE_TIME: changeTimeCompare,
    USER_NAME: userNameCompare,
    ERRORS: errorListCompare,
    PUSHED_TO_RATKO: pushedToRatkoCompare,
};

const nextSortDirection = {
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
