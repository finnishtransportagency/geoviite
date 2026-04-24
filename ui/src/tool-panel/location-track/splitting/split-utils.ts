import {
    DuplicateStatus,
    LayoutSwitch,
    LocationTrackDescriptionSuffixMode,
    LocationTrackId,
    LocationTrackNamingScheme,
    SplitPoint,
    splitPointsAreSame,
    SwitchNameParts,
} from 'track-layout/track-layout-model';
import {
    FirstSplitTargetCandidate,
    SplitRequest,
    SplitRequestTarget,
    SplitRequestTargetDuplicate,
    SplitTargetCandidate,
    SplitTargetId,
    SplitTargetOperation,
} from 'tool-panel/location-track/split-store';
import { FieldValidationIssue, FieldValidationIssueType } from 'utils/validation-utils';
import {
    validateLocationTrackDescriptionBase,
    validateLocationTrackName,
} from 'tool-panel/location-track/dialog/location-track-validation';
import { isEqualIgnoreCase } from 'utils/string-utils';
import { SwitchRelinkingValidationResult } from 'linking/linking-model';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

export const START_SPLIT_POINT_NOT_MATCHING_ERROR = 'split-point-not-matching-start';
export const END_SPLIT_POINT_NOT_MATCHING_ERROR = 'split-point-not-matching-end';

export type ValidatedSplit = {
    split: SplitTargetCandidate | FirstSplitTargetCandidate;
    nameIssues: FieldValidationIssue<SplitTargetCandidate>[];
    descriptionIssues: FieldValidationIssue<SplitTargetCandidate>[];
    switchIssues: FieldValidationIssue<SplitTargetCandidate>[];
};

export type SplitComponent = {
    component: React.JSX.Element;
    splitAndValidation: ValidatedSplit;
};

export const splitRequest = (
    sourceTrackId: LocationTrackId,
    firstSplit: FirstSplitTargetCandidate,
    splits: SplitTargetCandidate[],
): SplitRequest => ({
    sourceTrackId,
    targetTracks: [firstSplit, ...splits].map((s) => splitToRequestTarget(s)),
});

const splitToRequestTarget = (
    split: SplitTargetCandidate | FirstSplitTargetCandidate,
): SplitRequestTarget => {
    const duplicateTrack: SplitRequestTargetDuplicate | undefined =
        split.duplicateTrackId && split.operation !== 'CREATE'
            ? {
                  id: split.duplicateTrackId,
                  operation: split.operation,
              }
            : undefined;
    return {
        namingScheme: split.namingScheme,
        nameFreeText: split.nameFreeText,
        nameSpecifier: split.nameSpecifier,
        descriptionBase: split.descriptionBase ?? '',
        descriptionSuffix: split.suffixMode ?? 'NONE',
        duplicateTrack: duplicateTrack,
        startAtSwitchId:
            split.splitPoint.type === 'SWITCH_SPLIT_POINT' ? split.splitPoint.switchId : undefined,
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
    nameIssues: validateSplitName(
        split.name,
        allSplitNames,
        conflictingTrackNames,
        split.namingScheme,
        split.splitPoint,
        nextSplit ? nextSplit.splitPoint : lastSplitPoint,
    ),
    descriptionIssues: [
        ...validateSplitDescriptionBase(split.descriptionBase, split.duplicateTrackId),
        ...validateSplitDescriptionSuffix(
            split.suffixMode,
            split.splitPoint,
            nextSplit ? nextSplit.splitPoint : lastSplitPoint,
        ),
    ],
    switchIssues: validateSplitSwitch(
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
    splitNamingScheme: LocationTrackNamingScheme,
    startSplitPoint: SplitPoint,
    endSplitPoint: SplitPoint,
) => {
    const namingSchemeErrors: FieldValidationIssue<SplitTargetCandidate>[] = [];
    const startIsSwitch = startSplitPoint.type === 'SWITCH_SPLIT_POINT';
    const endIsSwitch = endSplitPoint.type === 'SWITCH_SPLIT_POINT';
    switch (splitNamingScheme) {
        case LocationTrackNamingScheme.CHORD:
        case LocationTrackNamingScheme.BETWEEN_OPERATIONAL_POINTS:
            if (!startIsSwitch || !endIsSwitch) {
                namingSchemeErrors.push({
                    field: 'namingScheme',
                    reason: 'name-switches-required-at-both-ends',
                    type: FieldValidationIssueType.ERROR,
                });
            }
            break;
        case LocationTrackNamingScheme.FREE_TEXT:
        case LocationTrackNamingScheme.WITHIN_OPERATIONAL_POINT:
        case LocationTrackNamingScheme.TRACK_NUMBER_TRACK:
            break;
        default:
            exhaustiveMatchingGuard(splitNamingScheme);
    }

    const conflictErrors: FieldValidationIssue<SplitTargetCandidate>[] = [];
    if (allSplitNames.filter((s) => s !== '' && isEqualIgnoreCase(s, splitName)).length > 1)
        conflictErrors.push({
            field: 'name',
            reason: 'conflicts-with-split',
            type: FieldValidationIssueType.ERROR,
        });
    if (conflictingTrackNames.map((t) => t.toLowerCase()).includes(splitName.toLowerCase())) {
        conflictErrors.push({
            field: 'name',
            reason: 'conflicts-with-track',
            type: FieldValidationIssueType.ERROR,
        });
    }

    return [...namingSchemeErrors, ...conflictErrors, ...validateLocationTrackName(splitName)];
};

const validateSplitDescriptionBase = (
    description: string,
    duplicateOf: LocationTrackId | undefined,
) => {
    const errors: FieldValidationIssue<SplitTargetCandidate>[] =
        validateLocationTrackDescriptionBase(description);
    if (!duplicateOf && description === '')
        errors.push({
            field: 'descriptionBase',
            reason: 'mandatory-field',
            type: FieldValidationIssueType.ERROR,
        });
    return errors;
};

const validateSplitDescriptionSuffix = (
    suffixMode: LocationTrackDescriptionSuffixMode,
    startSplitPoint: SplitPoint,
    endSplitPoint: SplitPoint,
): FieldValidationIssue<SplitTargetCandidate>[] => {
    const startIsSwitch = startSplitPoint.type === 'SWITCH_SPLIT_POINT';
    const endIsSwitch = endSplitPoint.type === 'SWITCH_SPLIT_POINT';

    switch (suffixMode) {
        case 'NONE':
            return [];
        case 'SWITCH_TO_SWITCH':
            if (!startIsSwitch || !endIsSwitch) {
                return [
                    {
                        field: 'suffixMode',
                        reason: 'description-switches-required-at-both-ends',
                        type: FieldValidationIssueType.ERROR,
                    },
                ];
            }
            return [];
        case 'SWITCH_TO_BUFFER':
        case 'SWITCH_TO_OWNERSHIP_BOUNDARY':
            if (!startIsSwitch && !endIsSwitch) {
                return [
                    {
                        field: 'suffixMode',
                        reason: 'no-end-switches',
                        type: FieldValidationIssueType.ERROR,
                    },
                ];
            } else if (
                startIsSwitch &&
                endIsSwitch &&
                startSplitPoint.switchId !== endSplitPoint.switchId
            ) {
                return [
                    {
                        field: 'suffixMode',
                        reason: 'switches-not-allowed-at-both-ends',
                        type: FieldValidationIssueType.ERROR,
                    },
                ];
            }
            return [];
    }
};

export const validateSplitSwitch = (
    split: SplitTargetCandidate | FirstSplitTargetCandidate,
    nextSplit: SplitTargetCandidate | FirstSplitTargetCandidate | undefined,
    switches: LayoutSwitch[],
    lastSplitPoint: SplitPoint,
): FieldValidationIssue<SplitTargetCandidate>[] => {
    const errors: FieldValidationIssue<SplitTargetCandidate>[] = [];

    if (
        !switches.some(
            (sw) =>
                split.splitPoint.type === 'SWITCH_SPLIT_POINT' &&
                sw.id === split.splitPoint.switchId,
        )
    ) {
        errors.push({
            field: 'splitPoint',
            reason: 'switch-not-found',
            type: FieldValidationIssueType.ERROR,
        });
    }
    if (
        split.duplicateStatus?.startSplitPoint &&
        !splitPointsAreSame(split.splitPoint, split.duplicateStatus?.startSplitPoint)
    ) {
        const type =
            split.operation === 'TRANSFER'
                ? FieldValidationIssueType.ERROR
                : FieldValidationIssueType.WARNING;
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
            split.operation === 'TRANSFER'
                ? FieldValidationIssueType.ERROR
                : FieldValidationIssueType.WARNING;
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

const hasErrors = (errorsReasons: string[], predicate: (errorReason: string) => boolean) =>
    errorsReasons.filter(predicate).length > 0;

export const findFirstErroredField = (
    splitComponents: SplitComponent[],
    predicate: (errorReason: string) => boolean,
    getNameRef: (id: SplitTargetId) => HTMLInputElement | undefined,
    getDescriptionBaseRef: (id: SplitTargetId) => HTMLInputElement | undefined,
): HTMLInputElement | undefined => {
    const invalidNameIndex = splitComponents.findIndex((s) =>
        hasErrors(
            s.splitAndValidation.nameIssues.map((err) => err.reason),
            predicate,
        ),
    );
    const invalidDescriptionBaseIndex = splitComponents.findIndex((s) =>
        hasErrors(
            s.splitAndValidation.descriptionIssues.map((err) => err.reason),
            predicate,
        ),
    );
    const invalidSwitchBaseIndex = splitComponents.findIndex((s) =>
        hasErrors(
            s.splitAndValidation.switchIssues.map((err) => err.reason),
            predicate,
        ),
    );
    const minIndex = [invalidNameIndex, invalidDescriptionBaseIndex, invalidSwitchBaseIndex]
        .filter((i) => i >= 0)
        .sort()[0];

    if (minIndex === undefined) return undefined;
    else {
        const id = splitComponents[minIndex]?.splitAndValidation?.split?.id;
        if (id !== undefined) {
            return minIndex === invalidNameIndex || minIndex === invalidSwitchBaseIndex
                ? getNameRef(id)
                : getDescriptionBaseRef(id);
        } else {
            return undefined;
        }
    }
};

export const hasUnrelinkableSwitches = (
    relinkingValidationResults: SwitchRelinkingValidationResult[],
) =>
    relinkingValidationResults.some((err) =>
        err.validationIssues.some((ve) => ve.type === 'ERROR'),
    );

export function getSwitchNameParts(
    splitPoint: SplitPoint,
    switches: LayoutSwitch[],
): SwitchNameParts | undefined {
    if (splitPoint.type === 'SWITCH_SPLIT_POINT') {
        return switches.find((s) => s.id === splitPoint.switchId)?.nameParts;
    }
    return undefined;
}

export const getOperation = (
    trackId: LocationTrackId,
    duplicateStatus: DuplicateStatus | undefined,
): SplitTargetOperation => {
    if (duplicateStatus === undefined) {
        // no duplicate -> new track
        return 'CREATE';
    } else if (
        duplicateStatus.duplicateOfId !== undefined &&
        duplicateStatus.duplicateOfId !== trackId
    ) {
        throw new Error(
            'Duplicate track is duplicate for some other track! This should be handled beforehand.',
        );
    } else {
        switch (duplicateStatus?.match) {
            case 'FULL':
                return 'OVERWRITE';
            case 'PARTIAL':
                return 'TRANSFER';
            case 'NONE':
                if (duplicateStatus.duplicateOfId === trackId) {
                    return 'OVERWRITE';
                } else {
                    throw new Error(
                        'Implicit duplicate track does not share any geometry with target track! This should be handled beforehand.',
                    );
                }
            default:
                throw new Error(
                    'Duplicate track does not share any geometry with target track! This should be handled beforehand.',
                );
        }
    }
};
