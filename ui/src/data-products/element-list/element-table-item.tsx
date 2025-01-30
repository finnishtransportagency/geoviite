import * as React from 'react';
import { formatTrackMeter } from 'utils/geography-utils';
import { Precision, roundToPrecision } from 'utils/rounding';
import { Srid, TimeStamp, TrackMeter } from 'common/common-model';
import CoordinateSystemView from 'geoviite-design-lib/coordinate-system/coordinate-system-view';
import { PlanNameLink } from 'geoviite-design-lib/geometry-plan/plan-name-link';
import { GeometryPlanId } from 'geometry/geometry-model';
import styles from '../data-product-table.scss';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { findCoordinateSystem } from 'data-products/data-products-utils';
import { useLoader } from 'utils/react-utils';
import { getSridList } from 'common/common-api';
import { formatDateShort } from 'utils/date-utils';

export type ElementTableItemProps = {
    trackNumber: string | undefined;
    geometryAlignmentName: string;
    type: string;
    locationTrackName: string;
    trackAddressStart: TrackMeter | undefined;
    trackAddressEnd: TrackMeter | undefined;
    locationStartE: number;
    locationStartN: number;
    locationEndE: number;
    locationEndN: number;
    length: number;
    curveRadiusStart: number | undefined;
    curveRadiusEnd: number | undefined;
    cantStart: number | undefined;
    cantEnd: number | undefined;
    angleStart: number;
    angleEnd: number;
    plan: string;
    source: string;
    coordinateSystem: Srid | undefined;
    planId: GeometryPlanId;
    showLocationTrackName: boolean;
    connectedSwitchName: string | undefined;
    isPartial: boolean;
    planTime?: TimeStamp;
};

const remarks = (
    t: TFunction<'translation', undefined>,
    connectedSwitchName: string | undefined,
    isPartial: boolean,
) =>
    [
        connectedSwitchName !== undefined
            ? t('data-products.element-list.remarks.connected-to-switch', {
                  switchName: connectedSwitchName,
              })
            : undefined,
        isPartial ? t('data-products.element-list.remarks.is-partial') : undefined,
    ].filter((remark) => remark !== undefined);

export const ElementTableItem: React.FC<ElementTableItemProps> = ({
    trackNumber,
    geometryAlignmentName,
    type,
    locationTrackName,
    trackAddressStart,
    trackAddressEnd,
    locationStartE,
    locationStartN,
    locationEndE,
    locationEndN,
    length,
    curveRadiusStart,
    curveRadiusEnd,
    cantStart,
    cantEnd,
    angleStart,
    angleEnd,
    plan,
    source,
    coordinateSystem,
    planId,
    showLocationTrackName,
    connectedSwitchName,
    isPartial,
    planTime,
}) => {
    const { t } = useTranslation();
    const coordinateSystems = useLoader(getSridList, []);
    return (
        <React.Fragment>
            <tr>
                <td>{trackNumber}</td>
                {showLocationTrackName && <td>{locationTrackName}</td>}
                <td>{geometryAlignmentName}</td>
                <td>{type}</td>
                <td>{trackAddressStart && formatTrackMeter(trackAddressStart)}</td>
                <td>{trackAddressEnd && formatTrackMeter(trackAddressEnd)}</td>
                <td>
                    {coordinateSystem && (
                        <CoordinateSystemView
                            coordinateSystem={findCoordinateSystem(
                                coordinateSystem,
                                coordinateSystems || [],
                            )}
                        />
                    )}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {roundToPrecision(locationStartE, Precision.coordinateMeters)}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {roundToPrecision(locationStartN, Precision.coordinateMeters)}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {roundToPrecision(locationEndE, Precision.coordinateMeters)}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {roundToPrecision(locationEndN, Precision.coordinateMeters)}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {roundToPrecision(length, Precision.measurementMeterDistance)}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {curveRadiusStart !== undefined &&
                        roundToPrecision(curveRadiusStart, Precision.radiusMeters)}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {curveRadiusEnd !== undefined &&
                        roundToPrecision(curveRadiusEnd, Precision.radiusMeters)}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {cantStart !== undefined &&
                        roundToPrecision(cantStart, Precision.cantMillimeters)}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {cantEnd !== undefined && roundToPrecision(cantEnd, Precision.cantMillimeters)}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {roundToPrecision(angleStart, Precision.angle6Decimals)}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {roundToPrecision(angleEnd, Precision.angle6Decimals)}
                </td>
                <td>
                    <PlanNameLink planId={planId} planName={plan} />
                </td>
                <td>{source}</td>
                <td>{planTime && formatDateShort(planTime)}</td>
                <td>{remarks(t, connectedSwitchName, isPartial).join(', ')}</td>
            </tr>
        </React.Fragment>
    );
};
