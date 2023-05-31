import * as React from 'react';
import { formatTrackMeter } from 'utils/geography-utils';
import { VerticalGeometryItem } from 'geometry/geometry-model';
import styles from 'data-products/data-product-table.scss';
import { Precision, roundToPrecision } from 'utils/rounding';
import { PlanNameLink } from 'geoviite-design-lib/geometry-plan/plan-name-link';
import { useTranslation } from 'react-i18next';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import CoordinateSystemView from 'geoviite-design-lib/coordinate-system/coordinate-system-view';
import { findCoordinateSystem } from 'data-products/data-products-utils';
import { useLoader } from 'utils/react-utils';
import { getSridList } from 'common/common-api';
import { formatDateShort } from 'utils/date-utils';

type VerticalGeometryTableItemProps = {
    verticalGeometry: VerticalGeometryItem;
    showLocationTrack: boolean;
    overlapsAnother: boolean;
};

export const VerticalGeometryTableItem: React.FC<VerticalGeometryTableItemProps> = ({
    verticalGeometry,
    showLocationTrack,
    overlapsAnother,
}) => {
    const { t } = useTranslation();
    const coordinateSystems = useLoader(getSridList, []);
    return (
        <tr>
            {showLocationTrack && (
                <td>
                    {overlapsAnother && (
                        <span
                            className={'data-product-table__overlaps-another'}
                            title={t('data-products.vertical-geometry.overlaps-another')}>
                            <Icons.Info color={IconColor.INHERIT} size={IconSize.MEDIUM_SMALL} />
                        </span>
                    )}{' '}
                    {verticalGeometry.locationTrackName}
                </td>
            )}
            <td>
                <PlanNameLink
                    planId={verticalGeometry.planId}
                    planName={verticalGeometry.fileName}
                />
            </td>
            <td>{formatDateShort(verticalGeometry.creationTime)}</td>
            <td>
                {verticalGeometry.coordinateSystemSrid && (
                    <CoordinateSystemView
                        coordinateSystem={findCoordinateSystem(
                            verticalGeometry.coordinateSystemSrid,
                            coordinateSystems || [],
                        )}
                    />
                )}
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
                {verticalGeometry.start.location &&
                    roundToPrecision(verticalGeometry.start.location.x, Precision.coordinateMeters)}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {verticalGeometry.start.location &&
                    roundToPrecision(verticalGeometry.start.location.y, Precision.coordinateMeters)}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {verticalGeometry.point.address && formatTrackMeter(verticalGeometry.point.address)}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {roundToPrecision(verticalGeometry.point.height, Precision.profileMeters)}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {verticalGeometry.point.location &&
                    roundToPrecision(verticalGeometry.point.location.x, Precision.coordinateMeters)}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {verticalGeometry.point.location &&
                    roundToPrecision(verticalGeometry.point.location.y, Precision.coordinateMeters)}
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
                {verticalGeometry.end.location &&
                    roundToPrecision(verticalGeometry.end.location.x, Precision.coordinateMeters)}
            </td>
            <td className={styles['data-product-table__column--number']}>
                {verticalGeometry.end.location &&
                    roundToPrecision(verticalGeometry.end.location.y, Precision.coordinateMeters)}
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
            <td>
                {verticalGeometry.verticalCoordinateSystem ??
                    t('data-products.vertical-geometry.unknown')}
            </td>
            <td>{overlapsAnother ? t('data-products.vertical-geometry.overlaps-another') : ''}</td>
        </tr>
    );
};
