import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';

type OperatingPointDeleteDraftConfirmDialogProps = {
    onClose: () => void;
    onRevert: () => void;
};

export const OperationalPointDeleteDraftConfirmDialog: React.FC<
    OperatingPointDeleteDraftConfirmDialogProps
> = ({ onClose, onRevert }) => {
    const { t } = useTranslation();

    return (
        <Dialog
            title={t('operational-point-dialog.confirm-revert')}
            onClose={onClose}
            variant={DialogVariant.DARK}
            allowClose={false}
            footerContent={
                <div className={dialogStyles['dialog__footer-content--centered']}>
                    <Button variant={ButtonVariant.SECONDARY} onClick={onClose}>
                        {t('button.cancel')}
                    </Button>
                    <Button variant={ButtonVariant.PRIMARY_WARNING} onClick={onRevert}>
                        {t('button.revert-draft')}
                    </Button>
                </div>
            }>
            {t('operational-point-dialog.revert-info')}
        </Dialog>
    );
};
