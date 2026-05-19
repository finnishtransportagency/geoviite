import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { LocationTrackId } from 'track-layout/track-layout-model';
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

type LocationTrackSplitRevertConfirmationDialogProps = {
    layoutContext: LayoutContext;
    changesBeingReverted: ChangesBeingReverted;
    onSave?: (id: LocationTrackId) => void;
    onClose: () => void;
};

const LocationTrackSplitRevertConfirmationDialog: React.FC<
    LocationTrackSplitRevertConfirmationDialogProps
> = ({
    layoutContext,
    changesBeingReverted,
    onSave,
    onClose,
}: LocationTrackSplitRevertConfirmationDialogProps) => {
    const { t } = useTranslation();

    const [isSaving, setIsSaving] = React.useState(false);

    const revertLocationTrack = () => {
        setIsSaving(true);
        revertPublicationCandidates(
            layoutContext.branch,
            changesBeingReverted.changeIncludingDependencies,
        )
            .then(
                () => {
                    Snackbar.success('tool-panel.location-track.split-revert-dialog.success');
                    onSave && onSave(brand(changesBeingReverted.requestedRevertChange.source.id));
                    onClose();
                },
                () => Snackbar.error('tool-panel.location-track.split-revert-dialog.failed'),
            )
            .finally(() => setIsSaving(false));
    };

    return (
        <Dialog
            title={t('tool-panel.location-track.split-revert-dialog.title')}
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
                        onClick={revertLocationTrack}>
                        {t('tool-panel.location-track.split-revert-dialog.revert-draft-count', {
                            count: changesBeingReverted.changeIncludingDependencies.length,
                        })}
                    </Button>
                </div>
            }>
            <p>{t('tool-panel.location-track.split-revert-dialog.guide')}</p>
            <PublicationRequestDependencyList
                layoutContext={layoutContext}
                changeTimes={getChangeTimes()}
                changesBeingReverted={changesBeingReverted}
            />
        </Dialog>
    );
};

export default LocationTrackSplitRevertConfirmationDialog;
