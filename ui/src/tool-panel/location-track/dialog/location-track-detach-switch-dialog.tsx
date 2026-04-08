import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import React from 'react';
import { useTranslation } from 'react-i18next';
import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { detachSwitchFromLocationTrack } from 'track-layout/layout-location-track-api';
import { updateLocationTrackChangeTime } from 'common/change-time-api';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { LayoutContext } from 'common/common-model';
import { LayoutSwitchId, LocationTrackId } from 'track-layout/track-layout-model';

type LocationTrackDetachSwitchDialogProps = {
    layoutContext: LayoutContext;
    locationTrackId: LocationTrackId;
    locationTrackName: string;
    switchId: LayoutSwitchId;
    switchName: string;
    onClose: () => void;
    onDetached: () => void;
};

export const LocationTrackDetachSwitchDialog: React.FC<
    LocationTrackDetachSwitchDialogProps
> = ({
    layoutContext,
    locationTrackId,
    locationTrackName,
    switchId,
    switchName,
    onClose,
    onDetached,
}) => {
    const { t } = useTranslation();
    const [isDetaching, setIsDetaching] = React.useState(false);

    const detachSwitch = () => {
        setIsDetaching(true);
        detachSwitchFromLocationTrack(layoutContext.branch, locationTrackId, switchId)
            .then(() => updateLocationTrackChangeTime())
            .then(() => {
                Snackbar.success(
                    'tool-panel.location-track.detach-switch-links-dialog.success-toast',
                );
                onDetached();
                onClose();
            })
            .finally(() => setIsDetaching(false));
    };

    return (
        <Dialog
            title={t('tool-panel.location-track.detach-switch-links-dialog.title')}
            variant={DialogVariant.DARK}
            allowClose={true}
            onClose={onClose}
            footerContent={
                <>
                    <Button
                        onClick={onClose}
                        variant={ButtonVariant.SECONDARY}
                        disabled={isDetaching}>
                        {t('button.cancel')}
                    </Button>
                    <div className={dialogStyles['dialog__footer-content--right-aligned']}>
                        <Button
                            disabled={isDetaching}
                            isProcessing={isDetaching}
                            variant={ButtonVariant.PRIMARY_WARNING}
                            onClick={() => detachSwitch()}>
                            {t(
                                'tool-panel.location-track.detach-switch-links-dialog.detach-button',
                            )}
                        </Button>
                    </div>
                </>
            }>
            <div className={'dialog__text'}>
                {t('tool-panel.location-track.detach-switch-links-dialog.message', {
                    switchName: switchName,
                    trackName: locationTrackName,
                })}
            </div>
        </Dialog>
    );
};
