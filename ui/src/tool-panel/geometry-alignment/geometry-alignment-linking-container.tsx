import * as React from 'react';
import { useAppDispatch, useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import {
    LayoutTrackNumberId,
    LocationTrackId,
    MapAlignment,
    MapSegment,
} from 'track-layout/track-layout-model';
import { LinkingState } from 'linking/linking-model';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { GeometryPlanId } from 'geometry/geometry-model';
import { PublishType } from 'common/common-model';
import GeometryAlignmentInfobox from 'tool-panel/geometry-alignment/geometry-alignment-infobox';
import {
    useLocationTrack,
    useTrackNumberReferenceLine,
} from 'track-layout/track-layout-react-utils';
import { getMaxTimestamp } from 'utils/date-utils';

type GeometryAlignmentLinkingContainerProps = {
    geometryAlignment: MapAlignment;
    segment?: MapSegment;
    selectedLocationTrackId?: LocationTrackId;
    selectedTrackNumberId?: LayoutTrackNumberId;
    planId: GeometryPlanId;
    linkingState?: LinkingState;
    publishType: PublishType;
};

const GeometryAlignmentLinkingContainer: React.FC<GeometryAlignmentLinkingContainerProps> = ({
    geometryAlignment,
    segment,
    selectedLocationTrackId,
    selectedTrackNumberId,
    planId,
    linkingState,
    publishType,
}: GeometryAlignmentLinkingContainerProps) => {
    const dispatch = useAppDispatch();
    const delegates = React.useMemo(() => {
        return createDelegates(dispatch, TrackLayoutActions);
    }, []);

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
            geometryAlignment={geometryAlignment}
            layoutLocationTrack={selectedLocationTrackId ? locationTrack : undefined}
            layoutReferenceLine={selectedLocationTrackId ? undefined : referenceLine}
            segment={segment}
            planId={planId}
            alignmentChangeTime={getMaxTimestamp(
                changeTimes.layoutReferenceLine,
                changeTimes.layoutLocationTrack,
            )}
            trackNumberChangeTime={changeTimes.layoutTrackNumber}
            linkingState={linkingState}
            onLinkingStart={delegates.startAlignmentLinking}
            onLockAlignment={delegates.lockAlignmentSelection}
            onStopLinking={delegates.stopLinking}
            resolution={trackLayoutState.map.viewport.resolution}
            publishType={publishType}
            showArea={delegates.showArea}
        />
    );
};

export default GeometryAlignmentLinkingContainer;
