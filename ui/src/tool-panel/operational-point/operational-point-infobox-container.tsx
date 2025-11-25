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
import { getOperationalPoint } from 'track-layout/layout-operational-point-api';
import { calculateBoundingBoxToShowAroundLocation } from 'map/map-utils';

type OperationalPointInfoboxContainerProps = {
    operationalPointId: OperationalPointId;
    visibilities: OperationalPointInfoboxVisibilities;
    onVisiblityChange: (visibilites: OperationalPointInfoboxVisibilities) => void;
    onDataChange: () => void;
};

export const OperationalPointInfoboxContainer: React.FC<OperationalPointInfoboxContainerProps> = ({
    operationalPointId,
    visibilities,
    onVisiblityChange,
    onDataChange,
}) => {
    const trackLayoutState = useTrackLayoutAppSelector((state) => state);
    const delegates = React.useMemo(() => createDelegates(TrackLayoutActions), []);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);

    const operationalPoint = useLoader(
        () =>
            getOperationalPoint(
                operationalPointId,
                trackLayoutState.layoutContext,
                changeTimes.operationalPoints,
            ),
        [operationalPointId, trackLayoutState.layoutContext, changeTimes.operationalPoints],
    );

    const showOnMap = () => {
        operationalPoint?.location &&
            delegates.showArea(calculateBoundingBoxToShowAroundLocation(operationalPoint.location));
    };

    const startPlacingOperationalPoint = () => {
        if (operationalPoint) {
            delegates.startPlacingOperationalPoint(operationalPoint);
            delegates.showLayers(['operational-points-placing-layer']);
        }
    };

    const stopPlacingOperationalPoint = () => {
        delegates.stopPlacingOperationalPoint();
        delegates.hideLayers(['operational-points-placing-layer']);
    };

    const startPlacingOperationalPointArea = () => {
        if (operationalPoint) {
            delegates.startPlacingOperationalPointArea(operationalPoint);
            delegates.showLayers(['operational-points-area-placing-layer']);
        }
    };

    const stopPlacingOperationalPointArea = () => {
        delegates.stopPlacingOperationalPointArea();
        delegates.hideLayers(['operational-points-area-placing-layer']);
    };

    return (
        <React.Fragment>
            {operationalPoint && (
                <OperationalPointInfobox
                    operationalPoint={operationalPoint}
                    layoutContext={trackLayoutState.layoutContext}
                    changeTimes={changeTimes}
                    layoutState={trackLayoutState}
                    visibilities={visibilities}
                    onVisibilityChange={onVisiblityChange}
                    onDataChange={onDataChange}
                    onSelect={delegates.onSelect}
                    onUnselect={delegates.onUnselect}
                    onStartPlacingLocation={startPlacingOperationalPoint}
                    onStopPlacingLocation={stopPlacingOperationalPoint}
                    onStartPlacingArea={startPlacingOperationalPointArea}
                    onStopPlacingArea={stopPlacingOperationalPointArea}
                    onShowOnMap={showOnMap}
                />
            )}
        </React.Fragment>
    );
};
