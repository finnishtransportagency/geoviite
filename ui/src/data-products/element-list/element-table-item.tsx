import * as React from 'react';
import { formatTrackMeter } from 'utils/geography-utils';
import { Precision, roundToPrecision } from 'utils/rounding';
import { CoordinateSystem, TrackMeter } from 'common/common-model';
import CoordinateSystemView from 'geoviite-design-lib/coordinate-system/coordinate-system-view';
import { PlanNameLink } from 'geoviite-design-lib/geometry-plan/plan-name-link';
import { GeometryPlanId } from 'geometry/geometry-model';
import styles from '../data-product-table.scss';
import { TFunction, useTranslation } from 'react-i18next';

export type ElementTableItemProps = {
    id: string;
    trackNumber: string | undefined;
    geometryAlignmentName: string;
    type: string;
    locationTrackName: string;
    trackAddressStart: TrackMeter | null;
    trackAddressEnd: TrackMeter | null;
    locationStartE: number;
    locationStartN: number;
    locationEndE: number;
    locationEndN: number;
    length: number;
    curveRadiusStart: number | undefined;
    curveRadiusEnd: number | undefined;
    cantStart: number | null;
    cantEnd: number | null;
    angleStart: number;
    angleEnd: number;
    plan: string;
    source: string;
    coordinateSystem: CoordinateSystem | undefined;
    planId: GeometryPlanId;
    showLocationTrackName: boolean;
    connectedSwitchName: string | undefined;
    isPartial: boolean;
};

const remarks = (
    t: TFunction<'translation', undefined>,
    connectedSwitchName: string | undefined,
    isPartial: boolean,
) =>
    [
        connectedSwitchName !== null
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
}) => {
    const { t } = useTranslation();
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
                    <CoordinateSystemView coordinateSystem={coordinateSystem} />
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {roundToPrecision(locationStartE, Precision.TM35FIN)}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {roundToPrecision(locationStartN, Precision.TM35FIN)}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {roundToPrecision(locationEndE, Precision.TM35FIN)}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {roundToPrecision(locationEndN, Precision.TM35FIN)}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {roundToPrecision(length, Precision.measurementMeterDistance)}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {curveRadiusStart != undefined &&
                        roundToPrecision(curveRadiusStart, Precision.radiusMeters)}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {curveRadiusEnd != undefined &&
                        roundToPrecision(curveRadiusEnd, Precision.radiusMeters)}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {cantStart != null && roundToPrecision(cantStart, Precision.cantMillimeters)}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {cantEnd != null && roundToPrecision(cantEnd, Precision.cantMillimeters)}
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
                <td>{remarks(t, connectedSwitchName, isPartial).join(', ')}</td>
            </tr>
        </React.Fragment>
    );
};
