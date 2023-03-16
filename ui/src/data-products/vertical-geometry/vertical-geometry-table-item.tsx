import * as React from 'react';
import { formatTrackMeter } from 'utils/geography-utils';
import { VerticalGeometryItem } from 'geometry/geometry-model';
import styles from 'data-products/data-product-table.scss';
import { Precision, roundToPrecision } from 'utils/rounding';
import { PlanNameLink } from 'geoviite-design-lib/geometry-plan/plan-name-link';

type VerticalGeometryTableItemProps = {
    verticalGeometry: VerticalGeometryItem;
};

export const VerticalGeometryTableItem: React.FC<VerticalGeometryTableItemProps> = ({
    verticalGeometry,
}) => {
    return (
        <tr>
            <td>
                <PlanNameLink
                    planId={verticalGeometry.planId}
                    planName={verticalGeometry.fileName}
                />
            </td>
            <td>{verticalGeometry.alignmentName}</td>
            <td className={styles['data-product-table__column--number']}>
                {verticalGeometry.start.address && formatTrackMeter(verticalGeometry.start.address)}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {roundToPrecision(verticalGeometry.start.height, Precision.profileMeters)}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {roundToPrecision(verticalGeometry.start.angle, Precision.angle6Decimals)}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {verticalGeometry.point.address && formatTrackMeter(verticalGeometry.point.address)}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {roundToPrecision(verticalGeometry.point.height, Precision.profileMeters)}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {verticalGeometry.end.address && formatTrackMeter(verticalGeometry.end.address)}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {roundToPrecision(verticalGeometry.end.height, Precision.profileMeters)}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {roundToPrecision(verticalGeometry.end.angle, Precision.angle6Decimals)}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {roundToPrecision(verticalGeometry.radius, Precision.profileRadiusMeters)}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {roundToPrecision(verticalGeometry.tangent, Precision.profileTangent)}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {verticalGeometry.linearSectionBackward.stationValueDistance &&
                    roundToPrecision(
                        verticalGeometry.linearSectionBackward.stationValueDistance,
                        Precision.measurementMeterDistance,
                    )}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {verticalGeometry.linearSectionBackward.linearSegmentLength &&
                    roundToPrecision(
                        verticalGeometry.linearSectionBackward.linearSegmentLength,
                        Precision.measurementMeterDistance,
                    )}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {verticalGeometry.linearSectionForward.stationValueDistance &&
                    roundToPrecision(
                        verticalGeometry.linearSectionForward.stationValueDistance,
                        Precision.measurementMeterDistance,
                    )}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {verticalGeometry.linearSectionForward.linearSegmentLength &&
                    roundToPrecision(
                        verticalGeometry.linearSectionForward.linearSegmentLength,
                        Precision.measurementMeterDistance,
                    )}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {roundToPrecision(
                    verticalGeometry.start.station,
                    Precision.measurementMeterDistance,
                )}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {roundToPrecision(
                    verticalGeometry.point.station,
                    Precision.measurementMeterDistance,
                )}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {roundToPrecision(verticalGeometry.end.station, Precision.measurementMeterDistance)}
            </td>
        </tr>
    );
};
