import { Table, Th } from 'vayla-design-lib/table/table';
import * as React from 'react';
import { useTranslation } from 'react-i18next';
import {
    LayoutKmPostId,
    LayoutSwitchId,
    LayoutTrackNumber,
    LayoutTrackNumberId,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { getTrackNumbers } from 'track-layout/layout-track-number-api';
import styles from '../publication/publication-table.scss';
import { SelectedPublishChange } from 'track-layout/track-layout-store';
import { negComparator } from 'utils/array-utils';
import {
    getSortInfoForProp,
    InitiallyUnsorted,
    SortDirection,
    sortDirectionIcon,
    SortInformation,
    SortProps,
} from 'preview/change-table-sorting';
import {
    ChangeTableEntry,
    ChangeTableProps,
    kmPostChangeTableEntry,
    locationTrackToChangeTableEntry,
    referenceLineToChangeTableEntry,
    switchToChangeTableEntry,
    trackNumberToChangeTableEntry,
} from 'preview/change-table-entry-mapping';
import { PreviewTableItem } from 'preview/preview-table-item';
import { PublishCandidates, PublishValidationError } from 'publication/publication-model';

type PublicationId =
    | LayoutTrackNumberId
    | ReferenceLineId
    | LocationTrackId
    | LayoutSwitchId
    | LayoutKmPostId;

type PreviewTableEntry = {
    type: PreviewSelectType;
    errors: PublishValidationError[];
} & ChangeTableEntry;

enum PreviewSelectType {
    trackNumber = 'trackNumber',
    referenceLine = 'referenceLine',
    locationTrack = 'locationTrack',
    switch = 'switch',
    kmPost = 'kmPost',
}

const PreviewTable: React.FC<ChangeTableProps> = ({
    previewChanges,
    onPreviewSelect = undefined,
    publish = false,
}) => {
    const { t } = useTranslation();
    const [trackNumbers, setTrackNumbers] = React.useState<LayoutTrackNumber[]>([]);
    React.useEffect(() => {
        getTrackNumbers('DRAFT').then((trackNumbers) => setTrackNumbers(trackNumbers));
    }, []);

    const [sortInfo, setSortInfo] = React.useState<SortInformation>(InitiallyUnsorted);

    const defaultSelectedPublishChange: SelectedPublishChange = {
        trackNumber: undefined,
        referenceLine: undefined,
        locationTrack: undefined,
        switch: undefined,
        kmPost: undefined,
    };

    function handlePreviewSelect<T>(id: PublicationId, type: T) {
        switch (type) {
            case PreviewSelectType.trackNumber:
                onPreviewSelect &&
                    onPreviewSelect({ ...defaultSelectedPublishChange, trackNumber: id });
                break;
            case PreviewSelectType.referenceLine:
                onPreviewSelect &&
                    onPreviewSelect({ ...defaultSelectedPublishChange, referenceLine: id });
                break;
            case PreviewSelectType.locationTrack:
                onPreviewSelect &&
                    onPreviewSelect({ ...defaultSelectedPublishChange, locationTrack: id });
                break;
            case PreviewSelectType.switch:
                onPreviewSelect && onPreviewSelect({ ...defaultSelectedPublishChange, switch: id });
                break;
            case PreviewSelectType.kmPost:
                onPreviewSelect && onPreviewSelect({ ...defaultSelectedPublishChange, kmPost: id });
                break;
            default:
                onPreviewSelect && onPreviewSelect(defaultSelectedPublishChange);
        }
    }

    const changesToPublicationEntries = (previewChanges: PublishCandidates): PreviewTableEntry[] =>
        previewChanges.trackNumbers
            .map((trackNumberCandidate) => ({
                ...trackNumberToChangeTableEntry(trackNumberCandidate, t),
                errors: trackNumberCandidate.errors,
                type: PreviewSelectType.trackNumber,
            }))
            .concat(
                previewChanges.referenceLines.map((referenceLineCandidate) => ({
                    ...referenceLineToChangeTableEntry(referenceLineCandidate, trackNumbers, t),
                    errors: referenceLineCandidate.errors,
                    type: PreviewSelectType.referenceLine,
                })),
            )
            .concat(
                previewChanges.locationTracks.map((locationTrackCandidate) => ({
                    ...locationTrackToChangeTableEntry(locationTrackCandidate, trackNumbers, t),
                    errors: locationTrackCandidate.errors,
                    type: PreviewSelectType.locationTrack,
                })),
            )
            .concat(
                previewChanges.switches.map((switchCandidate) => ({
                    ...switchToChangeTableEntry(switchCandidate, trackNumbers, t),
                    errors: switchCandidate.errors,
                    type: PreviewSelectType.switch,
                })),
            )
            .concat(
                previewChanges.kmPosts.map((kmPostCandidate) => ({
                    ...kmPostChangeTableEntry(kmPostCandidate, trackNumbers, t),
                    errors: kmPostCandidate.errors,
                    type: PreviewSelectType.kmPost,
                })),
            );

    const publicationEntries = changesToPublicationEntries(previewChanges);

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

    return (
        <div className={styles['publication-table__container']}>
            <Table wide>
                <thead className={styles['publication-table__header']}>
                    <tr>
                        <Th
                            onClick={() => sortByProp(SortProps.NAME)}
                            icon={
                                sortInfo.propName === SortProps.NAME
                                    ? sortDirectionIcon(sortInfo.direction)
                                    : undefined
                            }>
                            {t('publication-table.change-target')}
                        </Th>
                        <Th
                            onClick={() => sortByProp(SortProps.TRACK_NUMBER)}
                            icon={
                                sortInfo.propName === SortProps.TRACK_NUMBER
                                    ? sortDirectionIcon(sortInfo.direction)
                                    : undefined
                            }>
                            {t('publication-table.track-number-short')}
                        </Th>
                        <Th
                            onClick={() => sortByProp(SortProps.OPERATION)}
                            icon={
                                sortInfo.propName === SortProps.OPERATION
                                    ? sortDirectionIcon(sortInfo.direction)
                                    : undefined
                            }>
                            {t('publication-table.change-type')}
                        </Th>
                        <Th
                            onClick={() => sortByProp(SortProps.CHANGE_TIME)}
                            icon={
                                sortInfo.propName === SortProps.CHANGE_TIME
                                    ? sortDirectionIcon(sortInfo.direction)
                                    : undefined
                            }>
                            {t('publication-table.modified-moment')}
                        </Th>
                        <Th
                            onClick={() => sortByProp(SortProps.USER_NAME)}
                            icon={
                                sortInfo.propName === SortProps.USER_NAME
                                    ? sortDirectionIcon(sortInfo.direction)
                                    : undefined
                            }>
                            {t('publication-table.user')}
                        </Th>
                        <Th
                            onClick={() => sortByProp(SortProps.ERRORS)}
                            icon={
                                sortInfo.propName === SortProps.ERRORS
                                    ? sortDirectionIcon(sortInfo.direction)
                                    : undefined
                            }>
                            {t('publication-table.status')}
                        </Th>

                        <Th>{t('publication-table.actions')}</Th>
                    </tr>
                </thead>
                <tbody>
                    {sortedPublicationEntries.map((entry) => (
                        <React.Fragment key={entry.id}>
                            {
                                <PreviewTableItem
                                    onPublishItemSelect={() =>
                                        handlePreviewSelect(entry.id, entry.type)
                                    }
                                    itemName={entry.uiName}
                                    trackNumber={entry.trackNumber}
                                    errors={entry.errors}
                                    changeTime={entry.changeTime}
                                    userName={entry.userName}
                                    operation={entry.operation}
                                    publish={publish}
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
