import * as React from 'react';
import { Map, MapLayer } from 'map/map-model';
import { Switch } from 'vayla-design-lib/switch/switch';
import styles from './map-layer-settings.scss';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { useTranslation } from 'react-i18next';
import { EnvRestricted } from 'environment/env-restricted';

export type MapLayersSettingsProps = {
    map: Map;
    onLayerVisibilityChange: (layerId: string, visible: boolean) => void;
    onTrackNumberVisibilityChange: (layerId: string, visible: boolean) => void;
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
            return true;
        default:
            return false;
    }
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

    return (
        <div className={styles['map-layer-settings']}>
            <Button
                variant={ButtonVariant.GHOST}
                icon={Icons.Close}
                onClick={() => props.onClose && props.onClose()}
            />
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
                                <LayerVisibilitySetting
                                    name="Ratanumerot"
                                    visible={layer.showTrackNumbers}
                                    disabled={!layer.visible}
                                    indented={true}
                                    onVisibilityChange={(visible) =>
                                        props.onTrackNumberVisibilityChange(layer.id, visible)
                                    }
                                />
                            )}
                        </React.Fragment>
                    );
                })}
            <div className={styles['map-layer-settings__title']}>
                {t('map-layer-settings.geometry')}
            </div>
            {props.map.mapLayers
                .filter((layer) => !isTrackLayoutLayer(layer) && !isDebugLayer(layer))
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
