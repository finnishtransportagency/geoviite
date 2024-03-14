import * as React from 'react';
import { LayoutLocationTrack } from 'track-layout/track-layout-model';
import { LinkingState } from 'linking/linking-model';
import {
    refreshLocationTrackSelection,
    useTrackNumber,
} from 'track-layout/track-layout-react-utils';
import { LocationTrackEditDialogContainer } from 'tool-panel/location-track/dialog/location-track-edit-dialog';
import { BoundingBox } from 'model/geometry';
import 'i18n/config';
import { PublishType } from 'common/common-model';
import LocationTrackDeleteConfirmationDialog from 'tool-panel/location-track/location-track-delete-confirmation-dialog';
import { OnSelectFunction, OptionalUnselectableItemCollections } from 'selection/selection-model';
import { LocationTrackRatkoPushDialog } from 'tool-panel/location-track/dialog/location-track-ratko-push-dialog';
import { LocationTrackGeometryInfobox } from 'tool-panel/location-track/location-track-geometry-infobox';
import { MapViewport } from 'map/map-model';
import { LocationTrackInfoboxVisibilities } from 'track-layout/track-layout-slice';
import { LocationTrackVerticalGeometryInfobox } from 'tool-panel/location-track/location-track-vertical-geometry-infobox';
import { HighlightedAlignment } from 'tool-panel/alignment-plan-section-infobox-content';
import { LocationTrackSplittingInfoboxContainer } from 'tool-panel/location-track/splitting/location-track-splitting-infobox';
import { SplittingState } from 'tool-panel/location-track/split-store';
import { EnvRestricted } from 'environment/env-restricted';
import { ChangeTimes } from 'common/common-slice';
import { LocationTrackValidationInfoboxContainer } from 'tool-panel/location-track/location-track-validation-infobox-container';
import { LocationTrackSwitchRelinkingDialogContainer } from 'tool-panel/location-track/dialog/location-track-switch-relinking-dialog';
import { LocationTrackBasicInfoInfoboxContainer } from 'tool-panel/location-track/location-track-basic-info-infobox';
import { LocationTrackLocationInfoboxContainer } from 'tool-panel/location-track/location-track-location-infobox';
import { LocationTrackChangeInfoInfobox } from 'tool-panel/location-track/location-track-change-info-infobox';
import { LocationTrackRatkoSyncInfobox } from 'tool-panel/location-track/location-track-ratko-sync-infobox';
import { PrivilegeRequired } from 'user/privilege-required';
import { PRIV_VIEW_GEOMETRY } from 'user/user-model';

type LocationTrackInfoboxProps = {
    locationTrack: LayoutLocationTrack;
    linkingState?: LinkingState;
    splittingState?: SplittingState;
    showArea: (area: BoundingBox) => void;
    onDataChange: () => void;
    publishType: PublishType;
    changeTimes: ChangeTimes;
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
    linkingState,
    splittingState,
    onDataChange,
    publishType,
    changeTimes,
    onSelect,
    onUnselect,
    viewport,
    visibilities,
    onVisibilityChange,
    verticalGeometryDiagramVisible,
    onVerticalGeometryDiagramVisibilityChange,
    onHighlightItem,
}: LocationTrackInfoboxProps) => {
    const trackNumber = useTrackNumber(publishType, locationTrack?.trackNumberId);

    const [showEditDialog, setShowEditDialog] = React.useState(false);
    const [confirmingDraftDelete, setConfirmingDraftDelete] = React.useState<boolean>();
    const [showRatkoPushDialog, setShowRatkoPushDialog] = React.useState<boolean>(false);
    const [confirmingSwitchRelinking, setConfirmingSwitchRelinking] = React.useState(false);

    const editingDisabled = publishType === 'OFFICIAL' || !!linkingState || !!splittingState;

    function openEditLocationTrackDialog() {
        setShowEditDialog(true);
        onDataChange();
    }
    function closeEditLocationTrackDialog() {
        setShowEditDialog(false);
        onDataChange();
    }

    const handleLocationTrackSave = refreshLocationTrackSelection('DRAFT', onSelect, onUnselect);

    const visibilityChange = (key: keyof LocationTrackInfoboxVisibilities) => {
        onVisibilityChange({ ...visibilities, [key]: !visibilities[key] });
    };

    return (
        <React.Fragment>
            {locationTrack && (
                <LocationTrackBasicInfoInfoboxContainer
                    locationTrack={locationTrack}
                    publishType={publishType}
                    trackNumber={trackNumber}
                    editingDisabled={editingDisabled}
                    visibilities={visibilities}
                    visibilityChange={visibilityChange}
                    openEditLocationTrackDialog={openEditLocationTrackDialog}
                />
            )}
            {splittingState && (
                <EnvRestricted restrictTo="test">
                    <LocationTrackSplittingInfoboxContainer
                        visibilities={visibilities}
                        visibilityChange={visibilityChange}
                    />
                </EnvRestricted>
            )}
            <LocationTrackLocationInfoboxContainer
                locationTrack={locationTrack}
                trackNumber={trackNumber}
                visibilities={visibilities}
                visibilityChange={visibilityChange}
                linkingState={linkingState}
                splittingState={splittingState}
                publishType={publishType}
            />
            <PrivilegeRequired privilege={PRIV_VIEW_GEOMETRY}>
                <LocationTrackGeometryInfobox
                    contentVisible={visibilities.geometry}
                    onContentVisibilityChange={() => visibilityChange('geometry')}
                    publishType={publishType}
                    locationTrackId={locationTrack.id}
                    viewport={viewport}
                    onHighlightItem={onHighlightItem}
                />
            </PrivilegeRequired>
            <LocationTrackVerticalGeometryInfobox
                contentVisible={visibilities.verticalGeometry}
                onContentVisibilityChange={() => visibilityChange('verticalGeometry')}
                onVerticalGeometryDiagramVisibilityChange={
                    onVerticalGeometryDiagramVisibilityChange
                }
                verticalGeometryDiagramVisible={verticalGeometryDiagramVisible}
            />
            {locationTrack.draftType !== 'NEW_DRAFT' && (
                <LocationTrackValidationInfoboxContainer
                    contentVisible={visibilities.validation}
                    onContentVisibilityChange={() => visibilityChange('validation')}
                    id={locationTrack.id}
                    publishType={publishType}
                    showLinkedSwitchesRelinkingDialog={() => setConfirmingSwitchRelinking(true)}
                    editingDisabled={editingDisabled}
                />
            )}
            <LocationTrackChangeInfoInfobox
                locationTrackId={locationTrack.id}
                publishType={publishType}
                visibilities={visibilities}
                visibilityChange={visibilityChange}
                setConfirmingDraftDelete={setConfirmingDraftDelete}
            />
            <LocationTrackRatkoSyncInfobox
                locationTrackId={locationTrack.id}
                visibilities={visibilities}
                visibilityChange={visibilityChange}
                setShowRatkoPushDialog={setShowRatkoPushDialog}
            />

            {showRatkoPushDialog && (
                <LocationTrackRatkoPushDialog
                    locationTrackId={locationTrack.id}
                    onClose={() => setShowRatkoPushDialog(false)}
                    changeTimes={changeTimes}
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
                />
            )}

            {confirmingSwitchRelinking && (
                <LocationTrackSwitchRelinkingDialogContainer
                    locationTrackId={locationTrack.id}
                    publishType={publishType}
                    name={locationTrack.name}
                    closeDialog={() => setConfirmingSwitchRelinking(false)}
                />
            )}
        </React.Fragment>
    );
};

export default LocationTrackInfobox;
