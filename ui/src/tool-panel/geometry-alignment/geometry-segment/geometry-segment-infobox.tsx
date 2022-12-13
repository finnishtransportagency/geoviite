import * as React from 'react';
import { useLoader } from 'utils/react-utils';
import { MapSegment } from 'track-layout/track-layout-model';
import styles from './geometry-segment-infobox.scss';
import {
    GeometryBiquadraticParabola,
    GeometryClothoid,
    GeometryCurve,
    GeometryElementId,
    GeometryLine,
    GeometryPlanHeader,
    GeometryType,
} from 'geometry/geometry-model';
import CurveInfobox from 'tool-panel/geometry-alignment/geometry-segment/curve-infobox';
import LineInfoBox from 'tool-panel/geometry-alignment/geometry-segment/line-infobox';
import ClothoidInfobox from 'tool-panel/geometry-alignment/geometry-segment/clothoid-infobox';
import BiquadraticParabolaInfobox from 'tool-panel/geometry-alignment/geometry-segment/biquadratic-parabola-infobox';
import Infobox from 'tool-panel/infobox/infobox';
import GeometryProfileInfobox from 'tool-panel/geometry-alignment/geometry-segment/geometry-profile-infobox';
import { getGeometryElement } from 'geometry/geometry-api';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import 'i18n/config';
import { useTranslation } from 'react-i18next';

type SelectedGeometryItemInfoBoxProps = {
    chosenSegment: MapSegment;
    planHeader: GeometryPlanHeader;
};

const GeometrySegmentInfobox: React.FC<SelectedGeometryItemInfoBoxProps> = ({
    chosenSegment,
    planHeader,
}: SelectedGeometryItemInfoBoxProps) => {
    const { t } = useTranslation();

    const chosenGeometryElement = useLoader(
        () => getGeometryElement(chosenSegment.sourceId as GeometryElementId),
        [chosenSegment],
    );

    return (
        <React.Fragment>
            <Infobox
                title={t('tool-panel.alignment.geometry-segment.title')}
                qa-id="geometry-segment-horizontal-geometry-infobox">
                {chosenGeometryElement && (
                    <React.Fragment>
                        <InfoboxContent>
                            {chosenGeometryElement.type === GeometryType.LINE && (
                                <LineInfoBox geometryLine={chosenGeometryElement as GeometryLine} />
                            )}
                            {chosenGeometryElement.type === GeometryType.CURVE && (
                                <CurveInfobox
                                    chosenSegment={chosenSegment}
                                    geometryCurve={chosenGeometryElement as GeometryCurve}
                                />
                            )}
                            {chosenGeometryElement.type === GeometryType.CLOTHOID && (
                                <ClothoidInfobox
                                    chosenSegment={chosenSegment}
                                    geometryClothoid={chosenGeometryElement as GeometryClothoid}
                                />
                            )}
                            {chosenGeometryElement.type === GeometryType.BIQUADRATIC_PARABOLA && (
                                <BiquadraticParabolaInfobox
                                    chosenSegment={chosenSegment}
                                    geometryBiquadraticParabola={
                                        chosenGeometryElement as GeometryBiquadraticParabola
                                    }
                                />
                            )}
                        </InfoboxContent>
                        <InfoboxContent>
                            <GeometryProfileInfobox
                                chosenSegment={chosenSegment}
                                planHeader={planHeader}
                            />
                        </InfoboxContent>
                    </React.Fragment>
                )}
                {typeof chosenGeometryElement === 'undefined' && (
                    <p className={styles['geometry-segment__no-data-text']}>
                        <i>{t('tool-panel.alignment.geometry-segment.no-geometry-information')}</i>
                    </p>
                )}
            </Infobox>
        </React.Fragment>
    );
};

export default GeometrySegmentInfobox;
