import * as React from 'react';
import { Map, MapLayerMenuChange, MapLayerMenuItem } from 'map/map-model';
import { Switch } from 'vayla-design-lib/switch/switch';
import styles from './map-layer-menu.scss';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { useTranslation } from 'react-i18next';
import { EnvRestricted } from 'environment/env-restricted';

type MapLayerMenuProps = {
    map: Map;
    onMenuChange: (change: MapLayerMenuChange) => void;
    onClose?: () => void;
};

type MapLayerProps = {
    label: string;
    visible: boolean;
    onChange: () => void;
    disabled?: boolean;
    indented?: boolean;
};

type MapLayerGroupProps = {
    title: string;
    visibilities: MapLayerMenuItem[];
    onMenuChange: (change: MapLayerMenuChange) => void;
};

const MapLayer: React.FC<MapLayerProps> = ({
    label,
    visible,
    onChange,
    disabled = false,
    indented = false,
}) => {
    const [hover, setHover] = React.useState(false);
    return (
        <label
            className={`${styles['map-layer-menu__layer-visibility']} ${
                indented ? styles['map-layer-menu__layer-visibility--indented'] : ''
            }`}
            onMouseEnter={() => setHover(true)}
            onMouseLeave={() => setHover(false)}>
            <Switch
                checked={visible}
                onCheckedChange={onChange}
                hover={hover}
                disabled={disabled}
            />
            <span className={styles['map-layer-menu__label']}>{label}</span>
        </label>
    );
};

const MapLayerGroup: React.FC<MapLayerGroupProps> = ({ title, visibilities, onMenuChange }) => {
    const { t } = useTranslation();
    return (
        <React.Fragment>
            <div className={styles['map-layer-menu__title']}>{title}</div>
            {visibilities.flatMap((setting) => {
                return [
                    <MapLayer
                        key={setting.name}
                        label={t(`map-layer-menu.${setting.name}`)}
                        visible={setting.visible}
                        onChange={() =>
                            onMenuChange({
                                name: setting.name,
                                visible: !setting.visible,
                            })
                        }
                    />,
                    setting.subMenu?.map((subSetting) => {
                        return (
                            <MapLayer
                                key={subSetting.name}
                                label={t(`map-layer-menu.${subSetting.name}`)}
                                visible={subSetting.visible}
                                disabled={!setting.visible}
                                indented={true}
                                onChange={() =>
                                    onMenuChange({
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

export const MapLayerMenu: React.FC<MapLayerMenuProps> = ({
    onClose,
    map,
    onMenuChange,
}: MapLayerMenuProps) => {
    const { t } = useTranslation();

    return (
        <div className={styles['map-layer-menu']}>
            <span className={styles['map-layer-menu__close-button']}>
                <Button
                    variant={ButtonVariant.GHOST}
                    icon={Icons.Close}
                    onClick={() => onClose && onClose()}
                />
            </span>

            <MapLayerGroup
                title={t('map-layer-menu.layout-title')}
                visibilities={map.layerMenu.layout}
                onMenuChange={onMenuChange}
            />
            <MapLayerGroup
                title={t('map-layer-menu.geometry-title')}
                visibilities={map.layerMenu.geometry}
                onMenuChange={onMenuChange}
            />
            <EnvRestricted restrictTo="dev">
                <MapLayerGroup
                    title={t('map-layer-menu.debug-title')}
                    visibilities={map.layerMenu.debug}
                    onMenuChange={onMenuChange}
                />
            </EnvRestricted>
        </div>
    );
};
