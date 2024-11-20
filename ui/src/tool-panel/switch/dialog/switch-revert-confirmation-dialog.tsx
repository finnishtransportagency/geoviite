import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { LayoutSwitchId } from 'track-layout/track-layout-model';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { deleteDraftSwitch } from 'track-layout/layout-switch-api';
import { LayoutContext } from 'common/common-model';

type SwitchRevertConfirmationDialogProps = {
    layoutContext: LayoutContext;
    switchId: LayoutSwitchId;
    onSave: (id: LayoutSwitchId) => void;
    onClose: () => void;
};

const SwitchRevertConfirmationDialog: React.FC<SwitchRevertConfirmationDialogProps> = ({
    layoutContext,
    switchId,
    onSave,
    onClose,
}) => {
    const { t } = useTranslation();

    const [isSaving, setIsSaving] = React.useState(false);

    const revertSwitch = () => {
        setIsSaving(true);
        deleteDraftSwitch(layoutContext, switchId)
            .then((id) => {
                if (id) {
                    Snackbar.success('switch-revert-dialog.success');
                    onSave(id);
                    onClose();
                }
            })
            .finally(() => setIsSaving(false));
    };

    return (
        <Dialog
            title={t('switch-revert-dialog.title')}
            variant={DialogVariant.DARK}
            allowClose={false}
            footerContent={
                <div className={dialogStyles['dialog__footer-content--centered']}>
                    <Button disabled={isSaving} variant={ButtonVariant.SECONDARY} onClick={onClose}>
                        {t('button.cancel')}
                    </Button>
                    <Button
                        disabled={isSaving}
                        isProcessing={isSaving}
                        variant={ButtonVariant.PRIMARY_WARNING}
                        onClick={revertSwitch}>
                        {t('button.revert-draft')}
                    </Button>
                </div>
            }>
            <p>{t('switch-revert-dialog.guide')}</p>
        </Dialog>
    );
};

export default SwitchRevertConfirmationDialog;
