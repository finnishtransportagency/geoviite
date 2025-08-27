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
    SortablePreviewProps,
    SortedByTimeDesc,
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
import { SortDirection, TableSorting } from 'utils/table-utils';
import { useLoader } from 'utils/react-utils';
import { ChangeTimes } from 'common/common-slice';
import { draftLayoutContext, LayoutContext } from 'common/common-model';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { PublicationGroupAmounts } from 'publication/publication-utils';
import styles from './preview-view.scss';
import { PreviewTableItem } from 'preview/preview-table-item';
import { SortableTableHeader } from 'vayla-design-lib/table/sortable-table-header';

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
    canCancelChanges: boolean;
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
    canCancelChanges,
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

    const [sortInfo, setSortInfo] =
        React.useState<TableSorting<SortablePreviewProps>>(SortedByTimeDesc);

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
                  sortInfo.direction === SortDirection.ASCENDING
                      ? sortInfo.function
                      : negComparator(sortInfo.function),
              )
            : [...publicationTableEntries];

    const sortByProp = (propName: keyof SortablePreviewProps) => {
        const newSortInfo = getSortInfoForProp(sortInfo.direction, sortInfo.propName, propName);
        setSortInfo(newSortInfo);
    };

    return (
        <div
            className={styles['preview-table__container']}
            qa-id={
                tableValidationState === 'IN_PROGRESS' ? 'table-validation-in-progress' : undefined
            }>
            <Table wide>
                <thead className={styles['preview-table__header']}>
                    <tr>
                        <SortableTableHeader
                            prop={'name'}
                            translationKey={'preview-table.change-target'}
                            className={styles['preview-table__header--change-target']}
                            sortInfo={sortInfo}
                            sortByProp={sortByProp}
                        />
                        <SortableTableHeader
                            prop={'trackNumbers'}
                            translationKey={'preview-table.track-number-short'}
                            className={styles['preview-table__header--track-number-short']}
                            sortInfo={sortInfo}
                            sortByProp={sortByProp}
                        />
                        <SortableTableHeader
                            prop={'operation'}
                            translationKey={'preview-table.change-type'}
                            className={styles['preview-table__header--change-type']}
                            sortInfo={sortInfo}
                            sortByProp={sortByProp}
                        />
                        <SortableTableHeader
                            prop={'changeTime'}
                            translationKey={'preview-table.modified-moment'}
                            className={styles['preview-table__header--modified-moment']}
                            sortInfo={sortInfo}
                            sortByProp={sortByProp}
                        />
                        <SortableTableHeader
                            prop={'userName'}
                            translationKey={'preview-table.user'}
                            className={styles['preview-table__header--user']}
                            sortInfo={sortInfo}
                            sortByProp={sortByProp}
                        />
                        <SortableTableHeader
                            prop={'issues'}
                            translationKey={'preview-table.status'}
                            className={styles['preview-table__header--status']}
                            sortInfo={sortInfo}
                            sortByProp={sortByProp}
                        />
                        <Th className={styles['preview-table__header--actions']}>
                            {t('preview-table.actions')}
                        </Th>
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
                                    canCancelChanges={canCancelChanges}
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
