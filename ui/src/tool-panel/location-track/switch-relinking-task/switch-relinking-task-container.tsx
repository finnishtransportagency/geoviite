import * as React from 'react';
import styles from './switch-relinking-task.scss';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import {
    getSwitchPresentationJoint,
    LayoutSwitch,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import { useLoader, useLoaderWithStatus } from 'utils/react-utils';
import { getSwitchStructures } from 'common/common-api';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { calculateBoundingBoxToShowAroundLocation } from 'map/map-utils';
import { getChangeTimes } from 'common/change-time-api';
import { validateLocationTrackSwitchRelinking } from 'linking/linking-api';
import { getSwitches } from 'track-layout/layout-switch-api';
import { createPortal } from 'react-dom';
import { Point } from 'model/geometry';
import { getLocationTrack } from 'track-layout/layout-location-track-api';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { SwitchBadge } from 'geoviite-design-lib/switch/switch-badge';

type SwitchRelinkingTaskContainerProps = {
    locationTrackId: LocationTrackId;
    onClose: () => void;
};

type SwitchRelinkingTaskProps = {
    locationTrackId: LocationTrackId;
    onShowSwitch: (layoutSwitch: LayoutSwitch, point?: Point) => void;
    onClose: () => void;
};

export const SwitchRelinkingTaskContainer: React.FC<SwitchRelinkingTaskContainerProps> = ({
    locationTrackId,
    onClose,
}) => {
    const delegates = createDelegates(TrackLayoutActions);

    const onShowSwitch = (layoutSwitch: LayoutSwitch, point?: Point) => {
        if (point) {
            delegates.showArea(calculateBoundingBoxToShowAroundLocation(point));
        } else {
            Snackbar.info('SWITCH NO LOCATION. KÄYTÄ HAKUA');
        }

        delegates.onSelect({ switches: [layoutSwitch.id] });
    };

    return createPortal(
        <SwitchRelinkingTask
            locationTrackId={locationTrackId}
            onClose={onClose}
            onShowSwitch={onShowSwitch}
        />,
        document.body,
    );
};

const SwitchRelinkingTask: React.FC<SwitchRelinkingTaskProps> = ({
    locationTrackId,
    onShowSwitch,
    onClose,
}) => {
    const changeTimes = getChangeTimes();
    const switchStructures = useLoader(() => getSwitchStructures(), []);

    const [locationTrack, _locationTrackLoadingStatus] = useLoaderWithStatus(
        () => getLocationTrack(locationTrackId, 'DRAFT'),
        [locationTrackId, getChangeTimes().layoutLocationTrack],
    );

    const [switches, _switchesLoadingStatus] = useLoaderWithStatus(async () => {
        const switchIds = (await validateLocationTrackSwitchRelinking(locationTrackId))
            .filter((r) => r.validationErrors.length > 0)
            .map((s) => s.id);

        return await getSwitches(switchIds, 'DRAFT');
    }, [changeTimes.layoutSwitch, locationTrackId, changeTimes.layoutLocationTrack]);

    const onClick = (layoutSwitch: LayoutSwitch) => {
        const presJointNumber = switchStructures?.find(
            (s) => s.id == layoutSwitch.switchStructureId,
        )?.presentationJointNumber;

        const switchLocation = presJointNumber
            ? getSwitchPresentationJoint(layoutSwitch, presJointNumber)?.location
            : undefined;

        onShowSwitch(layoutSwitch, switchLocation);
    };

    return (
        <div className={styles['task-list']}>
            <h1 className={styles['task-list__title']}>
                Tehtävälista <Icons.Close onClick={onClose} />
            </h1>
            <section className={'task-list__content'}>
                <span>
                    Seuraavissa raiteen &quot;{locationTrack?.name}&quot; vaihteissa on ongelmia
                </span>
                <ul className="switch-relinking-task__switches">
                    {switches?.map((r) => (
                        <li
                            key={r.id}
                            className="switch-relinking-task__switch"
                            onClick={() => onClick(r)}>
                            <SwitchBadge switchItem={r} />
                        </li>
                    ))}
                </ul>
            </section>
        </div>
    );
};
