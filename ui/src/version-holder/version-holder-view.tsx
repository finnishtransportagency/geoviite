import * as React from 'react';
import styles from './version-holder-view.scss';

export const VersionHolderView: React.FC = () => {
    const clearStorage = () => {
        const isOk = confirm('Välimuisti tyhjennetään ja sivu ladataan uudelleen');
        if (isOk) {
            localStorage.clear();
            location.reload();
        }
    };

    return (
        <div className={styles['version-holder-view']} onClick={clearStorage}>
            {GEOVIITE_UI_VERSION || 'Geoviite'} {(GEOVIITE_HASH || '').substring(0, 8)}
        </div>
    );
};
