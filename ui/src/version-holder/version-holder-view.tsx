import * as React from 'react';
import styles from './version-holder-view.scss';
import { getEnvironmentInfo } from 'environment/environment-info';

export const VersionHolderView: React.FC = () => {
    const clearStorage = () => {
        const isOk = confirm('Välimuisti tyhjennetään ja sivu ladataan uudelleen');
        if (isOk) {
            localStorage.clear();
            location.reload();
        }
    };

    const version = getEnvironmentInfo()?.releaseVersion;

    return (
        <div className={styles['version-holder-view']} onClick={clearStorage}>
            {version && version.substring(0, 8)}
        </div>
    );
};
