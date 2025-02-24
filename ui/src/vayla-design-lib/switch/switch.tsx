import * as React from 'react';
import styles from './switch.scss';
import { createClassName } from 'vayla-design-lib/utils';

export type SwitchContentOrder = 'SWITCH_FIRST' | 'CONTENT_FIRST';

export type SwitchProps = {
    checked: boolean;
    disabled?: boolean;
    onCheckedChange?: (checked: boolean) => void;
    contentOrder?: SwitchContentOrder;

    /**
     * If true, switch is shown in hover state
     */
    hover?: boolean;

    /**
     * In simple cases "children" property can be used to provide a label text,
     * although usually the label text is rendered separately for a better control
     * of layout.
     */
    children?: React.ReactNode;

    qaId?: string;
};

export const Switch: React.FC<SwitchProps> = ({
    disabled = false,
    hover,
    contentOrder = 'SWITCH_FIRST',
    ...props
}: SwitchProps) => {
    function setChecked(checked: boolean) {
        props.onCheckedChange && props.onCheckedChange(checked);
    }

    const className = createClassName(
        styles.switch,
        disabled && styles['switch--disabled'],
        hover && styles['switch--hover'],
    );

    const switchComesFirst = contentOrder === 'SWITCH_FIRST';

    return (
        <label className={className} qa-id={props.qaId}>
            {!switchComesFirst && props.children}
            <input
                className={styles.switch__checkbox}
                type="checkbox"
                checked={props.checked}
                onChange={(e) => setChecked(e.currentTarget?.checked)}
                disabled={disabled}
            />
            <div className={styles.switch__track}>
                <div className={styles.switch__thumb} />
            </div>
            {switchComesFirst && props.children}
        </label>
    );
};
