import * as React from 'react';
import { useTranslation } from 'react-i18next';
import InfoboxContent, { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';
import styles from 'tool-panel/location-track/location-track-infobox.scss';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import Infobox from 'tool-panel/infobox/infobox';
import {
    LocationTrackInfoboxVisibilities,
    trackLayoutActionCreators as TrackLayoutActions,
} from 'track-layout/track-layout-slice';
import {
    AddressPoint,
    AlignmentStartAndEnd,
    LayoutLocationTrack,
    LayoutSwitch,
    LayoutSwitchId,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import { MessageBox } from 'geoviite-design-lib/message-box/message-box';
import {
    FirstSplitTargetCandidate,
    sortSplitsByDistance,
    SplitRequest,
    SplitRequestTarget,
    SplitTargetCandidate,
    SwitchOnLocationTrack,
} from 'tool-panel/location-track/split-store';
import {
    useConflictingTracks,
    useLocationTrack,
    useLocationTrackInfoboxExtras,
    useLocationTracks,
    useLocationTrackStartAndEnd,
    useSwitches,
} from 'track-layout/track-layout-react-utils';
import { ValidationError, ValidationErrorType } from 'utils/validation-utils';
import {
    LocationTrackSplit,
    LocationTrackSplittingEndpoint,
} from 'tool-panel/location-track/splitting/location-track-split';
import { filterNotEmpty, findById } from 'utils/array-utils';
import {
    validateLocationTrackDescriptionBase,
    validateLocationTrackName,
} from 'tool-panel/location-track/dialog/location-track-validation';
import { Link } from 'vayla-design-lib/link/link';
import { TimeStamp } from 'common/common-model';
import { getChangeTimes } from 'common/change-time-api';
import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { validateLocationTrackSwitchRelinking } from 'linking/linking-api';
import { postSplitLocationTrack } from 'publication/split/split-api';
import { Spinner, SpinnerSize } from 'vayla-design-lib/spinner/spinner';
import { createDelegates } from 'store/store-utils';

type LocationTrackSplittingInfoboxContainerProps = {
    visibilities: LocationTrackInfoboxVisibilities;
    visibilityChange: (key: keyof LocationTrackInfoboxVisibilities) => void;
    firstSplit: FirstSplitTargetCandidate;
    splits: SplitTargetCandidate[];
    allowedSwitches: SwitchOnLocationTrack[];
    switchChangeTime: TimeStamp;
    disabled: boolean;
    removeSplit: (switchId: LayoutSwitchId) => void;
    locationTrackId: string;
    locationTrackChangeTime: TimeStamp;
    stopSplitting: () => void;
    updateSplit: (updatedSplit: SplitTargetCandidate | FirstSplitTargetCandidate) => void;
    setSplittingDisabled: (disabled: boolean) => void;
    isPostingSplit: boolean;
    returnToSplitting: () => void;
    startPostingSplit: () => void;
    markSplitOld: (switchId: LayoutSwitchId | undefined) => void;
};

type LocationTrackSplittingInfoboxProps = {
    visibilities: LocationTrackInfoboxVisibilities;
    visibilityChange: (key: keyof LocationTrackInfoboxVisibilities) => void;
    firstSplit: FirstSplitTargetCandidate;
    splits: SplitTargetCandidate[];
    allowedSwitches: SwitchOnLocationTrack[];
    switches: LayoutSwitch[];
    disabled: boolean;
    removeSplit: (switchId: LayoutSwitchId) => void;
    locationTrack: LayoutLocationTrack;
    conflictingLocationTracks: string[];
    startAndEnd: AlignmentStartAndEnd;
    stopSplitting: () => void;
    updateSplit: (updatedSplit: SplitTargetCandidate | FirstSplitTargetCandidate) => void;
    isPostingSplit: boolean;
    returnToSplitting: () => void;
    startPostingSplit: () => void;
    markSplitOld: (switchId: LayoutSwitchId | undefined) => void;
    onShowTaskList: (locationTrackId: LocationTrackId) => void;
};

const validateSplitName = (
    splitName: string,
    allSplitNames: string[],
    conflictingTrackNames: string[],
) => {
    const errors: ValidationError<SplitTargetCandidate>[] = validateLocationTrackName(splitName);

    if (
        allSplitNames.filter((s) => s !== '' && s.toLowerCase() === splitName.toLowerCase())
            .length > 1
    )
        errors.push({
            field: 'name',
            reason: 'conflicts-with-split',
            type: ValidationErrorType.ERROR,
        });
    if (conflictingTrackNames.map((t) => t.toLowerCase()).includes(splitName.toLowerCase())) {
        errors.push({
            field: 'name',
            reason: 'conflicts-with-track',
            type: ValidationErrorType.ERROR,
        });
    }
    return errors;
};

const validateSplitDescription = (
    description: string,
    duplicateOf: LocationTrackId | undefined,
) => {
    const errors: ValidationError<SplitTargetCandidate>[] =
        validateLocationTrackDescriptionBase(description);
    if (!duplicateOf && description === '')
        errors.push({
            field: 'descriptionBase',
            reason: 'mandatory-field',
            type: ValidationErrorType.ERROR,
        });
    return errors;
};

const validateSplitSwitch = (split: SplitTargetCandidate, switches: LayoutSwitch[]) => {
    const errors: ValidationError<SplitTargetCandidate>[] = [];
    const switchAtSplit = switches.find((s) => s.id === split.switchId);
    if (!switchAtSplit || switchAtSplit.stateCategory === 'NOT_EXISTING') {
        errors.push({
            field: 'switchId',
            reason: 'switch-not-found',
            type: ValidationErrorType.ERROR,
        });
    }
    return errors;
};

type ValidatedSplit = {
    split: SplitTargetCandidate | FirstSplitTargetCandidate;
    nameErrors: ValidationError<SplitTargetCandidate>[];
    descriptionErrors: ValidationError<SplitTargetCandidate>[];
    switchErrors: ValidationError<SplitTargetCandidate>[];
};

type SplitComponentAndRefs = {
    component: JSX.Element;
    splitAndValidation: ValidatedSplit;
    nameRef: React.RefObject<HTMLInputElement>;
    descriptionBaseRef: React.RefObject<HTMLInputElement>;
};

const mandatoryFieldMissing = (error: string) => error === 'mandatory-field';
const switchDeleted = (error: string) => error === 'switch-not-found';
const otherError = (error: string) => !mandatoryFieldMissing(error) && !switchDeleted(error);

export const LocationTrackSplittingInfoboxContainer: React.FC<
    LocationTrackSplittingInfoboxContainerProps
> = ({
    locationTrackId,
    locationTrackChangeTime,
    firstSplit,
    splits,
    visibilities,
    visibilityChange,
    allowedSwitches,
    switchChangeTime,
    removeSplit,
    stopSplitting,
    updateSplit,
    setSplittingDisabled,
    disabled,
    isPostingSplit,
    returnToSplitting,
    startPostingSplit,
    markSplitOld,
}) => {
    const delegates = createDelegates(TrackLayoutActions);
    const locationTrack = useLocationTrack(locationTrackId, 'DRAFT', locationTrackChangeTime);

    const [startAndEnd, _] = useLocationTrackStartAndEnd(
        locationTrackId,
        'DRAFT',
        locationTrackChangeTime,
    );
    const conflictingTracks = useConflictingTracks(
        locationTrack?.trackNumberId,
        [firstSplit, ...splits].map((s) => s.name),
        [firstSplit, ...splits].map((s) => s.duplicateOf).filter(filterNotEmpty),
        'DRAFT',
    );
    const allowedSwitchIds = React.useMemo(
        () => allowedSwitches.map((sw) => sw.switchId),
        [allowedSwitches],
    );
    const switches = useSwitches(allowedSwitchIds, 'DRAFT', switchChangeTime);

    React.useEffect(() => {
        locationTrack && setSplittingDisabled(locationTrack?.draftType !== 'OFFICIAL');
    }, [locationTrack, locationTrackChangeTime]);

    const onShowTaskList = (locationTrack: LocationTrackId) => {
        delegates.showLocationTrackTaskList(locationTrack);
    };

    return (
        locationTrack &&
        startAndEnd && (
            <LocationTrackSplittingInfobox
                visibilities={visibilities}
                visibilityChange={visibilityChange}
                firstSplit={firstSplit}
                splits={splits}
                allowedSwitches={allowedSwitches}
                switches={switches}
                removeSplit={removeSplit}
                stopSplitting={stopSplitting}
                updateSplit={updateSplit}
                startAndEnd={startAndEnd}
                conflictingLocationTracks={conflictingTracks?.map((t) => t.name) || []}
                locationTrack={locationTrack}
                disabled={disabled}
                isPostingSplit={isPostingSplit}
                returnToSplitting={returnToSplitting}
                startPostingSplit={startPostingSplit}
                markSplitOld={markSplitOld}
                onShowTaskList={onShowTaskList}
            />
        )
    );
};

const hasErrors = (errorsReasons: string[], predicate: (errorReason: string) => boolean) =>
    errorsReasons.filter(predicate).length > 0;

const findRefToFirstErroredField = (
    splitComponents: SplitComponentAndRefs[],
    predicate: (errorReason: string) => boolean,
): React.RefObject<HTMLInputElement> | undefined => {
    const invalidNameIndex = splitComponents.findIndex((s) =>
        hasErrors(
            s.splitAndValidation.nameErrors.map((err) => err.reason),
            predicate,
        ),
    );
    const invalidDescriptionBaseIndex = splitComponents.findIndex((s) =>
        hasErrors(
            s.splitAndValidation.descriptionErrors.map((err) => err.reason),
            predicate,
        ),
    );
    const minIndex = [invalidNameIndex, invalidDescriptionBaseIndex]
        .filter((i) => i >= 0)
        .sort()[0];

    if (minIndex === undefined) return undefined;
    else if (minIndex === invalidNameIndex) return splitComponents[minIndex].nameRef;
    else return splitComponents[minIndex].descriptionBaseRef;
};

const getSplitAddressPoint = (
    allowedSwitches: SwitchOnLocationTrack[],
    startAndEnd: AlignmentStartAndEnd | undefined,
    split: SplitTargetCandidate | FirstSplitTargetCandidate,
): AddressPoint | undefined => {
    if (split.type === 'SPLIT') {
        const switchAtSplit = allowedSwitches.find((s) => s.switchId === split.switchId);

        if (switchAtSplit?.location && switchAtSplit?.address) {
            return {
                point: { ...switchAtSplit.location, m: -1 },
                address: switchAtSplit.address,
            };
        }
    } else if (startAndEnd && startAndEnd.start) {
        return {
            point: startAndEnd.start.point,
            address: startAndEnd.start.address,
        };
    }

    return undefined;
};

const splitRequest = (
    sourceTrackId: LocationTrackId,
    firstSplit: FirstSplitTargetCandidate,
    splits: SplitTargetCandidate[],
    allDuplicates: LayoutLocationTrack[],
): SplitRequest => ({
    sourceTrackId,
    targetTracks: [firstSplit, ...splits].map((s) => {
        const dupe = s.duplicateOf ? findById(allDuplicates, s.duplicateOf) : undefined;
        return splitToRequestTarget(s, dupe);
    }),
});

const splitToRequestTarget = (
    split: SplitTargetCandidate | FirstSplitTargetCandidate,
    duplicate: LayoutLocationTrack | undefined,
): SplitRequestTarget => ({
    name: duplicate ? duplicate.name : split.name,
    descriptionBase: (duplicate ? duplicate.descriptionBase : split.descriptionBase) ?? '',
    descriptionSuffix: (duplicate ? duplicate.descriptionSuffix : split.suffixMode) ?? 'NONE',
    duplicateTrackId: split.duplicateOf,
    startAtSwitchId: split.type === 'SPLIT' ? split?.switchId : undefined,
});

export const LocationTrackSplittingInfobox: React.FC<LocationTrackSplittingInfoboxProps> = ({
    visibilities,
    visibilityChange,
    firstSplit,
    splits,
    allowedSwitches,
    switches,
    removeSplit,
    stopSplitting,
    updateSplit,
    startAndEnd,
    conflictingLocationTracks,
    locationTrack,
    disabled,
    isPostingSplit,
    returnToSplitting,
    startPostingSplit,
    markSplitOld,
    onShowTaskList,
}) => {
    const { t } = useTranslation();
    const [confirmExit, setConfirmExit] = React.useState(false);

    const sortedSplits = sortSplitsByDistance(splits);
    const allSplitNames = [firstSplit, ...splits].map((s) => s.name);
    const [switchRelinkingErrors, switchRelinkingState] = useLoaderWithStatus(async () => {
        const validations = await validateLocationTrackSwitchRelinking(locationTrack.id);
        return validations.filter((v) => v.validationErrors.length > 0).map((r) => r.id);
    }, [getChangeTimes().layoutLocationTrack, getChangeTimes().layoutSwitch]);

    const firstSplitValidated = {
        split: firstSplit,
        nameErrors: validateSplitName(firstSplit.name, allSplitNames, conflictingLocationTracks),
        descriptionErrors: validateSplitDescription(
            firstSplit.descriptionBase,
            firstSplit.duplicateOf,
        ),
        switchErrors: [],
    };
    const splitsValidated = sortedSplits.map((s) => ({
        split: s,
        nameErrors: validateSplitName(s.name, allSplitNames, conflictingLocationTracks),
        descriptionErrors: validateSplitDescription(s.descriptionBase, s.duplicateOf),
        switchErrors: validateSplitSwitch(s, switches),
    }));
    const allValidated = [firstSplitValidated, ...splitsValidated];
    const duplicateTracksInCurrentSplits = useLocationTracks(
        allValidated.map((s) => s.split.duplicateOf).filter(filterNotEmpty),
        'DRAFT',
        getChangeTimes().layoutLocationTrack,
    );
    const [locationTrackInfoboxExtras, _] = useLocationTrackInfoboxExtras(
        locationTrack.id,
        'DRAFT',
        getChangeTimes().layoutLocationTrack,
        getChangeTimes().layoutSwitch,
    );

    const allErrors = allValidated.flatMap((validated) => [
        ...validated.descriptionErrors,
        ...validated.nameErrors,
        ...validated.switchErrors,
    ]);
    const anyMissingFields = allErrors.map((s) => s.reason).some(mandatoryFieldMissing);
    const anyOtherErrors = allErrors.map((s) => s.reason).some(otherError);

    const splitComponents: SplitComponentAndRefs[] = allValidated.map(
        (splitValidated, splitIndex) => {
            const nameRef = React.createRef<HTMLInputElement>();
            const descriptionBaseRef = React.createRef<HTMLInputElement>();

            const switchExists =
                switches.find(
                    (s) =>
                        splitValidated.split.type === 'SPLIT' &&
                        s.id === splitValidated.split.switchId,
                )?.stateCategory !== 'NOT_EXISTING';

            const { split, nameErrors, descriptionErrors, switchErrors } = splitValidated;
            return {
                component: (
                    <LocationTrackSplit
                        key={`${split.location.x}_${split.location.y}`}
                        split={split}
                        addressPoint={getSplitAddressPoint(allowedSwitches, startAndEnd, split)}
                        onRemove={splitIndex > 0 ? removeSplit : undefined}
                        updateSplit={updateSplit}
                        duplicateOf={split.duplicateOf}
                        nameErrors={nameErrors}
                        descriptionErrors={descriptionErrors}
                        switchErrors={switchErrors}
                        editingDisabled={disabled || !switchExists || isPostingSplit}
                        deletingDisabled={disabled || isPostingSplit}
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
                splitAndValidation: splitValidated,
                nameRef,
                descriptionBaseRef,
            };
        },
    );

    const firstChangedDuplicateInSplits = duplicateTracksInCurrentSplits.find(
        (dupe) => dupe.draftType !== 'OFFICIAL',
    );

    const postSplit = () => {
        startPostingSplit();
        postSplitLocationTrack(
            splitRequest(
                locationTrack.id,
                firstSplit,
                sortSplitsByDistance(splits),
                duplicateTracksInCurrentSplits,
            ),
        )
            .then(() => stopSplitting())
            .catch(() => returnToSplitting());
    };

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

    return (
        <React.Fragment>
            {startAndEnd?.start && startAndEnd?.end && locationTrack && (
                <Infobox
                    contentVisible={visibilities.splitting}
                    onContentVisibilityChange={() => visibilityChange('splitting')}
                    title={t('tool-panel.location-track.splitting.title')}>
                    <InfoboxContent className={styles['location-track-infobox__split']}>
                        {splitComponents.map((split) => split.component)}
                        <LocationTrackSplittingEndpoint
                            addressPoint={startAndEnd.end}
                            editingDisabled={disabled}
                        />
                        {disabled && (
                            <InfoboxContentSpread>
                                <MessageBox type={'ERROR'}>
                                    {t(
                                        'tool-panel.location-track.splitting.validation.track-draft-exists',
                                    )}
                                </MessageBox>
                            </InfoboxContentSpread>
                        )}
                        {!disabled && (
                            <React.Fragment>
                                <InfoboxContentSpread>
                                    {switchRelinkingState == LoaderStatus.Ready &&
                                        switchRelinkingErrors &&
                                        switchRelinkingErrors.length > 0 && (
                                            <MessageBox>
                                                {t(
                                                    'tool-panel.location-track.splitting.relink-message',
                                                )}
                                                <div
                                                    className={
                                                        styles[
                                                            'location-track-infobox__relink-link'
                                                        ]
                                                    }>
                                                    <Link
                                                        onClick={() => {
                                                            stopSplitting();
                                                            onShowTaskList(locationTrack.id);
                                                        }}>
                                                        {t(
                                                            'tool-panel.location-track.splitting.cancel-and-relink',
                                                            {
                                                                count: switchRelinkingErrors.length,
                                                            },
                                                        )}
                                                    </Link>
                                                </div>
                                            </MessageBox>
                                        )}
                                    {switchRelinkingState == LoaderStatus.Loading && (
                                        <MessageBox>
                                            <span
                                                className={
                                                    styles[
                                                        'location-track-infobox__validate-switch-relinking'
                                                    ]
                                                }>
                                                {t(
                                                    'tool-panel.location-track.splitting.validation-in-progress',
                                                )}
                                                <Spinner size={SpinnerSize.SMALL} />
                                            </span>
                                        </MessageBox>
                                    )}
                                </InfoboxContentSpread>
                                {splits.length === 0 && (
                                    <InfoboxContentSpread>
                                        <MessageBox>
                                            {t(
                                                'tool-panel.location-track.splitting.splitting-guide',
                                            )}
                                        </MessageBox>
                                    </InfoboxContentSpread>
                                )}
                                {anyMissingFields && (
                                    <InfoboxContentSpread>
                                        <MessageBox>
                                            {t(
                                                'tool-panel.location-track.splitting.validation.missing-fields',
                                            )}
                                            ,{' '}
                                            <Link
                                                onClick={() =>
                                                    findRefToFirstErroredField(
                                                        splitComponents,
                                                        mandatoryFieldMissing,
                                                    )?.current?.focus()
                                                }>
                                                {t(
                                                    'tool-panel.location-track.splitting.validation.show',
                                                )}
                                            </Link>
                                        </MessageBox>
                                    </InfoboxContentSpread>
                                )}
                                {anyOtherErrors && (
                                    <InfoboxContentSpread>
                                        <MessageBox>
                                            {t(
                                                'tool-panel.location-track.splitting.validation.has-errors',
                                            )}
                                            ,{' '}
                                            <Link
                                                onClick={() =>
                                                    findRefToFirstErroredField(
                                                        splitComponents,
                                                        otherError,
                                                    )?.current?.focus()
                                                }>
                                                {t(
                                                    'tool-panel.location-track.splitting.validation.show',
                                                )}
                                            </Link>
                                        </MessageBox>
                                    </InfoboxContentSpread>
                                )}
                                {firstChangedDuplicateInSplits && (
                                    <InfoboxContentSpread>
                                        <MessageBox type={'ERROR'}>
                                            <div>
                                                {t(
                                                    'tool-panel.location-track.splitting.validation.duplicate-not-published',
                                                    {
                                                        duplicateName:
                                                            firstChangedDuplicateInSplits.name,
                                                    },
                                                )}
                                            </div>
                                            <br />
                                            <div>
                                                {t(
                                                    'tool-panel.location-track.splitting.validation.publish-duplicate',
                                                )}
                                            </div>
                                        </MessageBox>
                                    </InfoboxContentSpread>
                                )}
                            </React.Fragment>
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
                                    disabled ||
                                    anyMissingFields ||
                                    anyOtherErrors ||
                                    !!firstChangedDuplicateInSplits ||
                                    splits.length < 1 ||
                                    isPostingSplit
                                }>
                                {t('tool-panel.location-track.splitting.confirm-split')}
                            </Button>
                        </InfoboxButtons>
                    </InfoboxContent>
                </Infobox>
            )}
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
