import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { useTrackLayoutAppSelector } from 'store/hooks';
import { planDownloadAssetIdFromToolPanelAsset } from 'map/plan-download/plan-download-store';

type ConfirmMoveToMainOfficialDialogContainerProps = {
    onClose: () => void;
};

export const ConfirmMoveToMainOfficialDialogContainer: React.FC<
    ConfirmMoveToMainOfficialDialogContainerProps
> = ({ onClose }) => {
    const delegates = createDelegates(TrackLayoutActions);
    const state = useTrackLayoutAppSelector((state) => state);
    const initialAsset = state.selectedToolPanelTab
        ? planDownloadAssetIdFromToolPanelAsset(state.selectedToolPanelTab)
        : undefined;

    return (
        <ConfirmMoveToMainOfficialDialog
            moveToMainOfficial={() => delegates.onLayoutContextModeChange('MAIN_OFFICIAL')}
            openPlanDownloadDialog={() => delegates.onOpenPlanDownloadPopup(initialAsset)}
            onClose={onClose}
        />
    );
};

type ConfirmMoveToMainOfficialDialogProps = {
    onClose: () => void;
    openPlanDownloadDialog: () => void;
    moveToMainOfficial: () => void;
};

export const ConfirmMoveToMainOfficialDialog: React.FC<ConfirmMoveToMainOfficialDialogProps> = ({
    onClose,
    moveToMainOfficial,
    openPlanDownloadDialog,
}) => {
    const { t } = useTranslation();

    const onMoveToMainOfficial = () => {
        moveToMainOfficial();
        openPlanDownloadDialog();
        onClose();
    };

    return (
        <Dialog
            onClose={onClose}
            variant={DialogVariant.DARK}
            title={t('plan-download.move-to-official-confirm-title')}
            footerContent={
                <div className={dialogStyles['dialog__footer-content--centered']}>
                    <Button variant={ButtonVariant.SECONDARY} onClick={onClose}>
                        {t('button.cancel')}
                    </Button>
                    <Button variant={ButtonVariant.PRIMARY} onClick={onMoveToMainOfficial}>
                        {t('plan-download.move-to-official')}
                    </Button>
                </div>
            }>
            <p>{t('plan-download.move-to-official-confirm-content')}</p>
        </Dialog>
    );
};
