import {
    DuplicateStatus,
    LayoutLocationTrack,
    LayoutSwitch,
    LocationTrackId,
    LocationTrackNamingScheme,
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
import { FieldValidationIssue, FieldValidationIssueType } from 'utils/validation-utils';
import { validateLocationTrackDescriptionBase } from 'tool-panel/location-track/dialog/location-track-validation';
import { isEqualIgnoreCase } from 'utils/string-utils';
import { SwitchRelinkingValidationResult } from 'linking/linking-model';

export const START_SPLIT_POINT_NOT_MATCHING_ERROR = 'split-point-not-matching-start';
export const END_SPLIT_POINT_NOT_MATCHING_ERROR = 'split-point-not-matching-end';

export type ValidatedSplit = {
    split: SplitTargetCandidate | FirstSplitTargetCandidate;
    nameIssues: FieldValidationIssue<SplitTargetCandidate>[];
    descriptionIssues: FieldValidationIssue<SplitTargetCandidate>[];
    switchIssues: FieldValidationIssue<SplitTargetCandidate>[];
};

export type SplitComponentAndRefs = {
    component: React.JSX.Element;
    splitAndValidation: ValidatedSplit;
    nameRef: React.RefObject<HTMLInputElement | null>;
    descriptionBaseRef: React.RefObject<HTMLInputElement | null>;
};

export const splitRequest = (
    sourceTrackId: LocationTrackId,
    firstSplit: FirstSplitTargetCandidate,
    splits: SplitTargetCandidate[],
    allDuplicates: LayoutLocationTrack[],
): SplitRequest => ({
    sourceTrackId,
    targetTracks: [firstSplit, ...splits].map((s) => {
        const duplicate = s.duplicateTrackId
            ? findById(allDuplicates, s.duplicateTrackId)
            : undefined;
        return splitToRequestTarget(s, duplicate);
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
        namingScheme: duplicate?.nameStructure?.namingScheme ?? LocationTrackNamingScheme.FREE_TEXT,
        nameFreeText:
            duplicate?.nameStructure && 'nameFreeText' in duplicate.nameStructure
                ? duplicate?.nameStructure?.nameFreeText
                : '',
        nameSpecifier:
            duplicate?.nameStructure && 'nameSpecifier' in duplicate.nameStructure
                ? duplicate?.nameStructure?.nameSpecifier
                : undefined,
        descriptionBase:
            (duplicate ? duplicate.descriptionStructure.descriptionBase : split.descriptionBase) ??
            '',
        descriptionSuffix:
            (duplicate ? duplicate.descriptionStructure.descriptionSuffix : split.suffixMode) ??
            'NONE',
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
    nameIssues: validateSplitName(split.name, allSplitNames, conflictingTrackNames),
    descriptionIssues: validateSplitDescription(split.descriptionBase, split.duplicateTrackId),
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
) => {
    // TODO: GVT-3080
    const errors: FieldValidationIssue<SplitTargetCandidate>[] = [];
    // validateLocationTrackName(splitName);

    if (allSplitNames.filter((s) => s !== '' && isEqualIgnoreCase(s, splitName)).length > 1)
        errors.push({
            field: 'name',
            reason: 'conflicts-with-split',
            type: FieldValidationIssueType.ERROR,
        });
    if (conflictingTrackNames.map((t) => t.toLowerCase()).includes(splitName.toLowerCase())) {
        errors.push({
            field: 'name',
            reason: 'conflicts-with-track',
            type: FieldValidationIssueType.ERROR,
        });
    }
    return errors;
};

const validateSplitDescription = (
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

export const findRefToFirstErroredField = (
    splitComponents: SplitComponentAndRefs[],
    predicate: (errorReason: string) => boolean,
): React.RefObject<HTMLInputElement | null> | undefined => {
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
    else if (minIndex === invalidNameIndex || minIndex === invalidSwitchBaseIndex)
        return splitComponents[minIndex]?.nameRef;
    else return splitComponents[minIndex]?.descriptionBaseRef;
};

export const hasUnrelinkableSwitches = (
    relinkingValidationResults: SwitchRelinkingValidationResult[],
) =>
    relinkingValidationResults.some((err) =>
        err.validationIssues.some((ve) => ve.type === 'ERROR'),
    );

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
