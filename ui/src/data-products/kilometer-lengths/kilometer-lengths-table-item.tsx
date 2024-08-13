import * as React from 'react';
import { Precision, roundToPrecision } from 'utils/rounding';
import styles from '../data-product-table.scss';
import { GeometrySource, GkLocationSource, LAYOUT_SRID } from 'track-layout/track-layout-model';
import { useTranslation } from 'react-i18next';
import { LocationPrecision } from 'data-products/kilometer-lengths/kilometer-lengths-search';
import { CoordinateSystem } from 'common/common-model';
import { GeometryPoint } from 'model/geometry';
import CoordinateSystemView from 'geoviite-design-lib/coordinate-system/coordinate-system-view';
import { useCoordinateSystem } from 'track-layout/track-layout-react-utils';

export type KilometerLengthsTableItemProps = {
    trackNumber: string | undefined;
    kilometer: string;
    length: number;
    startM: number;
    endM: number;
    coordinateSystem: CoordinateSystem;
    locationE: number | undefined;
    locationN: number | undefined;
    source: GeometrySource;
    gkLocation: GeometryPoint | undefined;
    gkLocationPrecision: GkLocationSource | undefined;
    gkLocationConfirmed: boolean;
    locationPrecision: LocationPrecision;
    linkedFromGeometry: boolean;
};

export const KilometerLengthTableItem: React.FC<KilometerLengthsTableItemProps> = ({
    trackNumber,
    kilometer,
    length,
    startM,
    endM,
    locationE,
    locationN,
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

    const hasLocation = locationE !== undefined && locationN !== undefined;
    const hasGkLocation = gkLocation !== undefined;
    const showingPreciseLocation = locationPrecision === 'PRECISE';

    const coordinateSystem = () => {
        if (showingPreciseLocation && hasGkLocation) {
            return kmPostCoordinateSystem;
        } else if (hasLocation) {
            return layoutCoordinateSystem;
        }
        return undefined;
    };

    const locationSource = (): string => {
        if (showingPreciseLocation) {
            return gkLocationPrecision ? t(`enum.gk-location-source.${gkLocationPrecision}`) : '';
        } else if (hasLocation && source !== 'GENERATED') {
            return linkedFromGeometry
                ? t('data-products.km-lengths.table.from-geometry')
                : t('data-products.km-lengths.table.from-ratko');
        } else {
            return '';
        }
    };

    const locationConfirmed = (): string => {
        if (showingPreciseLocation) {
            return gkLocationConfirmed
                ? t('data-products.km-lengths.table.confirmed')
                : t('data-products.km-lengths.table.not-confirmed');
        } else {
            return t('data-products.km-lengths.table.not-confirmed');
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
                    {showingPreciseLocation && hasGkLocation
                        ? roundToPrecision(gkLocation.x, Precision.coordinateMeters)
                        : hasLocation && roundToPrecision(locationE, Precision.coordinateMeters)}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {showingPreciseLocation && hasGkLocation
                        ? roundToPrecision(gkLocation.y, Precision.coordinateMeters)
                        : hasLocation && roundToPrecision(locationN, Precision.coordinateMeters)}
                </td>
                <td>{locationSource()}</td>
                <td>{locationConfirmed()}</td>
                <td>
                    {hasLocation &&
                        (source == 'IMPORTED' || gkLocationPrecision === 'FROM_LAYOUT') &&
                        t('data-products.km-lengths.table.imported-warning')}

                    {hasLocation &&
                        source == 'GENERATED' &&
                        t('data-products.km-lengths.table.generated-warning')}
                </td>
            </tr>
        </React.Fragment>
    );
};
