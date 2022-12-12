import { Table, Th } from 'vayla-design-lib/table/table';
import { PublicationTableItem } from 'publication/publication-table-item';
import * as React from 'react';
import {
    KmPostPublishCandidate,
    LocationTrackPublishCandidate,
    Operation,
    PublishCandidates,
    PublishValidationError,
    ReferenceLinePublishCandidate,
    SwitchPublishCandidate,
    TrackNumberPublishCandidate,
} from 'publication/publication-model';
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
import { TimeStamp } from 'common/common-model';
import styles from './publication-table.scss';
import { SelectedPublishChange } from 'track-layout/track-layout-store';
import { negComparator } from 'utils/array-utils';
import { Icons } from 'vayla-design-lib/icon/Icon';
import {
    getSortInfoForProp,
    InitiallyUnsorted,
    SortDirection,
    SortInformation,
    SortProps,
} from 'publication/publication-table-sorting';

type PublicationTableProps = {
    previewChanges: PublishCandidates;
    showRatkoPushDate?: boolean;
    showStatus?: boolean;
    showActions?: boolean;
    ratkoPushDate?: TimeStamp;
    onPreviewSelect?: (selectedChanges: SelectedPublishChange) => void;
    publish?: boolean;
};

enum PreviewSelectType {
    trackNumber = 'trackNumber',
    referenceLine = 'referenceLine',
    locationTrack = 'locationTrack',
    switch = 'switch',
    kmPost = 'kmPost',
}

type PublicationId =
    | LayoutTrackNumberId
    | ReferenceLineId
    | LocationTrackId
    | LayoutSwitchId
    | LayoutKmPostId;

type PublicationEntry = {
    id: LayoutTrackNumberId | LocationTrackId | LayoutSwitchId | ReferenceLineId | LayoutKmPostId;
    type: PreviewSelectType;
    name: string;
    uiName: string;
    errors: PublishValidationError[];
    trackNumber: string;
    changeTime: string;
    userName: string;
    operation: Operation;
};

export const sortDirectionIcon = (direction: SortDirection) =>
    direction === SortDirection.ASCENDING
        ? Icons.Ascending
        : direction === SortDirection.DESCENDING
        ? Icons.Descending
        : undefined;

const PublicationTable: React.FC<PublicationTableProps> = ({
    previewChanges,
    showRatkoPushDate = false,
    showStatus = false,
    showActions = false,
    ratkoPushDate = undefined,
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
                onPreviewSelect &&
                    onPreviewSelect({ ...defaultSelectedPublishChange, locationTrack: id });
                break;
            case PreviewSelectType.kmPost:
                onPreviewSelect &&
                    onPreviewSelect({ ...defaultSelectedPublishChange, locationTrack: id });
                break;
            default:
                onPreviewSelect && onPreviewSelect(defaultSelectedPublishChange);
        }
    }

    const trackNumberToPublicationEntry = (trackNumber: TrackNumberPublishCandidate) => ({
        id: trackNumber.id,
        type: PreviewSelectType.trackNumber,
        uiName: `${t('publication-table.track-number-long')} ${trackNumber.number}`,
        name: trackNumber.number,
        userName: trackNumber.userName,
        trackNumber: trackNumber.number,
        changeTime: trackNumber.draftChangeTime,
        errors: trackNumber.errors,
        operation: trackNumber.operation,
    });
    const referenceLineToPublicationEntry = (referenceLine: ReferenceLinePublishCandidate) => {
        const trackNumber = trackNumbers.find((tn) => tn.id === referenceLine.trackNumberId);
        return {
            id: referenceLine.id,
            type: PreviewSelectType.referenceLine,
            uiName: `${t('publication-table.reference-line')} ${referenceLine.name}`,
            name: referenceLine.name,
            userName: referenceLine.userName,
            trackNumber: trackNumber ? trackNumber.number : '',
            changeTime: referenceLine.draftChangeTime,
            errors: referenceLine.errors,
            operation: referenceLine.operation,
        };
    };
    const locationTrackToPublicationEntry = (locationTrack: LocationTrackPublishCandidate) => {
        const trackNumber = trackNumbers.find((tn) => tn.id === locationTrack.trackNumberId);
        return {
            id: locationTrack.id,
            type: PreviewSelectType.locationTrack,
            uiName: `${t('publication-table.location-track')} ${locationTrack.name}`,
            name: locationTrack.name,
            userName: locationTrack.userName,
            trackNumber: trackNumber ? trackNumber.number : '',
            changeTime: locationTrack.draftChangeTime,
            errors: locationTrack.errors,
            operation: locationTrack.operation,
        };
    };
    const switchToPublicationEntry = (layoutSwitch: SwitchPublishCandidate) => {
        const trackNumber = trackNumbers
            .filter((tn) => layoutSwitch.trackNumberIds.some((lstn) => lstn == tn.id))
            .map((tn) => tn.number)
            .join(', ');
        return {
            id: layoutSwitch.id,
            type: PreviewSelectType.switch,
            uiName: `${t('publication-table.switch')} ${layoutSwitch.name}`,
            name: layoutSwitch.name,
            userName: layoutSwitch.userName,
            trackNumber,
            changeTime: layoutSwitch.draftChangeTime,
            errors: layoutSwitch.errors,
            operation: layoutSwitch.operation,
        };
    };
    const kmPostPublicationEntry = (kmPost: KmPostPublishCandidate) => {
        const trackNumber = trackNumbers.find((tn) => tn.id === kmPost.trackNumberId);
        return {
            id: kmPost.id,
            type: PreviewSelectType.kmPost,
            uiName: `${t('publication-table.km-post')} ${kmPost.kmNumber}`,
            name: kmPost.kmNumber,
            userName: kmPost.userName,
            trackNumber: trackNumber ? trackNumber.number : '',
            changeTime: kmPost.draftChangeTime,
            errors: kmPost.errors,
            operation: kmPost.operation,
        };
    };

    const publicationEntries: PublicationEntry[] =
        previewChanges.trackNumbers
            .map(trackNumberToPublicationEntry)
            .concat(previewChanges.referenceLines.map(referenceLineToPublicationEntry))
            .concat(previewChanges.locationTracks.map(locationTrackToPublicationEntry))
            .concat(previewChanges.switches.map(switchToPublicationEntry))
            .concat(previewChanges.kmPosts.map(kmPostPublicationEntry)) || [];

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
                        {showStatus && (
                            <Th
                                onClick={() => sortByProp(SortProps.ERRORS)}
                                icon={
                                    sortInfo.propName === SortProps.ERRORS
                                        ? sortDirectionIcon(sortInfo.direction)
                                        : undefined
                                }>
                                {t('publication-table.status')}
                            </Th>
                        )}
                        {showRatkoPushDate && <Th>{t('publication-table.exported-to-ratko')}</Th>}
                        {showActions && <Th>{t('publication-table.actions')}</Th>}
                    </tr>
                </thead>
                <tbody>
                    {sortedPublicationEntries.map((entry) => (
                        <React.Fragment key={entry.id}>
                            {
                                <PublicationTableItem
                                    onPublishItemSelect={() =>
                                        handlePreviewSelect(entry.id, entry.type)
                                    }
                                    itemName={entry.uiName}
                                    trackNumber={entry.trackNumber}
                                    errors={entry.errors}
                                    changeTime={entry.changeTime}
                                    ratkoPushDate={ratkoPushDate}
                                    showRatkoPushDate={showRatkoPushDate}
                                    showStatus={showStatus}
                                    showActions={showActions}
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

export default PublicationTable;
