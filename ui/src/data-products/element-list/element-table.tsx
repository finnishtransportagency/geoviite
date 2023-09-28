import * as React from 'react';
import { ElementTableItem } from 'data-products/element-list/element-table-item';
import { Table, Th } from 'vayla-design-lib/table/table';
import { useTranslation } from 'react-i18next';
import styles from '../data-product-table.scss';
import { ElementItem } from 'geometry/geometry-model';
import { useTrackNumbers } from 'track-layout/track-layout-react-utils';
import {
    ElementHeading,
    nonNumericHeading,
    numericHeading,
} from 'data-products/data-products-utils';

type ElementTableProps = {
    elements: ElementItem[];
    showLocationTrackName: boolean;
    isLoading: boolean;
};

const ElementTable = ({ elements, showLocationTrackName, isLoading }: ElementTableProps) => {
    const { t } = useTranslation();
    const trackNumbers = useTrackNumbers('OFFICIAL');
    const amount = elements.length;
    const commonTableHeadings: ElementHeading[] = [
        nonNumericHeading('alignment'),
        nonNumericHeading('element-type'),
        numericHeading('track-address-start'),
        numericHeading('track-address-end'),
        nonNumericHeading('coordinate-system'),
        numericHeading('location-start-e'),
        numericHeading('location-start-n'),
        numericHeading('location-end-e'),
        numericHeading('location-end-n'),
        numericHeading('length'),
        numericHeading('curve-radius-start'),
        numericHeading('curve-radius-end'),
        numericHeading('cant-start'),
        numericHeading('cant-end'),
        numericHeading('angle-start'),
        numericHeading('angle-end'),
        nonNumericHeading('plan'),
        nonNumericHeading('source'),
        nonNumericHeading('remarks'),
    ];

    const tableHeadingsToShowInUI = showLocationTrackName
        ? [nonNumericHeading('track-number'), nonNumericHeading('location-track')].concat(
              commonTableHeadings,
          )
        : [nonNumericHeading('track-number')].concat(commonTableHeadings);

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
                                    className={
                                        heading.numeric
                                            ? styles['data-product-table__column--number']
                                            : ''
                                    }>
                                    {t(
                                        `data-products.element-list.element-list-table.${heading.name}`,
                                    )}
                                </Th>
                            ))}
                        </tr>
                    </thead>
                    <tbody>
                        {elements.map((item) => (
                            <React.Fragment key={`${item.id}`}>
                                <ElementTableItem
                                    id={item.alignmentId}
                                    trackNumber={
                                        trackNumbers?.find((tn) => tn.id === item.trackNumberId)
                                            ?.number
                                    }
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
