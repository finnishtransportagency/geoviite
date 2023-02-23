import React from 'react';
import { CoordinateSystem as CoordinateSystemModel } from 'common/common-model';

type CoordinateSystemProps = {
    coordinateSystem: CoordinateSystemModel | undefined;
};

const CoordinateSystemView: React.FC<CoordinateSystemProps> = ({
    coordinateSystem,
}: CoordinateSystemProps) => {
    return (
        <React.Fragment>
            {coordinateSystem?.name} {coordinateSystem?.srid}
        </React.Fragment>
    );
};

export default CoordinateSystemView;
