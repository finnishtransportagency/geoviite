import * as React from 'react';
import { createClassName } from 'vayla-design-lib/utils';
import styles from './spinner.scss';

export enum SpinnerSize {
    SMALL,
    NORMAL,
}

export type SpinnerProps = {
    size?: SpinnerSize;
};

export const Spinner: React.FC<SpinnerProps> = ({ size = SpinnerSize.NORMAL }: SpinnerProps) => {
    const className = createClassName(
        styles.spinner,
        size === SpinnerSize.SMALL && styles['spinner--small'],
    );

    return <div className={className} />;
};
