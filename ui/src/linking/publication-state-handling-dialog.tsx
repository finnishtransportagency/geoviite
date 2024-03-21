import React from 'react';
import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { useTranslation } from 'react-i18next';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { PublicationState } from 'common/common-model';

export type PublicationStateHandlingDialogProps = {
    onPublicationStateChange: (state: PublicationState) => void;
    onClose: () => void;
};

export const PublicationStateHandlingDialog: React.FC<PublicationStateHandlingDialogProps> = ({
    onPublicationStateChange,
    onClose,
}) => {
    const { t } = useTranslation();

    return (
        <Dialog
            title={t(`publish-type-dialog-title`)}
            onClose={onClose}
            variant={DialogVariant.DARK}
            footerContent={
                <div className={dialogStyles['dialog__footer-content--centered']}>
                    <Button onClick={onClose} variant={ButtonVariant.SECONDARY}>
                        {t('publish-type-dialog-button-cancel')}
                    </Button>
                    <Button onClick={() => onPublicationStateChange('DRAFT')}>
                        {t('publish-type-dialog-button-continue')}
                    </Button>
                </div>
            }>
            {t(`publish-type-dialog-message`)}
        </Dialog>
    );
};
