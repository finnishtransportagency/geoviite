import * as React from 'react';
import { MapLayerMenuChange, MapLayerMenuGroups, MapLayerMenuItem } from 'map/map-model';
import { Switch } from 'vayla-design-lib/switch/switch';
import styles from './map-layer-menu.scss';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { useTranslation } from 'react-i18next';
import { EnvRestricted } from 'environment/env-restricted';
import { CloseableModal } from 'vayla-design-lib/closeable-modal/closeable-modal';

type MapLayerMenuProps = {
    onMenuChange: (change: MapLayerMenuChange) => void;
    onClose?: () => void;
    mapLayerMenuGroups: MapLayerMenuGroups;
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

//Aligns map layer menu with the map
const layerOffset = 48;

export const MapLayerMenu: React.FC<MapLayerMenuProps> = ({
    mapLayerMenuGroups,
    onMenuChange,
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
                    onClickOutside={() => undefined}>
                    <span className={styles['map-layer-menu__close-button']}>
                        <Button
                            variant={ButtonVariant.GHOST}
                            icon={Icons.Close}
                            onClick={() => setShowMapLayerMenu(false)}
                        />
                    </span>

                    <MapLayerGroup
                        title={t('map-layer-menu.layout-title')}
                        visibilities={mapLayerMenuGroups.layout}
                        onMenuChange={onMenuChange}
                    />
                    <MapLayerGroup
                        title={t('map-layer-menu.geometry-title')}
                        visibilities={mapLayerMenuGroups.geometry}
                        onMenuChange={onMenuChange}
                    />
                    <EnvRestricted restrictTo="dev">
                        <MapLayerGroup
                            title={t('map-layer-menu.debug-title')}
                            visibilities={mapLayerMenuGroups.debug}
                            onMenuChange={onMenuChange}
                        />
                    </EnvRestricted>
                </CloseableModal>
            )}
        </React.Fragment>
    );
};
