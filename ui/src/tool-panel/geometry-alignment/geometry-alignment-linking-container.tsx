import * as React from 'react';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import { LayoutTrackNumberId, LocationTrackId } from 'track-layout/track-layout-model';
import { createDelegates } from 'store/store-utils';
import {
    GeometryAlignmentInfoboxVisibilities,
    trackLayoutActionCreators as TrackLayoutActions,
} from 'track-layout/track-layout-slice';
import { GeometryPlanId } from 'geometry/geometry-model';
import GeometryAlignmentInfobox from 'tool-panel/geometry-alignment/geometry-alignment-infobox';
import {
    useLocationTrack,
    useTrackNumberReferenceLine,
} from 'track-layout/track-layout-react-utils';
import { GeometryAlignmentHeader } from 'track-layout/layout-map-api';

type GeometryAlignmentLinkingContainerProps = {
    geometryAlignment: GeometryAlignmentHeader;
    selectedLocationTrackId?: LocationTrackId;
    selectedTrackNumberId?: LayoutTrackNumberId;
    planId: GeometryPlanId;
    visibilities: GeometryAlignmentInfoboxVisibilities;
    onVisibilityChange: (visibilities: GeometryAlignmentInfoboxVisibilities) => void;
};

const GeometryAlignmentLinkingContainer: React.FC<GeometryAlignmentLinkingContainerProps> = ({
    geometryAlignment,
    selectedLocationTrackId,
    selectedTrackNumberId,
    planId,
    visibilities,
    onVisibilityChange,
}: GeometryAlignmentLinkingContainerProps) => {
    const delegates = React.useMemo(() => createDelegates(TrackLayoutActions), []);

    const trackLayoutState = useTrackLayoutAppSelector((state) => state);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);
    const locationTrack = useLocationTrack(
        selectedLocationTrackId,
        trackLayoutState.layoutContext,
        changeTimes.layoutLocationTrack,
    );
    const referenceLine = useTrackNumberReferenceLine(
        selectedTrackNumberId,
        trackLayoutState.layoutContext,
        changeTimes.layoutTrackNumber,
    );

    return (
        <GeometryAlignmentInfobox
            onSelect={delegates.onSelect}
            onUnselect={delegates.onUnselect}
            geometryAlignment={geometryAlignment}
            selectedLayoutLocationTrack={locationTrack}
            selectedLayoutReferenceLine={referenceLine}
            planId={planId}
            changeTimes={changeTimes}
            linkingState={trackLayoutState.linkingState}
            onLinkingStart={(params) => {
                delegates.showLayers(['alignment-linking-layer']);
                delegates.startAlignmentLinking(params);
            }}
            onLockAlignment={delegates.lockAlignmentSelection}
            onStopLinking={() => {
                delegates.hideLayers(['alignment-linking-layer']);
                delegates.stopLinking();
            }}
            resolution={trackLayoutState.map.viewport.resolution}
            layoutContext={trackLayoutState.layoutContext}
            showArea={delegates.showArea}
            visibilities={visibilities}
            onVisibilityChange={onVisibilityChange}
            onVerticalGeometryDiagramVisibilityChange={
                delegates.onVerticalGeometryDiagramVisibilityChange
            }
            verticalGeometryDiagramVisible={
                trackLayoutState.map.verticalGeometryDiagramState.visible
            }
        />
    );
};

export default GeometryAlignmentLinkingContainer;
