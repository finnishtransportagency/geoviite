import * as React from 'react';
import { createClassName } from 'vayla-design-lib/utils';
import styles from './field-layout.scss';

export type FieldLayoutProps = {
    label?: React.ReactNode;
    value?: React.ReactNode;
    help?: React.ReactNode;
    warnings?: string[];
    errors?: string[];
    disabled?: boolean;
    children?: React.ReactNode;
};

export const FieldLayout: React.FC<FieldLayoutProps> = (props: FieldLayoutProps) => {
    const className = createClassName(
        styles['field-layout'],
        props.errors?.length && styles['field-layout--has-error'],
    );

    const labelClassName = createClassName(
        styles['field-layout__label'],
        props.disabled && styles['field-layout__label--disabled'],
    );
    const valueClassName = createClassName(
        props.disabled && styles['field-layout__value--disabled'],
    );

    return (
        <div className={className}>
            <div className={labelClassName}>{props.label}</div>
            <div className={valueClassName}>{props.value}</div>
            <div className={styles['field-layout__help']}>{props.help}</div>
            {props.errors && (
                <div className={styles['field-layout__notices']}>
                    {props.errors.map((error, i) => (
                        <div className={styles['field-layout__error']} key={i}>
                            {error}
                        </div>
                    ))}
                </div>
            )}

            {props.warnings && (
                <div className={styles['field-layout__notices']}>
                    {props.warnings.map((error, i) => (
                        <div className={styles['field-layout__warning']} key={i}>
                            {error}
                        </div>
                    ))}
                </div>
            )}

            {props.children}
        </div>
    );
};
