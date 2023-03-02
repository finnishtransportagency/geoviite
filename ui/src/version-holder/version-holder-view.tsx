import * as React from 'react';
import styles from './version-holder-view.scss';

type VersionHolderViewProps = {
    version: string;
};

export const VersionHolderView: React.FC<VersionHolderViewProps> = ({ version }) => {
    const clearStorage = () => {
        const isOk = confirm('Välimuisti tyhjennetään ja sivu ladataan uudelleen');
        if (isOk) {
            localStorage.clear();
            location.reload();
        }
    };

    return (
        <div className={styles['version-holder-view']} onClick={clearStorage}>
            {version.substring(0, 8)}
        </div>
    );
};
