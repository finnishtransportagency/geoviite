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
    onDelete: () => void;
    onCancel: () => void;
};

const SwitchDeleteDialog: React.FC<SwitchDeleteDialogProps> = ({
    switchId,
    onDelete,
    onCancel,
}) => {
    const { t } = useTranslation();
    const deleteSwitch = () => {
        deleteDraftSwitch(switchId).then((r) => {
            if (r) {
                Snackbar.success(t('switch-delete-dialog.success'));
                onDelete();
            }
        });
    };

    return (
        <Dialog
            title={t('switch-delete-dialog.title')}
            variant={DialogVariant.DARK}
            allowClose={false}
            className={dialogStyles['dialog--normal']}
            footerContent={
                <React.Fragment>
                    <Button onClick={onCancel} variant={ButtonVariant.SECONDARY}>
                        {t('button.cancel')}
                    </Button>
                    <Button onClick={deleteSwitch}>{t('button.delete')}</Button>
                </React.Fragment>
            }>
            <p>{t('switch-delete-dialog.guide')}</p>
        </Dialog>
    );
};

export default SwitchDeleteDialog;
