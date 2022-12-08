import * as React from 'react';
import styles from './location-track-infobox.scss';
import Infobox from 'tool-panel/infobox/infobox';
import { LAYOUT_SRID, LayoutLocationTrack, MapAlignment } from 'track-layout/track-layout-model';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { Precision, roundToPrecision } from 'utils/rounding';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import {
    LinkingAlignment,
    LinkingState,
    LinkingType,
    toIntervalRequest,
} from 'linking/linking-model';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { updateLocationTrackGeometry } from 'linking/linking-api';
import {
    useCoordinateSystem,
    useLocationTrack,
    useLocationTrackChangeTimes,
    useLocationTrackDuplicates,
    useLocationTrackStartAndEnd,
    useTrackNumber,
} from 'track-layout/track-layout-react-utils';
import InfoboxText from 'tool-panel/infobox/infobox-text';
import { formatToTM35FINString } from 'utils/geography-utils';
import { formatDateShort } from 'utils/date-utils';
import { LocationTrackEditDialog } from 'tool-panel/location-track/dialog/location-track-edit-dialog';
import { BoundingBox } from 'model/geometry';
import 'i18n/config';
import { useTranslation } from 'react-i18next';
import TrackMeter from 'geoviite-design-lib/track-meter/track-meter';
import LayoutState from 'geoviite-design-lib/layout-state/layout-state';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { PublishType, TimeStamp } from 'common/common-model';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { TrackNumberLink } from 'geoviite-design-lib/track-number/track-number-link';
import LocationTrackDeleteConfirmationDialog from 'tool-panel/location-track/location-track-delete-confirmation-dialog';
import { getLocationTracksBySearchTerm } from 'track-layout/layout-location-track-api';
import LocationTrackTypeLabel from 'geoviite-design-lib/alignment/location-track-type-label';
import { useLoader } from 'utils/react-utils';
import { OnSelectFunction } from 'selection/selection-model';
import { LocationTrackInfoboxDuplicateOf } from 'tool-panel/location-track/location-track-infobox-duplicate-of';
import TopologicalConnectivityLabel from 'tool-panel/location-track/TopologicalConnectivityLabel';
import { getLocationTrackSegmentEnds } from 'track-layout/layout-map-api';

type LocationTrackInfoboxProps = {
    locationTrack: LayoutLocationTrack;
    onStartLocationTrackGeometryChange: (alignment: MapAlignment) => void;
    onEndLocationTrackGeometryChange: () => void;
    linkingState?: LinkingState;
    showArea: (area: BoundingBox) => void;
    onDataChange: () => void;
    publishType: PublishType;
    locationTrackChangeTime: TimeStamp;
    onUnselect: (track: LayoutLocationTrack) => void;
    onSelect: OnSelectFunction;
};

const LocationTrackInfobox: React.FC<LocationTrackInfoboxProps> = ({
    locationTrack,
    onStartLocationTrackGeometryChange,
    onEndLocationTrackGeometryChange,
    showArea,
    linkingState,
    onDataChange,
    publishType,
    locationTrackChangeTime,
    onUnselect,
}: LocationTrackInfoboxProps) => {
    const { t } = useTranslation();
    const trackNumber = useTrackNumber(publishType, locationTrack?.trackNumberId);
    const startAndEndPoints = useLocationTrackStartAndEnd(
        locationTrack?.id,
        publishType,
        locationTrackChangeTime,
    );
    const changeTimes = useLocationTrackChangeTimes(locationTrack?.id);
    const coordinateSystem = useCoordinateSystem(LAYOUT_SRID);
    const officialLocationTrack = useLocationTrack(
        locationTrack.id,
        'OFFICIAL',
        locationTrackChangeTime,
    );
    const [showEditDialog, setShowEditDialog] = React.useState(false);
    const [updatingLength, setUpdatingLength] = React.useState<boolean>(false);
    const [canUpdate, setCanUpdate] = React.useState<boolean>();
    const [confirmingDraftDelete, setConfirmingDraftDelete] = React.useState<boolean>();

    function isOfficial(): boolean {
        return publishType === 'OFFICIAL';
    }

    function openEditLocationTrackDialog() {
        setShowEditDialog(true);
        onDataChange();
    }

    function closeEditLocationTrackDialog() {
        setShowEditDialog(false);
        onDataChange();
    }

    function handleLocationTrackUpdate() {
        closeEditLocationTrackDialog();
    }

    const showLocationTrackDeleteConfirmation = () => {
        setConfirmingDraftDelete(true);
    };

    const closeLocationTrackDeleteConfirmation = () => {
        setConfirmingDraftDelete(false);
    };

    const closeConfirmationAndUnselect = (track: LayoutLocationTrack) => {
        closeLocationTrackDeleteConfirmation();
        onUnselect(track);
    };

    React.useEffect(() => {
        setCanUpdate(
            linkingState?.type === LinkingType.LinkingAlignment && linkingState.state === 'allSet',
        );
    }, [linkingState]);

    const updateAlignment = (state: LinkingAlignment) => {
        if (canUpdate && state.layoutAlignmentInterval.start && state.layoutAlignmentInterval.end) {
            setUpdatingLength(true);
            updateLocationTrackGeometry(state.layoutAlignmentId, {
                alignmentId: state.layoutAlignmentId,
                start: toIntervalRequest(state.layoutAlignmentInterval.start),
                end: toIntervalRequest(state.layoutAlignmentInterval.end),
            })
                .then(() => {
                    Snackbar.success(
                        t('tool-panel.location-track.location-track-endpoints-updated'),
                    );
                    onEndLocationTrackGeometryChange();
                })
                .finally(() => setUpdatingLength(false));
        }
    };

    const existingDuplicateOfList = useLoader(() => {
        const duplicateOftrack =
            locationTrack?.duplicateOf &&
            getLocationTracksBySearchTerm(locationTrack?.duplicateOf, publishType, 1);
        if (duplicateOftrack === '') return undefined;
        else if (duplicateOftrack === null) return undefined;
        else return duplicateOftrack;
    }, [locationTrack]);

    const existingDuplicate = existingDuplicateOfList && existingDuplicateOfList[0];
    const duplicatesOfLocationTrack = useLocationTrackDuplicates(locationTrack.id, publishType);

    return (
        <React.Fragment>
            <Infobox
                title={t('tool-panel.location-track.basic-info-heading')}
                qa-id="location-track-infobox">
                <InfoboxContent>
                    <InfoboxField
                        label={t('tool-panel.location-track.identifier')}
                        value={
                            locationTrack.externalId || t('tool-panel.location-track.unpublished')
                        }
                    />
                    <InfoboxField
                        label={t('tool-panel.location-track.track-name')}
                        value={locationTrack.name}
                        onEdit={openEditLocationTrackDialog}
                        iconDisabled={isOfficial()}
                    />
                    <InfoboxField
                        label={t('tool-panel.location-track.state')}
                        value={<LayoutState state={locationTrack.state} />}
                        onEdit={openEditLocationTrackDialog}
                        iconDisabled={isOfficial()}
                    />
                    <InfoboxField
                        label={t('tool-panel.location-track.type')}
                        value={<LocationTrackTypeLabel type={locationTrack.type} />}
                        onEdit={openEditLocationTrackDialog}
                        iconDisabled={isOfficial()}
                    />
                    <InfoboxField
                        label={t('tool-panel.location-track.description')}
                        value={locationTrack.description}
                        onEdit={openEditLocationTrackDialog}
                        iconDisabled={isOfficial()}
                    />
                    <InfoboxField
                        label={t('tool-panel.location-track.track-number')}
                        value={
                            <TrackNumberLink
                                trackNumberId={trackNumber?.id}
                                publishType={publishType}
                            />
                        }
                        onEdit={openEditLocationTrackDialog}
                        iconDisabled={isOfficial()}
                    />
                    <InfoboxText value={trackNumber?.description} />

                    {duplicatesOfLocationTrack !== undefined && (
                        <InfoboxField
                            label={
                                duplicatesOfLocationTrack.length > 0
                                    ? t('tool-panel.location-track.has-duplicates')
                                    : existingDuplicate
                                    ? t('tool-panel.location-track.duplicate-of')
                                    : t('tool-panel.location-track.not-a-duplicate')
                            }
                            value={
                                <LocationTrackInfoboxDuplicateOf
                                    existingDuplicate={existingDuplicate}
                                    duplicatesOfLocationTrack={duplicatesOfLocationTrack}
                                />
                            }
                            onEdit={
                                duplicatesOfLocationTrack.length > 0
                                    ? undefined
                                    : openEditLocationTrackDialog
                            }
                            iconDisabled={isOfficial()}
                        />
                    )}

                    <InfoboxField
                        label={t('tool-panel.location-track.topological-connectivity')}
                        value={
                            <TopologicalConnectivityLabel
                                topologicalConnectivity={locationTrack.topologicalConnectivity}
                            />
                        }
                        onEdit={openEditLocationTrackDialog}
                        iconDisabled={isOfficial()}
                    />

                    <InfoboxButtons>
                        <Button
                            variant={ButtonVariant.SECONDARY}
                            size={ButtonSize.SMALL}
                            onClick={() =>
                                locationTrack.boundingBox && showArea(locationTrack.boundingBox)
                            }>
                            {t('tool-panel.location-track.show-on-map')}
                        </Button>
                    </InfoboxButtons>
                </InfoboxContent>
            </Infobox>
            {startAndEndPoints && coordinateSystem && (
                <Infobox
                    title={t('tool-panel.location-track.track-location-heading')}
                    qa-id="location-track-location-infobox">
                    <InfoboxContent>
                        <InfoboxField label={t('tool-panel.location-track.start-location')}>
                            <TrackMeter value={startAndEndPoints?.start?.address} />
                        </InfoboxField>
                        <InfoboxField label={t('tool-panel.location-track.end-location')}>
                            <TrackMeter value={startAndEndPoints?.end?.address} />
                        </InfoboxField>

                        {linkingState === undefined && (
                            <InfoboxButtons>
                                <Button
                                    variant={ButtonVariant.SECONDARY}
                                    size={ButtonSize.SMALL}
                                    onClick={() => {
                                        getLocationTrackSegmentEnds(
                                            locationTrack.id,
                                            publishType,
                                        ).then(onStartLocationTrackGeometryChange);
                                    }}>
                                    {t('tool-panel.location-track.modify-start-or-end')}
                                </Button>
                            </InfoboxButtons>
                        )}
                        {linkingState?.type === LinkingType.LinkingAlignment && (
                            <React.Fragment>
                                <p
                                    className={
                                        styles['location-track-infobox__link-alignment-guide']
                                    }>
                                    {t('tool-panel.location-track.choose-start-and-end-points')}
                                </p>
                                <InfoboxButtons>
                                    <Button
                                        variant={ButtonVariant.SECONDARY}
                                        size={ButtonSize.SMALL}
                                        disabled={updatingLength}
                                        onClick={onEndLocationTrackGeometryChange}>
                                        {t('button.cancel')}
                                    </Button>
                                    <Button
                                        size={ButtonSize.SMALL}
                                        isProcessing={updatingLength}
                                        disabled={updatingLength || !canUpdate}
                                        onClick={() => {
                                            updateAlignment(linkingState);
                                        }}>
                                        {t('tool-panel.location-track.ready')}
                                    </Button>
                                </InfoboxButtons>
                            </React.Fragment>
                        )}

                        <InfoboxField
                            label={t('tool-panel.location-track.true-length')}
                            value={
                                roundToPrecision(
                                    locationTrack.length,
                                    Precision.alignmentLengthMeters,
                                ) + ' m'
                            }
                        />
                        <InfoboxField
                            label={`${t('tool-panel.location-track.start-coordinates')} ${
                                coordinateSystem.name
                            }`}
                            value={
                                startAndEndPoints?.start
                                    ? formatToTM35FINString(startAndEndPoints.start.point)
                                    : ''
                            }
                        />
                        <InfoboxField
                            label={`${t('tool-panel.location-track.end-coordinates')} ${
                                coordinateSystem.name
                            }`}
                            value={
                                startAndEndPoints?.end
                                    ? formatToTM35FINString(startAndEndPoints?.end.point)
                                    : '-'
                            }
                        />
                    </InfoboxContent>
                </Infobox>
            )}
            {changeTimes && (
                <Infobox
                    title={t('tool-panel.location-track.change-info-heading')}
                    qa-id="location-track-log-infobox">
                    <InfoboxContent>
                        <InfoboxField
                            label={t('tool-panel.location-track.created')}
                            value={formatDateShort(changeTimes.created)}
                        />
                        <InfoboxField
                            label={t('tool-panel.location-track.changed')}
                            value={formatDateShort(changeTimes.changed)}
                        />
                        {officialLocationTrack === undefined && (
                            <InfoboxButtons>
                                <Button
                                    onClick={() => showLocationTrackDeleteConfirmation()}
                                    icon={Icons.Delete}
                                    variant={ButtonVariant.WARNING}
                                    size={ButtonSize.SMALL}>
                                    {t('tool-panel.location-track.delete-draft')}
                                </Button>
                            </InfoboxButtons>
                        )}
                    </InfoboxContent>
                </Infobox>
            )}
            {confirmingDraftDelete && (
                <LocationTrackDeleteConfirmationDialog
                    id={locationTrack.id}
                    onClose={() => closeConfirmationAndUnselect(locationTrack)}
                    onCancel={closeLocationTrackDeleteConfirmation}
                />
            )}

            {showEditDialog && (
                <LocationTrackEditDialog
                    onClose={closeEditLocationTrackDialog}
                    onUpdate={handleLocationTrackUpdate}
                    locationTrack={locationTrack}
                    publishType={publishType}
                    locationTrackChangeTime={locationTrackChangeTime}
                    onUnselect={() => onUnselect(locationTrack)}
                    existingDuplicateTrack={existingDuplicate}
                    duplicatesExist={
                        duplicatesOfLocationTrack !== undefined &&
                        duplicatesOfLocationTrack.length > 0
                    }
                />
            )}
        </React.Fragment>
    );
};

export default LocationTrackInfobox;
