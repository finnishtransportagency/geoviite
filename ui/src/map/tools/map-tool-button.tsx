import * as React from 'react';
import styles from 'map/map.module.scss';
import { IconColor, IconComponent, IconSize } from 'vayla-design-lib/icon/Icon';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';

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
        <Button
            variant={ButtonVariant.GHOST}
            size={ButtonSize.BY_CONTENT}
            onClick={setActive}
            isPressed={isActive}>
            <div className={styles['map-tool-button-content']}>
                <div className={styles['map-tool-button-content__icon']}>
                    <IconComponent color={IconColor.INHERIT} size={IconSize.INHERIT} />
                </div>
            </div>
        </Button>
    );
};
