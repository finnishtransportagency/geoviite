import * as React from 'react';
import { createClassName } from 'vayla-design-lib/utils';
import styles from './progress-indicator-wrapper.scss';

export enum ProgressIndicatorType {
    Default,
    Subtle,
    Area,
}

export type ProgressIndicatorWrapperProps = {
    indicator?: ProgressIndicatorType;
    inProgress?: boolean;
    children?: React.ReactNode;
    inline?: boolean;
};

export const ProgressIndicatorWrapper: React.FC<ProgressIndicatorWrapperProps> = ({
    indicator = ProgressIndicatorType.Default,
    inProgress = false,
    children,
    inline = true,
}: ProgressIndicatorWrapperProps) => {
    const className = createClassName(
        styles['progress-indicator-wrapper'],
        inline && styles['progress-indicator-wrapper--inline'],
        indicator === ProgressIndicatorType.Default &&
            styles['progress-indicator-wrapper--default-indicator'],
        indicator === ProgressIndicatorType.Subtle &&
            styles['progress-indicator-wrapper--subtle-indicator'],
        indicator === ProgressIndicatorType.Area &&
            styles['progress-indicator-wrapper--area-indicator'],
        inProgress && styles['progress-indicator-wrapper--in-progress'],
    );

    return (
        <div className={className}>
            <div className={styles['progress-indicator-wrapper__content']}>{children}</div>
            <div className={styles['progress-indicator-wrapper__indicator']}></div>
        </div>
    );
};
