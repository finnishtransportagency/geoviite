import React from 'react';
import { CoordinateSystem as CoordinateSystemModel } from 'common/common-model';

type CoordinateSystemProps = {
    coordinateSystem: CoordinateSystemModel | null;
};

const CoordinateSystem: React.FC<CoordinateSystemProps> = ({
    coordinateSystem,
}: CoordinateSystemProps) => {
    return (
        <React.Fragment>
            {coordinateSystem?.name} {coordinateSystem?.srid}
        </React.Fragment>
    );
};

export default CoordinateSystem;
