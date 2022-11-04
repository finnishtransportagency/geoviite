import * as React from 'react';
import { createClassName } from 'vayla-design-lib/utils';
import styles from './spinner.scss';

export type SpinnerProps = {
    foo?: string;
};

export const Spinner: React.FC<SpinnerProps> = ({ ..._props }: SpinnerProps) => {
    const className = createClassName(styles.spinner);

    return <div className={className} />;
};
