import * as React from 'react';
import { GeometryBiquadraticParabola } from 'geometry/geometry-model';
import { MapSegment } from 'track-layout/track-layout-model';
import { CantRange } from 'tool-panel/geometry-alignment/geometry-segment/cant-range';
import { Precision, roundToPrecision } from 'utils/rounding';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import 'i18n/config';
import { useTranslation } from 'react-i18next';

type BiquadraticParabolaInfoBoxProps = {
    chosenSegment: MapSegment;
    geometryBiquadraticParabola: GeometryBiquadraticParabola;
};

function formatParabolaRadius(radius: number | null): string {
    return radius === null ? 'Suora' : `${roundToPrecision(radius, Precision.radiusMeters)} m`;
}

const BiquadraticParabolaInfobox: React.FC<BiquadraticParabolaInfoBoxProps> = ({
    chosenSegment,
    geometryBiquadraticParabola: parabola,
}: BiquadraticParabolaInfoBoxProps) => {
    const { t } = useTranslation();
    return (
        <React.Fragment>
            <InfoboxField
                label={t('tool-panel.alignment.geometry-segment.chosen-segment')}
                value={t('tool-panel.alignment.geometry-segment.biquadratic')}></InfoboxField>
            <InfoboxField
                label={t('tool-panel.alignment.geometry-segment.clothoid-length')}
                value={`${roundToPrecision(
                    parabola.calculatedLength,
                    Precision.distanceMeters,
                )} m`}></InfoboxField>
            <InfoboxField
                label={t('tool-panel.alignment.geometry-segment.clothoid-radius')}
                value={`${formatParabolaRadius(parabola.radiusStart)} - ${formatParabolaRadius(
                    parabola.radiusEnd,
                )}`}></InfoboxField>
            <CantRange chosenSegment={chosenSegment} />
        </React.Fragment>
    );
};

export default BiquadraticParabolaInfobox;
