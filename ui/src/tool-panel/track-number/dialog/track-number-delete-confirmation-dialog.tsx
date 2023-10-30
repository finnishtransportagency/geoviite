import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { LayoutTrackNumberId, LocationTrackId } from 'track-layout/track-layout-model';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import * as React from 'react';
import { useTranslation } from 'react-i18next';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { deleteTrackNumber } from 'track-layout/layout-track-number-api';

type TrackNumberDeleteConfirmationDialogProps = {
    id: LayoutTrackNumberId;
    onSave?: (trackNumberId: LayoutTrackNumberId) => void;
    onClose: () => void;
};

const TrackNumberDeleteConfirmationDialog: React.FC<TrackNumberDeleteConfirmationDialogProps> = ({
    id,
    onSave,
    onClose,
}: TrackNumberDeleteConfirmationDialogProps) => {
    const { t } = useTranslation();

    const deleteDraftLocationTrack = (id: LocationTrackId) => {
        deleteTrackNumber(id).then((result) => {
            result
                .map((trackNumberId) => {
                    Snackbar.success('tool-panel.track-number.delete-dialog.delete-succeeded');
                    onSave && onSave(trackNumberId);
                    onClose();
                })
                .mapErr(() => {
                    Snackbar.error('tool-panel.track-number.delete-dialog.delete-failed');
                });
        });
    };

    return (
        <Dialog
            title={t('tool-panel.track-number.delete-dialog.delete-draft-confirm')}
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
            <p>{t('tool-panel.track-number.delete-dialog.can-be-deleted')}</p>
        </Dialog>
    );
};

export default TrackNumberDeleteConfirmationDialog;
