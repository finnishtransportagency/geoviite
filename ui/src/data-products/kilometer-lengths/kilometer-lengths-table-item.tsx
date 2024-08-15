import * as React from 'react';
import { Precision, roundToPrecision } from 'utils/rounding';
import styles from '../data-product-table.scss';
import { GeometrySource, GkLocationSource, LAYOUT_SRID } from 'track-layout/track-layout-model';
import { useTranslation } from 'react-i18next';
import { CoordinateSystem } from 'common/common-model';
import { GeometryPoint, Point } from 'model/geometry';
import CoordinateSystemView from 'geoviite-design-lib/coordinate-system/coordinate-system-view';
import { useCoordinateSystem } from 'track-layout/track-layout-react-utils';
import { KmLengthsLocationPrecision } from 'data-products/data-products-slice';

export type KilometerLengthsTableItemProps = {
    trackNumber: string | undefined;
    kilometer: string;
    length: number;
    startM: number;
    endM: number;
    coordinateSystem: CoordinateSystem;
    layoutLocation: Point | undefined;
    source: GeometrySource;
    gkLocation: GeometryPoint | undefined;
    gkLocationPrecision: GkLocationSource | undefined;
    gkLocationConfirmed: boolean;
    locationPrecision: KmLengthsLocationPrecision;
    linkedFromGeometry: boolean;
};

export const KilometerLengthTableItem: React.FC<KilometerLengthsTableItemProps> = ({
    trackNumber,
    kilometer,
    length,
    startM,
    endM,
    layoutLocation,
    source,
    gkLocation,
    gkLocationConfirmed,
    gkLocationPrecision,
    locationPrecision,
    linkedFromGeometry,
}) => {
    const { t } = useTranslation();
    const kmPostCoordinateSystem = useCoordinateSystem(gkLocation?.srid);
    const layoutCoordinateSystem = useCoordinateSystem(LAYOUT_SRID);

    const hasLayoutLocation = layoutLocation !== undefined;
    const hasGkLocation = gkLocation !== undefined;
    const showingPreciseLocation = locationPrecision === 'PRECISE';

    const selectLocation = () => {
        if (showingPreciseLocation && hasGkLocation) {
            return gkLocation;
        } else if (hasLayoutLocation) {
            return layoutLocation;
        }
        return undefined;
    };
    const location = selectLocation();

    const coordinateSystem = () => {
        if (showingPreciseLocation && hasGkLocation) {
            return kmPostCoordinateSystem;
        } else if (hasLayoutLocation) {
            return layoutCoordinateSystem;
        }
        return undefined;
    };

    const locationSource = (): string => {
        if (showingPreciseLocation && hasGkLocation) {
            return t(`enum.gk-location-source.${gkLocationPrecision}`);
        } else if (hasLayoutLocation && source !== 'GENERATED') {
            return linkedFromGeometry
                ? t('data-products.km-lengths.table.from-geometry')
                : t('data-products.km-lengths.table.from-ratko');
        } else {
            return '';
        }
    };

    const locationConfirmed = (): string => {
        if (showingPreciseLocation && hasGkLocation) {
            return gkLocationConfirmed
                ? t('data-products.km-lengths.table.confirmed')
                : t('data-products.km-lengths.table.not-confirmed');
        } else if (hasLayoutLocation && source !== 'GENERATED') {
            return t('data-products.km-lengths.table.not-confirmed');
        } else {
            return '';
        }
    };

    return (
        <React.Fragment>
            <tr>
                <td>{trackNumber}</td>
                <td>{kilometer}</td>
                <td className={styles['data-product-table__column--number']}>
                    {roundToPrecision(startM, Precision.measurementMeterDistance)}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {roundToPrecision(endM, Precision.measurementMeterDistance)}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {roundToPrecision(length, Precision.measurementMeterDistance)}
                </td>
                <td>
                    <CoordinateSystemView coordinateSystem={coordinateSystem()} />
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {location && roundToPrecision(location.x, Precision.coordinateMeters)}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {location && roundToPrecision(location.y, Precision.coordinateMeters)}
                </td>
                <td>{locationSource()}</td>
                <td>{locationConfirmed()}</td>
                <td>
                    {hasLayoutLocation &&
                        (source == 'IMPORTED' || gkLocationPrecision === 'FROM_LAYOUT') &&
                        t('data-products.km-lengths.table.imported-warning')}

                    {hasLayoutLocation &&
                        source == 'GENERATED' &&
                        t('data-products.km-lengths.table.generated-warning')}
                </td>
            </tr>
        </React.Fragment>
    );
};
