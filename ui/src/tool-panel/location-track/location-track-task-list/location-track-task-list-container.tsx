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
import {
    LocationTrackTaskListType,
    trackLayoutActionCreators as TrackLayoutActions,
} from 'track-layout/track-layout-slice';
import { calculateBoundingBoxToShowAroundLocation } from 'map/map-utils';
import { getChangeTimes } from 'common/change-time-api';
import { validateLocationTrackSwitchRelinking } from 'linking/linking-api';
import { getSwitches } from 'track-layout/layout-switch-api';
import { createPortal } from 'react-dom';
import { Point } from 'model/geometry';
import { getLocationTrack } from 'track-layout/layout-location-track-api';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { SwitchBadge, SwitchBadgeStatus } from 'geoviite-design-lib/switch/switch-badge';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { useTrackLayoutAppSelector } from 'store/hooks';
import { useTranslation } from 'react-i18next';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { draftLayoutContext, LayoutContext } from 'common/common-model';

type SwitchRelinkingValidationTaskListProps = {
    layoutContext: LayoutContext;
    locationTrackId: LocationTrackId;
    onShowSwitch: (layoutSwitch: LayoutSwitch, point?: Point) => void;
    onClose: () => void;
    selectedSwitches: LayoutSwitchId[];
    selectingWorkspace: boolean;
};

export const LocationTrackTaskListContainer: React.FC<{ selectingWorkspace: boolean }> = ({
    selectingWorkspace,
}) => {
    const delegates = createDelegates(TrackLayoutActions);
    const locationTrackList = useTrackLayoutAppSelector((state) => state.locationTrackTaskList);
    const layoutContext = useTrackLayoutAppSelector((state) => state.layoutContext);
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

    React.useEffect(() => {
        if (
            locationTrackList &&
            (locationTrackList.designId !== layoutContext.designId || selectingWorkspace)
        ) {
            delegates.hideLocationTrackTaskList();
        }
    }, [layoutContext.designId, selectingWorkspace]);

    return createPortal(
        locationTrackList?.type == LocationTrackTaskListType.RELINKING_SWITCH_VALIDATION ? (
            <SwitchRelinkingValidationTaskList
                layoutContext={layoutContext}
                locationTrackId={locationTrackList.locationTrackId}
                onClose={onClose}
                onShowSwitch={onShowSwitch}
                selectedSwitches={selectedSwitches}
                selectingWorkspace={selectingWorkspace}
            />
        ) : (
            <React.Fragment />
        ),
        document.body,
    );
};

const SwitchRelinkingValidationTaskList: React.FC<SwitchRelinkingValidationTaskListProps> = ({
    layoutContext,
    locationTrackId,
    onShowSwitch,
    onClose,
    selectedSwitches,
}) => {
    const { t } = useTranslation();
    const changeTimes = getChangeTimes();
    const switchStructures = useLoader(() => getSwitchStructures(), []);

    const [locationTrack, locationTrackLoadingStatus] = useLoaderWithStatus(
        () => getLocationTrack(locationTrackId, draftLayoutContext(layoutContext)),
        [locationTrackId, getChangeTimes().layoutLocationTrack],
    );

    const [switchesAndErrors, switchesLoadingStatus] = useLoaderWithStatus(async () => {
        const relinkingResults = await validateLocationTrackSwitchRelinking(locationTrackId);
        const switchIds = relinkingResults
            .filter((r) => r.validationIssues.length > 0 || r.successfulSuggestion == null)
            .map((s) => s.id);
        const switches = await getSwitches(switchIds, draftLayoutContext(layoutContext));

        return {
            relinkingResults,
            switches,
        };
    }, [changeTimes.layoutSwitch, locationTrackId, changeTimes.layoutLocationTrack]);
    const switches = switchesAndErrors?.switches;
    const relinkingResults = switchesAndErrors?.relinkingResults;

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
        <div className={styles['switch-relinking-validation-task-list']}>
            <h1 className={styles['switch-relinking-validation-task-list__title']}>
                {t('tool-panel.location-track.task-list.switch-relinking.title')}
                <span className={styles['switch-relinking-validation-task-list__close']}>
                    <Icons.Close size={IconSize.SMALL} onClick={onClose} />
                </span>
            </h1>
            <section className={styles['switch-relinking-validation-task-list__content']}>
                {loadingInProgress && (
                    <div className={styles['switch-relinking-validation-task-list__loading']}>
                        <Spinner />
                    </div>
                )}

                {!loadingInProgress && switches && switches.length > 0 && (
                    <React.Fragment>
                        <span>
                            {t(
                                'tool-panel.location-track.task-list.switch-relinking.validation-errors-message',
                                {
                                    locationTrack: locationTrack?.name,
                                },
                            )}
                        </span>
                        <ul className={styles['switch-relinking-validation-task-list__switches']}>
                            {switches.map((lSwitch) => {
                                const selected = selectedSwitches.some((sId) => sId == lSwitch.id);
                                const switchRelinkingResult = relinkingResults?.find(
                                    (e) => e.id == lSwitch.id,
                                );

                                const errors =
                                    switchRelinkingResult?.validationIssues?.filter(
                                        (e) => e.type === 'ERROR',
                                    ) ?? [];

                                const title = errors
                                    .map((e) => t(e.localizationKey, e.params))
                                    .join('\n');

                                return (
                                    <li
                                        title={title}
                                        key={lSwitch.id}
                                        className={
                                            styles['switch-relinking-validation-task-list__switch']
                                        }>
                                        <SwitchBadge
                                            onClick={() => onClick(lSwitch)}
                                            switchItem={lSwitch}
                                            status={
                                                selected
                                                    ? SwitchBadgeStatus.SELECTED
                                                    : SwitchBadgeStatus.DEFAULT
                                            }
                                        />
                                        {errors.length > 0 && (
                                            <span
                                                className={
                                                    'switch-relinking-validation-task-list__critical-error'
                                                }>
                                                <Icons.StatusError
                                                    size={IconSize.SMALL}
                                                    color={IconColor.INHERIT}
                                                />
                                            </span>
                                        )}
                                    </li>
                                );
                            })}
                        </ul>
                    </React.Fragment>
                )}
                {!loadingInProgress && switches && switches.length == 0 && (
                    <div
                        className={
                            styles['switch-relinking-validation-task-list__message-container']
                        }>
                        <span>
                            {t(
                                'tool-panel.location-track.task-list.switch-relinking.no-validation-errors-message',
                                {
                                    locationTrack: locationTrack?.name,
                                },
                            )}
                        </span>
                        <div
                            className={
                                styles['switch-relinking-validation-task-list__message-buttons']
                            }>
                            <Button
                                variant={ButtonVariant.SECONDARY}
                                size={ButtonSize.SMALL}
                                onClick={onClose}>
                                {t(
                                    'tool-panel.location-track.task-list.switch-relinking.close-task-list',
                                )}
                            </Button>
                        </div>
                    </div>
                )}
            </section>
        </div>
    );
};
