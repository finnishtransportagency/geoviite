import * as React from 'react';
import { Map, MapLayer } from 'map/map-model';
import { Switch } from 'vayla-design-lib/switch/switch';
import styles from './map-layer-settings.scss';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { useTranslation } from 'react-i18next';
import { EnvRestricted } from 'environment/env-restricted';
import { useCommonDataAppSelector } from 'store/hooks';

export type MapLayersSettingsProps = {
    map: Map;
    onLayerVisibilityChange: (layerId: string, visible: boolean) => void;
    onReferenceLineVisibilityChange: (layerId: string, visible: boolean) => void;
    onMissingVerticalGeometryVisibilityChange: (layerId: string, visible: boolean) => void;
    onSegmentsFromSelectedPlanVisibilityChange: (layerId: string, visible: boolean) => void;
    onMissingLinkingVisibilityChange: (layerId: string, visible: boolean) => void;
    onDuplicateTrackVisibilityChange: (layerId: string, visible: boolean) => void;
    onClose?: () => void;
};

export type LayerVisibilitySettingProps = {
    visible: boolean;
    name: string;
    disabled?: boolean;
    indented?: boolean;
    onVisibilityChange: (visible: boolean) => void;
};

function isTrackLayoutLayer(layer: MapLayer): boolean {
    switch (layer.type) {
        case 'alignment':
        case 'tile':
        case 'kmPosts':
        case 'switches':
        case 'trackNumberDiagram':
            return true;
        default:
            return false;
    }
}

function showManualSwitchLinkingForWriteRoleOnly(
    layer: MapLayer,
    userHasWriteRole: boolean,
): boolean {
    return userHasWriteRole || layer.type != 'manualSwitchLinking';
}

const debugLayers = ['debug', 'debug1mPoints'];
const isDebugLayer = (layer: MapLayer) => debugLayers.includes(layer.id);

const LayerVisibilitySetting: React.FC<LayerVisibilitySettingProps> = (
    props: LayerVisibilitySettingProps,
) => {
    const [hover, setHover] = React.useState(false);
    return (
        <label
            className={`${styles['layer-visibility-setting']} ${
                props.indented ? styles['layer-visibility-setting--indented'] : ''
            }`}
            onMouseEnter={() => setHover(true)}
            onMouseLeave={() => setHover(false)}>
            <Switch
                checked={props.visible}
                onCheckedChange={props.onVisibilityChange}
                hover={hover}
                disabled={props.disabled}
            />
            <span className={styles['layer-visibility-setting__label']}>{props.name}</span>
        </label>
    );
};

export const MapLayersSettings: React.FC<MapLayersSettingsProps> = (
    props: MapLayersSettingsProps,
) => {
    const { t } = useTranslation();
    const userHasWriteRole = useCommonDataAppSelector((state) => state.userHasWriteRole);

    return (
        <div className={styles['map-layer-settings']}>
            <span className={styles['map-layer-settings__close-button']}>
                <Button
                    variant={ButtonVariant.GHOST}
                    icon={Icons.Close}
                    onClick={() => props.onClose && props.onClose()}
                />
            </span>
            <div className={styles['map-layer-settings__title']}>
                {t('map-layer-settings.layout')}
            </div>
            {props.map.mapLayers
                .filter((layer) => isTrackLayoutLayer(layer) && !isDebugLayer(layer))
                .map((layer) => {
                    return (
                        <React.Fragment key={layer.id}>
                            <LayerVisibilitySetting
                                name={layer.name}
                                visible={layer.visible}
                                onVisibilityChange={(visible) =>
                                    props.onLayerVisibilityChange(layer.id, visible)
                                }
                            />

                            {layer.type === 'alignment' && (
                                <>
                                    <LayerVisibilitySetting
                                        name={t('map-layer-settings.reference-lines')}
                                        visible={layer.showReferenceLines}
                                        disabled={!layer.visible}
                                        indented={true}
                                        onVisibilityChange={(visible) =>
                                            props.onReferenceLineVisibilityChange(layer.id, visible)
                                        }
                                    />
                                    <LayerVisibilitySetting
                                        name={t('map-layer-settings.missing-vertical-geometry')}
                                        visible={layer.showMissingVerticalGeometry}
                                        disabled={!layer.visible}
                                        indented={true}
                                        onVisibilityChange={(visible) => {
                                            props.onMissingVerticalGeometryVisibilityChange(
                                                layer.id,
                                                visible,
                                            );
                                        }}
                                    />
                                    {/*<LayerVisibilitySetting
                                        name={t('map-layer-settings.segments-from-plan')}
                                        visible={layer.showSegmentsFromSelectedPlan}
                                        disabled={!layer.visible}
                                        indented={true}
                                        onVisibilityChange={(visible) => {
                                            props.onSegmentsFromSelectedPlanVisibilityChange(
                                                layer.id,
                                                visible,
                                            );
                                        }}
                                    />*/}
                                    <LayerVisibilitySetting
                                        name={t('map-layer-settings.missing-linking')}
                                        visible={layer.showMissingLinking}
                                        disabled={!layer.visible}
                                        indented={true}
                                        onVisibilityChange={(visible) => {
                                            props.onMissingLinkingVisibilityChange(
                                                layer.id,
                                                visible,
                                            );
                                        }}
                                    />
                                    <LayerVisibilitySetting
                                        name={t('map-layer-settings.duplicate-tracks')}
                                        visible={layer.showDuplicateTracks}
                                        disabled={!layer.visible}
                                        indented={true}
                                        onVisibilityChange={(visible) => {
                                            props.onDuplicateTrackVisibilityChange(
                                                layer.id,
                                                visible,
                                            );
                                        }}
                                    />
                                </>
                            )}
                        </React.Fragment>
                    );
                })}
            <div className={styles['map-layer-settings__title']}>
                {t('map-layer-settings.geometry')}
            </div>
            {props.map.mapLayers
                .filter(
                    (layer) =>
                        !isTrackLayoutLayer(layer) &&
                        !isDebugLayer(layer) &&
                        showManualSwitchLinkingForWriteRoleOnly(layer, userHasWriteRole),
                )
                .map((layer) => {
                    return (
                        <LayerVisibilitySetting
                            key={layer.id}
                            name={layer.name}
                            visible={layer.visible}
                            onVisibilityChange={(visible) =>
                                props.onLayerVisibilityChange(layer.id, visible)
                            }
                        />
                    );
                })}
            <EnvRestricted restrictTo="dev">
                <div className={styles['map-layer-settings__title']}>
                    {t('map-layer-settings.debug')}
                </div>
                {props.map.mapLayers
                    .filter((layer) => isDebugLayer(layer))
                    .map((layer) => {
                        return (
                            <LayerVisibilitySetting
                                key={layer.id}
                                name={layer.name}
                                visible={layer.visible}
                                onVisibilityChange={(visible) =>
                                    props.onLayerVisibilityChange(layer.id, visible)
                                }
                            />
                        );
                    })}{' '}
            </EnvRestricted>
        </div>
    );
};
