import * as React from 'react';
import styles from './version-holder-view.scss';
import { purgePersistentState } from 'index';
import { useLocation, useNavigate } from 'react-router-dom';
import { appPath } from 'common/navigate';

type VersionHolderViewProps = {
    version: string;
};

export const VersionHolderView: React.FC<VersionHolderViewProps> = ({ version }) => {
    const routerLocation = useLocation();
    const navigate = useNavigate();

    const clearStorage = () => {
        const isOk = confirm('Välimuisti tyhjennetään ja sivu ladataan uudelleen');
        if (isOk) {
            purgePersistentState();

            const urlsRoutedToFrontPage: string[] = [appPath['inframodel-upload']];
            if (urlsRoutedToFrontPage.includes(routerLocation.pathname)) {
                navigate('/');
            }

            location.reload();
        }
    };

    return (
        <div className={styles['version-holder-view']} onClick={clearStorage}>
            {version.substring(0, 8)}
        </div>
    );
};
