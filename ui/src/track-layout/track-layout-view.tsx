import styles from "./track-layout.module.scss";
import * as React from "react";
import { MapContext, MapLayerMenuChange } from "map/map-store";
import { Map, MapViewport, OptionalShownItems } from "map/map-model";
import MapView from "map/map-view";
import {
    OnClickLocationFunction,
    OnHighlightItemsFunction,
    OnHoverLocationFunction,
    OnSelectFunction,
    Selection
} from "selection/selection-model";
import { ToolBar } from "tool-bar/tool-bar";
import { SelectionPanelContainer } from "selection-panel/selection-panel-container";
import { SwitchSuggestionCreatorContainer } from "linking/switch-suggestion-creator-container";
import ToolPanelContainer from "tool-panel/tool-panel-container";
import { BoundingBox } from "model/geometry";
import { PublishType } from "common/common-model";
import { LinkingState, LinkPoint } from "linking/linking-model";
import { ChangeTimes } from "common/common-slice";
import { MapLayerMenu } from "map/layer-menu/map-layer-menu";
import VerticalGeometryDiagram from "vertical-geometry/vertical-geometry-diagram";
import { createClassName } from "vayla-design-lib/utils";

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
    onLayerMenuItemChange: (change: MapLayerMenuChange) => void;
    changeTimes: ChangeTimes;
    onStopLinking: () => void;
};

export const TrackLayoutView: React.FC<TrackLayoutViewProps> = (props: TrackLayoutViewProps) => {
    const verticalDiagramAlignmentId = React.useMemo(
        () =>
            props.selection.selectedItems.locationTracks[0] && {
                locationTrackId: props.selection.selectedItems.locationTracks[0],
                publishType: props.publishType,
            },
        [props.selection.selectedItems.locationTracks[0], props.publishType],
    );

    const showVerticalGeometryDiagram =
        props.map.verticalGeometryDiagramVisible && verticalDiagramAlignmentId != undefined;

    const className = createClassName(
        styles['track-layout'],
        showVerticalGeometryDiagram && styles['track-layout--show-diagram'],
    );

    const [layerMenuVisible, setLayerMenuVisible] = React.useState(false);

    return (
        <div className={className} qa-id="track-layout-content">
            <ToolBar
                layerMenuVisible={layerMenuVisible}
                publishType={props.publishType}
                onMapLayerVisibilityChange={setLayerMenuVisible}
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
