import * as React from 'react';
import { useAppDispatch } from 'store/hooks';
import { LayoutTrackNumber } from 'track-layout/track-layout-model';
import { LinkingState } from 'linking/linking-model';
import { createDelegates } from 'store/store-utils';
import { actionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { PublishType, TimeStamp } from 'common/common-model';
import { useTrackNumberReferenceLine } from 'track-layout/track-layout-react-utils';
import TrackNumberInfobox from 'tool-panel/track-number/track-number-infobox';
import { OptionalUnselectableItemCollections } from 'selection/selection-model';
import { MapViewport } from 'map/map-model';

type TrackNumberInfoboxLinkingContainerProps = {
    trackNumber: LayoutTrackNumber;
    linkingState?: LinkingState;
    publishType: PublishType;
    referenceLineChangeTime: TimeStamp;
    onUnselect: (items: OptionalUnselectableItemCollections) => void;
    viewport: MapViewport;
};

const TrackNumberInfoboxLinkingContainer: React.FC<TrackNumberInfoboxLinkingContainerProps> = ({
    trackNumber,
    linkingState,
    publishType,
    referenceLineChangeTime,
    onUnselect,
    viewport,
}: TrackNumberInfoboxLinkingContainerProps) => {
    const dispatch = useAppDispatch();
    const delegates = createDelegates(dispatch, TrackLayoutActions);
    const referenceLine = useTrackNumberReferenceLine(
        trackNumber.id,
        publishType,
        referenceLineChangeTime,
    );

    return (
        <TrackNumberInfobox
            trackNumber={trackNumber}
            referenceLine={referenceLine}
            linkingState={linkingState}
            onStartReferenceLineGeometryChange={delegates.startAlignmentGeometryChange}
            onEndReferenceLineGeometryChange={delegates.stopLinking}
            showArea={delegates.showArea}
            publishType={publishType}
            viewport={viewport}
            referenceLineChangeTime={referenceLineChangeTime}
            onUnselect={() =>
                onUnselect({
                    trackNumbers: [trackNumber.id],
                    referenceLines: referenceLine ? [referenceLine.id] : [],
                })
            }
        />
    );
};

export default TrackNumberInfoboxLinkingContainer;
