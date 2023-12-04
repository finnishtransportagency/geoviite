import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { LayoutTrackNumberId } from 'track-layout/track-layout-model';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import * as React from 'react';
import { useTranslation } from 'react-i18next';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { ChangesBeingReverted } from 'preview/preview-view';
import {
    onlyDependencies,
    PublicationRequestDependencyList,
} from 'preview/publication-request-dependency-list';
import { ChangeTimes } from 'common/common-slice';
import { revertCandidates } from 'publication/publication-api';
import { getChangeTimes } from 'common/change-time-api';

type TrackNumberDeleteConfirmationDialogProps = {
    changesBeingReverted: ChangesBeingReverted;
    onSave?: (trackNumberId: LayoutTrackNumberId) => void;
    onClose: () => void;
    changeTimes: ChangeTimes;
};

const TrackNumberDeleteConfirmationDialog: React.FC<TrackNumberDeleteConfirmationDialogProps> = ({
    changesBeingReverted,
    onSave,
    onClose,
}: TrackNumberDeleteConfirmationDialogProps) => {
    const { t } = useTranslation();

    const deleteDraftLocationTrack = () => {
        revertCandidates(changesBeingReverted.changeIncludingDependencies).then((result) => {
            result
                .map(() => {
                    Snackbar.success('tool-panel.track-number.delete-dialog.delete-succeeded');
                    onSave && onSave(changesBeingReverted.requestedRevertChange.id);
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
                    <Button variant={ButtonVariant.SECONDARY} onClick={onClose}>
                        {t('button.cancel')}
                    </Button>
                    <Button
                        variant={ButtonVariant.PRIMARY_WARNING}
                        onClick={deleteDraftLocationTrack}>
                        {t('button.delete')}
                    </Button>
                </div>
            }>
            <p>{t('tool-panel.track-number.delete-dialog.can-be-deleted')}</p>
            <PublicationRequestDependencyList
                changeTimes={getChangeTimes()}
                dependencies={onlyDependencies(changesBeingReverted)}
            />
        </Dialog>
    );
};

export default TrackNumberDeleteConfirmationDialog;
