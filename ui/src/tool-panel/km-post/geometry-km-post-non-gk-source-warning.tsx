import React from 'react';
import { useTranslation } from 'react-i18next';
import { Srid } from 'common/common-model';
import { useCoordinateSystem } from 'track-layout/track-layout-react-utils';
import { formatWithSrid } from 'utils/geography-utils';

export const GeometryKmPostNonGkSourceWarning: React.FC<{ originalSrid: Srid }> = ({
    originalSrid,
}) => {
    const { t } = useTranslation();

    const originalCoordinateSystem = useCoordinateSystem(originalSrid);

    return (
        <React.Fragment>
            {t('gk-location-transform.location-converted', {
                originalCrs: originalCoordinateSystem
                    ? formatWithSrid(originalCoordinateSystem)
                    : '',
            })}
        </React.Fragment>
    );
};
