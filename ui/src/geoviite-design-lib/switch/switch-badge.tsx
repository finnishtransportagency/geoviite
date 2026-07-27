import * as React from 'react';
import styles from './switch-badge.scss';
import { LayoutSwitch, LayoutSwitchId } from 'track-layout/track-layout-model';
import { createClassName } from 'vayla-design-lib/utils';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { LayoutContext, TimeStamp } from 'common/common-model';
import { useSwitch } from 'track-layout/track-layout-react-utils';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { createDelegates } from 'store/store-utils';
import { Spinner } from 'vayla-design-lib/spinner/spinner';

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

type SwitchBadgeLinkProps = {
    switchId: LayoutSwitchId;
    layoutContext: LayoutContext;
    changeTime: TimeStamp;
    onClick?: (switchId: LayoutSwitchId) => void;
    status?: SwitchBadgeStatus;
};

export const SwitchBadgeLink: React.FC<SwitchBadgeLinkProps> = ({
    switchId,
    layoutContext,
    changeTime,
    onClick,
    status,
}: SwitchBadgeLinkProps) => {
    const switchItem = useSwitch(switchId, layoutContext, changeTime);

    const clickAction = React.useCallback(() => {
        if (onClick) {
            onClick(switchId);
        } else {
            const delegates = createDelegates(TrackLayoutActions);
            delegates.onSelect({
                switches: [switchId],
                selectedTab: { id: switchId, type: 'SWITCH' },
            });
        }
    }, [onClick, switchId]);

    return switchItem ? (
        <SwitchBadge switchItem={switchItem} onClick={clickAction} status={status} />
    ) : (
        <Spinner />
    );
};
