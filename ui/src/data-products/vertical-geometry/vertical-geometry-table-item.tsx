import * as React from 'react';
import { formatTrackMeter } from 'utils/geography-utils';
import { VerticalGeometry } from 'geometry/geometry-model';
import styles from 'data-products/data-product-table.scss';
import { Precision, roundToPrecision } from 'utils/rounding';
import { PlanNameLink } from 'geoviite-design-lib/geometry-plan/plan-name-link';

type VerticalGeometryTableItemProps = {
    verticalGeometry: VerticalGeometry;
};

export const VerticalGeometryTableItem: React.FC<VerticalGeometryTableItemProps> = ({
    verticalGeometry,
}) => {
    return (
        <tr>
            <td>
                <PlanNameLink
                    planId={verticalGeometry.planId}
                    planName={verticalGeometry.planFileName}
                />
            </td>
            <td>{verticalGeometry.locationTrack}</td>
            <td className={styles['data-product-table__column--number']}>
                {formatTrackMeter(verticalGeometry.curveStart.address)}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {roundToPrecision(verticalGeometry.curveStart.height, Precision.profileMeters)}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {roundToPrecision(verticalGeometry.curveStart.angle, Precision.angle6Decimals)}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {formatTrackMeter(verticalGeometry.pviAddress)}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {roundToPrecision(verticalGeometry.pviHeight, Precision.profileMeters)}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {formatTrackMeter(verticalGeometry.curveEnd.address)}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {roundToPrecision(verticalGeometry.curveEnd.height, Precision.profileMeters)}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {roundToPrecision(verticalGeometry.curveEnd.angle, Precision.angle6Decimals)}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {roundToPrecision(verticalGeometry.radius, Precision.profileRadiusMeters)}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {roundToPrecision(verticalGeometry.tangent, Precision.profileTangent)}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {roundToPrecision(
                    verticalGeometry.linearSectionBackwards.length,
                    Precision.measurementMeterDistance,
                )}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {roundToPrecision(
                    verticalGeometry.linearSectionBackwards.linearSection,
                    Precision.measurementMeterDistance,
                )}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {roundToPrecision(
                    verticalGeometry.linearSectionForwards.length,
                    Precision.measurementMeterDistance,
                )}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {roundToPrecision(
                    verticalGeometry.linearSectionForwards.linearSection,
                    Precision.measurementMeterDistance,
                )}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {roundToPrecision(
                    verticalGeometry.station.start,
                    Precision.measurementMeterDistance,
                )}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {roundToPrecision(verticalGeometry.station.pvi, Precision.measurementMeterDistance)}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {roundToPrecision(verticalGeometry.station.end, Precision.measurementMeterDistance)}
            </td>
        </tr>
    );
};
