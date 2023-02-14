import * as React from 'react';
import { ElementTableItem } from 'data-products/element-list/element-table-item';
import { Table } from 'vayla-design-lib/table/table';
import { useTranslation } from 'react-i18next';
import styles from './element-list-view.scss';
import { ElementItem } from 'geometry/geometry-model';
import { useTrackNumbers } from 'track-layout/track-layout-react-utils';

type ElementTableProps = {
    elements: ElementItem[];
    showLocationTrackName: boolean;
};

export const ElementTable = ({ elements, showLocationTrackName }: ElementTableProps) => {
    const { t } = useTranslation();
    const trackNumbers = useTrackNumbers('OFFICIAL');
    const amount = elements.length;
    const commonTableHeadings = [
        'alignment',
        'element-type',
        'track-address-start',
        'track-address-end',
        'coordinate-system',
        'location-start-e',
        'location-start-n',
        'location-end-e',
        'location-end-n',
        'length',
        'curve-radius-start',
        'curve-radius-end',
        'cant-start',
        'cant-end',
        'angle-start',
        'angle-end',
        'plan',
        'source',
    ];

    const tableHeadingsToShowInUI = showLocationTrackName
        ? ['track-number', 'location-track'].concat(commonTableHeadings)
        : ['track-number'].concat(commonTableHeadings);

    return (
        <React.Fragment>
            <p className={styles['element-list-view__element-count']}>
                {t(`data-products.element-list.geometry-elements`, { amount })}
            </p>
            <div className={styles['element-list-view__table-container']}>
                <Table wide>
                    <thead className={styles['element-list-view__table-heading']}>
                        <tr>
                            {tableHeadingsToShowInUI.map((heading) => (
                                <th key={heading}>
                                    {t(`data-products.element-list.element-list-table.${heading}`)}
                                </th>
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
                                    coordinateSystem={
                                        item.coordinateSystemSrid ?? item.coordinateSystemName
                                    }
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
                                    showLocationTrackName={showLocationTrackName}
                                />
                            </React.Fragment>
                        ))}
                    </tbody>
                </Table>
            </div>
        </React.Fragment>
    );
};
