import * as React from 'react';
import {
    MapLayerMenuChange,
    MapLayerMenuGroups,
    MapLayerMenuItem,
    MapLayerName,
} from 'map/map-model';
import { Switch } from 'vayla-design-lib/switch/switch';
import styles from './map-layer-menu.scss';
import { Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { useTranslation } from 'react-i18next';
import { CloseableModal } from 'vayla-design-lib/closeable-modal/closeable-modal';
import {
    isLayerInProxyLayerCollection,
    layersToHideByProxy,
    layersToShowByProxy,
    menuContainsMapLayer,
} from 'map/map-store';
import { VIEW_DEBUG_LAYERS, VIEW_GEOMETRY } from 'user/user-model';
import { PrivilegeRequired } from 'user/privilege-required';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { useUserHasPrivilege } from 'store/hooks';
import { useEnvironmentInfo } from 'environment/environment-info';

type MapLayerMenuProps = {
    onMenuChange: (change: MapLayerMenuChange) => void;
    onClose?: () => void;
    mapLayerMenuGroups: MapLayerMenuGroups;
    visibleLayers: MapLayerName[];
    forcedVisibleLayers?: MapLayerName[];
    forcedHiddenLayers?: MapLayerName[];
};

type MapLayerProps = {
    label: string;
    visible: boolean;
    onChange: () => void;
    disabled?: boolean;
    indented?: boolean;
    qaId: string;
};

type MapLayerGroupProps = {
    title: string;
    menuItemVisibilities: MapLayerMenuItem[];
    mapLayerVisibilities: MapLayerName[];
    onMenuChange: (change: MapLayerMenuChange) => void;
    forcedVisibleLayers?: MapLayerName[];
    forcedHiddenLayers?: MapLayerName[];
};

const MapLayer: React.FC<MapLayerProps> = ({
    label,
    visible,
    onChange,
    disabled = false,
    indented = false,
    qaId,
}) => {
    const [hover, setHover] = React.useState(false);
    return (
        <label
            className={`${styles['map-layer-menu__layer-visibility']} ${
                indented ? styles['map-layer-menu__layer-visibility--indented'] : ''
            }`}
            qa-id={qaId}
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
    forcedVisibleLayers = [],
    forcedHiddenLayers = [],
}) => {
    const { t } = useTranslation();
    return (
        <React.Fragment>
            <div className={styles['map-layer-menu__title']}>{title}</div>
            {menuItemVisibilities.flatMap((layerMenuItem) => {
                const isForcedVisible = menuContainsMapLayer(layerMenuItem, forcedVisibleLayers);
                const isForcedHidden = menuContainsMapLayer(layerMenuItem, forcedHiddenLayers);
                const isMapLayerVisibilityForced = isForcedVisible || isForcedHidden;
                const enabledByProxy = isLayerInProxyLayerCollection(
                    layerMenuItem.name,
                    mapLayerVisibilities,
                    layersToShowByProxy,
                );
                const disabledByProxy = isLayerInProxyLayerCollection(
                    layerMenuItem.name,
                    mapLayerVisibilities,
                    layersToHideByProxy,
                );
                return [
                    <MapLayer
                        key={layerMenuItem.name}
                        qaId={`layer-menu-item-${layerMenuItem.name}`}
                        label={t(`map-layer-menu.${layerMenuItem.name}`)}
                        visible={
                            (enabledByProxy || layerMenuItem.selected || isForcedVisible) &&
                            !disabledByProxy &&
                            !isForcedHidden
                        }
                        disabled={isMapLayerVisibilityForced || enabledByProxy || disabledByProxy}
                        onChange={() =>
                            onMenuChange({
                                name: layerMenuItem.name,
                                selected: !layerMenuItem.selected,
                            })
                        }
                    />,
                    layerMenuItem.subMenu?.map((subMenuItem) => {
                        const isForcedVisible = menuContainsMapLayer(
                            subMenuItem,
                            forcedVisibleLayers,
                        );
                        const isForcedHidden = menuContainsMapLayer(
                            subMenuItem,
                            forcedHiddenLayers,
                        );
                        const isMapLayerVisibilityForced = isForcedVisible || isForcedHidden;
                        const enabledByProxy = isLayerInProxyLayerCollection(
                            subMenuItem.name,
                            mapLayerVisibilities,
                            layersToShowByProxy,
                        );
                        const disabledByProxy = isLayerInProxyLayerCollection(
                            layerMenuItem.name,
                            mapLayerVisibilities,
                            layersToHideByProxy,
                        );
                        return (
                            <MapLayer
                                key={subMenuItem.name}
                                qaId={`layer-menu-item-${subMenuItem.name}`}
                                label={t(`map-layer-menu.${subMenuItem.name}`)}
                                visible={
                                    (enabledByProxy || subMenuItem.selected || isForcedVisible) &&
                                    !disabledByProxy &&
                                    !isForcedHidden
                                }
                                disabled={
                                    isMapLayerVisibilityForced ||
                                    enabledByProxy ||
                                    disabledByProxy ||
                                    !layerMenuItem.selected
                                }
                                indented={true}
                                onChange={() =>
                                    onMenuChange({
                                        name: subMenuItem.name,
                                        selected: !subMenuItem.selected,
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

function useShouldShowDebugLayers() {
    const userHasDebugLayersPrivilege = useUserHasPrivilege(VIEW_DEBUG_LAYERS);
    const environmentName = useEnvironmentInfo()?.environmentName;

    return userHasDebugLayersPrivilege || environmentName === 'dev' || environmentName === 'local';
}

const MapLayerMenuM: React.FC<MapLayerMenuProps> = ({
    mapLayerMenuGroups,
    onMenuChange,
    visibleLayers,
    forcedVisibleLayers = [],
    forcedHiddenLayers = [],
}: MapLayerMenuProps) => {
    const { t } = useTranslation();
    const [showMapLayerMenu, setShowMapLayerMenu] = React.useState(false);

    const buttonRef = React.useRef(null);

    const showDebugLayers = useShouldShowDebugLayers();

    return (
        <React.Fragment>
            <div ref={buttonRef}>
                <Button
                    className={styles['map-layers-button']}
                    qa-id="map-layers-button"
                    title={t('map-layer-menu.title')}
                    variant={ButtonVariant.GHOST}
                    size={ButtonSize.BY_CONTENT}
                    icon={Icons.Layers}
                    iconProps={{ size: IconSize.INHERIT, extraClassName: 'map-layers-button-icon' }}
                    onClick={() => setShowMapLayerMenu(!showMapLayerMenu)}
                />
            </div>
            {showMapLayerMenu && (
                <CloseableModal
                    className={styles['map-layer-menu']}
                    anchorElementRef={buttonRef}
                    onClickOutside={() => setShowMapLayerMenu(false)}
                    allowReposition={false}>
                    <MapLayerGroup
                        title={t('map-layer-menu.layout-title')}
                        menuItemVisibilities={mapLayerMenuGroups.layout}
                        onMenuChange={onMenuChange}
                        mapLayerVisibilities={visibleLayers}
                        forcedVisibleLayers={forcedVisibleLayers}
                        forcedHiddenLayers={forcedHiddenLayers}
                    />
                    <PrivilegeRequired privilege={VIEW_GEOMETRY}>
                        <MapLayerGroup
                            title={t('map-layer-menu.geometry-title')}
                            menuItemVisibilities={mapLayerMenuGroups.geometry}
                            onMenuChange={onMenuChange}
                            mapLayerVisibilities={visibleLayers}
                            forcedVisibleLayers={forcedVisibleLayers}
                            forcedHiddenLayers={forcedHiddenLayers}
                        />
                    </PrivilegeRequired>
                    {showDebugLayers && (
                        <MapLayerGroup
                            title={t('map-layer-menu.debug-title')}
                            menuItemVisibilities={mapLayerMenuGroups.debug}
                            onMenuChange={onMenuChange}
                            mapLayerVisibilities={visibleLayers}
                            forcedVisibleLayers={forcedVisibleLayers}
                            forcedHiddenLayers={forcedHiddenLayers}
                        />
                    )}
                </CloseableModal>
            )}
        </React.Fragment>
    );
};

export const MapLayerMenu = React.memo(MapLayerMenuM);
