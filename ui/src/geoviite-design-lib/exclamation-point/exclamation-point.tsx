import * as React from 'react';
import styles from './exclamation-point.scss';

export const ExclamationPoint: React.FC<{ title?: string }> = ({ title }) => {
    return (
        <span title={title} className={styles['exclamation-point']}>
            !
        </span>
    );
};
