import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { LayoutSwitchId } from 'track-layout/track-layout-model';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { deleteDraftSwitch } from 'track-layout/layout-switch-api';

type SwitchDeleteConfirmationDialogProps = {
    switchId: LayoutSwitchId;
    onSave: (id: LayoutSwitchId) => void;
    onClose: () => void;
};

const SwitchDeleteConfirmationDialog: React.FC<SwitchDeleteConfirmationDialogProps> = ({
    switchId,
    onSave,
    onClose,
}) => {
    const { t } = useTranslation();
    const deleteSwitch = () => {
        deleteDraftSwitch(switchId).then((id) => {
            if (id) {
                Snackbar.success('switch-delete-dialog.success');
                onSave(id);
                onClose();
            }
        });
    };

    return (
        <Dialog
            title={t('switch-delete-dialog.title')}
            variant={DialogVariant.DARK}
            allowClose={false}
            footerContent={
                <div className={dialogStyles['dialog__footer-content--centered']}>
                    <Button variant={ButtonVariant.SECONDARY} onClick={onClose}>
                        {t('button.cancel')}
                    </Button>
                    <Button variant={ButtonVariant.PRIMARY_WARNING} onClick={deleteSwitch}>
                        {t('button.delete')}
                    </Button>
                </div>
            }>
            <p>{t('switch-delete-dialog.guide')}</p>
        </Dialog>
    );
};

export default SwitchDeleteConfirmationDialog;
