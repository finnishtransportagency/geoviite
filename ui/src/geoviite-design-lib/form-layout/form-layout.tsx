import React from 'react';
import styles from './form-layout.scss';
import { createClassName } from 'vayla-design-lib/utils';

export type FormLayoutProps = {
    children?: React.ReactNode;
    isProcessing?: boolean;
};

export type FormLayoutColumnProps = {
    children?: React.ReactNode;
};

export const FormLayout: React.FC<FormLayoutProps> = (props: FormLayoutProps) => {
    const className = createClassName(
        styles['form-layout'],
        props.isProcessing && styles['form-layout--is-processing'],
    );

    return (
        <div className={className}>
            {props.isProcessing && <div className={styles['form-layout__glass']} />}
            {props.children}
        </div>
    );
};

export const FormLayoutColumn: React.FC<FormLayoutColumnProps> = (props: FormLayoutColumnProps) => {
    const className = createClassName(styles['form-layout__column']);

    return <div className={className}>{props.children}</div>;
};
