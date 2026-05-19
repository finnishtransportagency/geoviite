import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { LayoutSwitchId } from 'track-layout/track-layout-model';
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

type SwitchSplitRevertConfirmationDialogProps = {
    layoutContext: LayoutContext;
    changesBeingReverted: ChangesBeingReverted;
    onSave: (id: LayoutSwitchId) => void;
    onClose: () => void;
};

const SwitchSplitRevertConfirmationDialog: React.FC<SwitchSplitRevertConfirmationDialogProps> = ({
    layoutContext,
    changesBeingReverted,
    onSave,
    onClose,
}) => {
    const { t } = useTranslation();

    const [isSaving, setIsSaving] = React.useState(false);

    const revertSwitch = () => {
        setIsSaving(true);
        revertPublicationCandidates(
            layoutContext.branch,
            changesBeingReverted.changeIncludingDependencies,
        )
            .then(() => {
                Snackbar.success('switch-split-revert-dialog.success');
                onSave(brand(changesBeingReverted.requestedRevertChange.source.id));
                onClose();
            })
            .catch(() => Snackbar.error('switch-split-revert-dialog.failed'))
            .finally(() => setIsSaving(false));
    };

    return (
        <Dialog
            title={t('switch-split-revert-dialog.title')}
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
                        {t('switch-split-revert-dialog.revert-draft-count', {
                            count: changesBeingReverted.changeIncludingDependencies.length,
                        })}
                    </Button>
                </div>
            }>
            <p>{t('switch-split-revert-dialog.guide')}</p>
            <PublicationRequestDependencyList
                layoutContext={layoutContext}
                changeTimes={getChangeTimes()}
                changesBeingReverted={changesBeingReverted}
            />
        </Dialog>
    );
};

export default SwitchSplitRevertConfirmationDialog;
