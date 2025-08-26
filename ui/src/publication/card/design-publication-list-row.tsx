import React from 'react';
import { useTranslation } from 'react-i18next';
import { PublicationCause, PublicationDetails } from 'publication/publication-model';
import styles from 'publication/card/publication-list-row.scss';
import { createClassName } from 'vayla-design-lib/utils';
import { formatDateFull } from 'utils/date-utils';

type DesignPublicationListRowProps = {
    publication: PublicationDetails;
    designName: string | undefined;
};

const ManualPublicationRowContent: React.FC<DesignPublicationListRowProps> = ({
    publication,
    designName,
}) => {
    const { t } = useTranslation();
    return (
        <div className={styles['publication-list-item']}>
            <span className={styles['publication-list-item__timestamp']}>
                <span className={styles['publication-list-item__header']}>
                    {formatDateFull(publication.publicationTime)}
                </span>
            </span>
            <span>
                <React.Fragment>
                    <div className={styles['publication-list-item__design-name']}>
                        {designName ?? t('publication-card.missing-design-name')}
                    </div>
                    <div className={styles['publication-list-item__message']}>
                        {publication.message}
                    </div>
                </React.Fragment>
            </span>
        </div>
    );
};

const GeneratedPublicationRowContent: React.FC<DesignPublicationListRowProps> = ({
    publication,
    designName,
}) => {
    const { t } = useTranslation();
    const itemClassNames = createClassName(
        styles['publication-list-item'],
        publication.cause !== PublicationCause.MANUAL &&
            styles['publication-list-item--generated-publication'],
    );

    return (
        <div className={itemClassNames}>
            <span className={styles['publication-list-item__timestamp']}>
                <span className={styles['publication-list-item__header']}>
                    {formatDateFull(publication.publicationTime)}
                </span>
            </span>
            <span className={styles['publication-list-item__design-name']}>
                {designName ?? t('publication-card.missing-design-name')}
            </span>
            {publication.cause !== PublicationCause.MANUAL && (
                <div className={styles['publication-list-item__message']}>
                    {t(`publication-card.design-publication-cause.${publication.cause}`)}
                </div>
            )}
        </div>
    );
};

export const DesignPublicationListRow: React.FC<DesignPublicationListRowProps> = ({
    publication,
    designName,
}) => {
    return (
        <div className={styles['publication-list-item-container']}>
            {publication.cause === PublicationCause.MANUAL ? (
                <ManualPublicationRowContent publication={publication} designName={designName} />
            ) : (
                <GeneratedPublicationRowContent publication={publication} designName={designName} />
            )}
        </div>
    );
};
