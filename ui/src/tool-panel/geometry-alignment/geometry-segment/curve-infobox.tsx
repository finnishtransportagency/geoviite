import InfoboxField from 'tool-panel/infobox/infobox-field';
import * as React from 'react';
import { GeometryCurve } from 'geometry/geometry-model';
import { CantRange } from 'tool-panel/geometry-alignment/geometry-segment/cant-range';
import { Precision, roundToPrecision } from 'utils/rounding';
import 'i18n/config';
import { useTranslation } from 'react-i18next';
import { LayoutPoint } from 'track-layout/track-layout-model';

type CurveInfoBoxProps = {
    points: LayoutPoint[];
    geometryCurve: GeometryCurve;
};

const CurveInfobox: React.FC<CurveInfoBoxProps> = ({
    points,
    geometryCurve,
}: CurveInfoBoxProps) => {
    const { t } = useTranslation();
    return (
        <React.Fragment>
            <InfoboxField
                label={t('tool-panel.alignment.geometry-segment.chosen-segment')}
                value={t('tool-panel.alignment.geometry-segment.curve')}></InfoboxField>
            <InfoboxField
                label={t('tool-panel.alignment.geometry-segment.curve-length')}
                value={`${roundToPrecision(
                    geometryCurve.calculatedLength,
                    Precision.distanceMeters,
                )} m`}></InfoboxField>
            <InfoboxField
                label={t('tool-panel.alignment.geometry-segment.curve-radius')}
                value={`${roundToPrecision(
                    geometryCurve.radius,
                    Precision.radiusMeters,
                )} m`}></InfoboxField>
            <CantRange points={points} />
        </React.Fragment>
    );
};

export default CurveInfobox;
