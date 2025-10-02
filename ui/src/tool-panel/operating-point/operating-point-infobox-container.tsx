import * as React from 'react';
import {
    OperatingPointInfoboxVisibilities,
    trackLayoutActionCreators as TrackLayoutActions,
} from 'track-layout/track-layout-slice';
import { createDelegates } from 'store/store-utils';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import { OperationalPointInfobox } from 'tool-panel/operating-point/operational-point-infobox';
import { OperationalPointId } from 'track-layout/track-layout-model';
import { useLoader } from 'utils/react-utils';
import { getOperationalPoint } from 'track-layout/layout-operating-point-api';

type OperatingPointInfoboxContainerProps = {
    operationalPointId: OperationalPointId;
    visibilities: OperatingPointInfoboxVisibilities;
    onVisiblityChange: (visibilites: OperatingPointInfoboxVisibilities) => void;
};

export const OperatingPointInfoboxContainer: React.FC<OperatingPointInfoboxContainerProps> = ({
    operationalPointId,
    visibilities,
    onVisiblityChange,
}) => {
    const trackLayoutState = useTrackLayoutAppSelector((state) => state);
    const _delegates = React.useMemo(() => createDelegates(TrackLayoutActions), []);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);

    const operationalPoint = useLoader(
        () =>
            getOperationalPoint(
                operationalPointId,
                trackLayoutState.layoutContext,
                changeTimes.operatingPoints,
            ),
        [changeTimes.operatingPoints],
    );

    return (
        <React.Fragment>
            {operationalPoint && (
                <OperationalPointInfobox
                    operationalPoint={operationalPoint}
                    layoutContext={trackLayoutState.layoutContext}
                    visibilities={visibilities}
                    onVisibilityChange={onVisiblityChange}
                />
            )}
        </React.Fragment>
    );
};
