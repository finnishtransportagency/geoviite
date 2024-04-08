import * as React from 'react';
import styles from './checkbox.scss';
import { createClassName } from 'vayla-design-lib/utils';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';

export type CheckboxProps = {
    foo?: string;
    qaId?: string;
} & React.InputHTMLAttributes<HTMLInputElement>;

export const Checkbox: React.FC<CheckboxProps> = ({ children, qaId, ...props }: CheckboxProps) => {
    const [touched, setTouched] = React.useState(false);

    const className = createClassName(styles.checkbox, touched && styles['checkbox--touched']);
    return (
        <label
            className={className}
            onClick={() => {
                setTouched(true);
            }}
            {...(qaId && { 'qa-id': qaId })}>
            <input {...props} type="checkbox" className={styles.checkbox__input} />
            <span className={styles.checkbox__visualization}>
                <span className={styles['checkbox__checked-icon']}>
                    <Icons.Selected size={IconSize.SMALL} color={IconColor.INHERIT} />
                </span>
            </span>
            {children && <span className={styles['checkbox__label-text']}>{children}</span>}
        </label>
    );
};
