import * as React from 'react';
import styles from './switch.scss';
import { createClassName } from 'vayla-design-lib/utils';

export type SwitchProps = {
    checked: boolean;
    disabled?: boolean;
    onCheckedChange?: (checked: boolean) => void;

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
};

export const Switch: React.FC<SwitchProps> = ({
    disabled = false,
    hover,
    ...props
}: SwitchProps) => {
    function setChecked(checked: boolean) {
        props.onCheckedChange && props.onCheckedChange(checked);
    }

    const className = createClassName(
        styles.switch,
        disabled && styles['switch--disabled'],
        props.children && styles['switch--has-label'],
        hover && styles['switch--hover'],
    );

    return (
        <label className={className}>
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
            <div className={styles.switch__label}>{props.children}</div>
        </label>
    );
};
