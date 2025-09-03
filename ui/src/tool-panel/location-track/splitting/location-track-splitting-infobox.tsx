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
    AlignmentEndPoint,
    LayoutLocationTrack,
    LayoutSwitch,
    LayoutSwitchId,
    LocationTrackId,
    SplitPoint,
} from 'track-layout/track-layout-model';
import {
    FirstSplitTargetCandidate,
    SplitTargetCandidate,
    SplitTargetId,
    SplittingState,
} from 'tool-panel/location-track/split-store';
import {
    useConflictingTracks,
    useLocationTrack,
    useLocationTracks,
    useLocationTrackStartAndEnd,
    useSwitches,
} from 'track-layout/track-layout-react-utils';
import {
    getShowSwitchOnMapBoundingBox,
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
    LocationTrackSplittingDuplicateTrackNotPublishedErrorNotice,
    LocationTrackSplittingErrorNotice,
    LocationTrackSplittingGuideNotice,
    NoticeWithNavigationLink,
} from 'tool-panel/location-track/splitting/location-track-split-notices';
import { LocationTrackSplitRelinkingNotice } from 'tool-panel/location-track/splitting/location-track-split-relinking-notice';
import {
    findFirstErroredField,
    hasUnrelinkableSwitches,
    mandatoryFieldMissing,
    otherError,
    splitRequest,
    ValidatedSplit,
    validateSplit,
} from 'tool-panel/location-track/splitting/split-utils';
import { draftLayoutContext, LayoutContext, officialLayoutContext } from 'common/common-model';
import { BoundingBox, boundingBoxAroundPoints, multiplyBoundingBox } from 'model/geometry';
import {
    LoaderStatus,
    useLoaderWithStatus,
    useMinimallyUpdatedList,
    useMinimallyUpdatedMappedList,
    useRefMap,
} from 'utils/react-utils';
import { validateLocationTrackSwitchRelinking } from 'linking/linking-api';
import { SwitchRelinkingValidationResult } from 'linking/linking-model';
import { FieldValidationIssueType } from 'utils/validation-utils';
import { expectDefined } from 'utils/type-utils';

type LocationTrackSplittingInfoboxContainerProps = {
    layoutContext: LayoutContext;
    visibilities: LocationTrackInfoboxVisibilities;
    visibilityChange: (key: keyof LocationTrackInfoboxVisibilities) => void;
};

type LocationTrackSplittingInfoboxProps = {
    locationTrack: LayoutLocationTrack;
    changeTimes: ChangeTimes;
    splittingState: SplittingState;
    removeSplit: (splitPoint: SplitPoint) => void;
    sourceEnd: AlignmentEndPoint;
    stopSplitting: () => void;
    updateSplit: (updatedSplit: SplitTargetCandidate | FirstSplitTargetCandidate) => void;
    returnToSplitting: () => void;
    startPostingSplit: () => void;
    unfocusSplit: (splitPoint: SplitPoint) => void;
    onShowTaskList: (locationTrackId: LocationTrackId) => void;
    switchRelinkingErrors: SwitchRelinkingValidationResult[];
    switchRelinkingLoaderState: LoaderStatus;
    showArea: (bbox: BoundingBox) => void;
    setFocusedSplit: (split: SplitTargetId | undefined) => void;
    setHighlightedSplit: (split: SplitTargetId | undefined) => void;
    setHighlightedSplitPoint: (splitPoint: SplitPoint | undefined) => void;
} & LocationTrackSplittingInfoboxContainerProps;

type ExitSplittingConfirmationDialogProps = {
    showTaskList?: () => void;
    closeDialog: () => void;
    stopSplitting: () => void;
};

const ExitSplittingConfirmationDialog: React.FC<ExitSplittingConfirmationDialogProps> = ({
    closeDialog,
    showTaskList,
    stopSplitting,
}) => {
    const { t } = useTranslation();

    return (
        <Dialog
            title={t('tool-panel.location-track.splitting.exit-title')}
            allowClose={false}
            variant={DialogVariant.DARK}
            footerContent={
                <div className={dialogStyles['dialog__footer-content--centered']}>
                    <Button onClick={closeDialog} variant={ButtonVariant.SECONDARY}>
                        {t('tool-panel.location-track.splitting.back')}
                    </Button>
                    <Button
                        variant={ButtonVariant.WARNING}
                        onClick={() => {
                            if (showTaskList) {
                                showTaskList();
                            }
                            closeDialog();
                            stopSplitting();
                        }}>
                        {t('tool-panel.location-track.splitting.exit')}
                    </Button>
                </div>
            }>
            {t('tool-panel.location-track.splitting.confirm-exit')}
        </Dialog>
    );
};

export const LocationTrackSplittingInfoboxContainer: React.FC<
    LocationTrackSplittingInfoboxContainerProps
> = ({ visibilities, visibilityChange, layoutContext }) => {
    const trackLayoutState = useTrackLayoutAppSelector((state) => state);
    const delegates = React.useMemo(() => createDelegates(TrackLayoutActions), []);
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

    const [switchRelinkingErrors, switchRelinkingLoaderState] = useLoaderWithStatus(
        () =>
            splittingState
                ? validateLocationTrackSwitchRelinking(
                      layoutContext.branch,
                      splittingState.originLocationTrack.id,
                  ).then((results) => results.filter((res) => res.validationIssues.length > 0))
                : Promise.resolve([]),
        [changeTimes.layoutLocationTrack, changeTimes.layoutSwitch],
    );

    const hasOfficialLocationTrack =
        useLocationTrack(
            splittingState?.originLocationTrack.id,
            officialLayoutContext(layoutContext),
            changeTimes.layoutLocationTrack,
        ) !== undefined;

    React.useEffect(() => {
        locationTrack &&
            delegates.setDisabled(
                !locationTrack ||
                    (locationTrack.isDraft && !hasOfficialLocationTrack) ||
                    hasUnrelinkableSwitches(switchRelinkingErrors || []),
            );
    }, [
        locationTrack,
        hasOfficialLocationTrack,
        switchRelinkingErrors,
        changeTimes.layoutLocationTrack,
        changeTimes.layoutSwitch,
    ]);

    const onShowTaskList = React.useCallback(
        (locationTrack: LocationTrackId) => {
            delegates.showLocationTrackTaskList({
                type: LocationTrackTaskListType.RELINKING_SWITCH_VALIDATION,
                locationTrackId: locationTrack,
                branch: layoutContext.branch,
            });
        },
        [delegates],
    );

    const stopSplitting = React.useCallback(() => {
        delegates.stopSplitting();
        delegates.hideLayers(['location-track-split-location-layer']);
    }, [delegates]);

    return (
        splittingState &&
        locationTrack &&
        startAndEnd?.start &&
        startAndEnd?.end && (
            <LocationTrackSplittingInfobox
                locationTrack={locationTrack}
                visibilities={visibilities}
                visibilityChange={visibilityChange}
                layoutContext={layoutContext}
                changeTimes={changeTimes}
                splittingState={splittingState}
                removeSplit={delegates.removeSplit}
                stopSplitting={stopSplitting}
                updateSplit={delegates.updateSplit}
                sourceEnd={startAndEnd.end}
                returnToSplitting={delegates.returnToSplitting}
                startPostingSplit={delegates.startPostingSplit}
                unfocusSplit={delegates.unfocusSplit}
                onShowTaskList={onShowTaskList}
                switchRelinkingErrors={switchRelinkingErrors || []}
                switchRelinkingLoaderState={switchRelinkingLoaderState}
                showArea={delegates.showArea}
                setFocusedSplit={delegates.setFocusedSplit}
                setHighlightedSplit={delegates.setHighlightedSplit}
                setHighlightedSplitPoint={delegates.setHighlightedSplitPoint}
            />
        )
    );
};

export function getSplitPointName(
    splitPoint: SplitPoint,
    getSwitchName: (switchId: LayoutSwitchId) => string | undefined,
    endPointTerm: string,
): string | undefined {
    switch (splitPoint.type) {
        case 'ENDPOINT_SPLIT_POINT':
            return endPointTerm;
        case 'SWITCH_SPLIT_POINT':
            return getSwitchName(splitPoint.switchId);
    }
}

const splitKey = (split: SplitTargetCandidate | FirstSplitTargetCandidate) =>
    `${split.location.x}_${split.location.y}`;

const createSplitComponent = (
    validatedSplit: ValidatedSplit,
    switches: LayoutSwitch[],
    splittingState: SplittingState,
    removeSplit: (splitPoint: SplitPoint) => void,
    updateSplit: (split: FirstSplitTargetCandidate | SplitTargetCandidate) => void,
    isPostingSplit: boolean,
    duplicateTracksInCurrentSplits: LayoutLocationTrack[],
    showArea: (bbox: BoundingBox) => void,
    showSplitTrackOnMap: (id: SplitTargetId) => void,
    setFocusedSplit: (id: undefined | SplitTargetId) => void,
    setHighlightedSplit: (id: undefined | SplitTargetId) => void,
    setHighlightedSplitPoint: (splitPoint: undefined | SplitPoint) => void,
    setNameRef: (key: SplitTargetId, value: HTMLInputElement | null) => void,
    setDescriptionBaseRef: (key: SplitTargetId, value: HTMLInputElement | null) => void,
) => {
    const splitPoint = validatedSplit.split.splitPoint;
    const splitPointExists =
        splitPoint.type === 'ENDPOINT_SPLIT_POINT' ||
        (splitPoint.type === 'SWITCH_SPLIT_POINT' &&
            switches.find((s) => s.id === splitPoint.switchId)?.stateCategory !== 'NOT_EXISTING');
    const { split, nameIssues, descriptionIssues, switchIssues } = validatedSplit;

    return {
        component: (
            <LocationTrackSplit
                locationTrackId={splittingState.originLocationTrack.id}
                key={splitKey(split)}
                split={split}
                onRemove={split.type === 'SPLIT' ? removeSplit : undefined}
                updateSplit={updateSplit}
                duplicateTrackId={split.duplicateTrackId}
                nameIssues={nameIssues}
                descriptionIssues={descriptionIssues}
                switchIssues={switchIssues}
                editingDisabled={splittingState.disabled || !splitPointExists || isPostingSplit}
                deletingDisabled={splittingState.disabled || isPostingSplit}
                setNameRef={setNameRef}
                setDescriptionBaseRef={setDescriptionBaseRef}
                allDuplicateLocationTracks={splittingState.duplicateTracks}
                duplicateLocationTrack={
                    split.duplicateTrackId
                        ? findById(duplicateTracksInCurrentSplits, split.duplicateTrackId)
                        : undefined
                }
                underlyingAssetExists={splitPointExists}
                showArea={showArea}
                onSplitTrackClicked={showSplitTrackOnMap}
                setFocusedSplit={setFocusedSplit}
                setHighlightedSplit={setHighlightedSplit}
                setHighlightedSplitPoint={setHighlightedSplitPoint}
            />
        ),

        splitAndValidation: validatedSplit,
    };
};

function useShowSplitTrackOnMap(
    allSplits: (SplitTargetCandidate | FirstSplitTargetCandidate)[],
    splittingState: SplittingState,
    showArea: (bbox: BoundingBox) => void,
): (id: SplitTargetId) => void {
    const splitLocations = useMinimallyUpdatedList(
        allSplits.map((split) => [split.id, split.location] as const),
        ([id]) => id,
    );
    return React.useCallback(
        (id: SplitTargetId) => {
            const index = splitLocations.findIndex(([splitId]) => splitId === id);
            if (index !== -1) {
                const startPoint = expectDefined(splitLocations[index])[1];
                const endPoint =
                    index + 1 < allSplits.length
                        ? expectDefined(splitLocations[index + 1])[1]
                        : splittingState.endLocation;
                showArea(
                    multiplyBoundingBox(boundingBoxAroundPoints([startPoint, endPoint]), 1.15),
                );
            }
        },
        [splitLocations, splittingState.endLocation],
    );
}

export const LocationTrackSplittingInfobox: React.FC<LocationTrackSplittingInfoboxProps> = ({
    locationTrack,
    layoutContext,
    visibilities,
    visibilityChange,
    changeTimes,
    splittingState,
    removeSplit,
    stopSplitting,
    updateSplit,
    sourceEnd,
    returnToSplitting,
    startPostingSplit,
    unfocusSplit,
    onShowTaskList,
    switchRelinkingErrors,
    switchRelinkingLoaderState,
    showArea,
    setFocusedSplit,
    setHighlightedSplit,
    setHighlightedSplitPoint,
}) => {
    const { t } = useTranslation();
    const [confirmExit, setConfirmExit] = React.useState(false);
    const [confirmOpenTaskListAndExit, setConfirmOpenTaskListAndExit] = React.useState(false);
    const allSplits = useMinimallyUpdatedList(
        [splittingState.firstSplit, ...splittingState.splits],
        (split) => splitKey(split),
    );
    const switches = useSwitches(
        splittingState.trackSwitches.map((sw) => sw.switchId),
        draftLayoutContext(layoutContext),
        changeTimes.layoutSwitch,
    );
    const conflictingLocationTracks = useConflictingTracks(
        locationTrack.trackNumberId,
        allSplits.map((s) => s.name),
        allSplits.map((s) => s.duplicateTrackId).filter(filterNotEmpty),
        draftLayoutContext(layoutContext),
    )?.map((t) => t.name);
    const duplicateTracksInCurrentSplits = useLocationTracks(
        allSplits.map((s) => s.duplicateTrackId).filter(filterNotEmpty),
        draftLayoutContext(layoutContext),
        getChangeTimes().layoutLocationTrack,
    );
    const splitsValidated = useMinimallyUpdatedList(
        allSplits.map((s, index) =>
            validateSplit(
                s,
                allSplits[index + 1],
                allSplits.map((s) => s.name),
                conflictingLocationTracks || [],
                switches,
                splittingState.endSplitPoint,
            ),
        ),
        (validatedSplit) => splitKey(validatedSplit.split),
    );
    const allErrors = splitsValidated.flatMap((validated) => [
        ...validated.descriptionIssues,
        ...validated.nameIssues,
        ...validated.switchIssues,
    ]);
    const anyMissingFields = allErrors.map((s) => s.reason).some(mandatoryFieldMissing);
    const anyOtherErrors = allErrors
        .filter((e) => e.type === FieldValidationIssueType.ERROR)
        .map((s) => s.reason)
        .some(otherError);
    const isPostingSplit = splittingState.state === 'POSTING';
    const firstChangedDuplicateInSplits = duplicateTracksInCurrentSplits.find(
        (dupe) => dupe.isDraft,
    );
    const unusedNonOverlappingDuplicates = splittingState.duplicateTracks.filter(
        (duplicateTrack) => {
            const duplicateTrackIsUsedInSplit = allSplits.some(
                (split) => split.duplicateTrackId === duplicateTrack.id,
            );
            return duplicateTrack.status.match === 'NONE' && !duplicateTrackIsUsedInSplit;
        },
    );
    const unusedNonOverlappingDuplicateNames = unusedNonOverlappingDuplicates.map(
        (duplicate) => duplicate.name,
    );
    const anyNonOverlappingDuplicates = unusedNonOverlappingDuplicates.length > 0;

    const [getNameRef, setNameRef] = useRefMap<SplitTargetId, HTMLInputElement>();
    const [getDescriptionBaseRef, setDescriptionBaseRef] = useRefMap<
        SplitTargetId,
        HTMLInputElement
    >();

    const postSplit = () => {
        startPostingSplit();
        postSplitLocationTrack(
            splitRequest(
                locationTrack.id,
                splittingState.firstSplit,
                splittingState.splits,
                duplicateTracksInCurrentSplits,
            ),
            layoutContext.branch,
        )
            .then(async () => {
                await updateAllChangeTimes();
                stopSplitting();
                success(
                    t('tool-panel.location-track.splitting.splitting-success', {
                        locationTrackName: locationTrack.name,
                        count: splitsValidated.length,
                    }),
                    undefined,
                    {
                        id: 'toast-splitting-success',
                    },
                );
            })
            .catch(() => returnToSplitting());
    };

    const focusFirstErrorFieldByPredicate = (predicate: (errorReason: string) => boolean) =>
        findFirstErroredField(
            splitComponents,
            predicate,
            getNameRef,
            getDescriptionBaseRef,
        )?.focus();

    React.useEffect(() => {
        const splitComponentToFocus = splitComponents.find(
            (s) => s.splitAndValidation.split.focusBehaviour === 'FOCUS',
        );
        if (splitComponentToFocus) {
            getNameRef(splitComponentToFocus.splitAndValidation.split.id)?.focus();
            unfocusSplit(splitComponentToFocus.splitAndValidation.split.splitPoint);
        }
    });
    const showSplitTrackOnMap = useShowSplitTrackOnMap(allSplits, splittingState, showArea);

    const splitComponents = useMinimallyUpdatedMappedList(
        splitsValidated,
        (split) =>
            createSplitComponent(
                split,
                switches,
                splittingState,
                removeSplit,
                updateSplit,
                isPostingSplit,
                duplicateTracksInCurrentSplits,
                showArea,
                showSplitTrackOnMap,
                setFocusedSplit,
                setHighlightedSplit,
                setHighlightedSplitPoint,
                setNameRef,
                setDescriptionBaseRef,
            ),
        (split) => split.split.id,
    );

    return (
        <React.Fragment>
            <Infobox
                contentVisible={visibilities.splitting}
                onContentVisibilityChange={() => visibilityChange('splitting')}
                title={t('tool-panel.location-track.splitting.title')}
                qa-id={'location-track-splitting-infobox'}>
                <InfoboxContent className={styles['location-track-infobox__split']}>
                    {splitComponents.map((split) => split.component)}
                    <LocationTrackSplittingEndpoint
                        splitPoint={splittingState.endSplitPoint}
                        addressPoint={sourceEnd}
                        editingDisabled={splittingState.disabled}
                        showArea={showArea}
                        onSplitPointClick={() =>
                            showArea(getShowSwitchOnMapBoundingBox(sourceEnd.point))
                        }
                    />
                    {splittingState.disabled && locationTrack.isDraft && (
                        <LocationTrackSplittingDraftExistsErrorNotice />
                    )}
                    {anyNonOverlappingDuplicates && (
                        <LocationTrackSplittingErrorNotice
                            msg={t(
                                'tool-panel.location-track.splitting.validation.unused-non-overlapping-duplicates-exist',
                                {
                                    duplicateNames: unusedNonOverlappingDuplicateNames.join(', '),
                                },
                            )}
                        />
                    )}
                    <LocationTrackSplitRelinkingNotice
                        splittingState={splittingState}
                        onClickRelink={() => setConfirmOpenTaskListAndExit(true)}
                        switchRelinkingErrors={switchRelinkingErrors}
                        switchRelinkingLoadingState={switchRelinkingLoaderState}
                    />
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
                            onClick={() => setConfirmExit(true)}
                            qa-id={'cancel-split'}>
                            {t('button.cancel')}
                        </Button>
                        <Button
                            size={ButtonSize.SMALL}
                            onClick={() => postSplit()}
                            isProcessing={isPostingSplit}
                            disabled={
                                splittingState.disabled ||
                                anyNonOverlappingDuplicates ||
                                anyMissingFields ||
                                anyOtherErrors ||
                                !!firstChangedDuplicateInSplits ||
                                splittingState.splits.length < 1 ||
                                isPostingSplit
                            }
                            qa-id={'confirm-split'}>
                            {t('tool-panel.location-track.splitting.confirm-split')}
                        </Button>
                    </InfoboxButtons>
                </InfoboxContent>
            </Infobox>
            {confirmExit && (
                <ExitSplittingConfirmationDialog
                    closeDialog={() => setConfirmExit(false)}
                    stopSplitting={stopSplitting}
                />
            )}
            {confirmOpenTaskListAndExit && (
                <ExitSplittingConfirmationDialog
                    closeDialog={() => setConfirmOpenTaskListAndExit(false)}
                    stopSplitting={stopSplitting}
                    showTaskList={() => onShowTaskList(locationTrack.id)}
                />
            )}
        </React.Fragment>
    );
};
