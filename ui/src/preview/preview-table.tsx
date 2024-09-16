import { Table, Th } from 'vayla-design-lib/table/table';
import * as React from 'react';
import { useTranslation } from 'react-i18next';
import {
    LayoutKmPostId,
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { getTrackNumbers } from 'track-layout/layout-track-number-api';

import { negComparator } from 'utils/array-utils';
import {
    getSortInfoForProp,
    InitiallyUnsorted,
    SortInformation,
    SortProps,
} from 'preview/change-table-sorting';
import {
    ChangeTableEntry,
    kmPostChangeTableEntry,
    locationTrackToChangeTableEntry,
    referenceLineToChangeTableEntry,
    switchToChangeTableEntry,
    trackNumberToChangeTableEntry,
} from 'preview/change-table-entry-mapping';
import {
    DraftChangeType,
    PublicationCandidate,
    LayoutValidationIssue,
    PublicationValidationState,
} from 'publication/publication-model';
import { ChangesBeingReverted, PreviewOperations } from 'preview/preview-view';
import { BoundingBox } from 'model/geometry';
import { calculateBoundingBoxToShowAroundLocation } from 'map/map-utils';
import { getSortDirectionIcon, SortDirection } from 'utils/table-utils';
import { useLoader } from 'utils/react-utils';
import { ChangeTimes } from 'common/common-slice';
import { draftLayoutContext, LayoutContext } from 'common/common-model';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { PublicationGroupAmounts } from 'publication/publication-utils';
import styles from './preview-view.scss';
import { PreviewTableItem } from 'preview/preview-table-item';

export type PublishableObjectId =
    | LayoutTrackNumberId
    | ReferenceLineId
    | LocationTrackId
    | LayoutSwitchId
    | LayoutKmPostId;

export type PreviewTableEntry = {
    publishCandidate: PublicationCandidate;
    type: DraftChangeType;
    issues: LayoutValidationIssue[];
    validationState: PublicationValidationState;
    boundingBox?: BoundingBox;
} & ChangeTableEntry;

type PreviewTableProps = {
    layoutContext: LayoutContext;
    publicationCandidates: PublicationCandidate[];
    staged: boolean;
    changesBeingReverted?: ChangesBeingReverted;
    onShowOnMap: (bbox: BoundingBox) => void;
    changeTimes: ChangeTimes;
    publicationGroupAmounts: PublicationGroupAmounts;
    displayedTotalPublicationAssetAmount: number;
    previewOperations: PreviewOperations;
    tableValidationState: PublicationValidationState;
    tableEntryValidationState: (tableEntry: PreviewTableEntry) => PublicationValidationState;
    canRevertChanges: boolean;
};

const PreviewTable: React.FC<PreviewTableProps> = ({
    layoutContext,
    publicationCandidates,
    staged,
    changesBeingReverted,
    changeTimes,
    publicationGroupAmounts,
    displayedTotalPublicationAssetAmount,
    previewOperations,
    onShowOnMap,
    tableValidationState,
    tableEntryValidationState,
    canRevertChanges,
}) => {
    const { t } = useTranslation();
    const trackNumbers =
        useLoader(
            () =>
                getTrackNumbers(
                    draftLayoutContext(layoutContext),
                    changeTimes.layoutTrackNumber,
                    true,
                ),
            [changeTimes.layoutTrackNumber],
        ) || [];

    const [sortInfo, setSortInfo] = React.useState<SortInformation>(InitiallyUnsorted);

    const getTableEntryByType = (candidate: PublicationCandidate): ChangeTableEntry => {
        switch (candidate.type) {
            case DraftChangeType.TRACK_NUMBER:
                return trackNumberToChangeTableEntry(candidate);
            case DraftChangeType.LOCATION_TRACK:
                return locationTrackToChangeTableEntry(candidate, trackNumbers);
            case DraftChangeType.REFERENCE_LINE:
                return referenceLineToChangeTableEntry(candidate, trackNumbers);
            case DraftChangeType.SWITCH:
                return switchToChangeTableEntry(candidate, trackNumbers);
            case DraftChangeType.KM_POST:
                return kmPostChangeTableEntry(candidate, trackNumbers);

            default:
                return exhaustiveMatchingGuard(candidate);
        }
    };

    const getBoundingBox = (candidate: PublicationCandidate): BoundingBox | undefined => {
        const candidateType = candidate.type;
        switch (candidateType) {
            case DraftChangeType.TRACK_NUMBER:
            case DraftChangeType.LOCATION_TRACK:
            case DraftChangeType.REFERENCE_LINE:
                return candidate.boundingBox;

            case DraftChangeType.SWITCH:
            case DraftChangeType.KM_POST:
                return candidate.location
                    ? calculateBoundingBoxToShowAroundLocation(candidate.location)
                    : undefined;

            default:
                return exhaustiveMatchingGuard(candidateType);
        }
    };

    const publicationTableEntries: PreviewTableEntry[] = publicationCandidates.map((candidate) => {
        const tableEntry = getTableEntryByType(candidate);
        const boundingBox = getBoundingBox(candidate);

        return {
            ...tableEntry,
            publishCandidate: candidate,
            boundingBox,
            issues: candidate.issues,
            type: candidate.type,
            validationState: candidate.validationState,
        };
    });

    const sortedPublicationEntries =
        sortInfo && sortInfo.direction !== SortDirection.UNSORTED
            ? [...publicationTableEntries].sort(
                  sortInfo.direction == SortDirection.ASCENDING
                      ? sortInfo.function
                      : negComparator(sortInfo.function),
              )
            : [...publicationTableEntries];

    const sortByProp = (propName: SortProps) => {
        const newSortInfo = getSortInfoForProp(sortInfo.direction, sortInfo.propName, propName);
        setSortInfo(newSortInfo);
    };

    const sortableTableHeader = (prop: SortProps, translationKey: string) => (
        <Th
            onClick={() => sortByProp(prop)}
            qa-id={translationKey}
            icon={
                sortInfo.propName === prop ? getSortDirectionIcon(sortInfo.direction) : undefined
            }>
            {t(translationKey)}
        </Th>
    );

    return (
        <div
            className={styles['preview-table__container']}
            qa-id={
                tableValidationState === 'IN_PROGRESS' ? 'table-validation-in-progress' : undefined
            }>
            <Table wide>
                <thead className={styles['preview-table__header']}>
                    <tr>
                        {sortableTableHeader(SortProps.NAME, 'preview-table.change-target')}
                        {sortableTableHeader(
                            SortProps.TRACK_NUMBER,
                            'preview-table.track-number-short',
                        )}
                        {sortableTableHeader(SortProps.OPERATION, 'preview-table.change-type')}
                        {sortableTableHeader(
                            SortProps.CHANGE_TIME,
                            'preview-table.modified-moment',
                        )}
                        {sortableTableHeader(SortProps.USER_NAME, 'preview-table.user')}
                        {sortableTableHeader(SortProps.ISSUES, 'preview-table.status')}
                        <Th>{t('preview-table.actions')}</Th>
                    </tr>
                </thead>
                <tbody>
                    {sortedPublicationEntries.map((entry) => (
                        <React.Fragment key={`${entry.type}_${entry.id}`}>
                            {
                                <PreviewTableItem
                                    tableEntry={entry}
                                    publish={staged}
                                    changesBeingReverted={changesBeingReverted}
                                    onShowOnMap={onShowOnMap}
                                    previewOperations={previewOperations}
                                    publicationGroupAmounts={publicationGroupAmounts}
                                    displayedTotalPublicationAssetAmount={
                                        displayedTotalPublicationAssetAmount
                                    }
                                    validationState={tableEntryValidationState(entry)}
                                    canRevertChanges={canRevertChanges}
                                />
                            }
                        </React.Fragment>
                    ))}
                </tbody>
            </Table>
        </div>
    );
};

export default PreviewTable;
