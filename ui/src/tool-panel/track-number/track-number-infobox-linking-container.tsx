import * as React from 'react';
import { LayoutTrackNumber } from 'track-layout/track-layout-model';
import { createDelegates } from 'store/store-utils';
import {
    trackLayoutActionCreators as TrackLayoutActions,
    TrackNumberInfoboxVisibilities,
} from 'track-layout/track-layout-slice';
import { useTrackNumberReferenceLine } from 'track-layout/track-layout-react-utils';
import TrackNumberInfobox from 'tool-panel/track-number/track-number-infobox';
import { HighlightedAlignment } from 'tool-panel/alignment-plan-section-infobox-content';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';

type TrackNumberInfoboxLinkingContainerProps = {
    trackNumber: LayoutTrackNumber;
    visibilities: TrackNumberInfoboxVisibilities;
    onVisibilityChange: (visibilities: TrackNumberInfoboxVisibilities) => void;
    setHoveredOverItem: (item: HighlightedAlignment | undefined) => void;
};

const TrackNumberInfoboxLinkingContainer: React.FC<TrackNumberInfoboxLinkingContainerProps> = ({
    trackNumber,
    visibilities,
    onVisibilityChange,
    setHoveredOverItem,
}: TrackNumberInfoboxLinkingContainerProps) => {
    const trackLayoutState = useTrackLayoutAppSelector((state) => state);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);
    const delegates = React.useMemo(() => createDelegates(TrackLayoutActions), []);

    const referenceLine = useTrackNumberReferenceLine(
        trackNumber.id,
        trackLayoutState.publishType,
        changeTimes.layoutReferenceLine,
    );

    return (
        <TrackNumberInfobox
            trackNumber={trackNumber}
            referenceLine={referenceLine}
            linkingState={trackLayoutState.linkingState}
            onStartReferenceLineGeometryChange={(interval) => {
                delegates.showLayers(['alignment-linking-layer']);
                delegates.startAlignmentGeometryChange(interval);
            }}
            onEndReferenceLineGeometryChange={() => {
                delegates.hideLayers(['alignment-linking-layer']);
                delegates.stopLinking();
            }}
            showArea={delegates.showArea}
            publishType={trackLayoutState.publishType}
            viewport={trackLayoutState.map.viewport}
            changeTimes={changeTimes}
            onSelect={delegates.onSelect}
            onUnselect={delegates.onUnselect}
            visibilities={visibilities}
            onVisibilityChange={onVisibilityChange}
            onHighlightItem={setHoveredOverItem}
        />
    );
};

export default TrackNumberInfoboxLinkingContainer;
