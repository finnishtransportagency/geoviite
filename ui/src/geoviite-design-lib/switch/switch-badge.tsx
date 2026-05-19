import * as React from 'react';
import styles from './switch-badge.scss';
import { LayoutSwitch } from 'track-layout/track-layout-model';
import { createClassName } from 'vayla-design-lib/utils';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';

type SwitchBadgeProps = {
    switchItem: LayoutSwitch;
    switchIsValid?: boolean;
    onClick?: React.MouseEventHandler;
    status?: SwitchBadgeStatus;
};

export enum SwitchBadgeStatus {
    DEFAULT = 'switch-badge--default',
    LINKED = 'switch-badge--linked',
    UNLINKED = 'switch-badge--unlinked',
    SELECTED = 'switch-badge--selected',
    DISABLED = 'switch-badge--disabled',
}

export const SwitchBadge: React.FC<SwitchBadgeProps> = ({
    switchItem,
    onClick,
    switchIsValid = true,
    status = SwitchBadgeStatus.DEFAULT,
}: SwitchBadgeProps) => {
    const disabled = status === SwitchBadgeStatus.DISABLED;

    const classes = createClassName(
        styles['switch-badge'],
        status,
        !switchIsValid && styles['switch-badge--invalid'],
        !disabled && onClick && styles['switch-badge--clickable'],
    );
    return (
        <span className={classes} onClick={!disabled ? onClick : undefined}>
            <Icons.Switch size={IconSize.SMALL} color={IconColor.INHERIT} />
            <span>{switchItem.name}</span>
        </span>
    );
};
