import * as React from 'react';
import { ElementTableItem } from 'data-products/element-list/element-table-item';
import { Table } from 'vayla-design-lib/table/table';
import { useTranslation } from 'react-i18next';
import { GeometryType } from 'geometry/geometry-model';
import styles from './element-list-view.scss';

type ElementItem = {
    id: string;
    trackNumber: string;
    track: string;
    type: GeometryType;
    trackAddressStart: string;
    trackAddressEnd: string;
    locationStartE: string;
    locationStartN: string;
    locationEndE: string;
    locationEndN: string;
    length: string;
    curveRadiusStart: string;
    curveRadiusEnd: string;
    cantStart: string;
    cantEnd: string;
    angleStart: string;
    angleEnd: string;
    plan: string;
    coordinateSystem: string;
};
/*
type ElementTableProps = {
    plans: JOTAIN;
    continuousGeometrySelected: boolean;
};

const ElementTable: React.FC<ElementTableProps> = ({
    plans,
    continuousGeometrySelected,
*/

export const ElementTable = () => {
    const { t } = useTranslation();
    const amount = 123;

    //testidataa; simuloi bäkkäristä tulevaa dataa
    const item1: ElementItem = {
        id: 'abcde',
        trackNumber: '123',
        track: 'HKI-TRE',
        type: GeometryType.LINE,
        trackAddressStart: 'aa',
        trackAddressEnd: ' bb',
        locationStartE: 'cc',
        locationStartN: 'dd',
        locationEndE: 'hh',
        locationEndN: 'ii',
        length: 'ee',
        curveRadiusStart: 'jj',
        curveRadiusEnd: 'uhuh',
        cantStart: 'ee',
        cantEnd: 'ww',
        angleStart: 'ijij',
        angleEnd: 'eye',
        plan: 'ueue',
        coordinateSystem: 'iauhew',
    };
    const item2: ElementItem = {
        id: 'fghij',
        trackNumber: '456',
        track: 'HKI-JKL',
        type: GeometryType.CLOTHOID,
        trackAddressStart: 'aa',
        trackAddressEnd: ' bb',
        locationStartE: 'cc',
        locationStartN: 'dd',
        locationEndE: 'hh',
        locationEndN: 'ii',
        length: 'ee',
        curveRadiusStart: 'jj',
        curveRadiusEnd: 'uhuh',
        cantStart: 'ee',
        cantEnd: 'ww',
        angleStart: 'ijij',
        angleEnd: 'eye',
        plan: 'ueue',
        coordinateSystem: 'iauhew',
    };
    const myList: ElementItem[] = [item1, item2];

    //Siirrä translations-tiedostoon "Geometriaelementit (x kpl)"?
    return (
        <div>
            <p>
                {t('data-products.element-list.geometry-elements') + ` (${amount}`}
                {t('data-products.element-list.pcs')} {')'}
            </p>
            <div className={styles['element-list-view__table-container']}>
                <Table wide>
                    <thead>
                        <tr>
                            <th>
                                {t('data-products.element-list.element-list-table.track-number')}
                            </th>
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
                                    'data-products.element-list.element-list-table.track-address-start',
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
                            <th>
                                {t(
                                    'data-products.element-list.element-list-table.coordinate-system',
                                )}
                            </th>
                        </tr>
                    </thead>
                    <tbody>
                        {myList.map((item) => (
                            <React.Fragment key={item.id}>
                                {
                                    <ElementTableItem
                                        id={item.id}
                                        trackNumber={item.trackNumber}
                                        track={item.track}
                                        type={t(
                                            `data-products.element-list.element-list-table.${item.type}`,
                                        )}
                                        trackAddressStart={item.trackAddressStart}
                                        trackAddressEnd={item.trackAddressEnd}
                                        locationStartE={item.locationStartE}
                                        locationStartN={item.locationStartN}
                                        locationEndE={item.locationEndE}
                                        locationEndN={item.locationEndN}
                                        length={item.length}
                                        curveRadiusStart={item.curveRadiusEnd}
                                        curveRadiusEnd={item.curveRadiusEnd}
                                        cantStart={item.cantStart}
                                        cantEnd={item.cantEnd}
                                        angleStart={item.angleStart}
                                        angleEnd={item.angleEnd}
                                        plan={item.plan}
                                        coordinateSystem={item.coordinateSystem}
                                    />
                                }
                            </React.Fragment>
                        ))}
                    </tbody>
                </Table>
            </div>
        </div>
    );
};
