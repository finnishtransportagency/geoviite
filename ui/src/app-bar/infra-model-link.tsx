import * as React from 'react';
import styles from './app-bar.scss';
import { ExclamationPoint } from 'geoviite-design-lib/exclamation-point/exclamation-point';
import { useTranslation } from 'react-i18next';

type InfraModelLinkProps = {
    visibility: boolean;
};
export const InfraModelLink: React.FC<InfraModelLinkProps> = ({ visibility }) => {
    const { t } = useTranslation();
    return (
        <span>
            {t('app-bar.infra-model')}
            {visibility && (
                <span className={styles['app-bar__link--exclamation-point']}>
                    <ExclamationPoint />
                </span>
            )}
        </span>
    );
};
