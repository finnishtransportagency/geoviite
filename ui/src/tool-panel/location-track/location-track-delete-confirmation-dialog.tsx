import { Dialog, DialogVariant } from 'vayla-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { LocationTrackId } from 'track-layout/track-layout-model';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import * as React from 'react';
import { useTranslation } from 'react-i18next';
import dialogStyles from 'vayla-design-lib/dialog/dialog.scss';
import { deleteLocationTrack } from 'track-layout/layout-location-track-api';

type LocationTrackDeleteConfirmationDialogProps = {
    id: LocationTrackId;
    onSave?: (id: LocationTrackId) => void;
    onClose: () => void;
};

const LocationTrackDeleteConfirmationDialog: React.FC<
    LocationTrackDeleteConfirmationDialogProps
> = ({ id, onSave, onClose }: LocationTrackDeleteConfirmationDialogProps) => {
    const { t } = useTranslation();

    const deleteDraftLocationTrack = (id: LocationTrackId) => {
        deleteLocationTrack(id).then((result) => {
            result
                .map((locationTrackId) => {
                    Snackbar.success(t('tool-panel.location-track.delete-dialog.delete-succeeded'));
                    onSave && onSave(locationTrackId);
                    onClose();
                })
                .mapErr(() => {
                    Snackbar.error(t('tool-panel.location-track.delete-dialog.delete-failed'));
                });
        });
    };

    return (
        <Dialog
            title={t('tool-panel.location-track.delete-dialog.delete-draft-confirm')}
            variant={DialogVariant.DARK}
            allowClose={false}
            footerContent={
                <div className={dialogStyles['dialog__footer-content--centered']}>
                    <Button onClick={onClose} variant={ButtonVariant.SECONDARY}>
                        {t('button.cancel')}
                    </Button>
                    <Button onClick={() => deleteDraftLocationTrack(id)}>
                        {t('button.delete')}
                    </Button>
                </div>
            }>
            <p>{t('tool-panel.location-track.delete-dialog.can-be-deleted')}</p>
        </Dialog>
    );
};

export default LocationTrackDeleteConfirmationDialog;
