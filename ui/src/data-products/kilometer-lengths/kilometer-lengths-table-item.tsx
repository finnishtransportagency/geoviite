import * as React from 'react';
import { Precision, roundToPrecision } from 'utils/rounding';
import styles from '../data-product-table.scss';
import { GeometrySource, GkLocationSource, LAYOUT_SRID } from 'track-layout/track-layout-model';
import { useTranslation } from 'react-i18next';
import { CoordinateSystem } from 'common/common-model';
import { GeometryPoint, Point } from 'model/geometry';
import CoordinateSystemView from 'geoviite-design-lib/coordinate-system/coordinate-system-view';
import { useCoordinateSystem } from 'track-layout/track-layout-react-utils';
import { KmLengthsLocationPrecision } from 'data-products/data-products-slice';

export type KilometerLengthsTableItemProps = {
    trackNumber: string | undefined;
    kilometer: string;
    length: number;
    startM: number;
    endM: number;
    coordinateSystem: CoordinateSystem;
    layoutLocation: Point | undefined;
    layoutGeometrySource: GeometrySource;
    gkLocation: GeometryPoint | undefined;
    gkLocationSource: GkLocationSource | undefined;
    gkLocationConfirmed: boolean;
    locationPrecision: KmLengthsLocationPrecision;
    linkedFromGeometry: boolean;
};

export const KilometerLengthTableItem: React.FC<KilometerLengthsTableItemProps> = ({
    trackNumber,
    kilometer,
    length,
    startM,
    endM,
    layoutLocation,
    layoutGeometrySource,
    gkLocation,
    gkLocationConfirmed,
    gkLocationSource,
    locationPrecision,
    linkedFromGeometry,
}) => {
    const { t } = useTranslation();
    const kmPostCoordinateSystem = useCoordinateSystem(gkLocation?.srid);
    const layoutCoordinateSystem = useCoordinateSystem(LAYOUT_SRID);

    const hasLayoutLocation = layoutLocation !== undefined;
    const hasGkLocation = gkLocation !== undefined;
    const showingPreciseLocation = locationPrecision === 'PRECISE_LOCATION';
    const generatedRow = layoutGeometrySource === 'GENERATED';

    const location = showingPreciseLocation ? gkLocation : layoutLocation;
    const coordinateSystem = showingPreciseLocation
        ? kmPostCoordinateSystem
        : layoutCoordinateSystem;

    let locationSourceString = '';
    if (!generatedRow) {
        const gkLocationSourceString = hasGkLocation
            ? t(`enum.gk-location-source.${gkLocationSource}`)
            : '';
        const layoutLocationSourceString = linkedFromGeometry
            ? t('data-products.km-lengths.table.from-geometry')
            : t('data-products.km-lengths.table.from-ratko');

        locationSourceString = showingPreciseLocation
            ? gkLocationSourceString
            : layoutLocationSourceString;
    }

    let locationPrecisionString = '';
    if (!generatedRow) {
        const gkLocationConfirmationString = gkLocationConfirmed
            ? t('data-products.km-lengths.table.confirmed')
            : t('data-products.km-lengths.table.not-confirmed');
        const layoutLocationConfirmationString = t('data-products.km-lengths.table.not-confirmed');

        locationPrecisionString = showingPreciseLocation
            ? gkLocationConfirmationString
            : layoutLocationConfirmationString;
    }

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
                <td>
                    <CoordinateSystemView coordinateSystem={coordinateSystem} />
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {location && roundToPrecision(location.x, Precision.coordinateMeters)}
                </td>
                <td className={styles['data-product-table__column--number']}>
                    {location && roundToPrecision(location.y, Precision.coordinateMeters)}
                </td>
                <td>{locationSourceString}</td>
                <td>{locationPrecisionString}</td>
                <td>
                    {hasLayoutLocation &&
                        (layoutGeometrySource == 'IMPORTED' ||
                            gkLocationSource === 'FROM_LAYOUT') &&
                        t('data-products.km-lengths.table.imported-warning')}

                    {hasLayoutLocation &&
                        layoutGeometrySource == 'GENERATED' &&
                        t('data-products.km-lengths.table.generated-warning')}
                </td>
            </tr>
        </React.Fragment>
    );
};
