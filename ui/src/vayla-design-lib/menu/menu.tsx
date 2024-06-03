import * as React from 'react';
import styles from './menu.scss';
import { CloseableModal, OpenTowards } from 'vayla-design-lib/closeable-modal/closeable-modal';
import { createClassName } from 'vayla-design-lib/utils';

export type MenuOption = MenuSelectOption | MenuDividerOption;

export type OptionBase = { disabled: boolean; qaId: string };

type MenuClosingBehaviour = 'CLOSE_AFTER_SELECT' | 'CLOSE_MANUALLY';

export type MenuSelectOption = {
    type: 'SELECT';
    name: string;
    onSelect: () => void;
    closingBehaviour: MenuClosingBehaviour;
} & OptionBase;

export type MenuDividerOption = {
    type: 'DIVIDER';
};

export const menuOption = (
    onSelect: () => void,
    name: string,
    qaId: string,
    disabled: boolean = false,
    closingBehaviour: MenuClosingBehaviour = 'CLOSE_AFTER_SELECT',
): MenuSelectOption => ({
    type: 'SELECT',
    onSelect,
    name,
    disabled,
    closingBehaviour,
    qaId,
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

    return (
        <CloseableModal
            className={createClassName(styles['menu'], className)}
            onClickOutside={onClickOutside}
            positionRef={positionRef}
            openTowards={opensTowards}
            offsetY={offsetY + 6}>
            <ol className={styles['menu__items']} {...props}>
                {items.map((i, index) => {
                    if (i.type === 'DIVIDER') {
                        return <div key={`${index}`} className={styles['menu__divider']} />;
                    } else {
                        return (
                            <li
                                key={`${index}`}
                                qa-id={i.qaId}
                                title={`${i.name}`}
                                className={createClassName(
                                    styles['menu__item'],
                                    i.disabled && styles['menu__item--disabled'],
                                )}
                                onClick={() => {
                                    if (!i.disabled && i.type === 'SELECT') {
                                        i.onSelect();
                                        if (i.closingBehaviour === 'CLOSE_AFTER_SELECT') {
                                            onClose();
                                        }
                                    }
                                }}>
                                {i.name}
                            </li>
                        );
                    }
                })}
            </ol>
        </CloseableModal>
    );
};
