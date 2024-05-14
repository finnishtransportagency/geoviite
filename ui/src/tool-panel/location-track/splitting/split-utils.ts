import {
    DuplicateStatus,
    LayoutLocationTrack,
    LayoutSwitch,
    LocationTrackId,
    SplitPoint,
    splitPointsAreSame,
} from 'track-layout/track-layout-model';
import {
    FirstSplitTargetCandidate,
    SplitRequest,
    SplitRequestTarget,
    SplitRequestTargetDuplicate,
    SplitTargetCandidate,
    SplitTargetOperation,
} from 'tool-panel/location-track/split-store';
import { findById } from 'utils/array-utils';
import { ValidationError, ValidationErrorType } from 'utils/validation-utils';
import {
    validateLocationTrackDescriptionBase,
    validateLocationTrackName,
} from 'tool-panel/location-track/dialog/location-track-validation';
import { isEqualIgnoreCase } from 'utils/string-utils';
import { SwitchRelinkingValidationResult } from 'linking/linking-model';

export const START_SPLIT_POINT_NOT_MATCHING_ERROR = 'split-point-not-matching-start';
export const END_SPLIT_POINT_NOT_MATCHING_ERROR = 'split-point-not-matching-end';

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
        const dupe = s.duplicateTrackId ? findById(allDuplicates, s.duplicateTrackId) : undefined;
        return splitToRequestTarget(s, dupe);
    }),
});

const splitToRequestTarget = (
    split: SplitTargetCandidate | FirstSplitTargetCandidate,
    duplicate: LayoutLocationTrack | undefined,
): SplitRequestTarget => {
    const duplicateTrack: SplitRequestTargetDuplicate | undefined =
        split.duplicateTrackId && split.operation !== 'CREATE'
            ? {
                  id: split.duplicateTrackId,
                  operation: split.operation,
              }
            : undefined;
    return {
        name: duplicate ? duplicate.name : split.name,
        descriptionBase: (duplicate ? duplicate.descriptionBase : split.descriptionBase) ?? '',
        descriptionSuffix: (duplicate ? duplicate.descriptionSuffix : split.suffixMode) ?? 'NONE',
        duplicateTrack: duplicateTrack,
        // TODO: split point tännekkin
        startAtSwitchId:
            split.splitPoint.type == 'switchSplitPoint' ? split.splitPoint.switchId : undefined,
        //split.type === 'SPLIT' ? split?.switch.switchId : undefined,
    };
};

export const validateSplit = (
    split: FirstSplitTargetCandidate | SplitTargetCandidate,
    nextSplit: FirstSplitTargetCandidate | SplitTargetCandidate | undefined,
    allSplitNames: string[],
    conflictingTrackNames: string[],
    switches: LayoutSwitch[],
    lastSplitPoint: SplitPoint,
): ValidatedSplit => ({
    split: split,
    nameErrors: validateSplitName(split.name, allSplitNames, conflictingTrackNames),
    descriptionErrors: validateSplitDescription(split.descriptionBase, split.duplicateTrackId),
    switchErrors: validateSplitSwitch(
        split,
        nextSplit,
        switches.filter((layoutSwitch) => layoutSwitch.stateCategory !== 'NOT_EXISTING'),
        lastSplitPoint,
    ),
});

const validateSplitName = (
    splitName: string,
    allSplitNames: string[],
    conflictingTrackNames: string[],
) => {
    const errors: ValidationError<SplitTargetCandidate>[] = validateLocationTrackName(splitName);

    if (allSplitNames.filter((s) => s !== '' && isEqualIgnoreCase(s, splitName)).length > 1)
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

export const validateSplitSwitch = (
    split: SplitTargetCandidate | FirstSplitTargetCandidate,
    nextSplit: SplitTargetCandidate | FirstSplitTargetCandidate | undefined,
    switches: LayoutSwitch[],
    lastSplitPoint: SplitPoint,
): ValidationError<SplitTargetCandidate>[] => {
    const errors: ValidationError<SplitTargetCandidate>[] = [];

    if (
        !switches.some(
            (sw) =>
                split.splitPoint.type == 'switchSplitPoint' && sw.id == split.splitPoint.switchId,
        )
    ) {
        errors.push({
            field: 'splitPoint',
            reason: 'switch-not-found',
            type: ValidationErrorType.ERROR,
        });
    }

    if (
        split.duplicateStatus?.startSplitPoint &&
        !splitPointsAreSame(split.splitPoint, split.duplicateStatus?.startSplitPoint)
    ) {
        const type =
            split.operation == 'TRANSFER' ? ValidationErrorType.ERROR : ValidationErrorType.WARNING;
        errors.push({
            field: 'splitPoint',
            reason: START_SPLIT_POINT_NOT_MATCHING_ERROR,
            type: type,
            params: {
                expectedSplitPoint: split.duplicateStatus?.startSplitPoint.name,
                selectedSplitPoint: split.splitPoint.name,
                trackName: split.name,
            },
        });
    }

    const nextSplitPoint = nextSplit ? nextSplit.splitPoint : lastSplitPoint;
    if (
        split.duplicateStatus?.endSplitPoint &&
        !splitPointsAreSame(nextSplitPoint, split.duplicateStatus?.endSplitPoint)
    ) {
        const type =
            split.operation == 'TRANSFER' ? ValidationErrorType.ERROR : ValidationErrorType.WARNING;
        errors.push({
            field: 'splitPoint',
            reason: END_SPLIT_POINT_NOT_MATCHING_ERROR,
            type: type,
            params: {
                expectedSplitPoint: split.duplicateStatus?.endSplitPoint.name,
                selectedSplitPoint: nextSplitPoint.name,
                trackName: split.name,
            },
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
    else if (minIndex === invalidNameIndex) return splitComponents[minIndex]?.nameRef;
    else return splitComponents[minIndex]?.descriptionBaseRef;
};

export const hasUnrelinkableSwitches = (switchRelinkingErrors: SwitchRelinkingValidationResult[]) =>
    switchRelinkingErrors?.some((err) => !err.successfulSuggestion) || false;

export const getOperation = (
    trackId: LocationTrackId,
    _splitPoint: SplitPoint,
    duplicateStatus: DuplicateStatus | undefined,
): SplitTargetOperation => {
    if (duplicateStatus === undefined) {
        // no duplicate -> new track
        return 'CREATE';
    } else if (duplicateStatus.duplicateOfId != trackId) {
        throw new Error(
            'Duplicate track is duplicate for some other track! This should be handled beforehand.',
        );
    } else {
        switch (duplicateStatus?.match) {
            case 'FULL':
                return 'OVERWRITE';
            case 'PARTIAL':
                return 'TRANSFER';
            default:
                throw new Error(
                    'Duplicate track does not share any geometry with target track! This should be handled beforehand.',
                );
        }
    }
};
