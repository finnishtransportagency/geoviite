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

    return (
        <div>
            <p className={styles['element-list-view__table-container']}>
                {t(`data-products.element-list.geometry-elements`, { amount })}
            </p>
            <div className={styles['element-list-view__table-container']}>
                <Table wide>
                    <thead className={styles['element-list-view__table-heading']}>
                        <tr>
                            <th>
                                {t('data-products.element-list.element-list-table.track-number')}
                            </th>
                            {showLocationTrackName && (
                                <th>
                                    {t(
                                        'data-products.element-list.element-list-table.location-track',
                                    )}
                                </th>
                            )}
                            <th>{t('data-products.element-list.element-list-table.alignment')}</th>
                            <th>
                                {t('data-products.element-list.element-list-table.element-type')}
                            </th>
                            <th>
                                {t(
                                    'data-products.element-list.element-list-table.track-address-start',
                                )}
                            </th>
                            <th>
                                {t(
                                    'data-products.element-list.element-list-table.track-address-end',
                                )}
                            </th>
                            <th>
                                {t(
                                    'data-products.element-list.element-list-table.coordinate-system',
                                )}
                            </th>
                            <th>
                                {t(
                                    'data-products.element-list.element-list-table.location-start-e',
                                )}
                            </th>
                            <th>
                                {t(
                                    'data-products.element-list.element-list-table.location-start-n',
                                )}
                            </th>
                            <th>
                                {t('data-products.element-list.element-list-table.location-end-e')}
                            </th>
                            <th>
                                {t('data-products.element-list.element-list-table.location-end-n')}
                            </th>
                            <th>{t('data-products.element-list.element-list-table.length')}</th>
                            <th>
                                {t(
                                    'data-products.element-list.element-list-table.curve-radius-start',
                                )}
                            </th>
                            <th>
                                {t(
                                    'data-products.element-list.element-list-table.curve-radius-end',
                                )}
                            </th>
                            <th>{t('data-products.element-list.element-list-table.cant-start')}</th>
                            <th>{t('data-products.element-list.element-list-table.cant-end')}</th>
                            <th>
                                {t('data-products.element-list.element-list-table.angle-start')}
                            </th>
                            <th>{t('data-products.element-list.element-list-table.angle-end')}</th>
                            <th>{t('data-products.element-list.element-list-table.plan')}</th>
                            <th>{t('data-products.element-list.element-list-table.source')}</th>
                        </tr>
                    </thead>
                    <tbody>
                        {elements.map((item, index) => (
                            // Element list can contain the same element multiple times -> use index in list as key
                            <React.Fragment key={`${index}`}>
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
        </div>
    );
};
