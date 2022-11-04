import * as React from 'react';
import { useTrackLayoutAppDispatch, useTrackLayoutAppSelector } from 'store/hooks';
import {
    LocationTrackId,
    MapAlignment,
    MapSegment,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { LinkingState } from 'linking/linking-model';
import { createDelegates } from 'store/store-utils';
import { actionCreators as TrackLayoutActions } from 'track-layout/track-layout-store';
import { GeometryPlanId } from 'geometry/geometry-model';
import { PublishType } from 'common/common-model';
import GeometryAlignmentInfobox from 'tool-panel/geometry-alignment/geometry-alignment-infobox';
import { useLocationTrack, useReferenceLine } from 'track-layout/track-layout-react-utils';
import { getMaxTimestamp } from 'utils/date-utils';

type GeometryAlignmentLinkingContainerProps = {
    geometryAlignment: MapAlignment;
    segment?: MapSegment;
    selectedLocationTrackId?: LocationTrackId;
    selectedReferenceLineId?: ReferenceLineId;
    planId: GeometryPlanId;
    linkingState?: LinkingState;
    publishType: PublishType;
};

const GeometryAlignmentLinkingContainer: React.FC<GeometryAlignmentLinkingContainerProps> = ({
    geometryAlignment,
    segment,
    selectedLocationTrackId,
    selectedReferenceLineId,
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
    const referenceLine = useReferenceLine(
        selectedReferenceLineId,
        publishType,
        changeTimes.layoutReferenceLine,
    );

    return (
        <GeometryAlignmentInfobox
            onSelect={delegates.onSelect}
            geometryAlignment={geometryAlignment}
            layoutLocationTrack={selectedLocationTrackId ? locationTrack : undefined}
            layoutReferenceLine={selectedReferenceLineId ? referenceLine : undefined}
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
            resolution={store.map.viewport.resolution}
            publishType={publishType}
            showArea={delegates.showArea}
        />
    );
};

export default GeometryAlignmentLinkingContainer;
