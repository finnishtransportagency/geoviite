import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { LayoutTrackNumberId } from 'track-layout/track-layout-model';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import * as React from 'react';
import { useTranslation } from 'react-i18next';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { PublicationRequestDependencyList } from 'preview/publication-request-dependency-list';
import { revertPublicationCandidates } from 'publication/publication-api';
import { getChangeTimes } from 'common/change-time-api';
import { ChangesBeingReverted } from 'preview/preview-view';
import { LayoutContext } from 'common/common-model';
import { brand } from 'common/brand';

type TrackNumberRevertConfirmationDialogProps = {
    layoutContext: LayoutContext;
    changesBeingReverted: ChangesBeingReverted;
    onSave?: (trackNumberId: LayoutTrackNumberId) => void;
    onClose: () => void;
};

const TrackNumberRevertConfirmationDialog: React.FC<TrackNumberRevertConfirmationDialogProps> = ({
    layoutContext,
    changesBeingReverted,
    onSave,
    onClose,
}: TrackNumberRevertConfirmationDialogProps) => {
    const { t } = useTranslation();

    const [isSaving, setIsSaving] = React.useState(false);

    const revertLocationTrack = () => {
        setIsSaving(true);
        revertPublicationCandidates(
            layoutContext.branch,
            changesBeingReverted.changeIncludingDependencies,
        )
            .then((result) => {
                result
                    .map(() => {
                        Snackbar.success('tool-panel.track-number.revert-dialog.revert-succeeded');
                        onSave &&
                            onSave(brand(changesBeingReverted.requestedRevertChange.source.id));
                        onClose();
                    })
                    .mapErr(() => {
                        Snackbar.error('tool-panel.track-number.revert-dialog.revert-failed');
                    });
            })
            .finally(() => setIsSaving(false));
    };

    return (
        <Dialog
            title={t('tool-panel.track-number.revert-dialog.revert-draft-confirm')}
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
                        onClick={revertLocationTrack}>
                        {t('button.revert-draft')}
                    </Button>
                </div>
            }>
            <p>{t('tool-panel.track-number.revert-dialog.can-be-reverted')}</p>
            <PublicationRequestDependencyList
                layoutContext={layoutContext}
                changeTimes={getChangeTimes()}
                changesBeingReverted={changesBeingReverted}
            />
        </Dialog>
    );
};

export default TrackNumberRevertConfirmationDialog;
