import * as React from 'react';
import { Dialog, DialogVariant } from 'vayla-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { useTranslation } from 'react-i18next';
import { LocationTrackId, LocationTrackStartAndEndPoints } from 'track-layout/track-layout-model';
import {
    useLocationTrack,
    useLocationTrackStartAndEnd,
} from 'track-layout/track-layout-react-utils';
import styles from 'tool-panel/location-track/dialog/location-track-ratko-push-dialog.scss';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { Dropdown, DropdownSize } from 'vayla-design-lib/dropdown/dropdown';
import { KmNumber } from 'common/common-model';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { pushLocationTracksToRatko } from 'publication/ratko-api';

export type LocationTrackRatkoPushDialogProps = {
    locationTrackId: LocationTrackId;
    onClose: () => void;
};

function getKmOptions(startAndEnd: LocationTrackStartAndEndPoints): KmNumber[] {
    const start = Number.parseInt(startAndEnd.start.addressPoint.address.kmNumber || '');
    const end = Number.parseInt(startAndEnd.end.addressPoint.address.kmNumber || '');
    if (start && end) {
        const kms = [];
        for (let km = start; km <= end; km++) {
            kms.push(km + '');
        }
        return kms;
    }
    return [];
}

export const LocationTrackRatkoPushDialog: React.FC<LocationTrackRatkoPushDialogProps> = (
    props: LocationTrackRatkoPushDialogProps,
) => {
    const { t } = useTranslation();
    const locationTrack = useLocationTrack(props.locationTrackId, 'OFFICIAL', undefined);
    const startAndEndPoints = useLocationTrackStartAndEnd(locationTrack?.id, 'OFFICIAL');
    const [startKm, setStartKm] = React.useState<KmNumber>();
    const [endKm, setEndKm] = React.useState<KmNumber>();
    const kmOptions = startAndEndPoints && getKmOptions(startAndEndPoints);
    const [pushing, setPushing] = React.useState(false);
    const canPush = startKm && endKm && !pushing;
    const [pushDone, setPushDone] = React.useState(false);

    async function pushToRatko() {
        if (locationTrack && startKm && endKm && !pushing) {
            try {
                setPushing(true);
                const kms: KmNumber[] = [];
                for (let km = Number.parseInt(startKm); km <= Number.parseInt(endKm); km++) {
                    kms.push(km + '');
                }
                await pushLocationTracksToRatko([
                    {
                        locationTrackId: locationTrack.id,
                        changedKmNumbers: kms,
                    },
                ]);
                setPushDone(true);
            } finally {
                setPushing(false);
            }
        }
    }

    return (
        <React.Fragment>
            <Dialog
                title={
                    pushDone
                        ? t('tool-panel.location-track.ratko-push-dialog.title-done')
                        : t('tool-panel.location-track.ratko-push-dialog.title')
                }
                variant={DialogVariant.DARK}
                allowClose={false}
                footerContent={
                    <React.Fragment>
                        {!pushDone && (
                            <Button onClick={props.onClose} variant={ButtonVariant.SECONDARY}>
                                {t('button.cancel')}
                            </Button>
                        )}
                        {!pushDone && (
                            <Button
                                onClick={pushToRatko}
                                disabled={!canPush}
                                isProcessing={pushing}>
                                {t('tool-panel.location-track.ratko-push-dialog.push')}
                            </Button>
                        )}
                        {pushDone && <Button onClick={props.onClose}>{t('button.ok')}</Button>}
                    </React.Fragment>
                }
                scrollable={false}>
                <div className={styles['location-track-ratko-push-dialog']}>
                    {!pushDone && (
                        <React.Fragment>
                            <div
                                className={styles['location-track-ratko-push-dialog__warning-msg']}>
                                <span
                                    className={
                                        styles['location-track-ratko-push-dialog__warning-icon']
                                    }>
                                    <Icons.StatusError color={IconColor.INHERIT} />
                                </span>{' '}
                                {t('tool-panel.location-track.ratko-push-dialog.warning-msg')}
                            </div>
                            <FieldLayout
                                label={t('tool-panel.location-track.track-name')}
                                value={locationTrack?.name}
                            />
                            <FieldLayout
                                label={t('tool-panel.location-track.ratko-push-dialog.km-range')}
                                value={
                                    <div>
                                        <Dropdown
                                            value={startKm}
                                            options={kmOptions?.map((km) => ({
                                                name: km,
                                                value: km,
                                            }))}
                                            onChange={(value) => value && setStartKm(value)}
                                            size={DropdownSize.SMALL}
                                        />
                                        <span
                                            className={
                                                styles[
                                                    'location-track-ratko-push-dialog__field-separator'
                                                ]
                                            }>
                                            -
                                        </span>
                                        <Dropdown
                                            value={endKm}
                                            options={kmOptions?.map((km) => ({
                                                name: km,
                                                value: km,
                                            }))}
                                            onChange={(value) => value && setEndKm(value)}
                                            size={DropdownSize.SMALL}
                                        />
                                    </div>
                                }
                            />
                        </React.Fragment>
                    )}
                    {pushDone && (
                        <React.Fragment>
                            <div className={styles['location-track-ratko-push-dialog__done']}>
                                {/*{t('tool-panel.location-track.ratko-push-dialog.success-msg')}*/}
                                <div
                                    className={
                                        styles['location-track-ratko-push-dialog__done-icon']
                                    }>
                                    <Icons.Tick size={IconSize.LARGE} color={IconColor.INHERIT} />
                                </div>
                            </div>
                        </React.Fragment>
                    )}
                </div>
            </Dialog>
        </React.Fragment>
    );
};
