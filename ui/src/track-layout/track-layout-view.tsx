import styles from './track-layout.module.scss';
import * as React from 'react';
import { MapLayerMenuGroups } from 'map/map-model';
import { MapContext, MapLayerMenuChange } from 'map/map-store';
import { OnSelectFunction } from 'selection/selection-model';
import { ToolBar } from 'tool-bar/tool-bar';
import { SelectionPanelContainer } from 'selection-panel/selection-panel-container';
import { SwitchSuggestionCreatorContainer } from 'linking/switch-suggestion-creator-container';
import ToolPanelContainer from 'tool-panel/tool-panel-container';
import { BoundingBox } from 'model/geometry';
import { LayoutMode, PublishType } from 'common/common-model';
import { LinkingState, LinkingType } from 'linking/linking-model';
import { ChangeTimes } from 'common/common-slice';
import { MapLayerMenu } from 'map/layer-menu/map-layer-menu';
import { createClassName } from 'vayla-design-lib/utils';
import { HighlightedAlignment } from 'tool-panel/alignment-plan-section-infobox-content';
import { MapViewContainer } from 'map/map-view-container';
import { VerticalGeometryDiagramContainer } from 'vertical-geometry/vertical-geometry-diagram-container';

// For now use whole state and some extras as params
export type TrackLayoutViewProps = {
    publishType: PublishType;
    linkingState: LinkingState | undefined;
    onSelect: OnSelectFunction;
    onPublishTypeChange: (publishType: PublishType) => void;
    onLayoutModeChange: (mode: LayoutMode) => void;
    showArea: (area: BoundingBox) => void;
    onLayerMenuItemChange: (change: MapLayerMenuChange) => void;
    mapLayerMenuGroups: MapLayerMenuGroups;
    changeTimes: ChangeTimes;
    onStopLinking: () => void;
    showVerticalGeometryDiagram: boolean;
};

export const TrackLayoutView: React.FC<TrackLayoutViewProps> = (props) => {
    const className = createClassName(
        styles['track-layout'],
        props.showVerticalGeometryDiagram && styles['track-layout--show-diagram'],
    );

    const [layerMenuVisible, setLayerMenuVisible] = React.useState(false);
    const [hoveredOverPlanSection, setHoveredOverPlanSection] =
        React.useState<HighlightedAlignment>();

    return (
        <div className={className} qa-id="track-layout-content">
            <ToolBar
                disableNewMenu={
                    props.linkingState?.type === LinkingType.LinkingGeometryWithAlignment ||
                    props.linkingState?.type === LinkingType.LinkingGeometryWithEmptyAlignment
                }
                layerMenuVisible={layerMenuVisible}
                publishType={props.publishType}
                onMapLayerVisibilityChange={setLayerMenuVisible}
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
                onOpenPreview={() => props.onLayoutModeChange('PREVIEW')}
                changeTimes={props.changeTimes}
                onStopLinking={props.onStopLinking}
            />

            <div className={styles['track-layout__main-view']}>
                <div className={styles['track-layout__navi']}>
                    <SelectionPanelContainer />
                </div>

                {props.showVerticalGeometryDiagram && (
                    <div className={styles['track-layout__diagram']}>
                        <VerticalGeometryDiagramContainer />
                    </div>
                )}

                <div className={styles['track-layout__map']}>
                    {layerMenuVisible && (
                        <div className={styles['track-layout__map-settings']}>
                            <MapLayerMenu
                                onMenuChange={props.onLayerMenuItemChange}
                                onClose={() => setLayerMenuVisible(false)}
                                mapLayerMenuGroups={props.mapLayerMenuGroups}
                            />
                        </div>
                    )}

                    <MapContext.Provider value="track-layout">
                        <MapViewContainer hoveredOverPlanSection={hoveredOverPlanSection} />
                    </MapContext.Provider>
                </div>
                <div className={styles['track-layout__tool-panel']}>
                    <ToolPanelContainer setHoveredOverItem={setHoveredOverPlanSection} />
                </div>
            </div>

            <SwitchSuggestionCreatorContainer />
        </div>
    );
};
