import styles from './track-layout.module.scss';
import * as React from 'react';
import { MapLayerMenuChange, MapLayerMenuGroups, MapLayerName } from 'map/map-model';
import { MapContext } from 'map/map-store';
import { OnSelectFunction } from 'selection/selection-model';
import { ToolBar } from 'tool-bar/tool-bar';
import { SelectionPanelContainer } from 'selection-panel/selection-panel-container';
import { SwitchSuggestionCreatorContainer } from 'linking/switch-suggestion-creator-container';
import ToolPanelContainer from 'tool-panel/tool-panel-container';
import { BoundingBox } from 'model/geometry';
import { LayoutMode, PublishType } from 'common/common-model';
import { LinkingState, LinkingType } from 'linking/linking-model';
import { ChangeTimes } from 'common/common-slice';
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
    visibleMapLayers: MapLayerName[];
};

export const TrackLayoutView: React.FC<TrackLayoutViewProps> = ({
    publishType,
    linkingState,
    onSelect,
    onPublishTypeChange,
    onLayoutModeChange,
    showArea,
    onLayerMenuItemChange,
    mapLayerMenuGroups,
    changeTimes,
    onStopLinking,
    showVerticalGeometryDiagram,
    visibleMapLayers,
}) => {
    const className = createClassName(
        styles['track-layout'],
        showVerticalGeometryDiagram && styles['track-layout--show-diagram'],
    );

    const [hoveredOverPlanSection, setHoveredOverPlanSection] =
        React.useState<HighlightedAlignment>();

    return (
        <div className={className} qa-id="track-layout-content">
            <ToolBar
                disableNewMenu={
                    linkingState?.type === LinkingType.LinkingGeometryWithAlignment ||
                    linkingState?.type === LinkingType.LinkingGeometryWithEmptyAlignment
                }
                publishType={publishType}
                showArea={showArea}
                onSelectTrackNumber={(trackNumberId) =>
                    onSelect({
                        trackNumbers: [trackNumberId],
                    })
                }
                onSelectLocationTrack={(locationTrackId) =>
                    onSelect({
                        locationTracks: [locationTrackId],
                    })
                }
                onSelectSwitch={(switchId) =>
                    onSelect({
                        switches: [switchId],
                    })
                }
                onSelectKmPost={(kmPostId) =>
                    onSelect({
                        kmPosts: [kmPostId],
                    })
                }
                onPublishTypeChange={(publishType: PublishType) => {
                    onPublishTypeChange(publishType);
                }}
                onOpenPreview={() => onLayoutModeChange('PREVIEW')}
                changeTimes={changeTimes}
                onStopLinking={onStopLinking}
                onMapLayerChange={onLayerMenuItemChange}
                mapLayerMenuGroups={mapLayerMenuGroups}
                visibleLayers={visibleMapLayers}
            />

            <div className={styles['track-layout__main-view']}>
                <div className={styles['track-layout__navi']}>
                    <SelectionPanelContainer />
                </div>

                {showVerticalGeometryDiagram && (
                    <div className={styles['track-layout__diagram']}>
                        <VerticalGeometryDiagramContainer />
                    </div>
                )}

                <div className={styles['track-layout__map']}>
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
