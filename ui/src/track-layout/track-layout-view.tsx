import styles from './track-layout.module.scss';
import * as React from 'react';
import { MapContext, MapLayerMenuChange } from 'map/map-store';
import { Map, MapViewport, OptionalShownItems } from 'map/map-model';
import MapView from 'map/map-view';
import {
    OnClickLocationFunction,
    OnHighlightItemsFunction,
    OnSelectFunction,
    Selection,
} from 'selection/selection-model';
import { ToolBar } from 'tool-bar/tool-bar';
import { SelectionPanelContainer } from 'selection-panel/selection-panel-container';
import { SwitchSuggestionCreatorContainer } from 'linking/switch-suggestion-creator-container';
import ToolPanelContainer from 'tool-panel/tool-panel-container';
import { BoundingBox } from 'model/geometry';
import { PublishType } from 'common/common-model';
import { LinkingState, LinkingType, LinkPoint } from 'linking/linking-model';
import { ChangeTimes } from 'common/common-slice';
import { MapLayerMenu } from 'map/layer-menu/map-layer-menu';
import VerticalGeometryDiagram from 'vertical-geometry/vertical-geometry-diagram';
import { createClassName } from 'vayla-design-lib/utils';
import { HighlightedAlignment } from 'tool-panel/alignment-plan-section-infobox-content';

// For now use whole state and some extras as params
export type TrackLayoutViewProps = {
    publishType: PublishType;
    selection: Selection;
    map: Map;
    linkingState: LinkingState | undefined;
    onViewportChange: (viewport: MapViewport) => void;
    onSelect: OnSelectFunction;
    onHighlightItems: OnHighlightItemsFunction;
    onClickLocation: OnClickLocationFunction;
    onPublishTypeChange: (publishType: PublishType) => void;
    onOpenPreview: () => void;
    onShownItemsChange: (shownItems: OptionalShownItems) => void;
    showArea: (area: BoundingBox) => void;
    onSetLayoutClusterLinkPoint: (linkPoint: LinkPoint) => void;
    onSetGeometryClusterLinkPoint: (linkPoint: LinkPoint) => void;
    onRemoveGeometryLinkPoint: (linkPoint: LinkPoint) => void;
    onRemoveLayoutLinkPoint: (linkPoint: LinkPoint) => void;
    onLayerMenuItemChange: (change: MapLayerMenuChange) => void;
    changeTimes: ChangeTimes;
    onStopLinking: () => void;
};

export const TrackLayoutView: React.FC<TrackLayoutViewProps> = (props: TrackLayoutViewProps) => {
    const firstSelectedGeometryAlignment = props.selection.selectedItems.geometryAlignments[0];
    const firstSelectedLocationTrack = props.selection.selectedItems.locationTracks[0];
    const verticalDiagramPlanAlignmentId = React.useMemo(
        () =>
            firstSelectedGeometryAlignment
                ? {
                      planId: firstSelectedGeometryAlignment.planId,
                      alignmentId: firstSelectedGeometryAlignment.geometryItem.id,
                  }
                : undefined,
        [firstSelectedGeometryAlignment],
    );
    const verticalDiagramLayoutAlignmentId = React.useMemo(
        () =>
            firstSelectedLocationTrack
                ? {
                      locationTrackId: firstSelectedLocationTrack,
                      publishType: props.publishType,
                  }
                : undefined,
        [firstSelectedLocationTrack, props.publishType],
    );

    const verticalDiagramAlignmentId =
        verticalDiagramPlanAlignmentId ?? verticalDiagramLayoutAlignmentId;

    const showVerticalGeometryDiagram =
        props.map.verticalGeometryDiagramVisible && verticalDiagramAlignmentId != undefined;

    const className = createClassName(
        styles['track-layout'],
        showVerticalGeometryDiagram && styles['track-layout--show-diagram'],
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

                {showVerticalGeometryDiagram && verticalDiagramAlignmentId && (
                    <div className={styles['track-layout__diagram']}>
                        <VerticalGeometryDiagram
                            alignmentId={verticalDiagramAlignmentId}
                            onSelect={props.onSelect}
                            changeTimes={props.changeTimes}
                            showArea={props.showArea}
                        />
                    </div>
                )}

                <div className={styles['track-layout__map']}>
                    {layerMenuVisible && (
                        <div className={styles['track-layout__map-settings']}>
                            <MapLayerMenu
                                onMenuChange={props.onLayerMenuItemChange}
                                onClose={() => setLayerMenuVisible(false)}
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
                        onClickLocation={props.onClickLocation}
                        onShownLayerItemsChange={props.onShownItemsChange}
                        onSetLayoutClusterLinkPoint={props.onSetLayoutClusterLinkPoint}
                        onSetGeometryClusterLinkPoint={props.onSetGeometryClusterLinkPoint}
                        onRemoveGeometryLinkPoint={props.onRemoveGeometryLinkPoint}
                        onRemoveLayoutLinkPoint={props.onRemoveLayoutLinkPoint}
                        hoveredOverPlanSection={hoveredOverPlanSection}
                    />
                </div>
                <div className={styles['track-layout__tool-panel']}>
                    <MapContext.Provider value="trackLayout">
                        <ToolPanelContainer setHoveredOverItem={setHoveredOverPlanSection} />
                    </MapContext.Provider>
                </div>
            </div>

            <SwitchSuggestionCreatorContainer />
        </div>
    );
};
