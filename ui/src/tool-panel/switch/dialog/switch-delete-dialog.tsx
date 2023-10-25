import { Dialog, DialogVariant } from 'vayla-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { LayoutSwitchId } from 'track-layout/track-layout-model';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import dialogStyles from 'vayla-design-lib/dialog/dialog.scss';
import { deleteDraftSwitch } from 'track-layout/layout-switch-api';

type SwitchDeleteDialogProps = {
    switchId: LayoutSwitchId;
    onSave: (id: LayoutSwitchId) => void;
    onClose: () => void;
};

const SwitchDeleteDialog: React.FC<SwitchDeleteDialogProps> = ({ switchId, onSave, onClose }) => {
    const { t } = useTranslation();
    const deleteSwitch = () => {
        deleteDraftSwitch(switchId).then((id) => {
            if (id) {
                Snackbar.success(t('switch-delete-dialog.success'));
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
                    <Button onClick={onClose} variant={ButtonVariant.SECONDARY}>
                        {t('button.cancel')}
                    </Button>
                    <Button onClick={deleteSwitch}>{t('button.delete')}</Button>
                </div>
            }>
            <p>{t('switch-delete-dialog.guide')}</p>
        </Dialog>
    );
};

export default SwitchDeleteDialog;
