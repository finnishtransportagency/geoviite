import * as React from 'react';
import styles from './radio.scss';
import { createClassName } from 'vayla-design-lib/utils';

export type RadioProps = {
    qaId?: string;
} & React.InputHTMLAttributes<HTMLInputElement>;

export const Radio: React.FC<RadioProps> = ({ children, qaId, ...props }: RadioProps) => {
    const [touched, setTouched] = React.useState(false);

    const className = createClassName(
        styles.radio,
        props.disabled && styles['radio--disabled'],
        touched && styles['radio--touched'],
    );
    return (
        <label qa-id={qaId} className={className} onClick={() => setTouched(true)}>
            <input {...props} type="radio" className={styles.radio__input} />
            <span className={styles.radio__visualization}>
                <span className={styles['radio__checked-marker']} />
            </span>
            {children && <span className={styles['radio__label-text']}>{children}</span>}
        </label>
    );
};
