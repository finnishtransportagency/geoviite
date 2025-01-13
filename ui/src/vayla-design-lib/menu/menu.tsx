import * as React from 'react';
import styles from './menu.scss';
import { CloseableModal, OpenTowards } from 'vayla-design-lib/closeable-modal/closeable-modal';
import { createClassName } from 'vayla-design-lib/utils';
import { IconColor, IconComponent, IconSize } from 'vayla-design-lib/icon/Icon';

export type MenuOption = MenuSelectOption | MenuDividerOption;

export type OptionBase = { disabled: boolean; qaId: string };

type MenuClosingBehaviour = 'CLOSE_AFTER_SELECT' | 'CLOSE_MANUALLY';

export type MenuSelectOption = {
    type: 'SELECT';
    name: string;
    onSelect: () => void;
    closingBehaviour: MenuClosingBehaviour;
    icon: IconComponent | undefined;
} & OptionBase;

export type MenuDividerOption = {
    type: 'DIVIDER';
};

export function isMenuSelectOption(item: MenuOption): item is MenuSelectOption {
    return item.type === 'SELECT';
}

export const menuOption = (
    onSelect: () => void,
    name: string,
    qaId: string,
    disabled: boolean = false,
    closingBehaviour: MenuClosingBehaviour = 'CLOSE_AFTER_SELECT',
    icon: IconComponent | undefined = undefined,
): MenuSelectOption => ({
    type: 'SELECT',
    onSelect,
    name,
    disabled,
    closingBehaviour,
    qaId,
    icon,
});

export const menuDivider = (): MenuDividerOption => ({
    type: 'DIVIDER',
});

type MenuProps = {
    positionRef: React.MutableRefObject<HTMLElement | null>;
    onClickOutside: () => void;
    items: MenuOption[];
    opensTowards?: OpenTowards;
    onClose: () => void;
} & Omit<React.HTMLAttributes<HTMLElement>, 'onSelect'>;

export const Menu = function ({
    positionRef,
    onClickOutside,
    items,
    className,
    opensTowards = 'RIGHT',
    onClose,
    ...props
}: MenuProps) {
    const { height: offsetY } = positionRef.current?.getBoundingClientRect() ?? { height: 0 };

    const hasIcons = items.some((item) => isMenuSelectOption(item) && item.icon);

    const menuClassName = createClassName(
        styles['menu'],
        className,
        hasIcons && styles['menu--has-icons'],
    );

    return (
        <CloseableModal
            className={menuClassName}
            onClickOutside={onClickOutside}
            positionRef={positionRef}
            openTowards={opensTowards}
            offsetY={offsetY + 6}>
            <ol className={styles['menu__items']} {...props}>
                {items.map((item, index) => {
                    if (item.type === 'DIVIDER') {
                        return <div key={`${index}`} className={styles['menu__divider']} />;
                    } else {
                        return (
                            <li
                                key={`${index}`}
                                qa-id={item.qaId}
                                title={`${item.name}`}
                                className={createClassName(
                                    styles['menu__item'],
                                    item.disabled && styles['menu__item--disabled'],
                                )}
                                onClick={() => {
                                    if (!item.disabled && item.type === 'SELECT') {
                                        item.onSelect();
                                        if (item.closingBehaviour === 'CLOSE_AFTER_SELECT') {
                                            onClose();
                                        }
                                    }
                                }}>
                                {hasIcons && (
                                    <div className="menu__icon">
                                        {isMenuSelectOption(item) && item.icon && (
                                            <item.icon
                                                size={IconSize.SMALL}
                                                color={IconColor.INHERIT}
                                            />
                                        )}
                                    </div>
                                )}
                                {item.name}
                            </li>
                        );
                    }
                })}
            </ol>
        </CloseableModal>
    );
};
