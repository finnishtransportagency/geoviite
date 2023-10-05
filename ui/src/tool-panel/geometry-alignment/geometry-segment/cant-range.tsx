import * as React from 'react';
import { LayoutPoint } from 'track-layout/track-layout-model';
import { filterNotEmpty } from 'utils/array-utils';
import { Precision, roundToPrecision } from 'utils/rounding';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import 'i18n/config';
import { useTranslation } from 'react-i18next';

const getCantRange = (trackLayoutPoints: LayoutPoint[]): string => {
    const cants: number[] = trackLayoutPoints.map((point) => point.cant).filter(filterNotEmpty);
    if (cants.length === 0) {
        return '-';
    } else {
        const minCant = roundToPrecision(Math.min(...cants), Precision.cantMillimeters);
        const maxCant = roundToPrecision(Math.max(...cants), Precision.cantMillimeters);
        return minCant === maxCant ? `${minCant} mm` : `${minCant} - ${maxCant} mm`;
    }
};

type CantRangeComponentProps = {
    points: LayoutPoint[];
};

export const CantRange: React.FC<CantRangeComponentProps> = ({
    points,
}: CantRangeComponentProps) => {
    const { t } = useTranslation();
    return (
        <InfoboxField
            label={t('tool-panel.alignment.geometry-segment.cant')}
            value={getCantRange(points)}></InfoboxField>
    );
};
