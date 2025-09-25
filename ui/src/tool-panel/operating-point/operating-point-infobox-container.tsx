import * as React from 'react';
import {
    OperatingPointInfoboxVisibilities,
    trackLayoutActionCreators as TrackLayoutActions,
} from 'track-layout/track-layout-slice';
import { createDelegates } from 'store/store-utils';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import { OperatingPointInfobox } from 'tool-panel/operating-point/operating-point-infobox';

type OperatingPointInfoboxContainerProps = {
    visibilities: OperatingPointInfoboxVisibilities;
    onVisiblityChange: (visibilites: OperatingPointInfoboxVisibilities) => void;
};

export const OperatingPointInfoboxContainer: React.FC<OperatingPointInfoboxContainerProps> = ({
    visibilities,
    onVisiblityChange,
}) => {
    const trackLayoutState = useTrackLayoutAppSelector((state) => state);
    const _delegates = React.useMemo(() => createDelegates(TrackLayoutActions), []);
    const _changeTimes = useCommonDataAppSelector((state) => state.changeTimes);

    return (
        <OperatingPointInfobox
            layoutContext={trackLayoutState.layoutContext}
            visibilities={visibilities}
            onVisibilityChange={onVisiblityChange}
        />
    );
};
