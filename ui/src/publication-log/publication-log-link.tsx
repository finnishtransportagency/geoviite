import * as React from 'react';
import styles from './publication-log.scss';
import { useTranslation } from 'react-i18next';

type PublicationLogLinkProps = {
    setShowPublicationLogItems: () => void;
};

const PublicationLogLink: React.FC<PublicationLogLinkProps> = ({ setShowPublicationLogItems }) => {
    const { t } = useTranslation();

    return (
        <div className={styles['publication-log__link']}>
            {
                <div onClick={() => setShowPublicationLogItems()}>
                    {t('publication-log.link-text')}
                </div>
            }
        </div>
    );
};

export default PublicationLogLink;
