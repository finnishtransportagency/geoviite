import * as React from 'react';
import styles from './location-track-task-list.scss';
import {
    getSwitchPresentationJoint,
    LayoutSwitch,
    LayoutSwitchId,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import { LoaderStatus, useLoader, useLoaderWithStatus } from 'utils/react-utils';
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
import { Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { SwitchBadge, SwitchBadgeStatus } from 'geoviite-design-lib/switch/switch-badge';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { useTrackLayoutAppSelector } from 'store/hooks';
import { useTranslation } from 'react-i18next';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';

type LocationTrackTaskListProps = {
    locationTrackId: LocationTrackId;
    onShowSwitch: (layoutSwitch: LayoutSwitch, point?: Point) => void;
    onClose: () => void;
    selectedSwitches: LayoutSwitchId[];
};

export const LocationTrackTaskListContainer: React.FC = () => {
    const delegates = createDelegates(TrackLayoutActions);
    const locationTrackId = useTrackLayoutAppSelector((state) => state.locationTrackTaskList);
    const selectedSwitches = useTrackLayoutAppSelector(
        (state) => state.selection.selectedItems.switches,
    );

    const onShowSwitch = (layoutSwitch: LayoutSwitch, point?: Point) => {
        if (point) {
            delegates.showArea(calculateBoundingBoxToShowAroundLocation(point));
        }

        delegates.onSelect({ switches: [layoutSwitch.id] });
    };

    const onClose = () => {
        delegates.hideLocationTrackTaskList();
    };

    return createPortal(
        locationTrackId ? (
            <LocationTrackTaskList
                locationTrackId={locationTrackId}
                onClose={onClose}
                onShowSwitch={onShowSwitch}
                selectedSwitches={selectedSwitches}
            />
        ) : (
            <React.Fragment />
        ),
        document.body,
    );
};

const LocationTrackTaskList: React.FC<LocationTrackTaskListProps> = ({
    locationTrackId,
    onShowSwitch,
    onClose,
    selectedSwitches,
}) => {
    const { t } = useTranslation();
    const changeTimes = getChangeTimes();
    const switchStructures = useLoader(() => getSwitchStructures(), []);

    const [locationTrack, locationTrackLoadingStatus] = useLoaderWithStatus(
        () => getLocationTrack(locationTrackId, 'DRAFT'),
        [locationTrackId, getChangeTimes().layoutLocationTrack],
    );

    const [switches, switchesLoadingStatus] = useLoaderWithStatus(async () => {
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

    const loadingInProgress =
        locationTrackLoadingStatus == LoaderStatus.Loading ||
        switchesLoadingStatus == LoaderStatus.Loading;

    return (
        <div className={styles['location-track-task-list']}>
            <h1 className={styles['location-track-task-list__title']}>
                {t('tool-panel.location-track.task-list.title')}
                <span className={styles['location-track-task-list__close']}>
                    <Icons.Close size={IconSize.SMALL} onClick={onClose} />
                </span>
            </h1>
            <section className={styles['location-track-task-list__content']}>
                {loadingInProgress && <Spinner />}

                {!loadingInProgress && switches && switches.length > 0 && (
                    <React.Fragment>
                        <span>
                            {t('tool-panel.location-track.task-list.validation-errors-message', {
                                locationTrack: locationTrack?.name,
                            })}
                        </span>
                        <ul className={styles['location-track-task-list__switches']}>
                            {switches.map((r) => {
                                const selected = selectedSwitches.some((sid) => sid == r.id);

                                return (
                                    <li
                                        key={r.id}
                                        className={styles['location-track-task-list__switch']}>
                                        <SwitchBadge
                                            onClick={() => onClick(r)}
                                            switchItem={r}
                                            status={
                                                selected
                                                    ? SwitchBadgeStatus.SELECTED
                                                    : SwitchBadgeStatus.DEFAULT
                                            }
                                        />
                                    </li>
                                );
                            })}
                        </ul>
                    </React.Fragment>
                )}
                {!loadingInProgress && switches && switches.length == 0 && (
                    <div className={styles['location-track-task-list__message-container']}>
                        <span>
                            {t('tool-panel.location-track.task-list.no-validation-errors-message', {
                                locationTrack: locationTrack?.name,
                            })}
                        </span>
                        <div className={styles['location-track-task-list__message-buttons']}>
                            <Button
                                variant={ButtonVariant.SECONDARY}
                                size={ButtonSize.SMALL}
                                onClick={onClose}>
                                {t('tool-panel.location-track.task-list.close-task-list')}
                            </Button>
                        </div>
                    </div>
                )}
            </section>
        </div>
    );
};
