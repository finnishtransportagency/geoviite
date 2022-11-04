import * as React from 'react';
import { createClassName } from 'vayla-design-lib/utils';
import styles from './infra-model-form.module.scss';

export type InfraModelTextFieldProps = {
    children?: React.ReactNode;
    hasError?: boolean;
};

export const InfraModelTextField: React.FC<InfraModelTextFieldProps> = ({children, hasError}: InfraModelTextFieldProps) => {
    const className = createClassName(
        hasError && styles['infra-model-text-field--has-error'],
    );

    return (
        <div className={className}>
            {children}
        </div>
    );
};
