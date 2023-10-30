import React from 'react';
import { PublishType } from 'common/common-model';
import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { useTranslation } from 'react-i18next';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';

export type PublishTypeHandlingDialogProps = {
    onPublishTypeChange: (publishType: PublishType) => void;
    onClose: () => void;
};

export const PublishTypeHandlingDialog: React.FC<PublishTypeHandlingDialogProps> = ({
    onPublishTypeChange,
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
                    <Button onClick={() => onPublishTypeChange('DRAFT')}>
                        {t('publish-type-dialog-button-continue')}
                    </Button>
                </div>
            }>
            {t(`publish-type-dialog-message`)}
        </Dialog>
    );
};
