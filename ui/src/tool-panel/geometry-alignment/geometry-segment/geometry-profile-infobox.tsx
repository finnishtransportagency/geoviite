import * as React from 'react';
import { GeometryProfileRange } from 'tool-panel/geometry-alignment/geometry-segment/geometry-profile-range';
import { GeometryPlanHeader } from 'geometry/geometry-model';
import { AlignmentPoint } from 'track-layout/track-layout-model';

type VerticalGeometryInfoBoxProps = {
    points: AlignmentPoint[];
    planHeader: GeometryPlanHeader;
};

const GeometryProfileInfobox: React.FC<VerticalGeometryInfoBoxProps> = ({
    points,
    planHeader,
}: VerticalGeometryInfoBoxProps) => {
    return <GeometryProfileRange points={points} planHeader={planHeader} />;
};

export default GeometryProfileInfobox;
