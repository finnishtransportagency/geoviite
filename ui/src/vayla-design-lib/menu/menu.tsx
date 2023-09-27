import * as React from 'react';
import styles from './menu.scss';
import { CloseableModal } from 'vayla-design-lib/closeable-modal/closeable-modal';
import { createClassName } from 'vayla-design-lib/utils';

type MenuOption = { name: string | number; disabled?: boolean };

export type MenuValueOption<TValue> = {
    value: TValue;
    onSelect?: never;
} & MenuOption;

export type MenuSelectOption = {
    value?: never;
    onSelect: () => void;
} & MenuOption;

type MenuProps<TValue> = {
    positionRef: React.MutableRefObject<HTMLElement | null>;
    onClickOutside: () => void;
    onSelect?: (item: TValue) => void;
    items: (MenuValueOption<TValue> | MenuSelectOption)[];
} & Omit<React.HTMLAttributes<HTMLElement>, 'onSelect'>;

export const Menu = function <TValue>({
    positionRef,
    onClickOutside,
    items,
    onSelect,
    className,
    ...props
}: MenuProps<TValue>) {
    const { height: offsetY } = positionRef.current?.getBoundingClientRect() ?? { height: 0 };

    return (
        <CloseableModal
            className={createClassName(styles['menu'], className)}
            onClickOutside={onClickOutside}
            positionRef={positionRef}
            offsetY={offsetY + 6}>
            <ol className={styles['menu__items']} {...props}>
                {items.map((i, index) => {
                    return (
                        <li
                            key={`${index}_${i.name}`}
                            className={createClassName(
                                styles['menu__item'],
                                i.disabled && styles['menu__item--disabled'],
                            )}
                            onClick={() => {
                                if (!i.disabled) {
                                    if (i.onSelect) i.onSelect();
                                    else if (onSelect) onSelect(i.value);
                                }
                            }}>
                            {i.name}
                        </li>
                    );
                })}
            </ol>
        </CloseableModal>
    );
};
