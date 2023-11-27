import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import * as React from 'react';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { useTranslation } from 'react-i18next';
import { ChangesBeingReverted } from 'preview/preview-view';
import {
    onlyDependencies,
    PublicationRequestDependencyList,
    publicationRequestTypeTranslationKey,
} from 'preview/publication-request-dependency-list';
import { getChangeTimes } from 'common/change-time-api';

export interface PreviewRejectConfirmDialogProps {
    changesBeingReverted: ChangesBeingReverted;
    confirmRevertChanges: () => void;
    cancelRevertChanges: () => void;
}

export const PreviewConfirmRevertChangesDialog: React.FC<PreviewRejectConfirmDialogProps> = ({
    changesBeingReverted,
    cancelRevertChanges,
    confirmRevertChanges,
}) => {
    const { t } = useTranslation();
    const [isReverting, setIsReverting] = React.useState(false);

    return (
        <Dialog
            title={t('publish.revert-confirm.title')}
            variant={DialogVariant.LIGHT}
            allowClose={!isReverting}
            onClose={cancelRevertChanges}
            footerContent={
                <div className={dialogStyles['dialog__footer-content--centered']}>
                    <Button
                        onClick={cancelRevertChanges}
                        disabled={isReverting}
                        variant={ButtonVariant.SECONDARY}>
                        {t('publish.revert-confirm.cancel')}
                    </Button>
                    <Button
                        icon={Icons.Delete}
                        disabled={isReverting}
                        isProcessing={isReverting}
                        variant={ButtonVariant.WARNING}
                        onClick={() => {
                            setIsReverting(true);
                            confirmRevertChanges();
                        }}>
                        {t('publish.revert-confirm.confirm')}
                    </Button>
                </div>
            }>
            <div>{`${t('publish.revert-confirm.description')} ${t(
                `publish.revert-confirm.revert-target.${publicationRequestTypeTranslationKey(
                    changesBeingReverted.requestedRevertChange.type,
                )}`,
            )} ${changesBeingReverted.requestedRevertChange.name}?`}</div>
            <PublicationRequestDependencyList
                changeTimes={getChangeTimes()}
                dependencies={onlyDependencies(changesBeingReverted)}
            />
        </Dialog>
    );
};
