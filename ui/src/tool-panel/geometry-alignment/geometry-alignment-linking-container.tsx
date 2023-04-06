import * as React from 'react';
import { useTrackLayoutAppDispatch, useTrackLayoutAppSelector } from 'store/hooks';
import {
    LayoutTrackNumberId,
    LocationTrackId,
    MapAlignment,
    MapSegment,
} from 'track-layout/track-layout-model';
import { LinkingState } from 'linking/linking-model';
import { createDelegates } from 'store/store-utils';
import { actionCreators as TrackLayoutActions } from 'track-layout/track-layout-store';
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
    const dispatch = useTrackLayoutAppDispatch();
    const delegates = React.useMemo(() => {
        return createDelegates(dispatch, TrackLayoutActions);
    }, []);

    const store = useTrackLayoutAppSelector((state) => state.trackLayout);
    const changeTimes = store.changeTimes;
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
            selectedLayoutLocationTrack={selectedLocationTrackId ? locationTrack : undefined}
            selectedLayoutReferenceLine={selectedLocationTrackId ? undefined : referenceLine}
            segment={segment}
            planId={planId}
            locationTrackChangeTime={getMaxTimestamp(
                changeTimes.layoutReferenceLine,
                changeTimes.layoutLocationTrack,
            )}
            trackNumberChangeTime={changeTimes.layoutTrackNumber}
            linkingState={linkingState}
            onLinkingStart={delegates.startAlignmentLinking}
            onLockAlignment={delegates.lockAlignmentSelection}
            onStopLinking={delegates.stopLinking}
            resolution={store.map.viewport.resolution}
            publishType={publishType}
            showArea={delegates.showArea}
        />
    );
};

export default GeometryAlignmentLinkingContainer;
