import * as React from 'react';
import { useTranslation } from 'react-i18next';
import InfoboxContent, { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';
import styles from 'tool-panel/location-track/location-track-infobox.scss';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import Infobox from 'tool-panel/infobox/infobox';
import { LocationTrackInfoboxVisibilities } from 'track-layout/track-layout-slice';
import {
    AddressPoint,
    AlignmentStartAndEnd,
    LayoutLocationTrack,
    LayoutSwitch,
    LayoutSwitchId,
    LocationTrackDuplicate,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import { MessageBox } from 'geoviite-design-lib/message-box/message-box';
import {
    InitialSplit,
    sortSplitsByDistance,
    Split,
    SwitchOnLocationTrack,
} from 'tool-panel/location-track/split-store';
import {
    useConflictingTracks,
    useLocationTrack,
    useLocationTrackStartAndEnd,
    useSwitches,
} from 'track-layout/track-layout-react-utils';
import { ValidationError, ValidationErrorType } from 'utils/validation-utils';
import {
    LocationTrackSplit,
    LocationTrackSplittingEndpoint,
} from 'tool-panel/location-track/splitting/location-track-split';
import { filterNotEmpty } from 'utils/array-utils';
import {
    validateLocationTrackDescriptionBase,
    validateLocationTrackName,
} from 'tool-panel/location-track/dialog/location-track-validation';
import { Link } from 'vayla-design-lib/link/link';
import { TimeStamp } from 'common/common-model';

type LocationTrackSplittingInfoboxContainerProps = {
    duplicateLocationTracks: LocationTrackDuplicate[];
    visibilities: LocationTrackInfoboxVisibilities;
    visibilityChange: (key: keyof LocationTrackInfoboxVisibilities) => void;
    initialSplit: InitialSplit;
    splits: Split[];
    allowedSwitches: SwitchOnLocationTrack[];
    switchChangeTime: TimeStamp;
    disabled: boolean;
    removeSplit: (switchId: LayoutSwitchId) => void;
    locationTrackId: string;
    locationTrackChangeTime: TimeStamp;
    cancelSplitting: () => void;
    updateSplit: (updatedSplit: Split | InitialSplit) => void;
    setSplittingDisabled: (disabled: boolean) => void;
};

type LocationTrackSplittingInfoboxProps = {
    duplicateLocationTracks: LocationTrackDuplicate[];
    visibilities: LocationTrackInfoboxVisibilities;
    visibilityChange: (key: keyof LocationTrackInfoboxVisibilities) => void;
    initialSplit: InitialSplit;
    splits: Split[];
    allowedSwitches: SwitchOnLocationTrack[];
    switches: LayoutSwitch[];
    disabled: boolean;
    removeSplit: (switchId: LayoutSwitchId) => void;
    locationTrack: LayoutLocationTrack;
    conflictingLocationTracks: string[];
    startAndEnd: AlignmentStartAndEnd;
    cancelSplitting: () => void;
    updateSplit: (updatedSplit: Split | InitialSplit) => void;
};

const validateSplitName = (
    splitName: string,
    allSplitNames: string[],
    conflictingTrackNames: string[],
) => {
    const errors: ValidationError<Split>[] = validateLocationTrackName(splitName);

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
    const errors: ValidationError<Split>[] = validateLocationTrackDescriptionBase(description);
    if (!duplicateOf && description === '')
        errors.push({
            field: 'descriptionBase',
            reason: 'mandatory-field',
            type: ValidationErrorType.ERROR,
        });
    return errors;
};

const validateSplitSwitch = (split: Split, switches: LayoutSwitch[]) => {
    const errors: ValidationError<Split>[] = [];
    if ('switchId' in split) {
        const switchAtSplit = switches.find((s) => s.id === split.switchId);
        if (!switchAtSplit || switchAtSplit.stateCategory === 'NOT_EXISTING') {
            errors.push({
                field: 'switchId',
                reason: 'switch-not-found',
                type: ValidationErrorType.ERROR,
            });
        }
    }
    return errors;
};

type ValidatedSplit = {
    split: Split | InitialSplit;
    nameErrors: ValidationError<Split>[];
    descriptionErrors: ValidationError<Split>[];
    switchErrors: ValidationError<Split>[];
};

type SplitComponentAndRefs = {
    component: JSX.Element;
    split: ValidatedSplit;
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
    initialSplit,
    splits,
    duplicateLocationTracks,
    visibilities,
    visibilityChange,
    allowedSwitches,
    switchChangeTime,
    removeSplit,
    cancelSplitting,
    updateSplit,
    setSplittingDisabled,
    disabled,
}) => {
    const locationTrack = useLocationTrack(locationTrackId, 'DRAFT', locationTrackChangeTime);
    const [startAndEnd, _] = useLocationTrackStartAndEnd(
        locationTrackId,
        'DRAFT',
        locationTrackChangeTime,
    );
    const conflictingTracks = useConflictingTracks(
        locationTrack?.trackNumberId,
        [initialSplit, ...splits].map((s) => s.name),
        [initialSplit, ...splits].map((s) => s.duplicateOf).filter(filterNotEmpty),
        'DRAFT',
    );
    const switches = useSwitches(
        allowedSwitches.map((sw) => sw.switchId),
        'DRAFT',
        switchChangeTime,
    );

    React.useEffect(() => {
        locationTrack && setSplittingDisabled(locationTrack?.draftType !== 'OFFICIAL');
    }, [locationTrack, locationTrackChangeTime]);

    return (
        locationTrack &&
        startAndEnd && (
            <LocationTrackSplittingInfobox
                duplicateLocationTracks={duplicateLocationTracks}
                visibilities={visibilities}
                visibilityChange={visibilityChange}
                initialSplit={initialSplit}
                splits={splits}
                allowedSwitches={allowedSwitches}
                switches={switches}
                removeSplit={removeSplit}
                cancelSplitting={cancelSplitting}
                updateSplit={updateSplit}
                startAndEnd={startAndEnd}
                conflictingLocationTracks={conflictingTracks?.map((t) => t.name) || []}
                locationTrack={locationTrack}
                disabled={disabled}
            />
        )
    );
};

const hasErrors = (errorsReasons: string[], predicate: (errorReason: string) => boolean) =>
    errorsReasons.filter(predicate).length > 0;

function findAndFocusFirstErroredField(
    splitComponents: SplitComponentAndRefs[],
    predicate: (errorReason: string) => boolean,
) {
    const invalidNameIndex = splitComponents.findIndex((s) =>
        hasErrors(
            s.split.nameErrors.map((err) => err.reason),
            predicate,
        ),
    );
    const invalidDescriptionBaseIndex = splitComponents.findIndex((s) =>
        hasErrors(
            s.split.descriptionErrors.map((err) => err.reason),
            predicate,
        ),
    );

    if (invalidNameIndex === -1 && invalidDescriptionBaseIndex === -1) return;
    if (
        invalidDescriptionBaseIndex === -1 ||
        (invalidNameIndex >= 0 && invalidNameIndex <= invalidDescriptionBaseIndex)
    ) {
        splitComponents[invalidNameIndex]?.nameRef?.current?.focus();
    } else {
        splitComponents[invalidDescriptionBaseIndex]?.descriptionBaseRef?.current?.focus();
    }
}

const getSplitAddressPoint = (
    allowedSwitches: SwitchOnLocationTrack[],
    startAndEnd: AlignmentStartAndEnd | undefined,
    split: Split | InitialSplit,
): AddressPoint | undefined => {
    if ('switchId' in split) {
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

export const LocationTrackSplittingInfobox: React.FC<LocationTrackSplittingInfoboxProps> = ({
    duplicateLocationTracks,
    visibilities,
    visibilityChange,
    initialSplit,
    splits,
    allowedSwitches,
    switches,
    removeSplit,
    cancelSplitting,
    updateSplit,
    startAndEnd,
    conflictingLocationTracks,
    locationTrack,
    disabled,
}) => {
    const { t } = useTranslation();

    const sortedSplits = sortSplitsByDistance(splits);
    const allSplitNames = [initialSplit, ...splits].map((s) => s.name);

    const initialSplitValidated = {
        split: initialSplit,
        nameErrors: validateSplitName(initialSplit.name, allSplitNames, conflictingLocationTracks),
        descriptionErrors: validateSplitDescription(
            initialSplit.descriptionBase,
            initialSplit.duplicateOf,
        ),
        switchErrors: [],
    };
    const splitsValidated = sortedSplits.map((s) => ({
        split: s,
        nameErrors: validateSplitName(s.name, allSplitNames, conflictingLocationTracks),
        descriptionErrors: validateSplitDescription(s.descriptionBase, s.duplicateOf),
        switchErrors: validateSplitSwitch(s, switches),
    }));
    const allValidated = [initialSplitValidated, ...splitsValidated];

    const allErrors = allValidated.flatMap((validated) => [
        ...validated.descriptionErrors,
        ...validated.nameErrors,
        ...validated.switchErrors,
    ]);
    const anyMissingFields = allErrors.map((s) => s.reason).some(mandatoryFieldMissing);
    const anyOtherErrors = allErrors.map((s) => s.reason).some(otherError);

    const splitComponents: SplitComponentAndRefs[] = allValidated.map((splitValidated) => {
        const nameRef = React.createRef<HTMLInputElement>();
        const descriptionBaseRef = React.createRef<HTMLInputElement>();

        const switchExists =
            switches.find(
                (s) => 'switchId' in splitValidated.split && s.id === splitValidated.split.switchId,
            )?.stateCategory !== 'NOT_EXISTING';

        const { split, nameErrors, descriptionErrors, switchErrors } = splitValidated;
        return {
            component: (
                <LocationTrackSplit
                    key={`${split.location.x}_${split.location.y}`}
                    split={split}
                    addressPoint={getSplitAddressPoint(allowedSwitches, startAndEnd, split)}
                    onRemove={removeSplit}
                    duplicateLocationTracks={duplicateLocationTracks}
                    updateSplit={updateSplit}
                    duplicateOf={split.duplicateOf}
                    nameErrors={nameErrors}
                    descriptionErrors={descriptionErrors}
                    switchErrors={switchErrors}
                    editingDisabled={disabled || !switchExists}
                    deletingDisabled={disabled}
                    nameRef={nameRef}
                    descriptionBaseRef={descriptionBaseRef}
                />
            ),
            split: splitValidated,
            nameRef,
            descriptionBaseRef,
        };
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
                                                    findAndFocusFirstErroredField(
                                                        splitComponents,
                                                        mandatoryFieldMissing,
                                                    )
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
                                                    findAndFocusFirstErroredField(
                                                        splitComponents,
                                                        otherError,
                                                    )
                                                }>
                                                {t(
                                                    'tool-panel.location-track.splitting.validation.show',
                                                )}
                                            </Link>
                                        </MessageBox>
                                    </InfoboxContentSpread>
                                )}
                            </React.Fragment>
                        )}
                        <InfoboxButtons>
                            <Button
                                variant={ButtonVariant.SECONDARY}
                                size={ButtonSize.SMALL}
                                onClick={cancelSplitting}>
                                {t('button.cancel')}
                            </Button>
                            <Button
                                size={ButtonSize.SMALL}
                                disabled={disabled || anyMissingFields || anyOtherErrors}>
                                {t('tool-panel.location-track.splitting.confirm-split')}
                            </Button>
                        </InfoboxButtons>
                    </InfoboxContent>
                </Infobox>
            )}
        </React.Fragment>
    );
};