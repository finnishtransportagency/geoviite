import { LayoutValidationIssue, validationIssueIsError } from 'publication/publication-model';
import { Comparator, fieldComparator } from 'utils/array-utils';
import { nextSortDirection, SortDirection, TableSorting } from 'utils/table-utils';
import { publicationOperationCompare } from 'sorting/publication-sorting';
import { PreviewTableEntry } from 'preview/preview-table';

export type SortablePreviewProps = Pick<
    PreviewTableEntry,
    'name' | 'trackNumbers' | 'operation' | 'changeTime' | 'userName' | 'issues'
>;

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

const nameCompare = fieldComparator((entry: Pick<PreviewTableEntry, 'name'>) =>
    entry.name.toLocaleLowerCase(),
);

const trackNumberCompare = fieldComparator((entry: Pick<PreviewTableEntry, 'trackNumbers'>) =>
    entry.trackNumbers.join(','),
);

const userNameCompare = fieldComparator(
    (entry: Pick<PreviewTableEntry, 'userName'>) => entry.userName,
);

const changeTimeCompare: Comparator<PreviewTableEntry> = fieldComparator(
    (entry: PreviewTableEntry) => entry.changeTime,
);

const issueListCompare = (
    a: Pick<PreviewTableEntry, 'issues'>,
    b: Pick<PreviewTableEntry, 'issues'>,
) => {
    const priorityBySeverity = issueSeverityPriority(b.issues) - issueSeverityPriority(a.issues);
    return priorityBySeverity !== 0 ? priorityBySeverity : b.issues.length - a.issues.length;
};

const sortFunctionsByPropName: {
    [key in keyof SortablePreviewProps]: Comparator<SortablePreviewProps>;
} = {
    name: nameCompare,
    trackNumbers: trackNumberCompare,
    operation: publicationOperationCompare,
    changeTime: changeTimeCompare,
    userName: userNameCompare,
    issues: issueListCompare,
};

export const SortedByTimeDesc: TableSorting<SortablePreviewProps> = {
    propName: 'changeTime',
    direction: SortDirection.DESCENDING,
    function: changeTimeCompare,
};

export const getSortInfoForProp = (
    oldSortDirection: SortDirection,
    oldSortPropName: keyof SortablePreviewProps,
    newSortPropName: keyof SortablePreviewProps,
) => ({
    propName: newSortPropName,
    direction:
        oldSortPropName === newSortPropName
            ? nextSortDirection[oldSortDirection]
            : SortDirection.ASCENDING,
    function: sortFunctionsByPropName[newSortPropName],
});
