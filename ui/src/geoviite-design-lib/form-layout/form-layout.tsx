import React from 'react';
import styles from './form-layout.scss';
import { createClassName } from 'vayla-design-lib/utils';

export type FormLayoutProps = {
    children?: React.ReactNode;
    doubleColumn?: boolean;
    isProcessing?: boolean;
};

export type FormLayoutColumnProps = {
    children?: React.ReactNode;
};

export const FormLayout: React.FC<FormLayoutProps> = (props: FormLayoutProps) => {
    const className = createClassName(
        styles['form-layout'],
        props.isProcessing && styles['form-layout--is-processing'],
        props.doubleColumn && styles['form-layout--double-column'],
    );

    return (
        <div className={className}>
            {props.isProcessing && <div className={styles['form-layout__glass']} />}
            {props.children}
        </div>
    );
};

export const FormLayoutColumn: React.FC<FormLayoutColumnProps> = (props: FormLayoutColumnProps) => {
    return <div className="form-layout__column">{props.children}</div>;
};
