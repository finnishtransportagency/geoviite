import * as React from 'react';
import { ElementTableItem } from 'data-products/element-list/element-table-item';
import { Table, Th } from 'vayla-design-lib/table/table';
import { useTranslation } from 'react-i18next';
import styles from '../data-product-table.scss';
import { ElementItem } from 'geometry/geometry-model';
import {
    ElementHeadingWithClassName,
    nonNumericHeading,
    numericHeading,
    withClassName,
} from 'data-products/data-products-utils';
import { LayoutContext } from 'common/common-model';

type ElementTableProps = {
    layoutContext: LayoutContext;
    elements: ElementItem[];
    showLocationTrackName: boolean;
    isLoading: boolean;
};

const ElementTable = ({ elements, showLocationTrackName, isLoading }: ElementTableProps) => {
    const { t } = useTranslation();
    const amount = elements.length;
    const commonTableHeadings: ElementHeadingWithClassName[] = [
        withClassName(
            nonNumericHeading('alignment'),
            styles['data-product-table__header--alignment'],
        ),
        withClassName(
            nonNumericHeading('element-type'),
            styles['data-product-table__header--element-type'],
        ),
        withClassName(
            numericHeading('track-address-start'),
            styles['data-product-table__header--track-address'],
        ),
        withClassName(
            numericHeading('track-address-end'),
            styles['data-product-table__header--track-address'],
        ),
        withClassName(
            nonNumericHeading('coordinate-system'),
            styles['data-product-table__header--coordinate-system'],
        ),
        withClassName(
            numericHeading('location-start-e'),
            styles['data-product-table__header--location'],
        ),
        withClassName(
            numericHeading('location-start-n'),
            styles['data-product-table__header--location'],
        ),
        withClassName(
            numericHeading('location-end-e'),
            styles['data-product-table__header--location'],
        ),
        withClassName(
            numericHeading('location-end-n'),
            styles['data-product-table__header--location'],
        ),
        withClassName(numericHeading('length'), styles['data-product-table__header--length']),
        withClassName(
            numericHeading('curve-radius-start'),
            styles['data-product-table__header--curve'],
        ),
        withClassName(
            numericHeading('curve-radius-end'),
            styles['data-product-table__header--curve'],
        ),
        withClassName(numericHeading('cant-start'), styles['data-product-table__header--cant']),
        withClassName(numericHeading('cant-end'), styles['data-product-table__header--cant']),
        withClassName(numericHeading('angle-start'), styles['data-product-table__header--angle']),
        withClassName(numericHeading('angle-end'), styles['data-product-table__header--angle']),
        withClassName(nonNumericHeading('plan'), styles['data-product-table__header--plan']),
        withClassName(nonNumericHeading('source'), styles['data-product-table__header--source']),
        withClassName(
            nonNumericHeading('plan-time'),
            styles['data-product-table__header--plan-time'],
        ),
        withClassName(nonNumericHeading('remarks'), styles['data-product-table__header--remarks']),
    ];

    const trackNumberHeading = withClassName(
        nonNumericHeading('track-number'),
        styles['data-product-table__header--track-number'],
    );

    const tableHeadingsToShowInUI = showLocationTrackName
        ? [
              trackNumberHeading,
              withClassName(
                  nonNumericHeading('location-track'),
                  styles['data-product-table__header--location-track'],
              ),
              ...commonTableHeadings,
          ]
        : [trackNumberHeading, ...commonTableHeadings];

    return (
        <React.Fragment>
            <p className={styles['data-product-table__element-count']}>
                {t(`data-products.element-list.geometry-elements`, { amount })}
            </p>
            <div className={styles['data-product-table__table-container']}>
                <Table wide isLoading={isLoading}>
                    <thead className={styles['data-product-table__table-heading']}>
                        <tr>
                            {tableHeadingsToShowInUI.map((heading) => (
                                <Th
                                    qa-id={`data-products.element-list.element-list-table.${heading.name}`}
                                    key={heading.name}
                                    className={heading?.className ?? ''}>
                                    {t(
                                        `data-products.element-list.element-list-table.${heading.name}`,
                                    )}
                                </Th>
                            ))}
                        </tr>
                    </thead>
                    <tbody>
                        {elements.map((item) => (
                            <React.Fragment key={item.id}>
                                <ElementTableItem
                                    trackNumber={item.trackNumber}
                                    geometryAlignmentName={item.alignmentName}
                                    locationTrackName={item.locationTrackName}
                                    type={t(
                                        `data-products.element-list.element-list-table.${item.elementType}`,
                                    )}
                                    trackAddressStart={item.start.address}
                                    trackAddressEnd={item.end.address}
                                    coordinateSystem={item.coordinateSystemSrid}
                                    locationStartE={item.start.coordinate.x}
                                    locationStartN={item.start.coordinate.y}
                                    locationEndE={item.end.coordinate.x}
                                    locationEndN={item.end.coordinate.y}
                                    length={item.lengthMeters}
                                    curveRadiusStart={item.start.radiusMeters}
                                    curveRadiusEnd={item.end.radiusMeters}
                                    cantStart={item.start.cant}
                                    cantEnd={item.end.cant}
                                    angleStart={item.start.directionGrads}
                                    angleEnd={item.end.directionGrads}
                                    plan={item.fileName}
                                    source={item.planSource}
                                    planId={item.planId}
                                    showLocationTrackName={showLocationTrackName}
                                    connectedSwitchName={item.connectedSwitchName}
                                    isPartial={item.isPartial}
                                    planTime={item.planTime}
                                />
                            </React.Fragment>
                        ))}
                    </tbody>
                </Table>
            </div>
        </React.Fragment>
    );
};

export default React.memo(ElementTable);
