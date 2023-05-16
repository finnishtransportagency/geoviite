import styles from './track-layout.module.scss';
import * as React from 'react';
import { MapContext, MapLayerSettingChange } from 'map/map-store';
import { Map, MapViewport, OptionalShownItems } from 'map/map-model';
import MapView from 'map/map-view';
import {
    OnClickLocationFunction,
    OnHighlightItemsFunction,
    OnHoverLocationFunction,
    OnSelectFunction,
    Selection,
} from 'selection/selection-model';
import { ToolBar } from 'tool-bar/tool-bar';
import { SelectionPanelContainer } from 'selection-panel/selection-panel-container';
import { SwitchSuggestionCreatorContainer } from 'linking/switch-suggestion-creator-container';
import ToolPanelContainer from 'tool-panel/tool-panel-container';
import { BoundingBox } from 'model/geometry';
import { PublishType } from 'common/common-model';
import { LinkingState, LinkPoint } from 'linking/linking-model';
import { ChangeTimes } from 'common/common-slice';
import { MapLayerSettingsMenu } from 'map/settings-menu/map-layer-menu';

// For now use whole state and some extras as params
export type TrackLayoutViewProps = {
    publishType: PublishType;
    selection: Selection;
    map: Map;
    linkingState: LinkingState | undefined;
    onViewportChange: (viewport: MapViewport) => void;
    onSelect: OnSelectFunction;
    onHighlightItems: OnHighlightItemsFunction;
    onHoverLocation: OnHoverLocationFunction;
    onClickLocation: OnClickLocationFunction;
    onPublishTypeChange: (publishType: PublishType) => void;
    onOpenPreview: () => void;
    onShownItemsChange: (shownItems: OptionalShownItems) => void;
    showArea: (area: BoundingBox) => void;
    onSetLayoutClusterLinkPoint: (linkPoint: LinkPoint) => void;
    onSetGeometryClusterLinkPoint: (linkPoint: LinkPoint) => void;
    onRemoveGeometryLinkPoint: (linkPoint: LinkPoint) => void;
    onRemoveLayoutLinkPoint: (linkPoint: LinkPoint) => void;
    onLayerSettingsChange: (change: MapLayerSettingChange) => void;
    changeTimes: ChangeTimes;
    onStopLinking: () => void;
};

export const TrackLayoutView: React.FC<TrackLayoutViewProps> = (props: TrackLayoutViewProps) => {
    const [mapSettingsVisible, setMapSettingsVisible] = React.useState(false);

    return (
        <div className={styles['track-layout']} qa-id="track-layout-content">
            <ToolBar
                settingsVisible={mapSettingsVisible}
                publishType={props.publishType}
                onMapSettingsVisibilityChange={setMapSettingsVisible}
                selection={props.selection}
                showArea={props.showArea}
                onSelectTrackNumber={(trackNumberId) =>
                    props.onSelect({
                        trackNumbers: [trackNumberId],
                    })
                }
                onSelectLocationTrack={(locationTrackId) =>
                    props.onSelect({
                        locationTracks: [locationTrackId],
                    })
                }
                onSelectSwitch={(switchId) =>
                    props.onSelect({
                        switches: [switchId],
                    })
                }
                onSelectKmPost={(kmPostId) =>
                    props.onSelect({
                        kmPosts: [kmPostId],
                    })
                }
                onPublishTypeChange={(publishType: PublishType) => {
                    props.onPublishTypeChange(publishType);
                }}
                onOpenPreview={props.onOpenPreview}
                changeTimes={props.changeTimes}
                onStopLinking={props.onStopLinking}
            />

            <div className={styles['track-layout__main-view']}>
                <div className={styles['track-layout__navi']}>
                    <MapContext.Provider value="trackLayout">
                        <SelectionPanelContainer />
                    </MapContext.Provider>
                </div>

                <div className={styles['track-layout__map']}>
                    {mapSettingsVisible && (
                        <div className={styles['track-layout__map-settings']}>
                            <MapLayerSettingsMenu
                                onSettingChange={props.onLayerSettingsChange}
                                onClose={() => setMapSettingsVisible(false)}
                                map={props.map}
                            />
                        </div>
                    )}

                    <MapView
                        map={props.map}
                        onViewportUpdate={props.onViewportChange}
                        selection={props.selection}
                        publishType={props.publishType}
                        linkingState={props.linkingState}
                        changeTimes={props.changeTimes}
                        onSelect={props.onSelect}
                        onHighlightItems={props.onHighlightItems}
                        onHoverLocation={props.onHoverLocation}
                        onClickLocation={props.onClickLocation}
                        onShownLayerItemsChange={props.onShownItemsChange}
                        onSetLayoutClusterLinkPoint={props.onSetLayoutClusterLinkPoint}
                        onSetGeometryClusterLinkPoint={props.onSetGeometryClusterLinkPoint}
                        onRemoveGeometryLinkPoint={props.onRemoveGeometryLinkPoint}
                        onRemoveLayoutLinkPoint={props.onRemoveLayoutLinkPoint}
                    />
                </div>
                <div className={styles['track-layout__tool-panel']}>
                    <MapContext.Provider value="trackLayout">
                        <ToolPanelContainer />
                    </MapContext.Provider>
                </div>
            </div>

            <SwitchSuggestionCreatorContainer />
        </div>
    );
};
