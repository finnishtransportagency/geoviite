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
import styles from './preview-view.scss';
import { SelectedPublishChange } from 'track-layout/track-layout-slice';
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
import { ChangesBeingReverted, PreviewCandidates } from 'preview/preview-view';
import { getSortDirectionIcon, SortDirection } from 'publication/table/publication-table-utils';
import { BoundingBox } from 'model/geometry';
import { calculateBoundingBoxToShowAroundLocation } from 'map/map-utils';
import {
    getReferenceLine,
    getTrackNumberReferenceLine,
} from 'track-layout/layout-reference-line-api';
import { getLocationTrack } from 'track-layout/layout-location-track-api';
import { getKmPost } from 'track-layout/layout-km-post-api';
import { getSwitch } from 'track-layout/layout-switch-api';
import { getSwitchStructure } from 'common/common-api';

export type PublicationId =
    | LayoutTrackNumberId
    | ReferenceLineId
    | LocationTrackId
    | LayoutSwitchId
    | LayoutKmPostId;

export type PreviewTableEntry = {
    type: PreviewSelectType;
    errors: PublishValidationError[];
    pendingValidation: boolean;
} & ChangeTableEntry;

export enum PreviewSelectType {
    trackNumber = 'trackNumber',
    referenceLine = 'referenceLine',
    locationTrack = 'locationTrack',
    switch = 'switch',
    kmPost = 'kmPost',
}

type PreviewTableProps = {
    previewChanges: PreviewCandidates;
    onPreviewSelect: (selectedChanges: SelectedPublishChange) => void;
    onRevert: (entry: PreviewTableEntry) => void;
    staged: boolean;
    changesBeingReverted: ChangesBeingReverted | undefined;
    onShowOnMap: (bbox: BoundingBox) => void;
};

const PreviewTable: React.FC<PreviewTableProps> = ({
    previewChanges,
    onPreviewSelect,
    onRevert,
    staged,
    changesBeingReverted,
    onShowOnMap,
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

    const changesToPublicationEntries = (previewChanges: PreviewCandidates): PreviewTableEntry[] =>
        previewChanges.trackNumbers
            .map((trackNumberCandidate) => ({
                ...trackNumberToChangeTableEntry(trackNumberCandidate),
                errors: trackNumberCandidate.errors,
                type: PreviewSelectType.trackNumber,
                pendingValidation: trackNumberCandidate.pendingValidation,
            }))
            .concat(
                previewChanges.referenceLines.map((referenceLineCandidate) => ({
                    ...referenceLineToChangeTableEntry(referenceLineCandidate, trackNumbers),
                    errors: referenceLineCandidate.errors,
                    type: PreviewSelectType.referenceLine,
                    pendingValidation: referenceLineCandidate.pendingValidation,
                })),
            )
            .concat(
                previewChanges.locationTracks.map((locationTrackCandidate) => ({
                    ...locationTrackToChangeTableEntry(locationTrackCandidate, trackNumbers),
                    errors: locationTrackCandidate.errors,
                    type: PreviewSelectType.locationTrack,
                    pendingValidation: locationTrackCandidate.pendingValidation,
                })),
            )
            .concat(
                previewChanges.switches.map((switchCandidate) => ({
                    ...switchToChangeTableEntry(switchCandidate, trackNumbers),
                    errors: switchCandidate.errors,
                    type: PreviewSelectType.switch,
                    pendingValidation: switchCandidate.pendingValidation,
                })),
            )
            .concat(
                previewChanges.kmPosts.map((kmPostCandidate) => ({
                    ...kmPostChangeTableEntry(kmPostCandidate, trackNumbers),
                    errors: kmPostCandidate.errors,
                    type: PreviewSelectType.kmPost,
                    pendingValidation: kmPostCandidate.pendingValidation,
                })),
            );

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
            icon={
                sortInfo.propName === prop ? getSortDirectionIcon(sortInfo.direction) : undefined
            }>
            {t(translationKey)}
        </Th>
    );

    const showOnMap = (id: PublicationId, type: PreviewSelectType) => {
        if (type === PreviewSelectType.locationTrack) {
            getLocationTrack(id as LocationTrackId, 'DRAFT').then(
                (locationTrack) =>
                    locationTrack &&
                    locationTrack.boundingBox &&
                    onShowOnMap(locationTrack.boundingBox),
            );
        } else if (type === PreviewSelectType.referenceLine) {
            getReferenceLine(id as ReferenceLineId, 'DRAFT').then(
                (referenceLine) =>
                    referenceLine &&
                    referenceLine.boundingBox &&
                    onShowOnMap(referenceLine.boundingBox),
            );
        } else if (type === PreviewSelectType.switch) {
            getSwitch(id as LayoutSwitchId, 'DRAFT').then((sw) => {
                getSwitchStructure(sw?.switchStructureId).then((structure) => {
                    const presentationJoint = sw?.joints.find(
                        (joint) => joint.number === structure?.presentationJointNumber,
                    );
                    sw &&
                        presentationJoint &&
                        onShowOnMap(
                            calculateBoundingBoxToShowAroundLocation(presentationJoint.location),
                        );
                });
            });
        } else if (type === PreviewSelectType.kmPost) {
            getKmPost(id as LayoutKmPostId, 'DRAFT').then(
                (kmPost) =>
                    kmPost &&
                    kmPost.location &&
                    onShowOnMap(calculateBoundingBoxToShowAroundLocation(kmPost.location)),
            );
        } else if (type === PreviewSelectType.trackNumber) {
            getTrackNumberReferenceLine(id, 'DRAFT').then(
                (referenceLine) =>
                    referenceLine &&
                    referenceLine.boundingBox &&
                    onShowOnMap(referenceLine.boundingBox),
            );
        }
    };

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
                                    onPublishItemSelect={() =>
                                        handlePreviewSelect(entry.id, entry.type)
                                    }
                                    id={entry.id}
                                    type={entry.type}
                                    onRevert={() => onRevert(entry)}
                                    itemName={entry.uiName}
                                    trackNumber={entry.trackNumber}
                                    errors={entry.errors}
                                    changeTime={entry.changeTime}
                                    userName={entry.userName}
                                    pendingValidation={entry.pendingValidation}
                                    operation={entry.operation}
                                    publish={staged}
                                    changesBeingReverted={changesBeingReverted}
                                    onShowOnMap={showOnMap}
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
