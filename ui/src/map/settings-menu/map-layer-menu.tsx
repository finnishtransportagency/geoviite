import * as React from 'react';
import { Map, MapLayerSetting } from 'map/map-model';
import { Switch } from 'vayla-design-lib/switch/switch';
import styles from './map-layer-menu.scss';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { useTranslation } from 'react-i18next';
import { EnvRestricted } from 'environment/env-restricted';
import { MapLayerSettingChange } from 'map/map-store';

type MapLayersSettingMenuProps = {
    map: Map;
    onSettingChange: (change: MapLayerSettingChange) => void;
    onClose?: () => void;
};

type LayerSettingProps = {
    label: string;
    visible: boolean;
    onChange: () => void;
    disabled?: boolean;
    indented?: boolean;
};

type MapLayerSettingProps = {
    title: string;
    settings: MapLayerSetting[];
    onSettingChange: (change: MapLayerSettingChange) => void;
};

const LayerVisibilitySetting: React.FC<LayerSettingProps> = ({
    label,
    visible,
    onChange,
    disabled = false,
    indented = false,
}) => {
    const [hover, setHover] = React.useState(false);
    return (
        <label
            className={`${styles['layer-visibility-setting']} ${
                indented ? styles['layer-visibility-setting--indented'] : ''
            }`}
            onMouseEnter={() => setHover(true)}
            onMouseLeave={() => setHover(false)}>
            <Switch
                checked={visible}
                onCheckedChange={onChange}
                hover={hover}
                disabled={disabled}
            />
            <span className={styles['layer-visibility-setting__label']}>{label}</span>
        </label>
    );
};

const MapLayerSettings: React.FC<MapLayerSettingProps> = ({ title, settings, onSettingChange }) => {
    const { t } = useTranslation();
    return (
        <React.Fragment>
            <div className={styles['map-layer-settings__title']}>{title}</div>
            {settings.flatMap((setting) => {
                return [
                    <LayerVisibilitySetting
                        key={setting.name}
                        label={t(`map-layer-settings.${setting.name}`)}
                        visible={setting.visible}
                        onChange={() =>
                            onSettingChange({
                                name: setting.name,
                                visible: !setting.visible,
                            })
                        }
                    />,
                    setting.subSettings?.map((subSetting) => {
                        return (
                            <LayerVisibilitySetting
                                key={subSetting.name}
                                label={t(`map-layer-settings.${subSetting.name}`)}
                                visible={subSetting.visible}
                                disabled={!setting.visible}
                                indented={true}
                                onChange={() =>
                                    onSettingChange({
                                        name: subSetting.name,
                                        visible: !subSetting.visible,
                                    })
                                }
                            />
                        );
                    }),
                ];
            })}
        </React.Fragment>
    );
};

export const MapLayerSettingsMenu: React.FC<MapLayersSettingMenuProps> = (
    props: MapLayersSettingMenuProps,
) => {
    const { t } = useTranslation();

    return (
        <div className={styles['map-layer-settings']}>
            <span className={styles['map-layer-settings__close-button']}>
                <Button
                    variant={ButtonVariant.GHOST}
                    icon={Icons.Close}
                    onClick={() => props.onClose && props.onClose()}
                />
            </span>

            <MapLayerSettings
                title={t('map-layer-settings.layout-title')}
                settings={props.map.settingsMenu.layout}
                onSettingChange={props.onSettingChange}
            />
            <MapLayerSettings
                title={t('map-layer-settings.geometry-title')}
                settings={props.map.settingsMenu.geometry}
                onSettingChange={props.onSettingChange}
            />
            <EnvRestricted restrictTo="dev">
                <MapLayerSettings
                    title={t('map-layer-settings.debug-title')}
                    settings={props.map.settingsMenu.debug}
                    onSettingChange={props.onSettingChange}
                />
            </EnvRestricted>
        </div>
    );
};
