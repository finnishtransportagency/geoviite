import * as React from 'react';
import styles from './menu.scss';
import { CloseableModal, OpenTowards } from 'vayla-design-lib/closeable-modal/closeable-modal';
import { createClassName } from 'vayla-design-lib/utils';

export type MenuOption<TValue> = MenuValueOption<TValue> | MenuSelectOption | MenuDividerOption;

type MenuOptionBase = { disabled: boolean; qaId: string };

export type MenuValueOption<TValue> = {
    type: 'VALUE';
    name: string;
    value: TValue;
} & MenuOptionBase;

export type MenuSelectOption = {
    type: 'SELECT';
    name: string;
    onSelect: () => void;
} & MenuOptionBase;

export type MenuDividerOption = {
    type: 'DIVIDER';
};

export const menuValueOption = <TValue,>(
    value: TValue,
    name: string,
    qaId: string,
    disabled: boolean = false,
): MenuValueOption<TValue> => ({
    type: 'VALUE',
    name,
    value,
    disabled,
    qaId,
});

export const menuSelectOption = (
    onSelect: () => void,
    name: string,
    qaId: string,
    disabled: boolean = false,
): MenuSelectOption => ({
    type: 'SELECT',
    onSelect,
    name,
    disabled,
    qaId,
});

export const menuDividerOption = (): MenuDividerOption => ({
    type: 'DIVIDER',
});

type MenuProps<TValue> = {
    positionRef: React.MutableRefObject<HTMLElement | null>;
    onClickOutside: () => void;
    onSelect?: (item: TValue) => void;
    items: MenuOption<TValue>[];
    opensTowards?: OpenTowards;
} & Omit<React.HTMLAttributes<HTMLElement>, 'onSelect'>;

export const Menu = function <TValue>({
    positionRef,
    onClickOutside,
    items,
    onSelect,
    className,
    opensTowards = 'RIGHT',
    ...props
}: MenuProps<TValue>) {
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
                                    if (!i.disabled) {
                                        if (i.type === 'SELECT') i.onSelect();
                                        else if (onSelect) onSelect(i.value);
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
