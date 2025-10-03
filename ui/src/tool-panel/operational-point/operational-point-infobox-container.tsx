import * as React from 'react';
import {
    OperationalPointInfoboxVisibilities,
    trackLayoutActionCreators as TrackLayoutActions,
} from 'track-layout/track-layout-slice';
import { createDelegates } from 'store/store-utils';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import { OperationalPointInfobox } from 'tool-panel/operational-point/operational-point-infobox';
import { OperationalPointId } from 'track-layout/track-layout-model';
import { useLoader } from 'utils/react-utils';
import { getOperationalPoint } from 'track-layout/layout-operating-point-api';

type OperatingPointInfoboxContainerProps = {
    operationalPointId: OperationalPointId;
    visibilities: OperationalPointInfoboxVisibilities;
    onVisiblityChange: (visibilites: OperationalPointInfoboxVisibilities) => void;
};

export const OperationalPointInfoboxContainer: React.FC<OperatingPointInfoboxContainerProps> = ({
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
                    changeTimes={changeTimes}
                    visibilities={visibilities}
                    onVisibilityChange={onVisiblityChange}
                />
            )}
        </React.Fragment>
    );
};
