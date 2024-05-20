import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { LayoutDesign, updateLayoutDesign } from 'track-layout/layout-design-api';
import { updateLayoutDesignChangeTime } from 'common/change-time-api';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { LayoutContext } from 'common/common-model';

type WorkspaceDeleteConfirmDialogProps = {
    closeDialog: () => void;
    currentDesign: LayoutDesign;
    onLayoutContextChange: (layoutContext: LayoutContext) => void;
};

export const WorkspaceDeleteConfirmDialog: React.FC<WorkspaceDeleteConfirmDialogProps> = ({
    currentDesign,
    onLayoutContextChange,
    closeDialog,
}) => {
    const { t } = useTranslation();
    const onDelete = () => {
        updateLayoutDesign(currentDesign.id, {
            ...currentDesign,
            designState: 'DELETED',
        }).then(() => {
            updateLayoutDesignChangeTime();
            onLayoutContextChange({
                publicationState: 'OFFICIAL',
                designId: undefined,
            });
            closeDialog();
        });
    };

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
            <p>{t('workspace-dialog.delete-confirm.confirm')}</p>
        </Dialog>
    );
};
