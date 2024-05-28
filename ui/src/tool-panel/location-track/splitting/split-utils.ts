import {
    AddressPoint,
    DuplicateStatus,
    LayoutLocationTrack,
    LayoutSwitch,
    LayoutSwitchId,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import {
    FirstSplitTargetCandidate,
    SplitRequest,
    SplitRequestTarget,
    SplitRequestTargetDuplicate,
    SplitTargetCandidate,
    SplitTargetOperation,
    SwitchOnLocationTrack,
} from 'tool-panel/location-track/split-store';
import { findById } from 'utils/array-utils';
import { FieldValidationIssue, FieldValidationIssueType } from 'utils/validation-utils';
import {
    validateLocationTrackDescriptionBase,
    validateLocationTrackName,
} from 'tool-panel/location-track/dialog/location-track-validation';
import { isEqualIgnoreCase } from 'utils/string-utils';
import { SwitchRelinkingValidationResult } from 'linking/linking-model';

export const START_SWITCH_NOT_MATCHING_ERROR = 'switch-not-matching-start-switch';
export const END_SWITCH_NOT_MATCHING_ERROR = 'switch-not-matching-end-switch';

export type ValidatedSplit = {
    split: SplitTargetCandidate | FirstSplitTargetCandidate;
    nameIssues: FieldValidationIssue<SplitTargetCandidate>[];
    descriptionIssues: FieldValidationIssue<SplitTargetCandidate>[];
    switchIssues: FieldValidationIssue<SplitTargetCandidate>[];
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
        startAtSwitchId: split.type === 'SPLIT' ? split?.switch.switchId : undefined,
    };
};

export const validateSplit = (
    split: FirstSplitTargetCandidate | SplitTargetCandidate,
    nextSplit: FirstSplitTargetCandidate | SplitTargetCandidate | undefined,
    allSplitNames: string[],
    conflictingTrackNames: string[],
    switches: LayoutSwitch[],
    lastSwitch: LayoutSwitch | undefined,
): ValidatedSplit => ({
    split: split,
    nameIssues: validateSplitName(split.name, allSplitNames, conflictingTrackNames),
    descriptionIssues: validateSplitDescription(split.descriptionBase, split.duplicateTrackId),
    switchIssues: validateSplitSwitch(
        split,
        nextSplit,
        switches
            .filter((layoutSwitch) => layoutSwitch.stateCategory !== 'NOT_EXISTING')
            .map((layoutSwitch) => layoutSwitch.id),
        lastSwitch,
    ),
});

const validateSplitName = (
    splitName: string,
    allSplitNames: string[],
    conflictingTrackNames: string[],
) => {
    const errors: FieldValidationIssue<SplitTargetCandidate>[] =
        validateLocationTrackName(splitName);

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
    switchIds: LayoutSwitchId[],
    lastSwitch: LayoutSwitch | undefined,
): FieldValidationIssue<SplitTargetCandidate>[] => {
    const errors: FieldValidationIssue<SplitTargetCandidate>[] = [];
    const switchExists = split.switch !== undefined && switchIds.includes(split.switch.switchId);
    if (split.type === 'SPLIT' && !switchExists) {
        errors.push({
            field: 'switch',
            reason: 'switch-not-found',
            type: FieldValidationIssueType.ERROR,
        });
    }
    const switchIdAtDuplicateStart = split.duplicateStatus?.startSwitchId;
    if (switchIdAtDuplicateStart && split.switch?.switchId !== switchIdAtDuplicateStart) {
        const type =
            split.operation == 'TRANSFER'
                ? FieldValidationIssueType.ERROR
                : FieldValidationIssueType.WARNING;
        errors.push({
            field: 'switch',
            reason: START_SWITCH_NOT_MATCHING_ERROR,
            type: type,
            params: { selectedSwitchName: split.switch?.name, trackName: split.name },
        });
    }
    const switchIdAtDuplicateEnd = split.duplicateStatus?.endSwitchId;
    const nextSwitch = nextSplit?.switch || lastSwitch;
    if (switchIdAtDuplicateEnd && nextSplit?.switch?.switchId !== switchIdAtDuplicateEnd) {
        const type =
            split.operation == 'TRANSFER'
                ? FieldValidationIssueType.ERROR
                : FieldValidationIssueType.WARNING;
        errors.push({
            field: 'switch',
            reason: END_SWITCH_NOT_MATCHING_ERROR,
            type: type,
            params: { selectedSwitchName: nextSwitch?.name, trackName: split.name },
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
    const minIndex = [invalidNameIndex, invalidDescriptionBaseIndex]
        .filter((i) => i >= 0)
        .sort()[0];

    if (minIndex === undefined) return undefined;
    else if (minIndex === invalidNameIndex) return splitComponents[minIndex]?.nameRef;
    else return splitComponents[minIndex]?.descriptionBaseRef;
};

export const getSplitAddressPoint = (
    allowedSwitches: SwitchOnLocationTrack[],
    originLocationTrackStart: AddressPoint,
    split: SplitTargetCandidate | FirstSplitTargetCandidate,
): AddressPoint | undefined => {
    if (split.type === 'SPLIT') {
        const switchAtSplit = allowedSwitches.find((s) => s.switchId === split.switch.switchId);

        if (!switchAtSplit?.location || !switchAtSplit?.address) {
            return undefined;
        } else {
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
};

export const hasUnrelinkableSwitches = (
    relinkingValidationResults: SwitchRelinkingValidationResult[],
) =>
    relinkingValidationResults.some((err) =>
        err.validationIssues.some((ve) => ve.type === 'ERROR'),
    );

export const getOperation = (
    trackId: LocationTrackId,
    switchId: LayoutSwitchId | undefined,
    duplicateStatus: DuplicateStatus | undefined,
): SplitTargetOperation => {
    switch (duplicateStatus?.match) {
        case 'FULL':
            return 'OVERWRITE';
        case 'PARTIAL':
            return switchId !== undefined && duplicateStatus?.startSwitchId === switchId
                ? 'TRANSFER'
                : 'OVERWRITE';
        default:
            return duplicateStatus?.duplicateOfId === trackId ? 'OVERWRITE' : 'CREATE';
    }
};
