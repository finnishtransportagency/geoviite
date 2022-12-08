import { Table, Th } from 'vayla-design-lib/table/table';
import { PublicationTableItem } from 'publication/publication-table-item';
import * as React from 'react';
import { PublishCandidates } from 'publication/publication-model';
import { useTranslation } from 'react-i18next';
import { LayoutTrackNumber } from 'track-layout/track-layout-model';
import { getTrackNumbers } from 'track-layout/layout-track-number-api';
import { SelectedChanges } from 'preview/preview-view';
import { TimeStamp } from 'common/common-model';
import styles from './publication-table.scss';

type PublicationTableProps = {
    previewChanges: PublishCandidates;
    showRatkoPushDate?: boolean;
    showStatus?: boolean;
    showActions?: boolean;
    ratkoPushDate?: TimeStamp;
};

const PublicationTable: React.FC<PublicationTableProps> = ({
    previewChanges,
    showRatkoPushDate = false,
    showStatus = false,
    showActions = false,
    ratkoPushDate = undefined,
}) => {
    const { t } = useTranslation();
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


    return (
        <div className={styles['publication-table__container']}>
            <Table wide>
                <thead className={styles['publication-table__header']}>
                <tr>
                    <Th>{t('publication-table.change-target')}</Th>
                    <Th>{t('publication-table.track-number-short')}</Th>
                    <Th>{t('publication-table.change-type')}</Th>
                    {showStatus && <Th>{t('publication-table.status')}</Th>}
                    <Th>{t('publication-table.user')}</Th>
                    <Th>{t('publication-table.modified-moment')}</Th>
                    {showRatkoPushDate && <Th>{t('publication-table.exported-to-ratko')}</Th>}
                    {showActions && <Th>{t('publication-table.actions')}</Th>}
                </tr>
                </thead>
                <tbody>
                {previewChanges.trackNumbers.map((trackNumber) => (
                    <React.Fragment key={trackNumber.id}>
                        {
                            <PublicationTableItem
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
                                showStatus={showStatus}
                                showActions={showActions}
                                userName={trackNumber.userName}
                                operation={trackNumber.operation}
                            />
                        }
                    </React.Fragment>
                ))}
                {previewChanges.referenceLines.map((referenceLine) => (
                    <React.Fragment key={referenceLine.id}>
                        {
                            <PublicationTableItem
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
                                showStatus={showStatus}
                                showActions={showActions}
                                userName={referenceLine.userName}
                                operation={referenceLine.operation}
                            />
                        }
                    </React.Fragment>
                ))}
                {previewChanges.locationTracks.map((locationTrack) => (
                    <React.Fragment key={locationTrack.id}>
                        {
                            <PublicationTableItem
                                onChange={(e) => {
                                    // Checkbox selection - disabled for MVP to simplify validation
                                    const locationTracks = addOrRemoveByCheckbox(
                                        selectedChanges.locationTracks,
                                        locationTrack.id,
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
                                    locationTrack.name
                                }`}
                                trackNumber={
                                    trackNumbers.find((tn) => tn.id === locationTrack.trackNumberId)
                                        ?.number
                                }
                                errors={locationTrack.errors}
                                changeTime={locationTrack.draftChangeTime}
                                ratkoPushDate={ratkoPushDate}
                                showRatkoPushDate={showRatkoPushDate}
                                showStatus={showStatus}
                                showActions={showActions}
                                userName={locationTrack.userName}
                                operation={locationTrack.operation}
                            />
                        }
                    </React.Fragment>
                ))}
                {previewChanges.switches.map((layoutSwitch) => (
                    <React.Fragment key={layoutSwitch.id}>
                        {
                            <PublicationTableItem
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
                                showStatus={showStatus}
                                showActions={showActions}
                                userName={layoutSwitch.userName}
                                operation={layoutSwitch.operation}
                            />
                        }
                    </React.Fragment>
                ))}
                {previewChanges.kmPosts.map((kmPost) => (
                    <React.Fragment key={kmPost.id}>
                        {
                            <PublicationTableItem
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
                                showStatus={showStatus}
                                showActions={showActions}
                                userName={kmPost.userName}
                                operation={kmPost.operation}
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
