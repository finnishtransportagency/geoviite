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
import { VIEW_GEOMETRY } from 'user/user-model';
import { draftLayoutContext, LayoutContext } from 'common/common-model';

type LocationTrackInfoboxProps = {
    locationTrack: LayoutLocationTrack;
    linkingState?: LinkingState;
    splittingState?: SplittingState;
    showArea: (area: BoundingBox) => void;
    onDataChange: () => void;
    layoutContext: LayoutContext;
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
    layoutContext,
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
    const trackNumber = useTrackNumber(locationTrack.trackNumberId, layoutContext);

    const [showEditDialog, setShowEditDialog] = React.useState(false);
    const [confirmingDraftDelete, setConfirmingDraftDelete] = React.useState<boolean>();
    const [showRatkoPushDialog, setShowRatkoPushDialog] = React.useState<boolean>(false);
    const [confirmingSwitchRelinking, setConfirmingSwitchRelinking] = React.useState(false);

    const editingDisabled =
        layoutContext.publicationState === 'OFFICIAL' || !!linkingState || !!splittingState;

    function openEditLocationTrackDialog() {
        setShowEditDialog(true);
        onDataChange();
    }
    function closeEditLocationTrackDialog() {
        setShowEditDialog(false);
        onDataChange();
    }

    const handleLocationTrackSave = refreshLocationTrackSelection(
        draftLayoutContext(layoutContext),
        onSelect,
        onUnselect,
    );

    const visibilityChange = (key: keyof LocationTrackInfoboxVisibilities) => {
        onVisibilityChange({ ...visibilities, [key]: !visibilities[key] });
    };

    return (
        <React.Fragment>
            <LocationTrackBasicInfoInfoboxContainer
                locationTrack={locationTrack}
                layoutContext={layoutContext}
                trackNumber={trackNumber}
                editingDisabled={editingDisabled}
                visibilities={visibilities}
                visibilityChange={visibilityChange}
                openEditLocationTrackDialog={openEditLocationTrackDialog}
            />
            {splittingState && (
                <EnvRestricted restrictTo="test">
                    <LocationTrackSplittingInfoboxContainer
                        visibilities={visibilities}
                        visibilityChange={visibilityChange}
                        layoutContext={layoutContext}
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
                layoutContext={layoutContext}
            />
            <PrivilegeRequired privilege={VIEW_GEOMETRY}>
                <LocationTrackGeometryInfobox
                    contentVisible={visibilities.geometry}
                    onContentVisibilityChange={() => visibilityChange('geometry')}
                    layoutContext={layoutContext}
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
            <LocationTrackValidationInfoboxContainer
                contentVisible={visibilities.validation}
                onContentVisibilityChange={() => visibilityChange('validation')}
                id={locationTrack.id}
                layoutContext={layoutContext}
                showLinkedSwitchesRelinkingDialog={() => setConfirmingSwitchRelinking(true)}
                editingDisabled={editingDisabled}
            />
            <LocationTrackChangeInfoInfobox
                locationTrackId={locationTrack.id}
                layoutContext={layoutContext}
                visibilities={visibilities}
                visibilityChange={visibilityChange}
                setConfirmingDraftDelete={setConfirmingDraftDelete}
            />
            <LocationTrackRatkoSyncInfobox
                layoutContext={layoutContext}
                locationTrackId={locationTrack.id}
                visibilities={visibilities}
                visibilityChange={visibilityChange}
                setShowRatkoPushDialog={setShowRatkoPushDialog}
            />

            {showRatkoPushDialog && (
                <LocationTrackRatkoPushDialog
                    layoutContext={layoutContext}
                    locationTrackId={locationTrack.id}
                    onClose={() => setShowRatkoPushDialog(false)}
                    changeTimes={changeTimes}
                />
            )}

            {confirmingDraftDelete && (
                <LocationTrackDeleteConfirmationDialog
                    layoutContext={layoutContext}
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
                    layoutContext={layoutContext}
                    name={locationTrack.name}
                    closeDialog={() => setConfirmingSwitchRelinking(false)}
                />
            )}
        </React.Fragment>
    );
};

export default LocationTrackInfobox;
