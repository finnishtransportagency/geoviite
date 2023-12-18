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

type LocationTrackSplittingInfoboxContainerProps = {
    duplicateLocationTracks: LocationTrackDuplicate[];
    visibilities: LocationTrackInfoboxVisibilities;
    visibilityChange: (key: keyof LocationTrackInfoboxVisibilities) => void;
    initialSplit: InitialSplit;
    splits: Split[];
    allowedSwitches: SwitchOnLocationTrack[];
    removeSplit: (switchId: LayoutSwitchId) => void;
    locationTrackId: string;
    cancelSplitting: () => void;
    updateSplit: (updatedSplit: Split | InitialSplit) => void;
};

type LocationTrackSplittingInfoboxProps = {
    duplicateLocationTracks: LocationTrackDuplicate[];
    visibilities: LocationTrackInfoboxVisibilities;
    visibilityChange: (key: keyof LocationTrackInfoboxVisibilities) => void;
    initialSplit: InitialSplit;
    splits: Split[];
    allowedSwitches: SwitchOnLocationTrack[];
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

type ValidatedSplit = {
    split: Split | InitialSplit;
    nameErrors: ValidationError<Split>[];
    descriptionErrors: ValidationError<Split>[];
};

type SplitComponentAndRefs = {
    component: JSX.Element;
    split: ValidatedSplit;
    nameRef: React.RefObject<HTMLInputElement>;
    descriptionBaseRef: React.RefObject<HTMLInputElement>;
};

const mandatoryFieldMissing = (error: string) => error === 'mandatory-field';
const otherError = (error: string) => error !== 'mandatory-field';

export const LocationTrackSplittingInfoboxContainer: React.FC<
    LocationTrackSplittingInfoboxContainerProps
> = ({
    locationTrackId,
    initialSplit,
    splits,
    duplicateLocationTracks,
    visibilities,
    visibilityChange,
    allowedSwitches,
    removeSplit,
    cancelSplitting,
    updateSplit,
}) => {
    const locationTrack = useLocationTrack(locationTrackId, 'DRAFT');
    const [startAndEnd, _] = useLocationTrackStartAndEnd(locationTrackId, 'DRAFT');
    const conflictingTracks = useConflictingTracks(
        locationTrack?.trackNumberId,
        [initialSplit, ...splits].map((s) => s.name),
        [initialSplit, ...splits].map((s) => s.duplicateOf).filter(filterNotEmpty),
        'DRAFT',
    );

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
                removeSplit={removeSplit}
                cancelSplitting={cancelSplitting}
                updateSplit={updateSplit}
                startAndEnd={startAndEnd}
                conflictingLocationTracks={conflictingTracks?.map((t) => t.name) || []}
                locationTrack={locationTrack}
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

export const LocationTrackSplittingInfobox: React.FC<LocationTrackSplittingInfoboxProps> = ({
    duplicateLocationTracks,
    visibilities,
    visibilityChange,
    initialSplit,
    splits,
    allowedSwitches,
    removeSplit,
    cancelSplitting,
    updateSplit,
    startAndEnd,
    conflictingLocationTracks,
    locationTrack,
}) => {
    const { t } = useTranslation();
    const getSplitAddressPoint = (
        split: Split | InitialSplit,
        startAndEnd: AlignmentStartAndEnd | undefined,
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

    const sortedSplits = sortSplitsByDistance(splits);
    const allSplitNames = [initialSplit, ...splits].map((s) => s.name);

    const initialSplitValidated = {
        split: initialSplit,
        nameErrors: validateSplitName(initialSplit.name, allSplitNames, conflictingLocationTracks),
        descriptionErrors: validateSplitDescription(
            initialSplit.descriptionBase,
            initialSplit.duplicateOf,
        ),
    };
    const splitsValidated = sortedSplits.map((s) => ({
        split: s,
        nameErrors: validateSplitName(s.name, allSplitNames, conflictingLocationTracks),
        descriptionErrors: validateSplitDescription(s.descriptionBase, s.duplicateOf),
    }));
    const allValidated = [initialSplitValidated, ...splitsValidated];

    const allErrors = allValidated.flatMap((validated) => [
        ...validated.descriptionErrors,
        ...validated.nameErrors,
    ]);
    const anyMissingFields = allErrors.map((s) => s.reason).some(mandatoryFieldMissing);
    const anyOtherErrors = allErrors.map((s) => s.reason).some(otherError);

    const splitComponents: SplitComponentAndRefs[] = allValidated.map((splitValidated) => {
        const nameRef = React.createRef<HTMLInputElement>();
        const descriptionBaseRef = React.createRef<HTMLInputElement>();

        const { split, nameErrors, descriptionErrors } = splitValidated;
        return {
            component: (
                <LocationTrackSplit
                    key={`${split.location.x}_${split.location.y}`}
                    split={split}
                    addressPoint={getSplitAddressPoint(split, startAndEnd)}
                    onRemove={removeSplit}
                    duplicateLocationTracks={duplicateLocationTracks}
                    updateSplit={updateSplit}
                    duplicateOf={split.duplicateOf}
                    nameErrors={nameErrors}
                    descriptionErrors={descriptionErrors}
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
                        <LocationTrackSplittingEndpoint addressPoint={startAndEnd.end} />
                        {splits.length === 0 && (
                            <InfoboxContentSpread>
                                <MessageBox>
                                    {t('tool-panel.location-track.splitting.splitting-guide')}
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
                                        {t('tool-panel.location-track.splitting.validation.show')}
                                    </Link>
                                </MessageBox>
                            </InfoboxContentSpread>
                        )}
                        {anyOtherErrors && (
                            <InfoboxContentSpread>
                                <MessageBox>
                                    {t('tool-panel.location-track.splitting.validation.has-errors')}
                                    ,{' '}
                                    <Link
                                        onClick={() =>
                                            findAndFocusFirstErroredField(
                                                splitComponents,
                                                otherError,
                                            )
                                        }>
                                        {t('tool-panel.location-track.splitting.validation.show')}
                                    </Link>
                                </MessageBox>
                            </InfoboxContentSpread>
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
                                disabled={anyMissingFields || anyOtherErrors}>
                                {t('tool-panel.location-track.splitting.confirm-split')}
                            </Button>
                        </InfoboxButtons>
                    </InfoboxContent>
                </Infobox>
            )}
        </React.Fragment>
    );
};
