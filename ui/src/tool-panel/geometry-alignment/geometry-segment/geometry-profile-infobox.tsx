import * as React from 'react';
import { MapSegment } from 'track-layout/track-layout-model';
import { GeometryProfileRange } from 'tool-panel/geometry-alignment/geometry-segment/geometry-profile-range';
import { GeometryPlanHeader } from 'geometry/geometry-model';

type VerticalGeometryInfoBoxProps = {
    chosenSegment: MapSegment;
    planHeader: GeometryPlanHeader;
};

const GeometryProfileInfobox: React.FC<VerticalGeometryInfoBoxProps> = ({
    chosenSegment,
    planHeader,
}: VerticalGeometryInfoBoxProps) => {
    return <GeometryProfileRange chosenSegment={chosenSegment} planHeader={planHeader} />;
};

export default GeometryProfileInfobox;
