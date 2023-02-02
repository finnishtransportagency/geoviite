import * as React from 'react';
import { ElementTableItem } from 'data-products/element-list/element-table-item';
import { Table } from 'vayla-design-lib/table/table';
import { useTranslation } from 'react-i18next';
import styles from './element-list-view.scss';
import { LayoutTrackNumberId, LocationTrackId } from 'track-layout/track-layout-model';
import { GeometryTypeIncludingMissing } from 'data-products/element-list/element-list-store';
import { ElementLocation, Srid } from 'common/common-model';
import { GeometryElementId, GeometryPlanId, PlanSource } from 'geometry/geometry-model';
import { useTrackNumbers } from 'track-layout/track-layout-react-utils';

export type ElementItem = {
    alignmentId: LocationTrackId;
    alignmentName: string;
    elementId: GeometryElementId;
    elementType: GeometryTypeIncludingMissing;
    start: ElementLocation;
    end: ElementLocation;
    lengthMeters: number;
    planId: GeometryPlanId;
    planSource: PlanSource;
    fileName: string;
    coordinateSystemSrid: Srid;
    trackNumberId: LayoutTrackNumberId;
    trackNumberDescription: string;
    coordinateSystemName: string;
};

type ElementTableProps = {
    plans: ElementItem[];
};

export const ElementTable = ({ plans }: ElementTableProps) => {
    const { t } = useTranslation();
    const amount = plans.length;
    const trackNumbers = useTrackNumbers('OFFICIAL');

    //Siirrä translations-tiedostoon "Geometriaelementit (x kpl)"?
    return (
        <div>
            <p>
                {t('data-products.element-list.geometry-elements') + ` (${amount}`}
                {t('data-products.element-list.pcs')}
                {')'}
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
                            <th>{t('data-products.element-list.element-list-table.source')}</th>
                            <th>
                                {t(
                                    'data-products.element-list.element-list-table.coordinate-system',
                                )}
                            </th>
                        </tr>
                    </thead>
                    <tbody>
                        {plans.map((item, index) => (
                            // Element list can contain the same element multiple times -> use index in list as key
                            <React.Fragment key={`${index}`}>
                                <ElementTableItem
                                    id={item.alignmentId}
                                    trackNumber={
                                        trackNumbers?.find((tn) => tn.id === item.trackNumberId)
                                            ?.number
                                    }
                                    track={item.alignmentName}
                                    type={t(
                                        `data-products.element-list.element-list-table.${item.elementType}`,
                                    )}
                                    trackAddressStart={item.start.address}
                                    trackAddressEnd={item.start.address}
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
                                    coordinateSystem={
                                        item.coordinateSystemSrid ?? item.coordinateSystemName
                                    }
                                />
                            </React.Fragment>
                        ))}
                    </tbody>
                </Table>
            </div>
        </div>
    );
};
