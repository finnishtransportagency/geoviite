import * as React from 'react';
import { createClassName } from 'vayla-design-lib/utils';
import styles from 'map/map.module.scss';
import { IconColor, IconComponent } from 'vayla-design-lib/icon/Icon';

type MapToolButtonProps = {
    isActive: boolean;
    setActive: () => void;
    icon: IconComponent;
};

export const MapToolButton = ({
    isActive,
    setActive,
    icon: IconComponent,
}: MapToolButtonProps): React.JSX.Element => {
    return (
        <li
            onClick={() => setActive()}
            className={createClassName(
                styles['map__map-tool'],
                isActive && styles['map__map-tool--active'],
            )}>
            <IconComponent color={IconColor.INHERIT} />;
        </li>
    );
};
