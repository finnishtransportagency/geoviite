import * as React from 'react';
import { createClassName } from 'vayla-design-lib/utils';
import styles from './field-layout.scss';
import { flatten } from 'utils/array-utils';

export type FieldLayoutProps = {
    label?: React.ReactNode;
    value?: React.ReactNode;
    help?: React.ReactNode;
    warnings?: React.ReactNode[];
    errors?: React.ReactNode[];
    disabled?: boolean;
    children?: React.ReactNode;
    spacing?: boolean;
};

export const FieldLayout: React.FC<FieldLayoutProps> = ({
    spacing = true,
    ...props
}: FieldLayoutProps) => {
    const className = createClassName(
        styles['field-layout'],
        props.errors?.length && styles['field-layout--has-error'],
        spacing && styles['field-layout--extra-spacing'],
    );

    const labelClassName = createClassName(
        styles['field-layout__label'],
        props.disabled && styles['field-layout__label--disabled'],
    );
    const valueClassName = createClassName(
        props.disabled && styles['field-layout__value--disabled'],
    );

    const notices = flatten([props.errors ?? [], props.warnings ?? []]).length > 0;

    return (
        <div className={className}>
            <div className={labelClassName}>{props.label}</div>
            <div className={valueClassName}>{props.value}</div>
            {props.help && <div className={styles['field-layout__help']}>{props.help}</div>}
            {notices && (
                <div className={styles['field-layout__notice-area']}>
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
                </div>
            )}

            {props.children}
        </div>
    );
};
