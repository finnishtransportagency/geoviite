import styles from './track-layout.module.scss';
import * as React from 'react';
import { ChangeTimes, TrackLayoutState } from 'track-layout/track-layout-store';
import { MapContext } from 'map/map-store';
import { MapViewport, OptionalShownItems } from 'map/map-model';
import MapView from 'map/map-view';
import { MapLayersSettings } from 'map/settings/map-layer-settings';
import {
    OnClickLocationFunction,
    OnHighlightItemsFunction,
    OnHoverLocationFunction,
    OnSelectFunction,
} from 'selection/selection-model';
import { ToolBar } from 'tool-bar/tool-bar';
import { SelectionPanelContainer } from 'selection-panel/selection-panel-container';
import { SwitchSuggestionCreatorContainer } from 'linking/switch-suggestion-creator-container';
import ToolPanelContainer from 'tool-panel/tool-panel-container';
import { BoundingBox } from 'model/geometry';
import { PublishType } from 'common/common-model';
import { LinkPoint } from 'linking/linking-model';

// For now use whole state and some extras as params
export type TrackLayoutParams = TrackLayoutState & {
    onViewportChange: (viewport: MapViewport) => void;
    onSelect: OnSelectFunction;
    onHighlightItems: OnHighlightItemsFunction;
    onHoverLocation: OnHoverLocationFunction;
    onClickLocation: OnClickLocationFunction;
    onMapSettingsVisibilityChange: (visible: boolean) => void;
    onPublishTypeChange: (publishType: PublishType) => void;
    onOpenPreview: () => void;
    onLayerVisibilityChange: (layerId: string, visible: boolean) => void;
    onTrackNumberVisibilityChange: (layerId: string, visible: boolean) => void;
    onShownItemsChange: (shownItems: OptionalShownItems) => void;
    showArea: (area: BoundingBox) => void;
    onSetLayoutClusterLinkPoint: (linkPoint: LinkPoint) => void;
    onSetGeometryClusterLinkPoint: (linkPoint: LinkPoint) => void;
    onRemoveGeometryLinkPoint: (linkPoint: LinkPoint) => void;
    onRemoveLayoutLinkPoint: (linkPoint: LinkPoint) => void;
    changeTimes: ChangeTimes;
    onStopLinking: () => void;
};

export const TrackLayoutView: React.FC<TrackLayoutParams> = (props: TrackLayoutParams) => {
    return (
        <div className={styles['track-layout']} qa-id="track-layout-content">
            <ToolBar
                settingsVisible={props.map.settingsVisible}
                publishType={props.publishType}
                onMapSettingsVisibilityChange={props.onMapSettingsVisibilityChange}
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
                onSelectSwitch={(s) =>
                    props.onSelect({
                        switches: [s],
                    })
                }
                onSelectKmPost={(kmPost) =>
                    props.onSelect({
                        kmPosts: [kmPost],
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
                    {props.map.settingsVisible && (
                        <div className={styles['track-layout__map-settings']}>
                            <MapLayersSettings
                                map={props.map}
                                onTrackNumberVisibilityChange={props.onTrackNumberVisibilityChange}
                                onLayerVisibilityChange={props.onLayerVisibilityChange}
                                onClose={() => props.onMapSettingsVisibilityChange(false)}
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
