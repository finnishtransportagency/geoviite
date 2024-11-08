import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { LayoutKmPostId } from 'track-layout/track-layout-model';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import * as React from 'react';
import { useTranslation } from 'react-i18next';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { deleteDraftKmPost } from 'track-layout/layout-km-post-api';
import { LayoutContext } from 'common/common-model';

type KmPostRevertConfirmationDialogProps = {
    layoutContext: LayoutContext;
    id: LayoutKmPostId;
    onSave?: (id: LayoutKmPostId) => void;
    onClose: () => void;
};

const KmPostRevertConfirmationDialog: React.FC<KmPostRevertConfirmationDialogProps> = ({
    layoutContext,
    id,
    onSave,
    onClose,
}: KmPostRevertConfirmationDialogProps) => {
    const { t } = useTranslation();

    const [isSaving, setIsSaving] = React.useState(false);

    const revertDraft = (id: LayoutKmPostId) => {
        setIsSaving(true);
        deleteDraftKmPost(layoutContext, id)
            .then(
                (kmPostId) => {
                    Snackbar.success('km-post-revert-draft-dialog.revert-succeeded');
                    onSave && onSave(kmPostId);
                    onClose();
                },
                () => Snackbar.error('km-post-revert-draft-dialog.revert-failed'),
            )
            .finally(() => setIsSaving(false));
    };

    return (
        <Dialog
            title={t('km-post-revert-draft-dialog.revert-draft-confirm')}
            variant={DialogVariant.DARK}
            allowClose={false}
            footerContent={
                <div className={dialogStyles['dialog__footer-content--centered']}>
                    <Button variant={ButtonVariant.SECONDARY} disabled={isSaving} onClick={onClose}>
                        {t('button.cancel')}
                    </Button>
                    <Button
                        variant={ButtonVariant.PRIMARY_WARNING}
                        disabled={isSaving}
                        isProcessing={isSaving}
                        onClick={() => revertDraft(id)}>
                        {t('button.delete-draft')}
                    </Button>
                </div>
            }>
            <div>{t('km-post-revert-draft-dialog.can-be-reverted')}</div>
        </Dialog>
    );
};

export default KmPostRevertConfirmationDialog;
