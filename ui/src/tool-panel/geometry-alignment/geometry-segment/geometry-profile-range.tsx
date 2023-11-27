import * as React from 'react';
import { AlignmentPoint } from 'track-layout/track-layout-model';
import { filterNotEmpty } from 'utils/array-utils';
import { Precision, roundToPrecision } from 'utils/rounding';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { GeometryPlanHeader } from 'geometry/geometry-model';
import 'i18n/config';
import { useTranslation } from 'react-i18next';

const getProfileRange = (points: AlignmentPoint[]): string => {
    const z: number[] = points.map((point) => point.z).filter(filterNotEmpty);
    if (z.length === 0) {
        return '-';
    } else {
        const minZ = roundToPrecision(Math.min(...z), Precision.profileMeters);
        const maxZ = roundToPrecision(Math.max(...z), Precision.profileMeters);
        return minZ === maxZ ? `${minZ} m` : `${minZ} - ${maxZ} m`;
    }
};

type CantRangeComponentProps = {
    points: AlignmentPoint[];
    planHeader: GeometryPlanHeader;
};

export const GeometryProfileRange: React.FC<CantRangeComponentProps> = ({
    points,
    planHeader,
}: CantRangeComponentProps) => {
    const { t } = useTranslation();
    return (
        <React.Fragment>
            <InfoboxField
                label={t('tool-panel.alignment.geometry-segment.height')}
                value={getProfileRange(points)}></InfoboxField>
            <InfoboxField
                label={t('tool-panel.alignment.geometry-segment.vertical-coordinate-system')}
                value={planHeader.units.verticalCoordinateSystem}
            />
        </React.Fragment>
    );
};
