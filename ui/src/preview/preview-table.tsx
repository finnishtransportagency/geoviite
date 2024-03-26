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
import styles from './preview-view.scss';
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
import { PreviewTableItem } from 'preview/preview-table-item';
import { PublishValidationError } from 'publication/publication-model';
import { ChangesBeingReverted, PreviewOperations } from 'preview/preview-view';
import { BoundingBox } from 'model/geometry';
import { calculateBoundingBoxToShowAroundLocation } from 'map/map-utils';
import { getSortDirectionIcon, SortDirection } from 'utils/table-utils';
import { useLoader } from 'utils/react-utils';
import { ChangeTimes } from 'common/common-slice';
import { PreviewCandidates, PublicationAssetChangeAmounts } from 'preview/preview-view-data';
import { draftLayoutContext, LayoutContext } from 'common/common-model';

export type PublishableObjectId =
    | LayoutTrackNumberId
    | ReferenceLineId
    | LocationTrackId
    | LayoutSwitchId
    | LayoutKmPostId;

export type PreviewTableEntry = {
    type: PreviewSelectType;
    errors: PublishValidationError[];
    pendingValidation: boolean;
    boundingBox?: BoundingBox;
} & ChangeTableEntry;

export enum PreviewSelectType {
    trackNumber = 'trackNumber',
    referenceLine = 'referenceLine',
    locationTrack = 'locationTrack',
    switch = 'switch',
    kmPost = 'kmPost',
}

type PreviewTableProps = {
    layoutContext: LayoutContext;
    previewChanges: PreviewCandidates;
    staged: boolean;
    changesBeingReverted?: ChangesBeingReverted;
    onShowOnMap: (bbox: BoundingBox) => void;
    changeTimes: ChangeTimes;
    publicationAssetChangeAmounts: PublicationAssetChangeAmounts;
    previewOperations: PreviewOperations;
};

const PreviewTable: React.FC<PreviewTableProps> = ({
    layoutContext,
    previewChanges,
    staged,
    changesBeingReverted,
    changeTimes,
    publicationAssetChangeAmounts,
    previewOperations,
    onShowOnMap,
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

    const changesToPublicationEntries = (
        previewChanges: PreviewCandidates,
    ): PreviewTableEntry[] => [
        ...previewChanges.trackNumbers.map((trackNumberCandidate) => ({
            ...trackNumberToChangeTableEntry(trackNumberCandidate),
            errors: trackNumberCandidate.errors,
            type: PreviewSelectType.trackNumber,
            pendingValidation: trackNumberCandidate.pendingValidation,
            boundingBox: trackNumberCandidate.boundingBox,
        })),
        ...previewChanges.referenceLines.map((referenceLineCandidate) => ({
            ...referenceLineToChangeTableEntry(referenceLineCandidate, trackNumbers),
            errors: referenceLineCandidate.errors,
            type: PreviewSelectType.referenceLine,
            pendingValidation: referenceLineCandidate.pendingValidation,
            boundingBox: referenceLineCandidate.boundingBox,
        })),
        ...previewChanges.locationTracks.map((locationTrackCandidate) => ({
            ...locationTrackToChangeTableEntry(locationTrackCandidate, trackNumbers),
            errors: locationTrackCandidate.errors,
            type: PreviewSelectType.locationTrack,
            pendingValidation: locationTrackCandidate.pendingValidation,
            boundingBox: locationTrackCandidate.boundingBox,
        })),
        ...previewChanges.switches.map((switchCandidate) => ({
            ...switchToChangeTableEntry(switchCandidate, trackNumbers),
            errors: switchCandidate.errors,
            type: PreviewSelectType.switch,
            pendingValidation: switchCandidate.pendingValidation,
            boundingBox: switchCandidate.location
                ? calculateBoundingBoxToShowAroundLocation(switchCandidate.location)
                : undefined,
        })),
        ...previewChanges.kmPosts.map((kmPostCandidate) => ({
            ...kmPostChangeTableEntry(kmPostCandidate, trackNumbers),
            errors: kmPostCandidate.errors,
            type: PreviewSelectType.kmPost,
            pendingValidation: kmPostCandidate.pendingValidation,
            boundingBox: kmPostCandidate.location
                ? calculateBoundingBoxToShowAroundLocation(kmPostCandidate.location)
                : undefined,
        })),
    ];

    const publicationEntries: PreviewTableEntry[] = changesToPublicationEntries(previewChanges);

    const sortedPublicationEntries =
        sortInfo && sortInfo.direction !== SortDirection.UNSORTED
            ? [...publicationEntries].sort(
                  sortInfo.direction == SortDirection.ASCENDING
                      ? sortInfo.function
                      : negComparator(sortInfo.function),
              )
            : [...publicationEntries];

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
        <div className={styles['preview-table__container']}>
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
                        {sortableTableHeader(SortProps.ERRORS, 'preview-table.status')}
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
                                    publicationAssetChangeAmounts={publicationAssetChangeAmounts}
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
