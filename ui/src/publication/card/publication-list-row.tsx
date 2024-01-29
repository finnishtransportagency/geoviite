import React from 'react';
import { useTranslation } from 'react-i18next';
import { BulkTransferState, PublicationDetails } from 'publication/publication-model';
import { ratkoPushFailed, ratkoPushInProgress } from 'ratko/ratko-model';
import styles from 'publication/card/publication-list.scss';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { Spinner, SpinnerSize } from 'vayla-design-lib/spinner/spinner';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { useAppNavigate } from 'common/navigate';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import { createClassName } from 'vayla-design-lib/utils';
import { Link } from 'vayla-design-lib/link/link';
import { formatDateFull } from 'utils/date-utils';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import RatkoPublishButton from 'ratko/ratko-publish-button';

type PublicationRowProps = {
    publication: PublicationDetails;
};

const publicationStateIcon: React.FC<PublicationDetails> = (publication) => {
    if (ratkoPushFailed(publication.ratkoPushStatus)) {
        return (
            <span className={styles['publication-list-item--error']}>
                <Icons.StatusError size={IconSize.SMALL} color={IconColor.INHERIT} />
            </span>
        );
    } else if (ratkoPushInProgress(publication.ratkoPushStatus)) {
        return <Spinner size={SpinnerSize.SMALL} />;
    } else {
        return <span className={styles['publication-list-item__split-detail-no-icon']} />;
    }
};

const bulkTransferStateIcon = (bulkTransferState: BulkTransferState | undefined) => {
    switch (bulkTransferState) {
        case 'DONE':
        case 'PENDING':
        case undefined:
            return <span className={styles['publication-list-item__split-detail-no-icon']} />;
        case 'FAILED':
        case 'TEMPORARY_FAILURE':
            return (
                <span className={styles['publication-list-item--error']}>
                    <Icons.StatusError size={IconSize.SMALL} color={IconColor.INHERIT} />
                </span>
            );
        case 'IN_PROGRESS':
            return <Spinner size={SpinnerSize.SMALL} />;
        default:
            exhaustiveMatchingGuard(bulkTransferState);
    }
};

export const PublicationRow: React.FC<PublicationRowProps> = ({ publication }) => {
    const { t } = useTranslation();
    const navigate = useAppNavigate();

    const trackLayoutActionDelegates = React.useMemo(
        () => createDelegates(trackLayoutActionCreators),
        [],
    );

    const [open, setOpen] = React.useState(false);
    const buttonClassNames = createClassName(
        styles['publication-list-item__split-action-button'],
        open && styles['publication-list-item__split-action-button--open'],
    );
    return (
        <div className={styles['publication-list-item-container']}>
            <div className={styles['publication-list-item']}>
                <span className={styles['publication-list-item__timestamp']}>
                    {ratkoPushInProgress(publication.ratkoPushStatus) && (
                        <Spinner size={SpinnerSize.SMALL} />
                    )}
                    {ratkoPushFailed(publication.ratkoPushStatus) && (
                        <span className={styles['publication-list-item--error']}>
                            <Icons.StatusError size={IconSize.SMALL} color={IconColor.INHERIT} />
                        </span>
                    )}
                    <span className={styles['publication-list-item__text']}>
                        <Link
                            onClick={() => {
                                trackLayoutActionDelegates.setSelectedPublicationId(publication.id);
                                navigate('publication-view', publication.id);
                            }}>
                            {formatDateFull(publication.publicationTime)}
                        </Link>
                    </span>
                </span>
                <span>{publication.message}</span>
            </div>
            {publication.split && (
                <div className={styles['publication-list-item__split']}>
                    <div>
                        <div className={styles['publication-list-item__split-detail-row']}>
                            <span>{publicationStateIcon(publication)}</span>
                            <span>{t('publication-card.publication-status')}</span>
                        </div>
                        <div className={styles['publication-list-item__split-detail-row']}>
                            <span>
                                {bulkTransferStateIcon(publication.split.bulkTransferState)}
                            </span>
                            <span>{t('publication-card.bulk-transfer-status')}</span>
                        </div>
                    </div>
                    <Button
                        className={buttonClassNames}
                        size={ButtonSize.SMALL}
                        variant={ButtonVariant.SECONDARY}
                        onClick={() => setOpen(!open)}
                        icon={Icons.Down}>
                        {t('publication-card.actions')}
                    </Button>
                </div>
            )}
            {open && (
                <div className={styles['publication-list-item__split-actions']}>
                    <RatkoPublishButton />
                    <Button variant={ButtonVariant.SECONDARY}>
                        {t('publication-card.mark-as-successful')}
                    </Button>
                </div>
            )}
        </div>
    );
};
