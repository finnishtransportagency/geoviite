import * as React from 'react';
import { createClassName } from 'vayla-design-lib/utils';
import styles from './field-layout.scss';

export type FieldLayoutProps = {
    label?: React.ReactNode;
    value?: React.ReactNode;
    help?: React.ReactNode;
    warnings?: string[];
    errors?: string[];
};

export const FieldLayout: React.FC<FieldLayoutProps> = (props: FieldLayoutProps) => {
    const className = createClassName(
        styles['field-layout'],
        props.errors?.length && styles['field-layout--has-error'],
    );

    return (
        <div className={className}>
            <div className={styles['field-layout__label']}>{props.label}</div>
            <div className={styles['field-layout__value']}>{props.value}</div>
            <div className={styles['field-layout__help']}>{props.help}</div>
            {props.errors && (
                <div className={styles['field-layout__errors']}>
                    {props.errors.map((error, i) => (
                        <div className={styles['field-layout__error']} key={i}>
                            {error}
                        </div>
                    ))}
                </div>
            )}

            {props.warnings && (
                <div className={styles['field-layout__errors']}>
                    {props.warnings.map((error, i) => (
                        <div className={styles['field-layout__error']} key={i}>
                            {error}
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
};
