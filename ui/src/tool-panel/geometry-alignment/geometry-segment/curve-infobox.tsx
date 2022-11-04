import InfoboxField from 'tool-panel/infobox/infobox-field';
import * as React from 'react';
import { GeometryCurve } from 'geometry/geometry-model';
import { MapSegment } from 'track-layout/track-layout-model';
import { CantRange } from 'tool-panel/geometry-alignment/geometry-segment/cant-range';
import {
    Precision,
    roundToPrecision,
} from 'utils/rounding';
import 'i18n/config';
import { useTranslation } from 'react-i18next';

type CurveInfoBoxProps = {
    chosenSegment: MapSegment;
    geometryCurve: GeometryCurve;
};

const CurveInfobox: React.FC<CurveInfoBoxProps> = ({
    chosenSegment,
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
            <CantRange chosenSegment={chosenSegment} />
        </React.Fragment>
    );
};

export default CurveInfobox;
