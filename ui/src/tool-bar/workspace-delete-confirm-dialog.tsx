import * as React from 'react';
import { Trans, useTranslation } from 'react-i18next';
import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { LayoutDesign, updateLayoutDesign } from 'track-layout/layout-design-api';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { LayoutDesignId } from 'common/common-model';
import { updateLayoutDesignChangeTime } from 'common/change-time-api';

type WorkspaceDeleteConfirmDialogProps = {
    closeDialog: () => void;
    currentDesign: LayoutDesign;
    onDesignDeleted: (id: LayoutDesignId) => void;
};

export const WorkspaceDeleteConfirmDialog: React.FC<WorkspaceDeleteConfirmDialogProps> = ({
    currentDesign,
    onDesignDeleted,
    closeDialog,
}) => {
    const { t } = useTranslation();

    async function onDelete() {
        await updateLayoutDesign(currentDesign.id, {
            ...currentDesign,
            designState: 'DELETED',
        });
        await updateLayoutDesignChangeTime();
        onDesignDeleted(currentDesign.id);
        closeDialog();
    }

    return (
        <Dialog
            allowClose={false}
            variant={DialogVariant.DARK}
            title={t('workspace-dialog.delete-confirm.title')}
            footerContent={
                <div className={dialogStyles['dialog__footer-content--centered']}>
                    <Button onClick={closeDialog} variant={ButtonVariant.SECONDARY}>
                        {t('button.cancel')}
                    </Button>
                    <Button
                        qa-id="confirm-workspace-delete"
                        onClick={onDelete}
                        variant={ButtonVariant.PRIMARY_WARNING}>
                        {t('button.delete')}
                    </Button>
                </div>
            }>
            <p>{t('workspace-dialog.delete-confirm.guide')}</p>
            <p>
                <Trans
                    t={t}
                    i18nKey={'workspace-dialog.delete-confirm.confirm'}
                    values={{ name: currentDesign.name }}
                    components={{ strong: <i /> }}
                />
            </p>
        </Dialog>
    );
};
