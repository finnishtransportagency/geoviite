import * as React from 'react';
import { GeometryClothoid } from 'geometry/geometry-model';
import { MapSegment } from 'track-layout/track-layout-model';
import { CantRange } from 'tool-panel/geometry-alignment/geometry-segment/cant-range';
import {
    Precision,
    roundToPrecision,
} from 'utils/rounding';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import 'i18n/config';
import { useTranslation } from 'react-i18next';

type ClothoidInfoBoxProps = {
    chosenSegment: MapSegment;
    geometryClothoid: GeometryClothoid;
};

function formatClothoidRadius(radius: number | null): string {
    return radius === null ? 'Suora' : `${roundToPrecision(radius, Precision.radiusMeters)} m`;
}

const ClothoidInfobox: React.FC<ClothoidInfoBoxProps> = ({
    chosenSegment,
    geometryClothoid,
}: ClothoidInfoBoxProps) => {
    const { t } = useTranslation();
    return (
        <React.Fragment>
            <InfoboxField
                label={t('tool-panel.alignment.geometry-segment.chosen-segment')}
                value={t('tool-panel.alignment.geometry-segment.clothoid')}></InfoboxField>
            <InfoboxField
                label={t('tool-panel.alignment.geometry-segment.clothoid-length')}
                value={`${roundToPrecision(
                    geometryClothoid.calculatedLength,
                    Precision.distanceMeters,
                )} m`}></InfoboxField>
            <InfoboxField
                label={t('tool-panel.alignment.geometry-segment.clothoid-radius')}
                value={`${formatClothoidRadius(
                    geometryClothoid.radiusStart,
                )} - ${formatClothoidRadius(geometryClothoid.radiusEnd)}`}></InfoboxField>
            <CantRange chosenSegment={chosenSegment} />
        </React.Fragment>
    );
};

export default ClothoidInfobox;
