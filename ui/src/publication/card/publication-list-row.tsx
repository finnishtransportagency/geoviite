import React from 'react';
import { useTranslation } from 'react-i18next';
import { BulkTransferState, PublicationDetails } from 'publication/publication-model';
import { ratkoPushFailed, ratkoPushInProgress, ratkoPushSucceeded } from 'ratko/ratko-model';
import styles from 'publication/card/publication-list.scss';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { Spinner, SpinnerSize } from 'vayla-design-lib/spinner/spinner';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { createClassName } from 'vayla-design-lib/utils';
import { formatDateFull } from 'utils/date-utils';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Menu, menuOption, MenuSelectOption } from 'vayla-design-lib/menu/menu';
import { SplitDetailsDialog } from 'publication/split/split-details-dialog';
import { putBulkTransferExpeditedStart, putBulkTransferState } from 'publication/split/split-api';
import { success } from 'geoviite-design-lib/snackbar/snackbar';
import { getChangeTimes, updateSplitChangeTime } from 'common/change-time-api';
import { useLayoutDesign } from 'track-layout/track-layout-react-utils';
import { Link } from 'react-router-dom';

type PublicationListRowProps = {
    publication: PublicationDetails;
    setSelectedPublicationId: (id: string) => void;
};

const publicationStateIcon: React.FC<PublicationDetails> = (publication) => {
    if (ratkoPushSucceeded(publication.ratkoPushStatus)) {
        return (
            <span className={styles['publication-list-item--success']}>
                <Icons.Tick size={IconSize.SMALL} color={IconColor.INHERIT} />
            </span>
        );
    } else if (ratkoPushFailed(publication.ratkoPushStatus)) {
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
        case 'PENDING':
        case 'CREATED':
        case undefined:
            return <span className={styles['publication-list-item__split-detail-no-icon']} />;
        case 'DONE':
            return (
                <span className={styles['publication-list-item--success']}>
                    <Icons.Tick size={IconSize.SMALL} color={IconColor.INHERIT} />
                </span>
            );
        case 'FAILED':
            return (
                <span className={styles['publication-list-item--error']}>
                    <Icons.StatusError size={IconSize.SMALL} color={IconColor.INHERIT} />
                </span>
            );
        case 'IN_PROGRESS':
            return <Spinner size={SpinnerSize.SMALL} />;
        default:
            return exhaustiveMatchingGuard(bulkTransferState);
    }
};

export const PublicationListRow: React.FC<PublicationListRowProps> = ({ publication }) => {
    const { t } = useTranslation();

    const design = useLayoutDesign(
        getChangeTimes().layoutDesign,
        publication.layoutBranch.branch,
    )?.name;

    const [menuOpen, setMenuOpen] = React.useState(false);
    const [splitDetailsDialogOpen, setSplitDetailsDialogOpen] = React.useState(false);
    const buttonClassNames = createClassName(
        menuOpen && styles['publication-list-item__split-action-button--open'],
    );
    const menuRef = React.createRef<HTMLDivElement>();

    const actions: MenuSelectOption[] = [
        menuOption(
            () => {
                setSplitDetailsDialogOpen(true);
            },
            t('publication-card.show-split-info'),
            'show-split-info-link',
        ),
        menuOption(
            () => {
                if (publication.split)
                    putBulkTransferState(publication.split.id, 'DONE')
                        .then(() => {
                            success(
                                t('publication-card.bulk-transfer-marked-as-successful'),
                                undefined,
                                {
                                    id: 'toast-bulk-transfer-marked-as-successful',
                                },
                            );
                        })
                        .then(() => updateSplitChangeTime());
            },
            t('publication-card.mark-as-successful'),
            'mark-bulk-transfer-as-finished-link',
            publication.split?.bulkTransferState === 'DONE',
        ),
        menuOption(
            () => {
                if (publication.split)
                    putBulkTransferState(publication.split.id, 'PENDING')
                        .then(() => {
                            success(
                                t('publication-card.bulk-transfer-marked-as-pending'),
                                undefined,
                                {
                                    id: 'toast-bulk-transfer-marked-as-pending',
                                },
                            );
                        })
                        .then(() => updateSplitChangeTime());
            },
            t('publication-card.mark-as-pending'),
            'mark-bulk-transfer-as-pending',
            publication.split?.bulkTransferState === 'DONE',
        ),
        menuOption(
            () => {
                if (publication.split)
                    putBulkTransferExpeditedStart(publication.split.id, true)
                        .then(() => {
                            success(
                                t('publication-card.bulk-transfer-marked-as-expedited'),
                                undefined,
                                {
                                    id: 'toast-bulk-transfer-marked-as-expedited',
                                },
                            );
                        })
                        .then(() => updateSplitChangeTime());
            },
            t('publication-card.mark-as-expedited'),
            'mark-bulk-transfer-as-expedited',
            publication.split?.bulkTransferExpeditedStart === true,
        ),
    ];

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
                        {(() => {
                            const text = formatDateFull(publication.publicationTime);
                            return publication.layoutBranch.branch === 'MAIN' ? (
                                <Link to={`/publications/${publication.id}`}>{text}</Link>
                            ) : (
                                text
                            );
                        })()}
                    </span>
                </span>
                <span>
                    {design && (
                        <span className={styles['publication-list-item__design-name']}>
                            {`${design}:`}
                        </span>
                    )}
                    {publication.message}
                </span>
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
                                {bulkTransferStateIcon(publication.split?.bulkTransferState)}
                            </span>
                            <span>{t('publication-card.bulk-transfer-status')}</span>
                        </div>
                    </div>
                    <div className={styles['publication-list-item__split-action-button-container']}>
                        <Button
                            className={buttonClassNames}
                            size={ButtonSize.SMALL}
                            variant={ButtonVariant.SECONDARY}
                            onClick={() => setMenuOpen(!menuOpen)}
                            icon={Icons.Down}
                            qa-id={'publication-actions-menu-toggle'}>
                            {t('publication-card.actions')}
                        </Button>
                        {menuOpen && (
                            <div ref={menuRef}>
                                <Menu
                                    anchorElementRef={menuRef}
                                    onClickOutside={() => setMenuOpen(true)}
                                    items={actions}
                                    qa-id={'publication-actions-menu'}
                                    onClose={() => setMenuOpen(false)}
                                />
                            </div>
                        )}
                    </div>
                </div>
            )}
            {splitDetailsDialogOpen && (
                <SplitDetailsDialog
                    publicationId={publication.id}
                    onClose={() => setSplitDetailsDialogOpen(false)}
                />
            )}
        </div>
    );
};
