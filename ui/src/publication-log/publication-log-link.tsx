import * as React from 'react';
import styles from './publication-log.scss';
import { useTranslation } from 'react-i18next';

type PublicationLogLinkProps = {
    setPublicationLog: (isOn: boolean) => void;
}

const PublicationLogLink: React.FC<PublicationLogLinkProps> = ({setPublicationLog}) => {
    const {t} = useTranslation();

    return <div className={styles['publication-log__link']}>
        {
            <div onClick={() => setPublicationLog(true)}>
                {t('publication-log.link-text')}
            </div>
        }
    </div>;

};

export default PublicationLogLink;
