import * as React from 'react';
import { Precision, roundToPrecision } from 'utils/rounding';
import styles from '../data-product-table.scss';

export type KilometerLengthsTableItemProps = {
    trackNumber: string | undefined;
    kilometer: string;
    length: number;
    stationStart: number;
    stationEnd: number;
    locationE: number;
    locationN: number;
};

export const KilometerLengthTableItem: React.FC<KilometerLengthsTableItemProps> = ({
    trackNumber,
    kilometer,
    stationStart,
    stationEnd,
    locationE,
    locationN,
}) => {
    return (
        <React.Fragment>
            <tr>
                <td>{trackNumber}</td>
                <td>{kilometer}</td>
                <td className={styles['data-product-table__column--number']}>
                    {roundToPrecision(length, Precision.measurementMeterDistance)}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {roundToPrecision(stationStart, Precision.measurementMeterDistance)}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {roundToPrecision(stationEnd, Precision.measurementMeterDistance)}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {roundToPrecision(locationE, Precision.TM35FIN)}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {roundToPrecision(locationN, Precision.TM35FIN)}
                </td>
            </tr>
        </React.Fragment>
    );
};
