import * as React from 'react';
import { useTrackLayoutAppDispatch } from 'store/hooks';
import { LayoutTrackNumber } from 'track-layout/track-layout-model';
import { LinkingState } from 'linking/linking-model';
import { createDelegates } from 'store/store-utils';
import { actionCreators as TrackLayoutActions } from 'track-layout/track-layout-store';
import { PublishType, TimeStamp } from 'common/common-model';
import { useTrackNumberReferenceLine } from 'track-layout/track-layout-react-utils';
import TrackNumberInfobox from 'tool-panel/track-number/track-number-infobox';
import { OptionalUnselectableItemCollections } from 'selection/selection-model';

type TrackNumberInfoboxLinkingContainerProps = {
    trackNumber: LayoutTrackNumber;
    linkingState?: LinkingState;
    publishType: PublishType;
    referenceLineChangeTime: TimeStamp;
    onUnselect: (items: OptionalUnselectableItemCollections) => void;
};

const TrackNumberInfoboxLinkingContainer: React.FC<TrackNumberInfoboxLinkingContainerProps> = ({
    trackNumber,
    linkingState,
    publishType,
    referenceLineChangeTime,
    onUnselect,
}: TrackNumberInfoboxLinkingContainerProps) => {
    const dispatch = useTrackLayoutAppDispatch();
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
