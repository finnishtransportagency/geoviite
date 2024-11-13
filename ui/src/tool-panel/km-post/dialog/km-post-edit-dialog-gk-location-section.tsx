import { Heading, HeadingSize } from 'vayla-design-lib/heading/heading';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { filterUnique } from 'utils/array-utils';
import { Radio } from 'vayla-design-lib/radio/radio';
import { formatToTM35FINString, formatWithSrid } from 'utils/geography-utils';
import React from 'react';
import { useTranslation } from 'react-i18next';
import {
    actions,
    GK_FIN_COORDINATE_SYSTEMS,
    gkLocationSource,
    isWithinEastingMargin,
    KmPostEditState,
    parseGk,
} from 'tool-panel/km-post/dialog/km-post-edit-store';
import { KmPostEditFields } from 'linking/linking-model';
import { useCoordinateSystem, useCoordinateSystems } from 'track-layout/track-layout-react-utils';
import { GkLocationSource, LAYOUT_SRID } from 'track-layout/track-layout-model';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { GeometryPoint, Point } from 'model/geometry';
import styles from 'tool-panel/km-post/dialog/km-post-edit-dialog.scss';
import { Srid } from 'common/common-model';
import { KmPostEditDialogType } from 'tool-panel/km-post/dialog/km-post-edit-dialog';
import { Switch } from 'vayla-design-lib/switch/switch';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { createClassName } from 'vayla-design-lib/utils';
import proj4 from 'proj4';

type KmPostEditDialogGkLocationSectionProps = {
    state: KmPostEditState;
    stateActions: typeof actions;
    updateProp: <TKey extends keyof KmPostEditFields>(
        key: TKey,
        value: KmPostEditFields[TKey],
    ) => void;
    hasErrors: (prop: keyof KmPostEditFields) => boolean;
    getVisibleErrorsByProp: (prop: keyof KmPostEditFields) => string[];
    getVisibleWarningsByProp: (prop: keyof KmPostEditFields) => string[];
    geometryKmPostGkLocation?: GeometryPoint;
    editType: KmPostEditDialogType;
    geometryPlanSrid?: Srid;
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
                return exhaustiveMatchingGuard(source);
        }
    })();
    return `km-post-dialog.gk-location.source-${end}`;
}

const findProjection = (srid: Srid): string | undefined =>
    GK_FIN_COORDINATE_SYSTEMS.find(([s]) => s === srid)?.[1];

function transformGkToLayout(point: GeometryPoint): Point | undefined {
    const projection = findProjection(point.srid);
    if (projection === undefined) {
        return undefined;
    } else {
        try {
            const res = proj4(projection, LAYOUT_SRID).forward({ x: point.x, y: point.y });
            return Number.isFinite(res.x) && Number.isFinite(res.y) ? res : undefined;
        } catch (e) {
            console.log(`transformGkToLayout caught an exception: ${e}`);
            return undefined;
        }
    }
}

const gkLocationSourceTranslationKey = (
    source: GkLocationSource | undefined,
    gkLocationEnabled: boolean,
    dialogRole: KmPostEditDialogType,
) => {
    if (!gkLocationEnabled || source === undefined) {
        return 'km-post-dialog.gk-location.source-none';
    } else if (dialogRole === 'LINKING') {
        return gkLocationSourceI18nKey('FROM_GEOMETRY');
    } else {
        return gkLocationSourceI18nKey(source);
    }
};

const TransformedFromNonGkWarning: React.FC<{ originalSrid: Srid }> = ({ originalSrid }) => {
    const { t } = useTranslation();

    const originalCoordinateSystem = useCoordinateSystem(originalSrid);

    const className = createClassName(
        styles['km-post-edit-dialog__crs-transform-notice'],
        styles['km-post-edit-dialog__crs-transform-notice--warning'],
    );

    return (
        <span className={className}>
            {t('km-post-dialog.gk-location.location-converted', {
                originalCrs: originalCoordinateSystem
                    ? formatWithSrid(originalCoordinateSystem)
                    : '',
            })}
            <Icons.StatusError size={IconSize.SMALL} color={IconColor.INHERIT} />
        </span>
    );
};

const TransformedGkWarning: React.FC<{ originalSrid: Srid }> = ({ originalSrid }) => {
    const { t } = useTranslation();

    const originalCoordinateSystem = useCoordinateSystem(originalSrid);

    return (
        <span className={styles['km-post-edit-dialog__crs-transform-notice']}>
            {t('km-post-dialog.gk-location.location-converted', {
                originalCrs: originalCoordinateSystem
                    ? formatWithSrid(originalCoordinateSystem)
                    : '',
            })}

            <Icons.Info size={IconSize.SMALL} color={IconColor.INHERIT} />
        </span>
    );
};

export const KmPostEditDialogGkLocationSection: React.FC<
    KmPostEditDialogGkLocationSectionProps
> = ({
    state,
    stateActions,
    updateProp,
    hasErrors,
    getVisibleErrorsByProp,
    getVisibleWarningsByProp,
    editType,
    geometryPlanSrid,
}) => {
    const { t } = useTranslation();
    const isLinking = editType === 'LINKING';

    const kmPost = state.kmPost;
    const gkLocation = parseGk(kmPost.gkSrid, kmPost.gkLocationX, kmPost.gkLocationY);
    const layoutLocation = gkLocation ? transformGkToLayout(gkLocation) : undefined;
    const gkLocationEnabled = state.gkLocationEnabled;
    const fieldsEnabled = !isLinking && gkLocationEnabled;

    const coordinateSystems = useCoordinateSystems(GK_FIN_COORDINATE_SYSTEMS.map(([srid]) => srid));
    const isFromNonGkPlan =
        geometryPlanSrid !== undefined &&
        !GK_FIN_COORDINATE_SYSTEMS.find(([srid]) => srid === geometryPlanSrid);
    const isFromDifferentGk =
        geometryPlanSrid !== undefined &&
        geometryPlanSrid !== state.kmPost.gkSrid &&
        !!GK_FIN_COORDINATE_SYSTEMS.find(([srid]) => srid === geometryPlanSrid);

    const withinMargin = gkLocation ? isWithinEastingMargin(gkLocation) : false;

    const layoutLocationString = () => {
        if (gkLocationEnabled && layoutLocation && withinMargin) {
            return formatToTM35FINString(layoutLocation);
        } else {
            return '-';
        }
    };

    return (
        <>
            <Heading size={HeadingSize.SUB}>
                <span className={styles['km-post-edit-dialog__location-header-container']}>
                    {t('km-post-dialog.gk-location.title')}
                    <Switch
                        checked={gkLocationEnabled}
                        onCheckedChange={() =>
                            stateActions.setGkLocationEnabled(!gkLocationEnabled)
                        }
                        disabled={editType === 'LINKING'}
                    />
                </span>
            </Heading>
            <FieldLayout
                label={`${t('km-post-dialog.gk-location.coordinate-system-field')} *`}
                disabled={!fieldsEnabled}
                errors={getVisibleErrorsByProp('gkSrid')}
                value={
                    <Dropdown
                        options={(coordinateSystems ?? []).map((system) => ({
                            value: system.srid,
                            name: formatWithSrid(system),
                            qaId: system.srid,
                        }))}
                        wide
                        disabled={!fieldsEnabled}
                        value={gkLocationEnabled ? state.kmPost.gkSrid : undefined}
                        canUnselect
                        onChange={(srid) => updateProp('gkSrid', srid)}
                        hasError={hasErrors('gkSrid')}
                        onBlur={() => stateActions.onCommitField('gkSrid')}
                    />
                }
            />
            <FieldLayout
                label={`${t('km-post-dialog.gk-location.location-field')} *`}
                disabled={!fieldsEnabled}
                help={
                    isLinking && isFromNonGkPlan ? (
                        <TransformedFromNonGkWarning originalSrid={geometryPlanSrid} />
                    ) : isLinking && isFromDifferentGk ? (
                        <TransformedGkWarning originalSrid={geometryPlanSrid} />
                    ) : (
                        <React.Fragment />
                    )
                }
                value={
                    <div className={styles['km-post-edit-dialog__location']}>
                        <div className={styles['km-post-edit-dialog__location-axis']}>
                            <TextField
                                qa-id="km-post-gk-location-n"
                                value={gkLocationEnabled ? state.kmPost.gkLocationY : ''}
                                onChange={(e) => updateProp('gkLocationY', e.target.value)}
                                disabled={!fieldsEnabled}
                                onBlur={() => {
                                    stateActions.onCommitField('gkLocationY');
                                }}
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
                                value={gkLocationEnabled ? state.kmPost.gkLocationX : ''}
                                disabled={!fieldsEnabled}
                                onChange={(e) => updateProp('gkLocationX', e.target.value)}
                                onBlur={() => {
                                    stateActions.onCommitField('gkLocationX');
                                }}
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
                warnings={[
                    ...getVisibleWarningsByProp('gkLocationY'),
                    ...getVisibleWarningsByProp('gkLocationX'),
                ].filter(filterUnique)}
            />
            <FieldLayout
                label={`${t('km-post-dialog.gk-location.confirmed-field')} *`}
                disabled={!fieldsEnabled}
                value={
                    <div className={styles['km-post-edit-dialog__confirmed']}>
                        <Radio
                            checked={gkLocationEnabled && state.kmPost.gkLocationConfirmed}
                            onChange={() => updateProp('gkLocationConfirmed', true)}
                            disabled={!fieldsEnabled}>
                            {t('km-post-dialog.gk-location.confirmed')}
                        </Radio>
                        <Radio
                            checked={!gkLocationEnabled || !state.kmPost.gkLocationConfirmed}
                            onChange={() => updateProp('gkLocationConfirmed', false)}
                            disabled={!fieldsEnabled}>
                            {t('km-post-dialog.gk-location.not-confirmed')}
                        </Radio>
                    </div>
                }
            />
            <FieldLayout
                label={t('km-post-dialog.gk-location.source')}
                disabled={!fieldsEnabled}
                value={t(
                    gkLocationSourceTranslationKey(
                        gkLocationSource(state),
                        gkLocationEnabled,
                        editType,
                    ),
                )}
            />
            <FieldLayout
                label={t('km-post-dialog.gk-location.location-in-layout')}
                disabled={!fieldsEnabled}
                value={layoutLocationString()}
            />
        </>
    );
};
