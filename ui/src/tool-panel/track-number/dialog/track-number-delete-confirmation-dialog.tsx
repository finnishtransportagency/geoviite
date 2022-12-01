import { Dialog, DialogVariant } from 'vayla-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { LayoutTrackNumberId, LocationTrackId } from 'track-layout/track-layout-model';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import * as React from 'react';
import {
    actions,
    initialLocationTrackEditState,
    reducer,
} from 'tool-panel/location-track/dialog/location-track-edit-store';
import { createDelegates } from 'store/store-utils';
import { useTranslation } from 'react-i18next';
import dialogStyles from 'vayla-design-lib/dialog/dialog.scss';
import { deleteTrackNumber } from 'track-layout/layout-track-number-api';

type TrackNumberDeleteConfirmationDialogProps = {
    id: LayoutTrackNumberId;
    onClose: () => void;
    onCancel: () => void;
};

const TrackNumberDeleteConfirmationDialog: React.FC<TrackNumberDeleteConfirmationDialogProps> = ({
    id,
    onClose,
    onCancel,
}: TrackNumberDeleteConfirmationDialogProps) => {
    const { t } = useTranslation();
    const [, dispatcher] = React.useReducer(reducer, initialLocationTrackEditState);
    const stateActions = createDelegates(dispatcher, actions);

    const deleteDraftLocationTrack = (id: LocationTrackId) => {
        deleteTrackNumber(id)
            .then((result) => {
                result
                    .map((trackNumberId) => {
                        stateActions.onSaveSucceed(trackNumberId);
                        Snackbar.success(
                            t('tool-panel.track-number.delete-dialog.delete-succeeded'),
                        );
                        onClose();
                    })
                    .mapErr(() => {
                        stateActions.onSaveFailed();
                        Snackbar.error(t('tool-panel.track-number.delete-dialog.delete-failed'));
                        onClose();
                    });
            })
            .catch(() => {
                stateActions.onSaveFailed();
                onClose();
            });
    };

    return (
        <React.Fragment>
            <Dialog
                title={t('tool-panel.track-number.delete-dialog.delete-draft-confirm')}
                variant={DialogVariant.DARK}
                allowClose={false}
                className={dialogStyles['dialog--narrow']}
                footerContent={
                    <React.Fragment>
                        <Button onClick={onCancel} variant={ButtonVariant.SECONDARY}>
                            {t('button.cancel')}
                        </Button>
                        <Button onClick={() => deleteDraftLocationTrack(id)}>
                            {t('button.delete')}
                        </Button>
                    </React.Fragment>
                }>
                <div>{t('tool-panel.track-number.delete-dialog.can-be-deleted')}</div>
            </Dialog>
        </React.Fragment>
    );
};

export default TrackNumberDeleteConfirmationDialog;
