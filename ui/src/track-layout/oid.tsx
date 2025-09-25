import {
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import { LayoutBranch, Oid, TimeStamp } from 'common/common-model';
import { ChangeTimes } from 'common/common-slice';
import { getLocationTrackOids } from 'track-layout/layout-location-track-api';
import { useLoader } from 'utils/react-utils';
import { getTrackNumberOids } from 'track-layout/layout-track-number-api';
import { getSwitchOids } from 'track-layout/layout-switch-api';
import React from 'react';
import { Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import styles from 'track-layout/oid.scss';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { useTranslation } from 'react-i18next';
import { TFunction } from 'i18next/typescript/t';

type TrackNumberOidProps = OidProps<LayoutTrackNumberId>;

type LocationTrackOidProps = OidProps<LocationTrackId>;

type SwitchOidProps = OidProps<LayoutSwitchId>;

interface OidProps<Id> {
    id: Id;
    branch: LayoutBranch;
    changeTimes: ChangeTimes;
    getFallbackTextIfNoOid?: () => string;
}

function copyOidToClipBoard(t: TFunction<'translation', undefined>, oid: string): () => void {
    return () => {
        navigator.clipboard
            .writeText(oid)
            .then(() => Snackbar.success(t('tool-panel.oid.copy-success', { oid })))
            .catch((err) => {
                Snackbar.error(t('tool-panel.oid.copy-failed'), err);
            });
    };
}

function oidComponent<Id>(
    apiGetter: (id: Id, changeTime: TimeStamp) => Promise<{ [key in LayoutBranch]?: Oid }>,
    changeTimeGetter: (changeTimes: ChangeTimes) => TimeStamp,
): (props: OidProps<Id>) => React.JSX.Element {
    return ({ id, changeTimes, getFallbackTextIfNoOid }) => {
        const { t } = useTranslation();

        const changeTime = changeTimeGetter(changeTimes);
        const oids = useLoader(() => apiGetter(id, changeTime), [id, changeTime]);

        const oidExists = oids !== undefined && 'MAIN' in oids;
        const oidToDisplay = oidExists ? (oids['MAIN'] ?? '') : (getFallbackTextIfNoOid?.() ?? '');

        return oidExists ? (
            <span className={styles['oid-container']}>
                {oidToDisplay}
                <a
                    className={styles['oid-icon-container']}
                    onClick={copyOidToClipBoard(t, oidToDisplay)}>
                    <Icons.Copy size={IconSize.SMALL} />
                </a>
            </span>
        ) : (
            <span>{oidToDisplay}</span>
        );
    };
}

export const TrackNumberOid: React.FC<TrackNumberOidProps> = oidComponent(
    getTrackNumberOids,
    (changeTimes) => changeTimes.layoutTrackNumberExtId,
);

export const LocationTrackOid: React.FC<LocationTrackOidProps> = oidComponent(
    getLocationTrackOids,
    (changeTimes) => changeTimes.layoutLocationTrackExtId,
);

export const SwitchOid: React.FC<SwitchOidProps> = oidComponent(
    getSwitchOids,
    (changeTimes) => changeTimes.layoutSwitchExtId,
);

export const OperatingPointOid: React.FC = oidComponent(
    (_id, _changeTime) => Promise.resolve({ MAIN: 'Oiderooni' }),
    (_changeTimes) => new Date().toISOString(),
);
