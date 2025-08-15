import React from 'react';
import { useTranslation } from 'react-i18next';
import { PublicationCause, PublicationDetails } from 'publication/publication-model';
import styles from 'publication/card/publication-list-row.scss';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { createClassName } from 'vayla-design-lib/utils';
import { formatDateFull } from 'utils/date-utils';
import { LayoutDesign } from 'track-layout/layout-design-api';

type DesignPublicationListRowProps = {
    publication: PublicationDetails;
    design: LayoutDesign;
};

const DesignPublicationCauseIcon: React.FC<{ cause: PublicationCause }> = ({ cause }) => {
    switch (cause) {
        case PublicationCause.LAYOUT_DESIGN_DELETE:
            return <Icons.Delete size={IconSize.SMALL} color={IconColor.INHERIT} />;
        case PublicationCause.LAYOUT_DESIGN_CHANGE:
            return <Icons.Edit size={IconSize.SMALL} color={IconColor.INHERIT} />;
        case PublicationCause.CALCULATED_CHANGE:
        case PublicationCause.LAYOUT_DESIGN_CANCELLATION:
        case PublicationCause.MANUAL:
        case PublicationCause.MERGE_FINALIZATION:
            return <React.Fragment />;
        default:
            return exhaustiveMatchingGuard(cause);
    }
};

const DesignPublicationCauseText: React.FC<{ designName: string; cause: PublicationCause }> = ({
    designName,
    cause,
}) => {
    const { t } = useTranslation();
    switch (cause) {
        case PublicationCause.LAYOUT_DESIGN_DELETE:
            return (
                <span>
                    {t('publication-card.design-publication-cause.design-cancellation', {
                        designName,
                    })}
                </span>
            );
        case PublicationCause.LAYOUT_DESIGN_CHANGE:
            return (
                <span>
                    {t('publication-card.design-publication-cause.design-change', { designName })}
                </span>
            );
        case PublicationCause.CALCULATED_CHANGE:
        case PublicationCause.LAYOUT_DESIGN_CANCELLATION:
        case PublicationCause.MERGE_FINALIZATION:
        case PublicationCause.MANUAL:
            return <React.Fragment />;
        default:
            return exhaustiveMatchingGuard(cause);
    }
};

const ManualPublicationRowContent: React.FC<DesignPublicationListRowProps> = ({
    publication,
    design,
}) => {
    return (
        <div className={styles['publication-list-item']}>
            <span className={styles['publication-list-item__timestamp']}>
                <span className={styles['publication-list-item__text']}>
                    {formatDateFull(publication.publicationTime)}
                </span>
            </span>
            <span>
                <React.Fragment>
                    <span className={styles['publication-list-item__design-name']}>
                        {`${design.name}:`}
                    </span>
                    {publication.message}
                </React.Fragment>
            </span>
        </div>
    );
};

const GeneratedPublicationRowContent: React.FC<DesignPublicationListRowProps> = ({
    publication,
    design,
}) => {
    const itemClassNames = createClassName(
        styles['publication-list-item'],
        publication.cause !== PublicationCause.MANUAL &&
            styles['publication-list-item--generated-publication'],
    );

    return (
        <div className={itemClassNames}>
            <span className={styles['publication-list-item__timestamp']}>
                <DesignPublicationCauseIcon cause={publication.cause} />
                <span className={styles['publication-list-item__text']}>
                    {formatDateFull(publication.publicationTime)}
                </span>
            </span>
            <span className={styles['publication-list-item__design-name']}>
                {`${design.name}:`}
            </span>
            <DesignPublicationCauseText designName={design.name || ''} cause={publication.cause} />
        </div>
    );
};

export const DesignPublicationListRow: React.FC<DesignPublicationListRowProps> = ({
    publication,
    design,
}) => {
    return (
        <div className={styles['publication-list-item-container']}>
            {publication.cause === PublicationCause.MANUAL ? (
                <ManualPublicationRowContent publication={publication} design={design} />
            ) : (
                <GeneratedPublicationRowContent publication={publication} design={design} />
            )}
        </div>
    );
};
