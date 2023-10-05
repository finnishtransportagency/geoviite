import { Dialog, DialogVariant } from 'vayla-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { LayoutKmPostId } from 'track-layout/track-layout-model';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import * as React from 'react';
import { createDelegatesWithDispatcher } from 'store/store-utils';
import { useTranslation } from 'react-i18next';
import {
    actions,
    initialKmPostEditState,
    reducer,
} from 'tool-panel/km-post/dialog/km-post-edit-store';
import dialogStyles from 'vayla-design-lib/dialog/dialog.scss';
import { deleteDraftKmPost } from 'track-layout/layout-km-post-api';

type KmPostDeleteConfirmationDialogProps = {
    id: LayoutKmPostId;
    onClose: () => void;
    onCancel: () => void;
};

const KmPostDeleteConfirmationDialog: React.FC<KmPostDeleteConfirmationDialogProps> = ({
    id,
    onClose,
    onCancel,
}: KmPostDeleteConfirmationDialogProps) => {
    const { t } = useTranslation();
    const [, dispatcher] = React.useReducer(reducer, initialKmPostEditState);
    const stateActions = createDelegatesWithDispatcher(dispatcher, actions);

    const deleteKmPost = (id: LayoutKmPostId) => {
        deleteDraftKmPost(id)
            .then((result) => {
                result
                    .map((kmPostId) => {
                        stateActions.onSaveSucceed(kmPostId);
                        Snackbar.success(t('km-post-delete-draft-dialog.delete-succeeded'));
                        onClose();
                    })
                    .mapErr(() => {
                        stateActions.onSaveFailed();
                        Snackbar.error(t('km-post-delete-draft-dialog.delete-failed'));
                    });
            })
            .catch(() => {
                stateActions.onSaveFailed();
            });
    };

    return (
        <Dialog
            title={t('km-post-delete-draft-dialog.delete-draft-confirm')}
            variant={DialogVariant.DARK}
            allowClose={false}
            footerContent={
                <div className={dialogStyles['dialog__footer-content--centered']}>
                    <Button onClick={onCancel} variant={ButtonVariant.SECONDARY}>
                        {t('button.cancel')}
                    </Button>
                    <Button onClick={() => deleteKmPost(id)}>{t('button.delete')}</Button>
                </div>
            }>
            <div>{t('km-post-delete-draft-dialog.can-be-deleted')}</div>
        </Dialog>
    );
};

export default KmPostDeleteConfirmationDialog;
