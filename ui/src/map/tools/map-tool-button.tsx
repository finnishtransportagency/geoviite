import * as React from 'react';
import styles from 'map/map.module.scss';
import { IconColor, IconComponent, IconSize } from 'vayla-design-lib/icon/Icon';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { createClassName } from 'vayla-design-lib/utils';

type MapToolButtonProps = {
    isActive: boolean;
    setActive: () => void;
    icon: IconComponent;
    disabled?: boolean;
};

export const MapToolButton = ({
    isActive,
    setActive,
    icon: IconComponent,
    disabled,
}: MapToolButtonProps): React.JSX.Element => {
    const iconClassName = createClassName(
        styles['map-tool-button-content__icon'],
        disabled && styles['map-tool-button-content__icon--disabled'],
    );
    return (
        <Button
            variant={ButtonVariant.GHOST}
            size={ButtonSize.BY_CONTENT}
            onClick={setActive}
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
