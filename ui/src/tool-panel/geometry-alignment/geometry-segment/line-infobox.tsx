import * as React from 'react';
import { GeometryLine } from 'geometry/geometry-model';
import { Precision, roundToPrecision } from 'utils/rounding';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import 'i18n/config';
import { useTranslation } from 'react-i18next';

type LineInfoBoxProps = {
    geometryLine: GeometryLine;
};

const LineInfoBox: React.FC<LineInfoBoxProps> = ({ geometryLine }: LineInfoBoxProps) => {
    const { t } = useTranslation();
    return (
        <React.Fragment>
            <InfoboxField
                label={t('tool-panel.alignment.geometry-segment.chosen-segment')}
                value={t('tool-panel.alignment.geometry-segment.line')}></InfoboxField>
            <InfoboxField
                label={t('tool-panel.alignment.geometry-segment.line-length')}
                value={`${roundToPrecision(
                    geometryLine.calculatedLength,
                    Precision.distanceMeters,
                )} m`}></InfoboxField>
        </React.Fragment>
    );
};

export default LineInfoBox;
