import * as React from 'react';
import { Table, Th } from 'vayla-design-lib/table/table';
import { useTranslation } from 'react-i18next';
import styles from '../data-product-table.scss';
import {
    ElementHeading,
    nonNumericHeading,
    numericHeading,
} from 'data-products/data-products-utils';
import { KilometerLengthTableItem } from 'data-products/kilometer-lengths/kilometer-lengths-table-item';
import { LayoutKmLengthDetails } from 'track-layout/track-layout-model';
import { KmLengthsLocationPrecision } from 'data-products/data-products-slice';

type KilometerLengthsTableProps = {
    kmLengths: LayoutKmLengthDetails[];
    isLoading: boolean;
    locationPrecision: KmLengthsLocationPrecision;
};

const KilometerLengthsTable = ({
    kmLengths,
    isLoading,
    locationPrecision,
}: KilometerLengthsTableProps) => {
    const { t } = useTranslation();
    const amount = kmLengths.length;
    const headings: ElementHeading[] = [
        nonNumericHeading('track-number'),
        nonNumericHeading('kilometer'),
        numericHeading('station-start', 'station-start'),
        numericHeading('station-end'),
        numericHeading('length'),
        nonNumericHeading('coordinate-system'),
        numericHeading('location-e'),
        numericHeading('location-n'),
        nonNumericHeading('location-source'),
        nonNumericHeading('location-confirmed'),
        nonNumericHeading('warning'),
    ];

    return (
        <React.Fragment>
            <p className={styles['data-product-table__element-count']}>
                {t(`data-products.km-lengths.amount`, { amount })}
            </p>
            <div className={styles['data-product-table__table-container']}>
                <Table wide isLoading={isLoading}>
                    <thead className={styles['data-product-table__table-heading']}>
                        <tr>
                            {headings.map((heading) => (
                                <Th
                                    key={heading.name}
                                    className={
                                        heading.numeric
                                            ? styles['data-product-table__column--number']
                                            : ''
                                    }
                                    qa-id={heading.qaId && `km-length-header-${heading.qaId}`}>
                                    {t(`data-products.km-lengths.table.${heading.name}`)}
                                </Th>
                            ))}
                        </tr>
                    </thead>
                    <tbody>
                        {kmLengths.map((item) => (
                            <React.Fragment key={`${item.kmNumber}`}>
                                <KilometerLengthTableItem
                                    locationPrecision={locationPrecision}
                                    trackNumber={item.trackNumber}
                                    length={item.length}
                                    kilometer={item.kmNumber}
                                    startM={item.startM}
                                    endM={item.endM}
                                    coordinateSystem={item.coordinateSystem}
                                    layoutLocation={item.layoutLocation}
                                    layoutGeometrySource={item.layoutGeometrySource}
                                    gkLocation={item.gkLocation}
                                    linkedFromGeometry={item.gkLocationLinkedFromGeometry}
                                />
                            </React.Fragment>
                        ))}
                    </tbody>
                </Table>
            </div>
        </React.Fragment>
    );
};

export default React.memo(KilometerLengthsTable);
