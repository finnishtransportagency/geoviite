import { Table, Th } from 'vayla-design-lib/table/table';
import { PublicationTableItem } from 'publication/publication-table-item';
import * as React from 'react';
import { PublishCandidates } from 'publication/publication-model';
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
import { SelectedChanges } from 'preview/preview-view';
import { TimeStamp } from 'common/common-model';
import styles from './publication-table.scss';
import { SelectedPublishChange } from 'track-layout/track-layout-store';

type PublicationTableProps = {
    previewChanges: PublishCandidates;
    showRatkoPushDate?: boolean;
    ratkoPushDate?: TimeStamp;
    onPreviewSelect?: (selectedChanges: SelectedPublishChange) => void;
    publish?: boolean;
};

const PublicationTable: React.FC<PublicationTableProps> = ({
    previewChanges,
    showRatkoPushDate = false,
    ratkoPushDate = undefined,
    onPreviewSelect = undefined,
    publish = false,
}) => {
    const {t} = useTranslation();
    const [trackNumbers, setTrackNumbers] = React.useState<LayoutTrackNumber[]>([]);
    React.useEffect(() => {
        getTrackNumbers('DRAFT').then((trackNumbers) => setTrackNumbers(trackNumbers));
    }, []);

    const [selectedChanges, setSelectedChanges] = React.useState<SelectedChanges>({
        trackNumbers: [],
        referenceLines: [],
        locationTracks: [],
        switches: [],
        kmPosts: [],
    });
    React.useEffect(() => {
        setSelectedChanges({
            trackNumbers: previewChanges ? previewChanges.trackNumbers.map((tn) => tn.id) : [],
            referenceLines: previewChanges ? previewChanges.referenceLines.map((a) => a.id) : [],
            locationTracks: previewChanges ? previewChanges.locationTracks.map((a) => a.id) : [],
            switches: previewChanges ? previewChanges.switches.map((s) => s.id) : [],
            kmPosts: previewChanges ? previewChanges.kmPosts.map((kmp) => kmp.id) : [],
        });
    }, [previewChanges]);

    function addOrRemoveByCheckbox<T>(collection: T[], item: T, checkboxValue: boolean) {
        let modifiedCollection;
        if (checkboxValue) {
            modifiedCollection = [...collection, item];
        } else {
            modifiedCollection = collection.filter((existingItem) => existingItem !== item);
        }
        return modifiedCollection;
    }

    enum PreviewSelectType {
        trackNumber = 'trackNumber',
        referenceLine = 'referenceLine',
        locationTrack = 'locationTrack',
        switch = 'switch',
        kmPost = 'kmPost'
    }


    type PublishChangeId =
        LayoutTrackNumberId
        | ReferenceLineId
        | LocationTrackId
        | LayoutSwitchId
        | LayoutKmPostId
        | undefined


    const defaultSelectedPublishChange: SelectedPublishChange = {
        trackNumber: undefined,
        referenceLine: undefined,
        locationTrack: undefined,
        switch: undefined,
        kmPost: undefined,
    };

    function handlePreviewSelect<T>(id: PublishChangeId, type: T) {
        switch (type) {
            case (PreviewSelectType.trackNumber):
                onPreviewSelect && onPreviewSelect(
                    {...defaultSelectedPublishChange, trackNumber: id});
                break;
            case (PreviewSelectType.referenceLine):
                onPreviewSelect && onPreviewSelect(
                    {...defaultSelectedPublishChange, referenceLine: id});
                break;
            case (PreviewSelectType.locationTrack):
                onPreviewSelect && onPreviewSelect(
                    {...defaultSelectedPublishChange, locationTrack: id});
                break;
            case (PreviewSelectType.switch):
                onPreviewSelect && onPreviewSelect(
                    {...defaultSelectedPublishChange, locationTrack: id});
                break;
            case (PreviewSelectType.kmPost):
                onPreviewSelect && onPreviewSelect(
                    {...defaultSelectedPublishChange, locationTrack: id});
                break;
            default:
                onPreviewSelect && onPreviewSelect(defaultSelectedPublishChange);
        }
    }

    return (
        <div className={styles['publication-table__container']}>
            <Table wide>
                <thead className={styles['publication-table__header']}>
                <tr>
                    {/*<Th/>*/}
                    <Th>{t('publication-table.change-target')}</Th>
                    <Th>{t('publication-table.track-number-short')}</Th>
                    <Th>{t('publication-table.status')}</Th>
                    <Th>{t('publication-table.actions')}</Th>
                    <Th>{t('publication-table.modified-moment')}</Th>
                    {showRatkoPushDate && <Th>{t('publication-table.exported-to-ratko')}</Th>}
                </tr>
                </thead>
                <tbody>
                {previewChanges.trackNumbers.map((trackNumber) => (
                    <React.Fragment key={trackNumber.id}>
                        {
                            <PublicationTableItem
                                onPublishItemSelect={() => handlePreviewSelect(trackNumber.id, PreviewSelectType.trackNumber)}
                                onChange={(e) => {
                                    const trackNumbers = addOrRemoveByCheckbox(
                                        selectedChanges.trackNumbers,
                                        trackNumber.id,
                                        e.target.checked,
                                    );
                                    const _newSelectedChanges: SelectedChanges = {
                                        ...selectedChanges,
                                        trackNumbers,
                                    };
                                    // Checkbox selection - disabled for MVP to simplify validation
                                    // setSelectedChanges(newSelectedChanges);
                                }}
                                itemName={`${t('publication-table.track-number-long')} ${
                                    trackNumber.number
                                }`}
                                trackNumber={trackNumber.number}
                                errors={trackNumber.errors}
                                changeTime={trackNumber.draftChangeTime}
                                ratkoPushDate={ratkoPushDate}
                                showRatkoPushDate={showRatkoPushDate}
                                publish={publish}
                            />
                        }
                    </React.Fragment>
                ))}
                {previewChanges.referenceLines.map((referenceLine) => (
                    <React.Fragment key={referenceLine.id}>
                        {
                            <PublicationTableItem
                                onPublishItemSelect={() => handlePreviewSelect(referenceLine.id, PreviewSelectType.referenceLine)}
                                onChange={(e) => {
                                    // Checkbox selection - disabled for MVP to simplify validation
                                    const referenceLines = addOrRemoveByCheckbox(
                                        selectedChanges.referenceLines,
                                        referenceLine.id,
                                        e.target.checked,
                                    );
                                    const _newSelectedChanges: SelectedChanges = {
                                        ...selectedChanges,
                                        referenceLines,
                                    };
                                    // Checkbox selection - disabled for MVP to simplify validation
                                    // setSelectedChanges(newSelectedChanges);
                                }}
                                itemName={`${t('publication-table.reference-line')} ${
                                    referenceLine.name
                                }`}
                                trackNumber={
                                    trackNumbers.find((tn) => tn.id === referenceLine.trackNumberId)
                                        ?.number
                                }
                                errors={referenceLine.errors}
                                changeTime={referenceLine.draftChangeTime}
                                ratkoPushDate={ratkoPushDate}
                                showRatkoPushDate={showRatkoPushDate}
                                publish={publish}
                            />
                        }
                    </React.Fragment>
                ))}
                {previewChanges.locationTracks.map((referenceLine) => (
                    <React.Fragment key={referenceLine.id}>
                        {
                            <PublicationTableItem
                                onPublishItemSelect={() => handlePreviewSelect(referenceLine.id, PreviewSelectType.locationTrack)}
                                onChange={(e) => {
                                    // Checkbox selection - disabled for MVP to simplify validation
                                    const locationTracks = addOrRemoveByCheckbox(
                                        selectedChanges.locationTracks,
                                        referenceLine.id,
                                        e.target.checked,
                                    );
                                    const _newSelectedChanges: SelectedChanges = {
                                        ...selectedChanges,
                                        locationTracks,
                                    };
                                    // Checkbox selection - disabled for MVP to simplify validation
                                    // setSelectedChanges(newSelectedChanges);
                                }}
                                itemName={`${t('publication-table.location-track')} ${
                                    referenceLine.name
                                }`}
                                trackNumber={
                                    trackNumbers.find((tn) => tn.id === referenceLine.trackNumberId)
                                        ?.number
                                }
                                errors={referenceLine.errors}
                                changeTime={referenceLine.draftChangeTime}
                                ratkoPushDate={ratkoPushDate}
                                showRatkoPushDate={showRatkoPushDate}
                                publish={publish}
                            />
                        }
                    </React.Fragment>
                ))}
                {previewChanges.switches.map((layoutSwitch) => (
                    <React.Fragment key={layoutSwitch.id}>
                        {
                            <PublicationTableItem
                                onPublishItemSelect={() => handlePreviewSelect(layoutSwitch.id, PreviewSelectType.switch)}
                                onChange={(e) => {
                                    const switches = addOrRemoveByCheckbox(
                                        selectedChanges.switches,
                                        layoutSwitch.id,
                                        e.target.checked,
                                    );
                                    const _newSelectedChanges: SelectedChanges = {
                                        ...selectedChanges,
                                        switches,
                                    };
                                    // Checkbox selection - disabled for MVP to simplify validation
                                    // setSelectedChanges(newSelectedChanges);
                                }}
                                itemName={`${t('publication-table.switch')} ${layoutSwitch.name}`}
                                trackNumber={trackNumbers
                                    .filter((tn) => layoutSwitch.trackNumberIds.some((lstn) => lstn == tn.id))
                                    .map((tn) => tn.number)
                                    .join(', ')}
                                errors={layoutSwitch.errors}
                                changeTime={layoutSwitch.draftChangeTime}
                                ratkoPushDate={ratkoPushDate}
                                showRatkoPushDate={showRatkoPushDate}
                                publish={publish}
                            />
                        }
                    </React.Fragment>
                ))}
                {previewChanges.kmPosts.map((kmPost) => (
                    <React.Fragment key={kmPost.id}>
                        {
                            <PublicationTableItem
                                onPublishItemSelect={() => handlePreviewSelect(kmPost.id, PreviewSelectType.kmPost)}
                                onChange={(e) => {
                                    const kmPosts = addOrRemoveByCheckbox(
                                        selectedChanges.kmPosts,
                                        kmPost.id,
                                        e.target.checked,
                                    );
                                    const _newSelectedChanges: SelectedChanges = {
                                        ...selectedChanges,
                                        kmPosts,
                                    };
                                    // Checkbox selection - disabled for MVP to simplify validation
                                    // setSelectedChanges(newSelectedChanges);
                                }}
                                itemName={`${t('publication-table.km-post')} ${kmPost.kmNumber}`}
                                trackNumber={
                                    trackNumbers.find((tn) => tn.id === kmPost.trackNumberId)
                                        ?.number
                                }
                                errors={kmPost.errors}
                                changeTime={kmPost.draftChangeTime}
                                ratkoPushDate={ratkoPushDate}
                                showRatkoPushDate={showRatkoPushDate}
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
