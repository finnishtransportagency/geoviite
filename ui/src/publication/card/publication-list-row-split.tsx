import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { Menu, menuOption, MenuSelectOption } from 'vayla-design-lib/menu/menu';
import { SplitDetailsDialog } from 'publication/split/split-details-dialog';
import { PublicationId, SplitHeader } from 'publication/publication-model';
import styles from 'publication/card/publication-list-row.scss';
import {
    ratkoPushFailed,
    ratkoPushInProgress,
    RatkoPushStatus,
    ratkoPushSucceeded,
} from 'ratko/ratko-model';
import { Spinner, SpinnerSize } from 'vayla-design-lib/spinner/spinner';
import { createClassName } from 'vayla-design-lib/utils';
import { putBulkTransferState } from 'publication/split/split-api';
import { success } from 'geoviite-design-lib/snackbar/snackbar';
import { updateSplitChangeTime } from 'common/change-time-api';

export type PublicationListRowSplitProps = {
    split: SplitHeader;
    ratkoPushStatus: RatkoPushStatus | undefined;
    publicationId: PublicationId;
};

const PublicationStateIcon: React.FC<{ ratkoPushStatus: RatkoPushStatus | undefined }> = ({
    ratkoPushStatus,
}) => {
    if (ratkoPushSucceeded(ratkoPushStatus)) {
        return (
            <span className={styles['publication-list-item--success']}>
                <Icons.Tick size={IconSize.SMALL} color={IconColor.INHERIT} />
            </span>
        );
    } else if (ratkoPushFailed(ratkoPushStatus)) {
        return (
            <span className={styles['publication-list-item--error']}>
                <Icons.StatusError size={IconSize.SMALL} color={IconColor.INHERIT} />
            </span>
        );
    } else if (ratkoPushInProgress(ratkoPushStatus)) {
        return <Spinner size={SpinnerSize.SMALL} />;
    } else {
        return <span className={styles['publication-list-item__split-detail-no-icon']} />;
    }
};

export const PublicationListRowSplit: React.FC<PublicationListRowSplitProps> = ({
    split,
    ratkoPushStatus,
    publicationId,
}) => {
    const { t } = useTranslation();

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
                putBulkTransferState(split.id, 'DONE')
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
            split?.bulkTransferState === 'DONE',
        ),
    ];

    return (
        <React.Fragment>
            {
                <div className={styles['publication-list-item__split']}>
                    <div>
                        <div className={styles['publication-list-item__split-detail-row']}>
                            <span>
                                <PublicationStateIcon ratkoPushStatus={ratkoPushStatus} />
                            </span>
                            <span>{t('publication-card.publication-status')}</span>
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
            }
            {splitDetailsDialogOpen && (
                <SplitDetailsDialog
                    publicationId={publicationId}
                    onClose={() => setSplitDetailsDialogOpen(false)}
                />
            )}
        </React.Fragment>
    );
};
