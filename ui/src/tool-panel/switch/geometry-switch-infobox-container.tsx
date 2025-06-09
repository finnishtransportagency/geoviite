import * as React from 'react';
import { GeometryPlanId, GeometrySwitchId } from 'geometry/geometry-model';
import { LinkingState } from 'linking/linking-model';
import { Point } from 'model/geometry';
import {
    GeometrySwitchInfoboxVisibilities,
    trackLayoutActionCreators as TrackLayoutActions,
} from 'track-layout/track-layout-slice';
import { useLoader } from 'utils/react-utils';
import { getGeometrySwitch, getGeometrySwitchLayout } from 'geometry/geometry-api';
import { usePlanHeader, useSwitchStructure } from 'track-layout/track-layout-react-utils';
import { createDelegates } from 'store/store-utils';
import { calculateBoundingBoxToShowAroundLocation } from 'map/map-utils';
import GeometrySwitchInfobox from 'tool-panel/switch/geometry-switch-infobox';
import { useCommonDataAppSelector } from 'store/hooks';

type GeometrySwitchInfoboxContainerProps = {
    geometrySwitchId: GeometrySwitchId;
    geometryPlanId: GeometryPlanId;
    linkingState?: LinkingState;
    visibilities: GeometrySwitchInfoboxVisibilities;
    onVisibilityChange: (visibilities: GeometrySwitchInfoboxVisibilities) => void;
};

export const GeometrySwitchInfoboxContainer: React.FC<GeometrySwitchInfoboxContainerProps> = ({
    geometrySwitchId,
    geometryPlanId,
    linkingState,
    visibilities,
    onVisibilityChange,
}) => {
    const geometrySwitch = useLoader(
        () => (geometrySwitchId ? getGeometrySwitch(geometrySwitchId) : undefined),
        [geometrySwitchId],
    );
    const geometrySwitchLayout = useLoader(
        () => (geometrySwitchId ? getGeometrySwitchLayout(geometrySwitchId) : undefined),
        [geometrySwitchId],
    );
    const switchStructure = useSwitchStructure(geometrySwitch?.switchStructureId);
    const delegates = React.useMemo(() => createDelegates(TrackLayoutActions), []);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);

    const onShowOnMap = React.useCallback(
        (location: Point) => delegates.showArea(calculateBoundingBoxToShowAroundLocation(location)),
        [],
    );
    const planHeader = usePlanHeader(geometryPlanId);

    return (
        <GeometrySwitchInfobox
            geometrySwitch={geometrySwitch}
            geometrySwitchLayout={geometrySwitchLayout}
            planHeader={planHeader}
            switchStructure={switchStructure}
            changeTimes={changeTimes}
            onShowOnMap={onShowOnMap}
            visibilities={visibilities}
            onVisibilityChange={onVisibilityChange}
            linkingState={linkingState}
        />
    );
};
