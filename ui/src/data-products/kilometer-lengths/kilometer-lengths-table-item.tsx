import * as React from 'react';
import { Precision, roundToPrecision } from 'utils/rounding';
import styles from '../data-product-table.scss';

export type KilometerLengthsTableItemProps = {
    trackNumber: string | undefined;
    kilometer: string;
    length: number;
    startM: number;
    endM: number;
    locationE: number | undefined;
    locationN: number | undefined;
};

export const KilometerLengthTableItem: React.FC<KilometerLengthsTableItemProps> = ({
    trackNumber,
    kilometer,
    length,
    startM,
    endM,
    locationE,
    locationN,
}) => {
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
                    {locationE && roundToPrecision(locationE, Precision.TM35FIN)}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {locationN && roundToPrecision(locationN, Precision.TM35FIN)}
                </td>
            </tr>
        </React.Fragment>
    );
};
