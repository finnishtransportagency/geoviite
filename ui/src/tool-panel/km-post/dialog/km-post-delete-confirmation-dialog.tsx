import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { LayoutKmPostId } from 'track-layout/track-layout-model';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import * as React from 'react';
import { useTranslation } from 'react-i18next';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { deleteDraftKmPost } from 'track-layout/layout-km-post-api';

type KmPostDeleteConfirmationDialogProps = {
    id: LayoutKmPostId;
    onSave?: (id: LayoutKmPostId) => void;
    onClose: () => void;
};

const KmPostDeleteConfirmationDialog: React.FC<KmPostDeleteConfirmationDialogProps> = ({
    id,
    onSave,
    onClose,
}: KmPostDeleteConfirmationDialogProps) => {
    const { t } = useTranslation();

    const deleteKmPost = (id: LayoutKmPostId) =>
        deleteDraftKmPost(id).then((result) => {
            result
                .map((kmPostId) => {
                    Snackbar.success('km-post-delete-draft-dialog.delete-succeeded');
                    onSave && onSave(kmPostId);
                    onClose();
                })
                .mapErr(() => Snackbar.error('km-post-delete-draft-dialog.delete-failed'));
        });

    return (
        <Dialog
            title={t('km-post-delete-draft-dialog.delete-draft-confirm')}
            variant={DialogVariant.DARK}
            allowClose={false}
            footerContent={
                <div className={dialogStyles['dialog__footer-content--centered']}>
                    <Button variant={ButtonVariant.SECONDARY} onClick={onClose}>
                        {t('button.cancel')}
                    </Button>
                    <Button
                        variant={ButtonVariant.PRIMARY_WARNING}
                        onClick={() => deleteKmPost(id)}>
                        {t('button.delete')}
                    </Button>
                </div>
            }>
            <div>{t('km-post-delete-draft-dialog.can-be-deleted')}</div>
        </Dialog>
    );
};

export default KmPostDeleteConfirmationDialog;
