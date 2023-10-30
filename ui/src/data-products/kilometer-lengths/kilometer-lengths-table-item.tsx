import * as React from 'react';
import { Precision, roundToPrecision } from 'utils/rounding';
import styles from '../data-product-table.scss';
import { GeometrySource } from 'track-layout/track-layout-model';
import { useTranslation } from 'react-i18next';

export type KilometerLengthsTableItemProps = {
    trackNumber: string | undefined;
    kilometer: string;
    length: number;
    startM: number;
    endM: number;
    locationE: number | undefined;
    locationN: number | undefined;
    source: GeometrySource;
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
}) => {
    const { t } = useTranslation();
    const hasLocation = locationE !== undefined && locationN !== undefined;

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
                <td className={styles['data-product-table__column--number']}>
                    {hasLocation && roundToPrecision(locationE, Precision.coordinateMeters)}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {hasLocation && roundToPrecision(locationN, Precision.coordinateMeters)}
                </td>
                <td>
                    {hasLocation &&
                        source == 'IMPORTED' &&
                        t('data-products.km-lengths.table.imported-warning')}

                    {hasLocation &&
                        source == 'GENERATED' &&
                        t('data-products.km-lengths.table.generated-warning')}
                </td>
            </tr>
        </React.Fragment>
    );
};
