import * as React from 'react';
import styles from './loading-screen.scss';
import geoviiteLogo from 'geoviite-design-lib/geoviite-logo.svg';

export const LoadingScreen: React.FC = () => {
    return (
        <div className={styles['loading-screen']}>
            <img src={geoviiteLogo} alt="Latausikkuna" />
        </div>
    );
};
