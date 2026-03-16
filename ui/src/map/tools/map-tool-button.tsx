import * as React from 'react';
import styles from 'map/map.module.scss';
import { IconColor, IconComponent, IconSize } from 'vayla-design-lib/icon/Icon';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { createClassName } from 'vayla-design-lib/utils';
import { MapToolId } from 'map/tools/tool-model';

type MapToolButtonProps = {
    id: MapToolId;
    isActive: boolean;
    setActive: (id: MapToolId) => void;
    icon: IconComponent;
    disabled?: boolean;
    hidden?: boolean;
};

const MapToolButtonM = ({
    id,
    isActive,
    setActive,
    icon: IconComponent,
    disabled,
    hidden,
}: MapToolButtonProps): React.JSX.Element => {
    const iconClassName = createClassName(
        styles['map-tool-button-content__icon'],
        disabled && styles['map-tool-button-content__icon--disabled'],
    );
    return hidden ? (
        <React.Fragment />
    ) : (
        <Button
            variant={ButtonVariant.GHOST}
            size={ButtonSize.BY_CONTENT}
            onClick={() => setActive(id)}
            isPressed={isActive}
            disabled={disabled}>
            <div className={styles['map-tool-button-content']}>
                <div className={iconClassName}>
                    <IconComponent color={IconColor.INHERIT} size={IconSize.INHERIT} />
                </div>
            </div>
        </Button>
    );
};

export const MapToolButton = React.memo(MapToolButtonM);
