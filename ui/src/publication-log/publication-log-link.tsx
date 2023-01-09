import * as React from 'react';
import styles from './publication-log.scss';
import { useTranslation } from 'react-i18next';

type PublicationLogLinkProps = {
    onShowPublicationLog: () => void;
};

const PublicationLogLink: React.FC<PublicationLogLinkProps> = ({ onShowPublicationLog }) => {
    const { t } = useTranslation();

    return (
        <div className={styles['publication-log__link']} onClick={onShowPublicationLog}>
            {t('publication-log.link-text')}
        </div>
    );
};

export default PublicationLogLink;
