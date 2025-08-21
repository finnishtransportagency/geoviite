import * as React from 'react';
import { useTranslation } from 'react-i18next';
import Card from 'geoviite-design-lib/card/card';
import styles from './publication-card.scss';
import {
    ProgressIndicatorType,
    ProgressIndicatorWrapper,
} from 'vayla-design-lib/progress/progress-indicator-wrapper';
import { AnchorLink } from 'geoviite-design-lib/link/anchor-link';

export const PUBLICATION_LIST_PAGE_SIZE = 8;

export const PublicationCardSection: React.FC<{
    title: React.ReactNode;
    children: React.ReactNode;
}> = ({ children, title }) => {
    return (
        <section>
            <h3 className={styles['publication-card__subsection-title']}>{title}</h3>
            {children}
        </section>
    );
};

export const NoPublicationsInfo: React.FC<{ title: string }> = ({ title }) => {
    return <div className={styles['publication-card__no-publications']}>{title}</div>;
};

export const ShowMorePublicationsLink: React.FC<{ showMore: () => void }> = ({ showMore }) => {
    const { t } = useTranslation();
    return (
        <div className={styles['publication-card__show-more']}>
            <AnchorLink onClick={showMore}>{t('publication-card.show-more')}</AnchorLink>
        </div>
    );
};

type PublicationCardProps = {
    title: React.ReactNode;
    children?: React.ReactNode;
    loading: boolean;
};

const PublicationCard: React.FC<PublicationCardProps> = ({ title, children, loading }) => {
    return (
        <Card
            className={styles['publication-card']}
            content={
                <React.Fragment>
                    <h2 className={styles['publication-card__title']}>{title}</h2>
                    <ProgressIndicatorWrapper
                        indicator={ProgressIndicatorType.Area}
                        inProgress={loading}>
                        {children}
                    </ProgressIndicatorWrapper>
                </React.Fragment>
            }
        />
    );
};

export default PublicationCard;
