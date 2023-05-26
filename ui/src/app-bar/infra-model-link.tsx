import * as React from 'react';
import styles from './app-bar.scss';
import { ExclamationPoint } from 'geoviite-design-lib/exclamation-point/exclamation-point';
import { useTranslation } from 'react-i18next';

type InfraModelLinkProps = {
    exclamationPointVisibility: boolean;
};
export const InfraModelLink: React.FC<InfraModelLinkProps> = ({ exclamationPointVisibility }) => {
    const { t } = useTranslation();
    return (
        <span className={styles['app-bar__link--container']}>
            {t('app-bar.infra-model')}
            {exclamationPointVisibility && (
                <span className={styles['app-bar__link--exclamation-point']}>
                    <ExclamationPoint />
                </span>
            )}
        </span>
    );
};
