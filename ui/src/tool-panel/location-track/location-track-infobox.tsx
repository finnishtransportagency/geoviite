import * as React from 'react';
import styles from './location-track-infobox.scss';
import Infobox from 'tool-panel/infobox/infobox';
import {
    LAYOUT_SRID,
    LayoutLocationTrack,
    LayoutSwitchIdAndName,
} from 'track-layout/track-layout-model';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { Precision, roundToPrecision } from 'utils/rounding';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { LinkingAlignment, LinkingState, LinkingType, LinkInterval } from 'linking/linking-model';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { updateLocationTrackGeometry } from 'linking/linking-api';
import {
    refreshLocationTrackSelection,
    useCoordinateSystem,
    useLocationTrack,
    useLocationTrackChangeTimes,
    useLocationTrackInfoboxExtras,
    useLocationTrackStartAndEnd,
    useTrackNumber,
} from 'track-layout/track-layout-react-utils';
import InfoboxText from 'tool-panel/infobox/infobox-text';
import { formatToTM35FINString } from 'utils/geography-utils';
import { formatDateShort } from 'utils/date-utils';
import { LocationTrackEditDialogContainer } from 'tool-panel/location-track/dialog/location-track-edit-dialog';
import { BoundingBox } from 'model/geometry';
import 'i18n/config';
import { useTranslation } from 'react-i18next';
import TrackMeter from 'geoviite-design-lib/track-meter/track-meter';
import LayoutState from 'geoviite-design-lib/layout-state/layout-state';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { LocationTrackOwnerId, PublishType, TimeStamp } from 'common/common-model';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { TrackNumberLinkContainer } from 'geoviite-design-lib/track-number/track-number-link';
import LocationTrackDeleteConfirmationDialog from 'tool-panel/location-track/location-track-delete-confirmation-dialog';
import {
    getLocationTrackDescriptions,
    getSplittingInitializationParameters,
} from 'track-layout/layout-location-track-api';
import LocationTrackTypeLabel from 'geoviite-design-lib/alignment/location-track-type-label';
import { LoaderStatus, useLoader } from 'utils/react-utils';
import { OnSelectFunction, OptionalUnselectableItemCollections } from 'selection/selection-model';
import { LocationTrackInfoboxDuplicateOf } from 'tool-panel/location-track/location-track-infobox-duplicate-of';
import TopologicalConnectivityLabel from 'tool-panel/location-track/topological-connectivity-label';
import { LocationTrackRatkoPushDialog } from 'tool-panel/location-track/dialog/location-track-ratko-push-dialog';
import { LocationTrackGeometryInfobox } from 'tool-panel/location-track/location-track-geometry-infobox';
import { MapViewport } from 'map/map-model';
import { AssetValidationInfoboxContainer } from 'tool-panel/asset-validation-infobox-container';
import { getEndLinkPoints } from 'track-layout/layout-map-api';
import {
    LocationTrackInfoboxVisibilities,
    trackLayoutActionCreators as TrackLayoutActions,
} from 'track-layout/track-layout-slice';
import { WriteAccessRequired } from 'user/write-access-required';
import { LocationTrackVerticalGeometryInfobox } from 'tool-panel/location-track/location-track-vertical-geometry-infobox';
import { HighlightedAlignment } from 'tool-panel/alignment-plan-section-infobox-content';
import {
    ProgressIndicatorType,
    ProgressIndicatorWrapper,
} from 'vayla-design-lib/progress/progress-indicator-wrapper';
import { Link } from 'vayla-design-lib/link/link';
import { createDelegates } from 'store/store-utils';
import { LocationTrackSplittingInfobox } from 'tool-panel/location-track/location-track-splitting-infobox';
import { SplittingState } from 'tool-panel/location-track/split-store';
import { getLocationTrackOwners } from 'common/common-api';
import { calculateBoundingBoxToShowAroundLocation } from 'map/map-utils';

type LocationTrackInfoboxProps = {
    locationTrack: LayoutLocationTrack;
    onStartLocationTrackGeometryChange: (linkInterval: LinkInterval) => void;
    onEndLocationTrackGeometryChange: () => void;
    linkingState?: LinkingState;
    splittingState?: SplittingState;
    showArea: (area: BoundingBox) => void;
    onDataChange: () => void;
    publishType: PublishType;
    locationTrackChangeTime: TimeStamp;
    onSelect: OnSelectFunction;
    onUnselect: (items: OptionalUnselectableItemCollections) => void;
    viewport: MapViewport;
    visibilities: LocationTrackInfoboxVisibilities;
    onVisibilityChange: (visibilities: LocationTrackInfoboxVisibilities) => void;
    onVerticalGeometryDiagramVisibilityChange: (visibility: boolean) => void;
    verticalGeometryDiagramVisible: boolean;
    onHighlightItem: (item: HighlightedAlignment | undefined) => void;
};

const LocationTrackInfobox: React.FC<LocationTrackInfoboxProps> = ({
    locationTrack,
    onStartLocationTrackGeometryChange,
    onEndLocationTrackGeometryChange,
    showArea,
    linkingState,
    splittingState,
    onDataChange,
    publishType,
    locationTrackChangeTime,
    onSelect,
    onUnselect,
    viewport,
    visibilities,
    onVisibilityChange,
    verticalGeometryDiagramVisible,
    onVerticalGeometryDiagramVisibilityChange,
    onHighlightItem,
}: LocationTrackInfoboxProps) => {
    const { t } = useTranslation();
    const delegates = createDelegates(TrackLayoutActions);
    const trackNumber = useTrackNumber(publishType, locationTrack?.trackNumberId);
    const [startAndEndPoints, startAndEndPointFetchStatus] = useLocationTrackStartAndEnd(
        locationTrack?.id,
        publishType,
        locationTrackChangeTime,
    );
    const changeTimes = useLocationTrackChangeTimes(locationTrack?.id, publishType);
    const coordinateSystem = useCoordinateSystem(LAYOUT_SRID);
    const officialLocationTrack = useLocationTrack(
        locationTrack.id,
        'OFFICIAL',
        locationTrackChangeTime,
    );
    const description = useLoader(
        () =>
            getLocationTrackDescriptions([locationTrack.id], publishType).then(
                (value) => (value && value[0].description) ?? undefined,
            ),
        [locationTrack?.id, publishType, locationTrackChangeTime],
    );
    const locationTrackOwners = useLoader(() => getLocationTrackOwners(), []);
    const splitInitializationParameters = useLoader(
        () => getSplittingInitializationParameters(publishType, locationTrack.id),
        [publishType, locationTrack.id],
    );

    const [showEditDialog, setShowEditDialog] = React.useState(false);
    const [updatingLength, setUpdatingLength] = React.useState<boolean>(false);
    const [canUpdate, setCanUpdate] = React.useState<boolean>();
    const [confirmingDraftDelete, setConfirmingDraftDelete] = React.useState<boolean>();
    const [showRatkoPushDialog, setShowRatkoPushDialog] = React.useState<boolean>(false);

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

    const handleLocationTrackSave = refreshLocationTrackSelection('DRAFT', onSelect, onUnselect);

    function getLocationTrackOwnerName(ownerId: LocationTrackOwnerId) {
        const name = locationTrackOwners?.find((o) => o.id == ownerId)?.name;
        return name ?? '-';
    }

    function getSwitchLink(sw?: LayoutSwitchIdAndName) {
        if (sw) {
            const switchId = sw.id;
            return (
                <Link
                    onClick={() =>
                        delegates.onSelect({
                            switches: [switchId],
                        })
                    }>
                    {sw.name}
                </Link>
            );
        } else {
            return t('tool-panel.location-track.no-start-or-end-switch');
        }
    }

    React.useEffect(() => {
        setCanUpdate(
            linkingState?.type === LinkingType.LinkingAlignment && linkingState.state === 'allSet',
        );
    }, [linkingState]);

    const updateAlignment = (state: LinkingAlignment) => {
        if (canUpdate && state.layoutAlignmentInterval.start && state.layoutAlignmentInterval.end) {
            setUpdatingLength(true);
            updateLocationTrackGeometry(state.layoutAlignmentId, {
                min: state.layoutAlignmentInterval.start.m,
                max: state.layoutAlignmentInterval.end.m,
            })
                .then(() => {
                    Snackbar.success('tool-panel.location-track.location-track-endpoints-updated');
                    onEndLocationTrackGeometryChange();
                })
                .finally(() => setUpdatingLength(false));
        }
    };

    const [extraInfo, extraInfoLoadingStatus] = useLocationTrackInfoboxExtras(
        locationTrack?.id,
        publishType,
    );

    const visibilityChange = (key: keyof LocationTrackInfoboxVisibilities) => {
        onVisibilityChange({ ...visibilities, [key]: !visibilities[key] });
    };

    return (
        <React.Fragment>
            <Infobox
                contentVisible={
                    visibilities.basic && extraInfoLoadingStatus != LoaderStatus.Loading
                }
                onContentVisibilityChange={() => visibilityChange('basic')}
                title={t('tool-panel.location-track.basic-info-heading')}
                qa-id="location-track-infobox">
                <InfoboxContent>
                    <InfoboxField
                        qaId="location-track-oid"
                        label={t('tool-panel.location-track.identifier')}
                        value={
                            locationTrack.externalId || t('tool-panel.location-track.unpublished')
                        }
                    />
                    <InfoboxField
                        qaId="location-track-name"
                        label={t('tool-panel.location-track.track-name')}
                        value={locationTrack.name}
                        onEdit={openEditLocationTrackDialog}
                        iconDisabled={isOfficial()}
                    />
                    <InfoboxField
                        qaId="location-track-state"
                        label={t('tool-panel.location-track.state')}
                        value={<LayoutState state={locationTrack.state} />}
                        onEdit={openEditLocationTrackDialog}
                        iconDisabled={isOfficial()}
                    />
                    <InfoboxField
                        qaId="location-track-type"
                        label={t('tool-panel.location-track.type')}
                        value={<LocationTrackTypeLabel type={locationTrack.type} />}
                        onEdit={openEditLocationTrackDialog}
                        iconDisabled={isOfficial()}
                    />
                    <InfoboxField
                        qaId="location-track-description"
                        label={t('tool-panel.location-track.description')}
                        value={description}
                        onEdit={openEditLocationTrackDialog}
                        iconDisabled={isOfficial()}
                    />
                    <InfoboxField
                        qaId="location-track-track-number"
                        label={t('tool-panel.location-track.track-number')}
                        value={<TrackNumberLinkContainer trackNumberId={trackNumber?.id} />}
                        onEdit={openEditLocationTrackDialog}
                        iconDisabled={isOfficial()}
                    />
                    <InfoboxText value={trackNumber?.description} />
                    <InfoboxField
                        label={
                            locationTrack.duplicateOf
                                ? t('tool-panel.location-track.duplicate-of')
                                : extraInfo?.duplicates?.length ?? 0 > 0
                                ? t('tool-panel.location-track.has-duplicates')
                                : t('tool-panel.location-track.not-a-duplicate')
                        }
                        value={
                            <LocationTrackInfoboxDuplicateOf
                                existingDuplicate={extraInfo?.duplicateOf}
                                duplicatesOfLocationTrack={extraInfo?.duplicates ?? []}
                                publishType={publishType}
                                changeTime={locationTrackChangeTime}
                            />
                        }
                        onEdit={openEditLocationTrackDialog}
                        iconDisabled={isOfficial()}
                    />
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
                    <InfoboxField
                        label={t('tool-panel.location-track.owner')}
                        value={getLocationTrackOwnerName(locationTrack.ownerId)}
                        onEdit={openEditLocationTrackDialog}
                        iconDisabled={isOfficial()}
                    />
                    <InfoboxField
                        label={t('tool-panel.location-track.start-switch')}
                        value={extraInfo && getSwitchLink(extraInfo.switchAtStart)}
                    />
                    <InfoboxField
                        label={t('tool-panel.location-track.end-switch')}
                        value={extraInfo && getSwitchLink(extraInfo.switchAtEnd)}
                    />
                    <InfoboxButtons>
                        <Button
                            variant={ButtonVariant.SECONDARY}
                            size={ButtonSize.SMALL}
                            qa-id="zoom-to-location-track"
                            onClick={() =>
                                locationTrack.boundingBox && showArea(locationTrack.boundingBox)
                            }>
                            {t('tool-panel.location-track.show-on-map')}
                        </Button>
                    </InfoboxButtons>
                </InfoboxContent>
            </Infobox>
            {splittingState && (
                <LocationTrackSplittingInfobox
                    visibilities={visibilities}
                    visibilityChange={visibilityChange}
                    initialSplit={splittingState.initialSplit}
                    splits={splittingState.splits || []}
                    locationTrackId={splittingState.originLocationTrack.id}
                    removeSplit={delegates.removeSplit}
                    cancelSplitting={() => {
                        delegates.cancelSplitting();
                        delegates.hideLayers(['location-track-split-location-layer']);
                    }}
                    allowedSwitches={splittingState.allowedSwitches}
                    duplicateLocationTracks={extraInfo?.duplicates || []}
                    updateSplit={delegates.updateSplit}
                    showArea={showArea}
                />
            )}
            {startAndEndPoints && coordinateSystem && (
                <Infobox
                    contentVisible={visibilities.location}
                    onContentVisibilityChange={() => visibilityChange('location')}
                    title={t('tool-panel.location-track.track-location-heading')}
                    qa-id="location-track-location-infobox">
                    <InfoboxContent>
                        <ProgressIndicatorWrapper
                            indicator={ProgressIndicatorType.Area}
                            inProgress={startAndEndPointFetchStatus !== LoaderStatus.Ready}>
                            <React.Fragment>
                                <InfoboxField
                                    qaId="location-track-start-track-meter"
                                    label={t('tool-panel.location-track.start-location')}>
                                    {startAndEndPoints?.start?.address ? (
                                        <TrackMeter
                                            onShowOnMap={() =>
                                                startAndEndPoints?.start &&
                                                showArea(
                                                    calculateBoundingBoxToShowAroundLocation(
                                                        startAndEndPoints.start.point,
                                                    ),
                                                )
                                            }
                                            trackMeter={startAndEndPoints.start.address}
                                        />
                                    ) : (
                                        t('tool-panel.location-track.unset')
                                    )}
                                </InfoboxField>
                                <InfoboxField
                                    qaId="location-track-end-track-meter"
                                    label={t('tool-panel.location-track.end-location')}>
                                    {startAndEndPoints?.end?.address ? (
                                        <TrackMeter
                                            onShowOnMap={() =>
                                                startAndEndPoints.end &&
                                                showArea(
                                                    calculateBoundingBoxToShowAroundLocation(
                                                        startAndEndPoints.end.point,
                                                    ),
                                                )
                                            }
                                            trackMeter={startAndEndPoints.end.address}
                                        />
                                    ) : (
                                        t('tool-panel.location-track.unset')
                                    )}
                                </InfoboxField>

                                {linkingState === undefined && (
                                    <WriteAccessRequired>
                                        <InfoboxButtons>
                                            <Button
                                                variant={ButtonVariant.SECONDARY}
                                                size={ButtonSize.SMALL}
                                                qa-id="modify-start-or-end"
                                                disabled={
                                                    !startAndEndPoints.start?.point ||
                                                    !startAndEndPoints.end?.point
                                                }
                                                onClick={() => {
                                                    getEndLinkPoints(
                                                        locationTrack.id,
                                                        publishType,
                                                        'LOCATION_TRACK',
                                                        locationTrackChangeTime,
                                                    ).then(onStartLocationTrackGeometryChange);
                                                }}>
                                                {t('tool-panel.location-track.modify-start-or-end')}
                                            </Button>
                                        </InfoboxButtons>
                                    </WriteAccessRequired>
                                )}
                                {linkingState?.type === LinkingType.LinkingAlignment && (
                                    <React.Fragment>
                                        <p
                                            className={
                                                styles[
                                                    'location-track-infobox__link-alignment-guide'
                                                ]
                                            }>
                                            {t(
                                                'tool-panel.location-track.choose-start-and-end-points',
                                            )}
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
                                                qa-id="save-start-and-end-changes"
                                                onClick={() => {
                                                    updateAlignment(linkingState);
                                                }}>
                                                {t('tool-panel.location-track.ready')}
                                            </Button>
                                        </InfoboxButtons>
                                    </React.Fragment>
                                )}
                                <InfoboxButtons>
                                    {!linkingState && !splittingState && (
                                        <Button
                                            variant={ButtonVariant.SECONDARY}
                                            size={ButtonSize.SMALL}
                                            onClick={() => {
                                                if (
                                                    startAndEndPoints?.start &&
                                                    startAndEndPoints?.end
                                                ) {
                                                    delegates.onStartSplitting({
                                                        locationTrack: locationTrack,
                                                        allowedSwitches:
                                                            splitInitializationParameters?.switches ||
                                                            [],
                                                        duplicateTracks:
                                                            splitInitializationParameters?.duplicates ||
                                                            [],
                                                        startLocation:
                                                            startAndEndPoints.start.point,
                                                        endLocation: startAndEndPoints.end.point,
                                                    });
                                                    delegates.showLayers([
                                                        'location-track-split-location-layer',
                                                    ]);
                                                }
                                            }}>
                                            {t('tool-panel.location-track.start-splitting')}
                                        </Button>
                                    )}
                                </InfoboxButtons>

                                <InfoboxField
                                    qaId="location-track-true-length"
                                    label={t('tool-panel.location-track.true-length')}
                                    value={
                                        roundToPrecision(
                                            locationTrack.length,
                                            Precision.alignmentLengthMeters,
                                        ) + ' m'
                                    }
                                />
                                <InfoboxField
                                    qaId="location-track-start-coordinates"
                                    label={`${t('tool-panel.location-track.start-coordinates')} ${
                                        coordinateSystem.name
                                    }`}
                                    value={
                                        startAndEndPoints?.start
                                            ? formatToTM35FINString(startAndEndPoints.start.point)
                                            : t('tool-panel.location-track.unset')
                                    }
                                />
                                <InfoboxField
                                    qaId="location-track-end-coordinates"
                                    label={`${t('tool-panel.location-track.end-coordinates')} ${
                                        coordinateSystem.name
                                    }`}
                                    value={
                                        startAndEndPoints?.end
                                            ? formatToTM35FINString(startAndEndPoints?.end.point)
                                            : t('tool-panel.location-track.unset')
                                    }
                                />
                            </React.Fragment>
                        </ProgressIndicatorWrapper>
                    </InfoboxContent>
                </Infobox>
            )}
            <LocationTrackGeometryInfobox
                contentVisible={visibilities.geometry}
                onContentVisibilityChange={() => visibilityChange('geometry')}
                publishType={publishType}
                locationTrackId={locationTrack.id}
                viewport={viewport}
                onHighlightItem={onHighlightItem}
                showArea={showArea}
            />
            <LocationTrackVerticalGeometryInfobox
                contentVisible={visibilities.verticalGeometry}
                onContentVisibilityChange={() => visibilityChange('verticalGeometry')}
                onVerticalGeometryDiagramVisibilityChange={
                    onVerticalGeometryDiagramVisibilityChange
                }
                verticalGeometryDiagramVisible={verticalGeometryDiagramVisible}
            />
            {locationTrack.draftType !== 'NEW_DRAFT' && (
                <AssetValidationInfoboxContainer
                    contentVisible={visibilities.validation}
                    onContentVisibilityChange={() => visibilityChange('validation')}
                    id={locationTrack.id}
                    type={'LOCATION_TRACK'}
                    publishType={publishType}
                    changeTime={locationTrackChangeTime}
                />
            )}
            {changeTimes && (
                <Infobox
                    contentVisible={visibilities.log}
                    onContentVisibilityChange={() => visibilityChange('log')}
                    title={t('tool-panel.location-track.change-info-heading')}
                    qa-id="location-track-log-infobox">
                    <InfoboxContent>
                        <InfoboxField
                            qaId="location-track-created-date"
                            label={t('tool-panel.created')}
                            value={formatDateShort(changeTimes.created)}
                        />
                        <InfoboxField
                            qaId="location-track-changed-date"
                            label={t('tool-panel.changed')}
                            value={changeTimes.changed && formatDateShort(changeTimes.changed)}
                        />
                        {officialLocationTrack === undefined && (
                            <InfoboxButtons>
                                <Button
                                    onClick={() => setConfirmingDraftDelete(true)}
                                    icon={Icons.Delete}
                                    variant={ButtonVariant.WARNING}
                                    size={ButtonSize.SMALL}>
                                    {t('button.delete-draft')}
                                </Button>
                            </InfoboxButtons>
                        )}
                    </InfoboxContent>
                </Infobox>
            )}

            {officialLocationTrack && (
                <WriteAccessRequired>
                    <Infobox
                        contentVisible={visibilities.ratkoPush}
                        onContentVisibilityChange={() => visibilityChange('ratkoPush')}
                        title={t('tool-panel.location-track.ratko-info-heading')}
                        qa-id="location-track-ratko-infobox">
                        <InfoboxContent>
                            <InfoboxButtons>
                                <Button
                                    onClick={() => setShowRatkoPushDialog(true)}
                                    variant={ButtonVariant.SECONDARY}
                                    size={ButtonSize.SMALL}>
                                    {t('tool-panel.location-track.push-to-ratko')}
                                </Button>
                            </InfoboxButtons>
                        </InfoboxContent>
                    </Infobox>
                </WriteAccessRequired>
            )}

            {showRatkoPushDialog && (
                <LocationTrackRatkoPushDialog
                    locationTrackId={locationTrack.id}
                    onClose={() => setShowRatkoPushDialog(false)}
                    locationTrackChangeTime={locationTrackChangeTime}
                />
            )}

            {confirmingDraftDelete && (
                <LocationTrackDeleteConfirmationDialog
                    id={locationTrack.id}
                    onSave={handleLocationTrackSave}
                    onClose={() => setConfirmingDraftDelete(false)}
                />
            )}

            {showEditDialog && (
                <LocationTrackEditDialogContainer
                    onClose={closeEditLocationTrackDialog}
                    onSave={handleLocationTrackSave}
                    locationTrackId={locationTrack.id}
                    locationTrackChangeTime={locationTrackChangeTime}
                />
            )}
        </React.Fragment>
    );
};

export default LocationTrackInfobox;
