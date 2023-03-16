import * as React from 'react';
import { Table, Th } from 'vayla-design-lib/table/table';
import { VerticalGeometryTableItem } from 'data-products/vertical-geometry/vertical-geometry-table-item';
import { VerticalGeometryItem } from 'geometry/geometry-model';
import styles from 'data-products/data-product-table.scss';
import {
    nonNumericHeading,
    numericHeading,
    withSeparator,
} from 'data-products/data-products-utils';
import { useTranslation } from 'react-i18next';
import { createClassName } from 'vayla-design-lib/utils';

type VerticalGeometryTableProps = {
    verticalGeometry: VerticalGeometryItem[];
};

const HEADINGS = [
    nonNumericHeading('plan'),
    nonNumericHeading('alignment'),
    withSeparator(numericHeading('track-address')),
    numericHeading('height'),
    numericHeading('angle'),
    withSeparator(numericHeading('track-address')),
    numericHeading('height'),
    withSeparator(numericHeading('track-address')),
    numericHeading('height'),
    numericHeading('angle'),
    withSeparator(numericHeading('radius')),
    numericHeading('tangent'),
    withSeparator(numericHeading('length')),
    numericHeading('linear-section'),
    withSeparator(numericHeading('length')),
    numericHeading('linear-section'),
    withSeparator(numericHeading('station-start')),
    numericHeading('station-vertical-intersection'),
    numericHeading('station-end'),
];

export const VerticalGeometryTable: React.FC<VerticalGeometryTableProps> = ({
    verticalGeometry,
}) => {
    const { t } = useTranslation();
    const separatorAndCenteredClassName = createClassName(
        styles['data-product-table__table-heading--centered'],
        styles['data-product-table__table-heading--separator'],
    );
    const headingClassName = (numeric: boolean, hasSeparator: boolean) =>
        createClassName(
            numeric && styles['data-product-table__column--number'],
            hasSeparator && styles['data-product-table__table-heading--separator'],
        );

    return (
        <div className={styles['data-product-table__table-container']}>
            <Table wide>
                <thead className={styles['data-product-table__table-heading']}>
                    <tr>
                        <Th
                            colSpan={2}
                            scope={'colgroup'}
                            className={styles['data-product-table__table-heading--centered']}
                        />
                        <Th
                            colSpan={3}
                            scope={'colgroup'}
                            className={separatorAndCenteredClassName}>
                            {t(`data-products.vertical-geometry.table.curve-start`)}
                        </Th>
                        <Th
                            colSpan={2}
                            scope={'colgroup'}
                            className={separatorAndCenteredClassName}>
                            {t(
                                `data-products.vertical-geometry.table.point-of-vertical-intersection`,
                            )}
                        </Th>
                        <Th
                            colSpan={3}
                            scope={'colgroup'}
                            className={separatorAndCenteredClassName}>
                            {t(`data-products.vertical-geometry.table.curve-end`)}
                        </Th>
                        <Th
                            colSpan={2}
                            scope={'colgroup'}
                            className={separatorAndCenteredClassName}
                        />
                        <Th
                            colSpan={2}
                            scope={'colgroup'}
                            className={separatorAndCenteredClassName}>
                            {t(`data-products.vertical-geometry.table.linear-section-backward`)}
                        </Th>
                        <Th
                            colSpan={2}
                            scope={'colgroup'}
                            className={separatorAndCenteredClassName}>
                            {t(`data-products.vertical-geometry.table.linear-section-forward`)}
                        </Th>
                        <Th
                            colSpan={3}
                            scope={'colgroup'}
                            className={separatorAndCenteredClassName}>
                            {t(`data-products.vertical-geometry.table.station`)}
                        </Th>
                    </tr>
                    <tr>
                        {HEADINGS.map((heading, index) => (
                            <Th
                                // The table contains columns with the same heading, so heading names can't be used as indexes.
                                // Also columns never change dynamically, so dynamic data integrity is not a concern.
                                key={index}
                                className={headingClassName(heading.numeric, heading.hasSeparator)}
                                scope={'colgroup'}>
                                {t(`data-products.vertical-geometry.table.${heading.name}`)}
                            </Th>
                        ))}
                    </tr>
                </thead>
                <tbody>
                    {verticalGeometry.map((verticalGeom) => (
                        <VerticalGeometryTableItem
                            key={verticalGeom.id}
                            verticalGeometry={verticalGeom}
                        />
                    ))}
                </tbody>
            </Table>
        </div>
    );
};
