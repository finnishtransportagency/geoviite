import * as React from 'react';
import { createClassName } from 'vayla-design-lib/utils';
import styles from './spinner.scss';

export enum SpinnerSize {
    SMALL,
    NORMAL,
}

export type SpinnerProps = {
    size?: SpinnerSize;
    inline?: boolean;
    tableHeader?: boolean;
    qaId?: string;
};

export const Spinner: React.FC<SpinnerProps> = ({
    size = SpinnerSize.NORMAL,
    inline = false,
    tableHeader = false,
    qaId,
}: SpinnerProps) => {
    const className = createClassName(
        styles.spinner,
        inline && styles['spinner--inline'],
        tableHeader && styles['spinner--table-header'],
        size === SpinnerSize.SMALL && styles['spinner--small'],
    );

    return <div className={className} {...(qaId && { 'qa-id': qaId })} />;
};
