import {
    AddressPoint,
    LayoutLocationTrack,
    LayoutSwitch,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import {
    FirstSplitTargetCandidate,
    SplitRequest,
    SplitRequestTarget,
    SplitTargetCandidate,
    SwitchOnLocationTrack,
} from 'tool-panel/location-track/split-store';
import { findById } from 'utils/array-utils';
import { ValidationError, ValidationErrorType } from 'utils/validation-utils';
import {
    validateLocationTrackDescriptionBase,
    validateLocationTrackName,
} from 'tool-panel/location-track/dialog/location-track-validation';

export type ValidatedSplit = {
    split: SplitTargetCandidate | FirstSplitTargetCandidate;
    nameErrors: ValidationError<SplitTargetCandidate>[];
    descriptionErrors: ValidationError<SplitTargetCandidate>[];
    switchErrors: ValidationError<SplitTargetCandidate>[];
};

export type SplitComponentAndRefs = {
    component: JSX.Element;
    splitAndValidation: ValidatedSplit;
    nameRef: React.RefObject<HTMLInputElement>;
    descriptionBaseRef: React.RefObject<HTMLInputElement>;
};

export const splitRequest = (
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

export const validateSplit = (
    split: FirstSplitTargetCandidate | SplitTargetCandidate,
    allSplitNames: string[],
    conflictingTrackNames: string[],
    switches: LayoutSwitch[],
) => ({
    split: split,
    nameErrors: validateSplitName(split.name, allSplitNames, conflictingTrackNames),
    descriptionErrors: validateSplitDescription(split.descriptionBase, split.duplicateOf),
    switchErrors: split.type === 'SPLIT' ? validateSplitSwitch(split, switches) : [],
});

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

export const mandatoryFieldMissing = (error: string) => error === 'mandatory-field';
export const switchDeleted = (error: string) => error === 'switch-not-found';
export const otherError = (error: string) => !mandatoryFieldMissing(error) && !switchDeleted(error);

export const hasErrors = (errorsReasons: string[], predicate: (errorReason: string) => boolean) =>
    errorsReasons.filter(predicate).length > 0;

export const findRefToFirstErroredField = (
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

export const getSplitAddressPoint = (
    allowedSwitches: SwitchOnLocationTrack[],
    originLocationTrackStart: AddressPoint,
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
    } else {
        return {
            point: originLocationTrackStart.point,
            address: originLocationTrackStart.address,
        };
    }

    return undefined;
};
