import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { LocationTrackId } from 'track-layout/track-layout-model';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import * as React from 'react';
import { useTranslation } from 'react-i18next';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { deleteLocationTrack } from 'track-layout/layout-location-track-api';
import { LayoutContext } from 'common/common-model';

type LocationTrackRevertConfirmationDialogProps = {
    layoutContext: LayoutContext;
    id: LocationTrackId;
    onSave?: (id: LocationTrackId) => void;
    onClose: () => void;
};

const LocationTrackRevertConfirmationDialog: React.FC<
    LocationTrackRevertConfirmationDialogProps
> = ({ layoutContext, id, onSave, onClose }: LocationTrackRevertConfirmationDialogProps) => {
    const { t } = useTranslation();

    const [isSaving, setIsSaving] = React.useState(false);

    const revertLocationTrack = (id: LocationTrackId) => {
        setIsSaving(true);
        deleteLocationTrack(layoutContext, id)
            .then(
                (locationTrackId) => {
                    Snackbar.success('tool-panel.location-track.revert-dialog.revert-succeeded');
                    onSave && onSave(locationTrackId);
                    onClose();
                },
                () => Snackbar.error('tool-panel.location-track.revert-dialog.revert-failed'),
            )
            .finally(() => setIsSaving(false));
    };

    return (
        <Dialog
            title={t('tool-panel.location-track.revert-dialog.revert-draft-confirm')}
            variant={DialogVariant.DARK}
            allowClose={false}
            footerContent={
                <div className={dialogStyles['dialog__footer-content--centered']}>
                    <Button disabled={isSaving} variant={ButtonVariant.WARNING} onClick={onClose}>
                        {t('button.cancel')}
                    </Button>
                    <Button
                        disabled={isSaving}
                        isProcessing={isSaving}
                        variant={ButtonVariant.PRIMARY_WARNING}
                        onClick={() => revertLocationTrack(id)}>
                        {t('button.revert-draft')}
                    </Button>
                </div>
            }>
            <p>{t('tool-panel.location-track.revert-dialog.can-be-reverted')}</p>
        </Dialog>
    );
};

export default LocationTrackRevertConfirmationDialog;
