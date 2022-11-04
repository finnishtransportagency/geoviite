import * as React from 'react';
import styles from './menu.scss';
import { createClassName } from 'vayla-design-lib/utils';

export type Item<TItemValue> = {
    name: string;
    value: TItemValue;
};

export type MenuOptions<TItemValue> = Item<TItemValue>[];

export type MenuProps<TItemValue> = {
    items?: MenuOptions<TItemValue>;
    value?: TItemValue;
    onChange?: (item: TItemValue | undefined) => void;
};

export const Menu = function <TItemValue>({
    items,
    value,
    onChange,
}: MenuProps<TItemValue>): JSX.Element {
    const [currentValue, setCurrentValue] = React.useState<TItemValue | undefined>(undefined);

    React.useEffect(() => {
        setCurrentValue(value);
    }, [value]);

    function handleItemClick(item: Item<TItemValue> | undefined) {
        onChange && item && onChange(item.value);
    }

    return (
        <div className={createClassName(styles['menu'])}>
            {items &&
                items.map((item, index) => (
                    <div
                        className={createClassName(
                            styles['menu__item'],
                            item.value === currentValue && styles['menu__item--selected'],
                        )}
                        onClick={() => handleItemClick(item)}
                        key={index}>
                        {item.name}
                    </div>
                ))}
        </div>
    );
};
