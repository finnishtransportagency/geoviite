import { Heading, HeadingSize } from 'vayla-design-lib/heading/heading';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { filterUnique } from 'utils/array-utils';
import { Radio } from 'vayla-design-lib/radio/radio';
import { formatToTM35FINString } from 'utils/geography-utils';
import React from 'react';
import { useTranslation } from 'react-i18next';
import {
    actions,
    gkLocationSource,
    KmPostEditState,
} from 'tool-panel/km-post/dialog/km-post-edit-store';
import { KmPostEditFields } from 'linking/linking-model';
import { useCoordinateSystem, useCoordinateSystems } from 'track-layout/track-layout-react-utils';
import { GkLocationSource, LAYOUT_SRID } from 'track-layout/track-layout-model';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import proj4 from 'proj4';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { GeometryPoint, Point } from 'model/geometry';
import styles from 'tool-panel/km-post/dialog/km-post-edit-dialog.scss';
import { Srid } from 'common/common-model';
import { parseFloatOrUndefined } from 'utils/string-utils';

// GK-FIN coordinate systems currently only used for the live display of layout coordinates when editing km post
// positions manually
const GK_FIN_COORDINATE_SYSTEMS: [Srid, string][] = [...Array(12)].map((_, meridianIndex) => {
    const meridian = 19 + meridianIndex;
    const falseNorthing = meridian * 1e6 + 0.5e6;
    const srid = `EPSG:${3873 + meridianIndex}`;
    const projection = `+proj=tmerc +lat_0=0 +lon_0=${meridian} +k=1 +x_0=${falseNorthing} +y_0=0 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs`;
    return [srid, projection];
});

type KmPostEditDialogGkLocationSectionProps = {
    state: KmPostEditState;
    stateActions: typeof actions;
    updateProp: <TKey extends keyof KmPostEditFields>(
        key: TKey,
        value: KmPostEditFields[TKey],
    ) => void;
    hasErrors: (prop: keyof KmPostEditFields) => boolean;
    getVisibleErrorsByProp: (prop: keyof KmPostEditFields) => string[];
    geometryKmPostGkLocation?: GeometryPoint;
};

function gkLocationSourceI18nKey(source: GkLocationSource) {
    const end = (() => {
        switch (source) {
            case 'FROM_GEOMETRY':
                return 'from-geometry';
            case 'FROM_LAYOUT':
                return 'from-layout';
            case 'MANUAL':
                return 'manual';
            default:
                exhaustiveMatchingGuard(source);
        }
    })();
    return `km-post-dialog.gk-location.source-${end}`;
}

function gkToLayout(gkSrid: Srid | undefined, xStr: string, yStr: string): Point | undefined {
    const x = parseFloatOrUndefined(xStr);
    const y = parseFloatOrUndefined(yStr);
    const projection = GK_FIN_COORDINATE_SYSTEMS.find(([srid]) => srid === gkSrid)?.[1];
    if (projection === undefined || x === undefined || y === undefined) {
        return undefined;
    } else {
        try {
            const res = proj4(projection, LAYOUT_SRID).forward({ x, y });
            return Number.isFinite(res.x) && Number.isFinite(res.y) ? res : undefined;
        } catch (e) {
            return undefined;
        }
    }
}

export const KmPostEditDialogGkLocationSection: React.FC<
    KmPostEditDialogGkLocationSectionProps
> = ({
    state,
    stateActions,
    updateProp,
    hasErrors,
    getVisibleErrorsByProp,
    geometryKmPostGkLocation,
}) => {
    const { t } = useTranslation();

    const displayGkLocationSource = (source: GkLocationSource | undefined) =>
        source === undefined ? '' : t(gkLocationSourceI18nKey(source));

    const gkLocationEntered =
        geometryKmPostGkLocation !== undefined ||
        (state.kmPost.gkLocationY !== '' && state.kmPost.gkLocationX !== '');

    const gkCoordinateSystem = useCoordinateSystem(state.kmPost.gkSrid);
    const layoutLocation = gkToLayout(
        gkCoordinateSystem?.srid,
        state.kmPost.gkLocationX,
        state.kmPost.gkLocationY,
    );
    // const layoutLocation =
    //     gkCoordinateSystem === undefined || !gkLocationEntered
    //         ? undefined
    //         : (() => {
    //               try {
    //                   const projection = GK_FIN_COORDINATE_SYSTEMS.find(
    //                       ([srid]) => srid === gkCoordinateSystem.srid,
    //                   )?.[1];
    //                   return projection === undefined
    //                       ? undefined
    //                       : proj4(projection, LAYOUT_SRID).forward({
    //                             x: parseFloat(state.kmPost.gkLocationX),
    //                             y: parseFloat(state.kmPost.gkLocationY),
    //                         });
    //               } catch (e) {
    //                   return undefined;
    //               }
    //           })();

    const coordinateSystems = useCoordinateSystems(GK_FIN_COORDINATE_SYSTEMS.map(([srid]) => srid));

    return (
        <>
            <Heading size={HeadingSize.SUB}>{t('km-post-dialog.gk-location.title')}</Heading>
            <FieldLayout
                label={t('km-post-dialog.gk-location.location-field')}
                value={
                    <div className={styles['km-post-edit-dialog__location']}>
                        <div className={styles['km-post-edit-dialog__location-axis']}>
                            <TextField
                                qa-id="km-post-gk-location-n"
                                value={state.kmPost?.gkLocationY}
                                onChange={(e) => updateProp('gkLocationY', e.target.value)}
                                onBlur={() => stateActions.onCommitField('gkLocationY')}
                                hasError={hasErrors('gkLocationY')}
                                wide
                                className={styles['km-post-edit-dialog__location-axis-field']}
                            />{' '}
                            <div className={styles['km-post-edit-dialog__location-axis-letter']}>
                                N
                            </div>
                        </div>
                        <div className={styles['km-post-edit-dialog__location-axis']}>
                            <TextField
                                qa-id="km-post-gk-location-e"
                                value={state.kmPost?.gkLocationX}
                                onChange={(e) => updateProp('gkLocationX', e.target.value)}
                                onBlur={() => stateActions.onCommitField('gkLocationX')}
                                hasError={hasErrors('gkLocationX')}
                                wide
                                className={styles['km-post-edit-dialog__location-axis-field']}
                            />{' '}
                            <div className={styles['km-post-edit-dialog__location-axis-letter']}>
                                E
                            </div>
                        </div>
                    </div>
                }
                errors={[
                    ...getVisibleErrorsByProp('gkLocationY'),
                    ...getVisibleErrorsByProp('gkLocationX'),
                ].filter(filterUnique)}
            />
            <FieldLayout
                label={t('km-post-dialog.gk-location.coordinate-system-field')}
                value={
                    <Dropdown
                        options={(coordinateSystems ?? []).map((system) => ({
                            value: system.srid,
                            name: `${system.srid} ${system.name}`,
                            qaId: system.srid,
                        }))}
                        value={state.kmPost.gkSrid}
                        onChange={(srid) => updateProp('gkSrid', srid)}
                    />
                }
            />
            <FieldLayout
                label={t('km-post-dialog.gk-location.confirmed-field')}
                value={
                    <div className={styles['km-post-edit-dialog__confirmed']}>
                        <Radio
                            checked={state.kmPost.gkLocationConfirmed === true && gkLocationEntered}
                            onChange={() => updateProp('gkLocationConfirmed', true)}
                            disabled={!gkLocationEntered}>
                            {t('km-post-dialog.gk-location.confirmed')}
                        </Radio>
                        <Radio
                            checked={
                                state.kmPost.gkLocationConfirmed === false || !gkLocationEntered
                            }
                            onChange={() => updateProp('gkLocationConfirmed', false)}
                            disabled={!gkLocationEntered}>
                            {t('km-post-dialog.gk-location.not-confirmed')}
                        </Radio>
                    </div>
                }
            />
            <FieldLayout
                label={t('km-post-dialog.gk-location.source')}
                value={displayGkLocationSource(gkLocationSource(state))}
            />
            <Heading size={HeadingSize.SUB}>
                {t('km-post-dialog.gk-location.location-in-layout')}
            </Heading>
            <div className="field-layout__value">
                {layoutLocation === undefined
                    ? t('km-post-dialog.gk-location.location-not-defined')
                    : formatToTM35FINString(layoutLocation)}
            </div>
        </>
    );
};
