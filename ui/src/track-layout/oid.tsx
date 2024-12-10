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

type TrackNumberOidProps = OidProps<LayoutTrackNumberId>;

type LocationTrackOidProps = OidProps<LocationTrackId>;

type SwitchOidProps = OidProps<LayoutSwitchId>;

interface OidProps<Id> {
    id: Id;
    branch: LayoutBranch;
    changeTimes: ChangeTimes;
    getFallbackTextIfNoOid?: () => string;
}

function oidComponent<Id>(
    apiGetter: (id: Id, changeTime: TimeStamp) => Promise<{ [key in LayoutBranch]?: Oid }>,
    changeTimeGetter: (changeTimes: ChangeTimes) => TimeStamp,
): (props: OidProps<Id>) => string {
    return ({ id, branch, changeTimes, getFallbackTextIfNoOid }) => {
        const changeTime = changeTimeGetter(changeTimes);
        const oids = useLoader(() => apiGetter(id, changeTime), [id, branch, changeTime]);
        return oids === undefined
            ? ''
            : branch in oids
              ? oids[branch] ?? ''
              : getFallbackTextIfNoOid?.() ?? '';
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
