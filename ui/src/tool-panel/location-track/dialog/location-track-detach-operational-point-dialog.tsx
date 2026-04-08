import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import React from 'react';
import { useTranslation } from 'react-i18next';
import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { unlinkLocationTracksFromOperationalPoint } from 'track-layout/layout-location-track-api';
import { updateAllChangeTimes } from 'common/change-time-api';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { LayoutContext } from 'common/common-model';
import { LocationTrackId, OperationalPointId } from 'track-layout/track-layout-model';

type LocationTrackDetachOperationalPointDialogProps = {
    layoutContext: LayoutContext;
    locationTrackId: LocationTrackId;
    locationTrackName: string;
    operationalPointId: OperationalPointId;
    operationalPointName: string;
    onClose: () => void;
    onDetached: () => void;
};

export const LocationTrackDetachOperationalPointDialog: React.FC<
    LocationTrackDetachOperationalPointDialogProps
> = ({
    layoutContext,
    locationTrackId,
    locationTrackName,
    operationalPointId,
    operationalPointName,
    onClose,
    onDetached,
}) => {
    const { t } = useTranslation();
    const [isDetaching, setIsDetaching] = React.useState(false);

    const detachOperationalPoint = () => {
        setIsDetaching(true);
        unlinkLocationTracksFromOperationalPoint(
            layoutContext.branch,
            [locationTrackId],
            operationalPointId,
        )
            .then(async () => {
                await updateAllChangeTimes();
                Snackbar.success(
                    t(
                        'tool-panel.location-track.detach-operational-point-links-dialog.success-toast',
                        {
                            operationalPointName: operationalPointName,
                            trackName: locationTrackName,
                        },
                    ),
                );
                onDetached();
                onClose();
            })
            .finally(() => setIsDetaching(false));
    };

    return (
        <Dialog
            title={t(
                'tool-panel.location-track.detach-operational-point-links-dialog.title',
            )}
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
                            onClick={() => detachOperationalPoint()}>
                            {t(
                                'tool-panel.location-track.detach-operational-point-links-dialog.detach-button',
                            )}
                        </Button>
                    </div>
                </>
            }>
            <div className={'dialog__text'}>
                {t(
                    'tool-panel.location-track.detach-operational-point-links-dialog.message',
                    {
                        operationalPointName: operationalPointName,
                        trackName: locationTrackName,
                    },
                )}
            </div>
        </Dialog>
    );
};
