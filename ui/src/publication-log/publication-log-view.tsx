import * as React from 'react';
import styles from './publication-log.scss';
import { useTranslation } from 'react-i18next';
import { Link } from 'vayla-design-lib/link/link';
import { PublicationListingItem } from 'publication/publication-model';

export type PublicationLogViewProps = {
    onLogUnselected: () => void;
    selectedPublication: PublicationListingItem | undefined;
};

const PublicationLogView: React.FC<PublicationLogViewProps> = ({ onLogUnselected }) => {
    const { t } = useTranslation();

    return (
        <div className={styles['publication-log__view']}>
            <div className={styles['publication-log__title']}>
                <Link
                    onClick={() => {
                        onLogUnselected();
                    }}>
                    {t('frontpage.frontpage-link')}
                </Link>
                <span className={styles['publication-log__breadcrumbs']}>
                    {t('publication-log.breadcrumbs-text')}
                </span>
            </div>
        </div>
    );
};

export default PublicationLogView;
