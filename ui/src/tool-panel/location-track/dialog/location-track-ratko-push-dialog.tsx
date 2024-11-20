import * as React from 'react';
import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { useTranslation } from 'react-i18next';
import { AlignmentStartAndEnd, LocationTrackId } from 'track-layout/track-layout-model';
import {
    useLocationTrack,
    useLocationTrackStartAndEnd,
} from 'track-layout/track-layout-react-utils';
import styles from 'tool-panel/location-track/dialog/location-track-ratko-push-dialog.scss';
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';
import { Dropdown, DropdownSize, Item } from 'vayla-design-lib/dropdown/dropdown';
import { KmNumber, LayoutContext, officialLayoutContext } from 'common/common-model';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { pushLocationTracksToRatko } from 'ratko/ratko-api';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { ChangeTimes } from 'common/common-slice';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';

export type LocationTrackRatkoPushDialogProps = {
    layoutContext: LayoutContext;
    locationTrackId: LocationTrackId;
    changeTimes: ChangeTimes;
    onClose: () => void;
};

function getKmOptions(startAndEnd: AlignmentStartAndEnd): Item<string>[] {
    const start = Number.parseInt(startAndEnd.start?.address?.kmNumber || '');
    const end = Number.parseInt(startAndEnd.end?.address?.kmNumber || '');

    if (!Number.isNaN(start) && !Number.isNaN(end)) {
        return getKmsInRange(start, end).map((km) => ({
            name: km,
            value: km,
            qaId: km,
        }));
    }

    return [];
}

const getKmsInRange = (start: number, end: number) => {
    const kms = [];
    for (let km = start; km <= end; km++) {
        kms.push(km + '');
    }

    return kms;
};

export const LocationTrackRatkoPushDialog: React.FC<LocationTrackRatkoPushDialogProps> = (
    props: LocationTrackRatkoPushDialogProps,
) => {
    const { t } = useTranslation();
    const locationTrack = useLocationTrack(
        props.locationTrackId,
        officialLayoutContext(props.layoutContext),
        props.changeTimes.layoutLocationTrack,
    );

    const [startAndEndPoints, _] = useLocationTrackStartAndEnd(
        locationTrack?.id,
        officialLayoutContext(props.layoutContext),
        props.changeTimes,
    );
    const [startKm, setStartKm] = React.useState<KmNumber>();
    const [endKm, setEndKm] = React.useState<KmNumber>();
    const kmOptions = startAndEndPoints && getKmOptions(startAndEndPoints);
    const canPush = !!startKm && !!endKm;

    async function pushToRatko() {
        if (locationTrack && startKm && endKm) {
            try {
                const kms = getKmsInRange(Number.parseInt(startKm), Number.parseInt(endKm));

                pushLocationTracksToRatko([
                    {
                        locationTrackId: locationTrack.id,
                        changedKmNumbers: kms,
                    },
                ]);
                Snackbar.success(
                    t('tool-panel.location-track.ratko-push-dialog.pushing-started'),
                    t('tool-panel.location-track.ratko-push-dialog.follow-in-ratko'),
                );
            } finally {
                props.onClose();
            }
        }
    }

    return (
        <Dialog
            title={t('tool-panel.location-track.ratko-push-dialog.title')}
            variant={DialogVariant.DARK}
            allowClose={false}
            footerContent={
                <div className={dialogStyles['dialog__footer-content--centered']}>
                    <>
                        <Button onClick={props.onClose} variant={ButtonVariant.SECONDARY}>
                            {t('button.cancel')}
                        </Button>
                        <Button onClick={pushToRatko} disabled={!canPush}>
                            {t('tool-panel.location-track.ratko-push-dialog.push')}
                        </Button>
                    </>
                </div>
            }>
            <div className={styles['location-track-ratko-push-dialog']}>
                <React.Fragment>
                    <div className={styles['location-track-ratko-push-dialog__warning-msg']}>
                        <span className={styles['location-track-ratko-push-dialog__warning-icon']}>
                            <Icons.StatusError color={IconColor.INHERIT} />
                        </span>
                        {t('tool-panel.location-track.ratko-push-dialog.warning-msg')}
                    </div>
                    <FieldLayout
                        label={t('tool-panel.location-track.track-name')}
                        value={locationTrack?.name}
                    />
                    <FieldLayout
                        label={t('tool-panel.location-track.ratko-push-dialog.km-range')}
                        value={
                            <React.Fragment>
                                <Dropdown
                                    value={startKm}
                                    options={kmOptions}
                                    onChange={(value) => value && setStartKm(value)}
                                    size={DropdownSize.SMALL}
                                />
                                <span
                                    className={
                                        styles['location-track-ratko-push-dialog__field-separator']
                                    }>
                                    -
                                </span>
                                <Dropdown
                                    value={endKm}
                                    options={kmOptions}
                                    onChange={(value) => value && setEndKm(value)}
                                    size={DropdownSize.SMALL}
                                />
                            </React.Fragment>
                        }
                    />
                </React.Fragment>
            </div>
        </Dialog>
    );
};
