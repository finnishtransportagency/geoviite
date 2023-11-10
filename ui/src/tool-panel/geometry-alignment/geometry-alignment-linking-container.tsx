import * as React from 'react';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import { LayoutTrackNumberId, LocationTrackId } from 'track-layout/track-layout-model';
import { LinkingState } from 'linking/linking-model';
import { createDelegates } from 'store/store-utils';
import {
    GeometryAlignmentInfoboxVisibilities,
    trackLayoutActionCreators as TrackLayoutActions,
} from 'track-layout/track-layout-slice';
import { GeometryPlanId } from 'geometry/geometry-model';
import { PublishType } from 'common/common-model';
import GeometryAlignmentInfobox from 'tool-panel/geometry-alignment/geometry-alignment-infobox';
import {
    useLocationTrack,
    useTrackNumberReferenceLine,
} from 'track-layout/track-layout-react-utils';
import { getMaxTimestamp } from 'utils/date-utils';
import { AlignmentHeader } from 'track-layout/layout-map-api';

type GeometryAlignmentLinkingContainerProps = {
    geometryAlignment: AlignmentHeader;
    selectedLocationTrackId?: LocationTrackId;
    selectedTrackNumberId?: LayoutTrackNumberId;
    planId: GeometryPlanId;
    linkingState?: LinkingState;
    publishType: PublishType;
    visibilities: GeometryAlignmentInfoboxVisibilities;
    onVisibilityChange: (visibilities: GeometryAlignmentInfoboxVisibilities) => void;
    verticalGeometryDiagramVisible: boolean;
};

const GeometryAlignmentLinkingContainer: React.FC<GeometryAlignmentLinkingContainerProps> = ({
    geometryAlignment,
    selectedLocationTrackId,
    selectedTrackNumberId,
    planId,
    linkingState,
    publishType,
    visibilities,
    onVisibilityChange,
    verticalGeometryDiagramVisible,
}: GeometryAlignmentLinkingContainerProps) => {
    const delegates = React.useMemo(() => createDelegates(TrackLayoutActions), []);

    const trackLayoutState = useTrackLayoutAppSelector((state) => state);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);
    const locationTrack = useLocationTrack(
        selectedLocationTrackId,
        publishType,
        changeTimes.layoutLocationTrack,
    );
    const referenceLine = useTrackNumberReferenceLine(
        selectedTrackNumberId,
        publishType,
        changeTimes.layoutTrackNumber,
    );

    return (
        <GeometryAlignmentInfobox
            onSelect={delegates.onSelect}
            onUnselect={delegates.onUnselect}
            geometryAlignment={geometryAlignment}
            selectedLayoutLocationTrack={selectedLocationTrackId ? locationTrack : undefined}
            selectedLayoutReferenceLine={selectedLocationTrackId ? undefined : referenceLine}
            planId={planId}
            locationTrackChangeTime={getMaxTimestamp(
                changeTimes.layoutReferenceLine,
                changeTimes.layoutLocationTrack,
            )}
            trackNumberChangeTime={changeTimes.layoutTrackNumber}
            linkingState={linkingState}
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
            publishType={publishType}
            showArea={delegates.showArea}
            visibilities={visibilities}
            onVisibilityChange={onVisibilityChange}
            onVerticalGeometryDiagramVisibilityChange={
                delegates.onVerticalGeometryDiagramVisibilityChange
            }
            verticalGeometryDiagramVisible={verticalGeometryDiagramVisible}
        />
    );
};

export default GeometryAlignmentLinkingContainer;
