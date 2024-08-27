import React from 'react';
import { CoordinateSystem } from 'common/common-model';
import { formatWithSrid } from 'utils/geography-utils';

type CoordinateSystemViewProps = {
    coordinateSystem: CoordinateSystem | undefined;
};

const CoordinateSystemView: React.FC<CoordinateSystemViewProps> = ({
    coordinateSystem,
}: CoordinateSystemViewProps) => {
    return (
        <React.Fragment>{coordinateSystem ? formatWithSrid(coordinateSystem) : '-'}</React.Fragment>
    );
};

export default CoordinateSystemView;
