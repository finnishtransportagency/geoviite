import * as React from 'react';
import styles from './preview-view.scss';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { useTranslation } from 'react-i18next';
import { getLayoutDesignByBranch } from 'track-layout/layout-design-api';
import { useLoader } from 'utils/react-utils';
import { DesignBranch } from 'common/common-model';
import { getChangeTimes } from 'common/change-time-api';

export type PreviewMergeToMainConfirmationDialogProps = {
    designBranch: DesignBranch;
    isPublishing: boolean;
    onCancel: () => void;
    candidateCount: number;
    mergeToMain: () => void;
};

export const PreviewMergeToMainConfirmationDialog: React.FC<
    PreviewMergeToMainConfirmationDialogProps
> = ({ designBranch, isPublishing, onCancel, candidateCount, mergeToMain }) => {
    const { t } = useTranslation();

    const design =
        useLoader(
            () => getLayoutDesignByBranch(getChangeTimes().layoutDesign, designBranch),
            [designBranch],
        )?.name ?? '';

    return (
        <Dialog
            title={t('publish.merge-to-main-confirm.title')}
            variant={DialogVariant.LIGHT}
            allowClose={!isPublishing}
            onClose={onCancel}
            footerContent={
                <div className={dialogStyles['dialog__footer-content--centered']}>
                    <Button
                        onClick={onCancel}
                        disabled={candidateCount === 0 || isPublishing}
                        variant={ButtonVariant.SECONDARY}>
                        {t('publish.merge-to-main-confirm.cancel')}
                    </Button>
                    <Button
                        qa-id={'publication-confirm'}
                        disabled={isPublishing}
                        isProcessing={isPublishing}
                        onClick={mergeToMain}>
                        {t('publish.merge-to-main-confirm.confirm', {
                            candidates: candidateCount,
                        })}
                    </Button>
                </div>
            }>
            <div className={styles['preview-confirm__description']}>
                {t('publish.merge-to-main-confirm.description', { design, candidateCount })}
            </div>
        </Dialog>
    );
};
