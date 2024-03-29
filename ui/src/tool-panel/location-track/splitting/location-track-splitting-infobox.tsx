import * as React from 'react';
import { useTranslation } from 'react-i18next';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import styles from 'tool-panel/location-track/location-track-infobox.scss';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import Infobox from 'tool-panel/infobox/infobox';
import {
    LocationTrackInfoboxVisibilities,
    LocationTrackTaskListType,
    trackLayoutActionCreators as TrackLayoutActions,
} from 'track-layout/track-layout-slice';
import {
    AddressPoint,
    LayoutLocationTrack,
    LayoutSwitch,
    LayoutSwitchId,
    LocationTrackId,
    LocationTrackInfoboxExtras,
} from 'track-layout/track-layout-model';
import {
    FirstSplitTargetCandidate,
    SplitTargetCandidate,
    SplittingState,
} from 'tool-panel/location-track/split-store';
import {
    useConflictingTracks,
    useLocationTrack,
    useLocationTrackInfoboxExtras,
    useLocationTracks,
    useLocationTrackStartAndEnd,
    useSwitches,
} from 'track-layout/track-layout-react-utils';
import {
    LocationTrackSplit,
    LocationTrackSplittingEndpoint,
} from 'tool-panel/location-track/splitting/location-track-split';
import { filterNotEmpty, findById } from 'utils/array-utils';
import { getChangeTimes, updateAllChangeTimes } from 'common/change-time-api';
import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { postSplitLocationTrack } from 'publication/split/split-api';
import { createDelegates } from 'store/store-utils';
import { success } from 'geoviite-design-lib/snackbar/snackbar';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import { ChangeTimes } from 'common/common-slice';
import {
    LocationTrackSplittingDraftExistsErrorNotice,
    LocationTrackSplittingGuideNotice,
    NoticeWithNavigationLink,
    LocationTrackSplittingDuplicateTrackNotPublishedErrorNotice,
} from 'tool-panel/location-track/splitting/location-track-split-notices';
import { LocationTrackSplitRelinkingNotice } from 'tool-panel/location-track/splitting/location-track-split-relinking-notice';
import {
    findRefToFirstErroredField,
    getSplitAddressPoint,
    mandatoryFieldMissing,
    otherError,
    splitRequest,
    ValidatedSplit,
    validateSplit,
} from 'tool-panel/location-track/splitting/split-utils';
import { draftLayoutContext, LayoutContext } from 'common/common-model';

type LocationTrackSplittingInfoboxContainerProps = {
    layoutContext: LayoutContext;
    visibilities: LocationTrackInfoboxVisibilities;
    visibilityChange: (key: keyof LocationTrackInfoboxVisibilities) => void;
};

type LocationTrackSplittingInfoboxProps = {
    changeTimes: ChangeTimes;
    splittingState: SplittingState;
    removeSplit: (switchId: LayoutSwitchId) => void;
    sourceStart: AddressPoint;
    sourceEnd: AddressPoint;
    stopSplitting: () => void;
    updateSplit: (updatedSplit: SplitTargetCandidate | FirstSplitTargetCandidate) => void;
    returnToSplitting: () => void;
    startPostingSplit: () => void;
    markSplitOld: (switchId: LayoutSwitchId | undefined) => void;
    onShowTaskList: (locationTrackId: LocationTrackId) => void;
} & LocationTrackSplittingInfoboxContainerProps;

export const LocationTrackSplittingInfoboxContainer: React.FC<
    LocationTrackSplittingInfoboxContainerProps
> = ({ visibilities, visibilityChange, layoutContext }) => {
    const trackLayoutState = useTrackLayoutAppSelector((state) => state);
    const delegates = createDelegates(TrackLayoutActions);
    const splittingState = trackLayoutState.splittingState;
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);

    const locationTrack = useLocationTrack(
        splittingState?.originLocationTrack.id,
        draftLayoutContext(layoutContext),
        changeTimes.layoutLocationTrack,
    );
    const [startAndEnd, _] = useLocationTrackStartAndEnd(
        splittingState?.originLocationTrack.id,
        draftLayoutContext(layoutContext),
        changeTimes,
    );

    React.useEffect(() => {
        locationTrack && delegates.setDisabled(locationTrack?.editState !== 'UNEDITED');
    }, [locationTrack, changeTimes.layoutLocationTrack]);

    const onShowTaskList = (locationTrack: LocationTrackId) => {
        delegates.showLocationTrackTaskList({
            type: LocationTrackTaskListType.RELINKING_SWITCH_VALIDATION,
            locationTrackId: locationTrack,
        });
    };

    const stopSplitting = () => {
        delegates.stopSplitting();
        delegates.hideLayers(['location-track-split-location-layer']);
    };

    return (
        splittingState &&
        locationTrack &&
        startAndEnd?.start &&
        startAndEnd?.end && (
            <LocationTrackSplittingInfobox
                visibilities={visibilities}
                visibilityChange={visibilityChange}
                layoutContext={layoutContext}
                changeTimes={changeTimes}
                splittingState={splittingState}
                removeSplit={delegates.removeSplit}
                stopSplitting={stopSplitting}
                updateSplit={delegates.updateSplit}
                sourceStart={startAndEnd.start}
                sourceEnd={startAndEnd.end}
                returnToSplitting={delegates.returnToSplitting}
                startPostingSplit={delegates.startPostingSplit}
                markSplitOld={delegates.markSplitOld}
                onShowTaskList={onShowTaskList}
            />
        )
    );
};

const createSplitComponent = (
    validatedSplit: ValidatedSplit,
    switches: LayoutSwitch[],
    splittingState: SplittingState,
    originLocationTrackStart: AddressPoint,
    removeSplit: (id: LayoutSwitchId) => void,
    updateSplit: (split: FirstSplitTargetCandidate | SplitTargetCandidate) => void,
    isPostingSplit: boolean,
    locationTrackInfoboxExtras: LocationTrackInfoboxExtras | undefined,
    duplicateTracksInCurrentSplits: LayoutLocationTrack[],
) => {
    const nameRef = React.createRef<HTMLInputElement>();
    const descriptionBaseRef = React.createRef<HTMLInputElement>();

    const switchExists =
        switches.find(
            (s) => validatedSplit.split.type === 'SPLIT' && s.id === validatedSplit.split.switchId,
        )?.stateCategory !== 'NOT_EXISTING';

    const { split, nameErrors, descriptionErrors, switchErrors } = validatedSplit;
    return {
        component: (
            <LocationTrackSplit
                key={`${split.location.x}_${split.location.y}`}
                split={split}
                addressPoint={getSplitAddressPoint(
                    splittingState.allowedSwitches,
                    originLocationTrackStart,
                    split,
                )}
                onRemove={split.type === 'SPLIT' ? removeSplit : undefined}
                updateSplit={updateSplit}
                duplicateOf={split.duplicateOf}
                nameErrors={nameErrors}
                descriptionErrors={descriptionErrors}
                switchErrors={switchErrors}
                editingDisabled={splittingState.disabled || !switchExists || isPostingSplit}
                deletingDisabled={splittingState.disabled || isPostingSplit}
                nameRef={nameRef}
                descriptionBaseRef={descriptionBaseRef}
                allDuplicateLocationTracks={locationTrackInfoboxExtras?.duplicates ?? []}
                duplicateLocationTrack={
                    split.duplicateOf
                        ? findById(duplicateTracksInCurrentSplits, split.duplicateOf)
                        : undefined
                }
                underlyingAssetExists={switchExists}
            />
        ),
        splitAndValidation: validatedSplit,
        nameRef,
        descriptionBaseRef,
    };
};

export const LocationTrackSplittingInfobox: React.FC<LocationTrackSplittingInfoboxProps> = ({
    layoutContext,
    visibilities,
    visibilityChange,
    changeTimes,
    splittingState,
    removeSplit,
    stopSplitting,
    updateSplit,
    sourceStart,
    sourceEnd,
    returnToSplitting,
    startPostingSplit,
    markSplitOld,
    onShowTaskList,
}) => {
    const { t } = useTranslation();
    const [confirmExit, setConfirmExit] = React.useState(false);
    const allSplits = [splittingState.firstSplit, ...splittingState.splits];

    const allowedSwitchIds = React.useMemo(
        () => splittingState.allowedSwitches.map((sw) => sw.switchId),
        [splittingState.allowedSwitches],
    );
    const switches = useSwitches(
        allowedSwitchIds,
        draftLayoutContext(layoutContext),
        changeTimes.layoutSwitch,
    );
    const conflictingLocationTracks = useConflictingTracks(
        splittingState.originLocationTrack.trackNumberId,
        allSplits.map((s) => s.name),
        allSplits.map((s) => s.duplicateOf).filter(filterNotEmpty),
        draftLayoutContext(layoutContext),
    )?.map((t) => t.name);
    const duplicateTracksInCurrentSplits = useLocationTracks(
        allSplits.map((s) => s.duplicateOf).filter(filterNotEmpty),
        draftLayoutContext(layoutContext),
        getChangeTimes().layoutLocationTrack,
    );
    const [locationTrackInfoboxExtras, _] = useLocationTrackInfoboxExtras(
        splittingState.originLocationTrack.id,
        draftLayoutContext(layoutContext),
        getChangeTimes(),
    );

    const splitsValidated = allSplits.map((s) =>
        validateSplit(
            s,
            allSplits.map((s) => s.name),
            conflictingLocationTracks || [],
            switches,
        ),
    );
    const allErrors = splitsValidated.flatMap((validated) => [
        ...validated.descriptionErrors,
        ...validated.nameErrors,
        ...validated.switchErrors,
    ]);
    const anyMissingFields = allErrors.map((s) => s.reason).some(mandatoryFieldMissing);
    const anyOtherErrors = allErrors.map((s) => s.reason).some(otherError);
    const isPostingSplit = splittingState.state === 'POSTING';

    const firstChangedDuplicateInSplits = duplicateTracksInCurrentSplits.find(
        (dupe) => dupe.editState !== 'UNEDITED',
    );

    const postSplit = () => {
        startPostingSplit();
        postSplitLocationTrack(
            splitRequest(
                splittingState.originLocationTrack.id,
                splittingState.firstSplit,
                splittingState.splits,
                duplicateTracksInCurrentSplits,
            ),
        )
            .then(async () => {
                await updateAllChangeTimes();
                stopSplitting();
                success(
                    t('tool-panel.location-track.splitting.splitting-success', {
                        locationTrackName: splittingState.originLocationTrack.name,
                        count: splitsValidated.length,
                    }),
                );
            })
            .catch(() => returnToSplitting());
    };

    const focusFirstErrorFieldByPredicate = (predicate: (errorReason: string) => boolean) =>
        findRefToFirstErroredField(splitComponents, predicate)?.current?.focus();

    React.useEffect(() => {
        const newSplitComponent = splitComponents.find((s) => s.splitAndValidation.split.new);
        if (newSplitComponent) {
            newSplitComponent.nameRef.current?.focus();
            markSplitOld(
                newSplitComponent.splitAndValidation.split.type === 'SPLIT'
                    ? newSplitComponent.splitAndValidation.split.switchId
                    : undefined,
            );
        }
    });

    const splitComponents = splitsValidated.map((split) =>
        createSplitComponent(
            split,
            switches,
            splittingState,
            sourceStart,
            removeSplit,
            updateSplit,
            isPostingSplit,
            locationTrackInfoboxExtras,
            duplicateTracksInCurrentSplits,
        ),
    );

    return (
        <React.Fragment>
            <Infobox
                contentVisible={visibilities.splitting}
                onContentVisibilityChange={() => visibilityChange('splitting')}
                title={t('tool-panel.location-track.splitting.title')}>
                <InfoboxContent className={styles['location-track-infobox__split']}>
                    {splitComponents.map((split) => split.component)}
                    <LocationTrackSplittingEndpoint
                        addressPoint={sourceEnd}
                        editingDisabled={splittingState.disabled}
                    />
                    {splittingState.disabled && <LocationTrackSplittingDraftExistsErrorNotice />}
                    {!splittingState.disabled && (
                        <LocationTrackSplitRelinkingNotice
                            splittingState={splittingState}
                            onShowTaskList={onShowTaskList}
                            stopSplitting={stopSplitting}
                        />
                    )}
                    {!splittingState.disabled && splittingState.splits.length === 0 && (
                        <LocationTrackSplittingGuideNotice />
                    )}
                    {!splittingState.disabled && anyMissingFields && (
                        <NoticeWithNavigationLink
                            onClickLink={() =>
                                focusFirstErrorFieldByPredicate(mandatoryFieldMissing)
                            }
                            noticeLocalizationKey={
                                'tool-panel.location-track.splitting.validation.missing-fields'
                            }
                        />
                    )}
                    {!splittingState.disabled && anyOtherErrors && (
                        <NoticeWithNavigationLink
                            onClickLink={() => focusFirstErrorFieldByPredicate(otherError)}
                            noticeLocalizationKey={
                                'tool-panel.location-track.splitting.validation.has-errors'
                            }
                        />
                    )}
                    {firstChangedDuplicateInSplits && (
                        <LocationTrackSplittingDuplicateTrackNotPublishedErrorNotice
                            draftDuplicateName={firstChangedDuplicateInSplits.name}
                        />
                    )}
                    <InfoboxButtons>
                        <Button
                            variant={ButtonVariant.SECONDARY}
                            size={ButtonSize.SMALL}
                            disabled={isPostingSplit}
                            onClick={() => setConfirmExit(true)}>
                            {t('button.cancel')}
                        </Button>
                        <Button
                            size={ButtonSize.SMALL}
                            onClick={() => postSplit()}
                            isProcessing={isPostingSplit}
                            disabled={
                                splittingState.disabled ||
                                anyMissingFields ||
                                anyOtherErrors ||
                                !!firstChangedDuplicateInSplits ||
                                splittingState.splits.length < 1 ||
                                isPostingSplit
                            }>
                            {t('tool-panel.location-track.splitting.confirm-split')}
                        </Button>
                    </InfoboxButtons>
                </InfoboxContent>
            </Infobox>
            {confirmExit && (
                <Dialog
                    title={t('tool-panel.location-track.splitting.exit-title')}
                    allowClose={false}
                    variant={DialogVariant.DARK}
                    footerContent={
                        <div className={dialogStyles['dialog__footer-content--centered']}>
                            <Button
                                onClick={() => setConfirmExit(false)}
                                variant={ButtonVariant.SECONDARY}>
                                {t('tool-panel.location-track.splitting.back')}
                            </Button>
                            <Button
                                variant={ButtonVariant.WARNING}
                                onClick={() => {
                                    setConfirmExit(false);
                                    stopSplitting();
                                }}>
                                {t('tool-panel.location-track.splitting.exit')}
                            </Button>
                        </div>
                    }>
                    {t('tool-panel.location-track.splitting.confirm-exit')}
                </Dialog>
            )}
        </React.Fragment>
    );
};
