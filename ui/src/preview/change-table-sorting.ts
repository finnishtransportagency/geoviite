import { LayoutValidationIssue, validationIssueIsError } from 'publication/publication-model';
import { fieldComparator } from 'utils/array-utils';
import { nextSortDirection, SortDirection } from 'utils/table-utils';
import { publicationOperationCompare } from 'sorting/publication-sorting';

export enum SortProps {
    NAME = 'NAME',
    TRACK_NUMBER = 'TRACK_NUMBER',
    OPERATION = 'OPERATION',
    CHANGE_TIME = 'CHANGE_TIME',
    USER_NAME = 'USER_NAME',
    ISSUES = 'ISSUES',
}

export type SortInformation = {
    propName: SortProps;
    direction: SortDirection;
    function: (v1: unknown, v2: unknown) => number;
};

const includesErrors = (issues: LayoutValidationIssue[]) =>
    issues.some((err) => validationIssueIsError(err.type));
const includesWarnings = (issues: LayoutValidationIssue[]) =>
    issues.some((err) => !validationIssueIsError(err.type));
const issueSeverityPriority = (issues: LayoutValidationIssue[]) => {
    let priority = 0;
    if (includesErrors(issues)) priority += 2;
    if (includesWarnings(issues)) priority += 1;
    return priority;
};

const nameCompare = fieldComparator((entry: { name: string }) => entry.name.toLocaleLowerCase());
const trackNumberCompare = fieldComparator((entry: { trackNumber: string }) => entry.trackNumber);
const userNameCompare = fieldComparator((entry: { userName: string }) => entry.userName);
const changeTimeCompare = fieldComparator((entry: { changeTime: string }) => entry.changeTime);
const issueListCompare = (
    a: { issues: LayoutValidationIssue[] },
    b: { issues: LayoutValidationIssue[] },
) => {
    const priorityBySeverity = issueSeverityPriority(b.issues) - issueSeverityPriority(a.issues);
    return priorityBySeverity !== 0 ? priorityBySeverity : b.issues.length - a.issues.length;
};

const sortFunctionsByPropName = {
    NAME: nameCompare,
    TRACK_NUMBER: trackNumberCompare,
    OPERATION: publicationOperationCompare,
    CHANGE_TIME: changeTimeCompare,
    USER_NAME: userNameCompare,
    ISSUES: issueListCompare,
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
