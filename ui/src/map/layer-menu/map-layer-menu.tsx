import * as React from 'react';
import {
    MapLayerMenuChange,
    MapLayerMenuGroups,
    MapLayerMenuItem,
    MapLayerName,
} from 'map/map-model';
import { Switch } from 'vayla-design-lib/switch/switch';
import styles from './map-layer-menu.scss';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { useTranslation } from 'react-i18next';
import { EnvRestricted } from 'environment/env-restricted';
import { CloseableModal } from 'vayla-design-lib/closeable-modal/closeable-modal';
import {
    isLayerInProxyLayerCollection,
    layersToHideByProxy,
    relatedMapLayers,
} from 'map/map-store';
import { VIEW_GEOMETRY } from 'user/user-model';
import { PrivilegeRequired } from 'user/privilege-required';

type MapLayerMenuProps = {
    onMenuChange: (change: MapLayerMenuChange) => void;
    onClose?: () => void;
    mapLayerMenuGroups: MapLayerMenuGroups;
    visibleLayers: MapLayerName[];
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
    menuItemVisibilities: MapLayerMenuItem[];
    mapLayerVisibilities: MapLayerName[];
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

const MapLayerGroup: React.FC<MapLayerGroupProps> = ({
    title,
    menuItemVisibilities,
    mapLayerVisibilities,
    onMenuChange,
}) => {
    const { t } = useTranslation();
    return (
        <React.Fragment>
            <div className={styles['map-layer-menu__title']}>{title}</div>
            {menuItemVisibilities.flatMap((setting) => {
                const enabledByProxy = isLayerInProxyLayerCollection(
                    setting.name,
                    mapLayerVisibilities,
                    relatedMapLayers,
                );
                const disabledByProxy = isLayerInProxyLayerCollection(
                    setting.name,
                    mapLayerVisibilities,
                    layersToHideByProxy,
                );
                return [
                    <MapLayer
                        key={setting.name}
                        qa-id={setting.qaId}
                        label={t(`map-layer-menu.${setting.name}`)}
                        visible={(enabledByProxy || setting.visible) && !disabledByProxy}
                        disabled={enabledByProxy || disabledByProxy}
                        onChange={() =>
                            onMenuChange({
                                name: setting.name,
                                visible: !setting.visible,
                            })
                        }
                    />,
                    setting.subMenu?.map((subSetting) => {
                        const enabledByProxy = isLayerInProxyLayerCollection(
                            subSetting.name,
                            mapLayerVisibilities,
                            relatedMapLayers,
                        );
                        const disabledByProxy = isLayerInProxyLayerCollection(
                            setting.name,
                            mapLayerVisibilities,
                            layersToHideByProxy,
                        );
                        return (
                            <MapLayer
                                key={subSetting.name}
                                qa-id={setting.qaId}
                                label={t(`map-layer-menu.${subSetting.name}`)}
                                visible={(enabledByProxy || subSetting.visible) && !disabledByProxy}
                                disabled={enabledByProxy || disabledByProxy || !setting.visible}
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

//Aligns map layer menu with the map
const layerOffset = 48;

export const MapLayerMenu: React.FC<MapLayerMenuProps> = ({
    mapLayerMenuGroups,
    onMenuChange,
    visibleLayers,
}: MapLayerMenuProps) => {
    const { t } = useTranslation();
    const [showMapLayerMenu, setShowMapLayerMenu] = React.useState(false);

    const buttonRef = React.useRef(null);

    return (
        <React.Fragment>
            <div ref={buttonRef}>
                <Button
                    variant={ButtonVariant.SECONDARY}
                    icon={Icons.Layers}
                    size={ButtonSize.X_SMALL}
                    title={t('map-layer-menu.title')}
                    onClick={() => setShowMapLayerMenu(!showMapLayerMenu)}
                    qa-id="map-layers-button"
                />
            </div>
            {showMapLayerMenu && (
                <CloseableModal
                    offsetY={layerOffset}
                    className={styles['map-layer-menu']}
                    positionRef={buttonRef}
                    onClickOutside={() => setShowMapLayerMenu(false)}>
                    <MapLayerGroup
                        title={t('map-layer-menu.layout-title')}
                        menuItemVisibilities={mapLayerMenuGroups.layout}
                        onMenuChange={onMenuChange}
                        mapLayerVisibilities={visibleLayers}
                    />
                    <PrivilegeRequired privilege={VIEW_GEOMETRY}>
                        <MapLayerGroup
                            title={t('map-layer-menu.geometry-title')}
                            menuItemVisibilities={mapLayerMenuGroups.geometry}
                            onMenuChange={onMenuChange}
                            mapLayerVisibilities={visibleLayers}
                        />
                    </PrivilegeRequired>
                    <EnvRestricted restrictTo="dev">
                        <MapLayerGroup
                            title={t('map-layer-menu.debug-title')}
                            menuItemVisibilities={mapLayerMenuGroups.debug}
                            onMenuChange={onMenuChange}
                            mapLayerVisibilities={visibleLayers}
                        />
                    </EnvRestricted>
                </CloseableModal>
            )}
        </React.Fragment>
    );
};
