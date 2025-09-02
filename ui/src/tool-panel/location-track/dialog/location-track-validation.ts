import { FieldValidationIssue, FieldValidationIssueType } from 'utils/validation-utils';
import { LocationTrackNamingScheme } from 'track-layout/track-layout-model';
import { filterNotEmpty } from 'utils/array-utils';
import { LocationTrackSaveRequest } from 'linking/linking-model';
import { isNilOrBlank } from 'utils/string-utils';
import { isNil } from 'utils/type-utils';

export const ALIGNMENT_NAME_REGEX = /^[A-Za-zÄÖÅäöå0-9 \-_]+$/g;
export const ALIGNMENT_DESCRIPTION_REGEX = /^[A-ZÄÖÅa-zäöå0-9 _\-–—+().,'"/\\<>:!?&]+$/g;
export const ALIGNMENT_NAME_MAX_LENGTH = 50;

const MANDATORY_FREE_TEXT_SCHEMES: LocationTrackNamingScheme[] = [
    LocationTrackNamingScheme.FREE_TEXT,
    LocationTrackNamingScheme.WITHIN_OPERATING_POINT,
] as const;
const ALL_FREE_TEXT_SCHEMES: LocationTrackNamingScheme[] = [
    LocationTrackNamingScheme.TRACK_NUMBER_TRACK,
    ...MANDATORY_FREE_TEXT_SCHEMES,
] as const;

const MANDATORY_NAME_SPECIFIER_SCHEMES: LocationTrackNamingScheme[] = [
    LocationTrackNamingScheme.TRACK_NUMBER_TRACK,
] as const;

export const validateLocationTrackName = (
    name: string | undefined,
): FieldValidationIssue<{ name: string }>[] => {
    if (isNilOrBlank(name)) {
        return [
            {
                field: 'name',
                reason: `mandatory-field`,
                type: FieldValidationIssueType.ERROR,
            },
        ];
    } else if (
        name &&
        (!name.match(ALIGNMENT_NAME_REGEX) || name.length > ALIGNMENT_NAME_MAX_LENGTH)
    ) {
        return [
            {
                field: 'name',
                reason: `invalid-name`,
                type: FieldValidationIssueType.ERROR,
            },
        ];
    } else {
        return [];
    }
};

export const validateLocationTrackNameStructure = (
    saveRequest: LocationTrackSaveRequest,
): FieldValidationIssue<LocationTrackSaveRequest>[] => {
    const namingScheme = saveRequest.namingScheme;
    const nameFreeText = saveRequest.nameFreeText;
    const nameSpecifier = saveRequest.nameSpecifier;

    return (
        [
            isNil(namingScheme)
                ? {
                      field: 'namingScheme',
                      reason: 'mandatory-field',
                      type: FieldValidationIssueType.ERROR,
                  }
                : undefined,
            MANDATORY_FREE_TEXT_SCHEMES.includes(
                namingScheme ?? LocationTrackNamingScheme.FREE_TEXT,
            ) && isNilOrBlank(nameFreeText)
                ? {
                      field: 'nameFreeText',
                      reason: 'mandatory-field',
                      type: FieldValidationIssueType.ERROR,
                  }
                : undefined,
            ALL_FREE_TEXT_SCHEMES.includes(namingScheme ?? LocationTrackNamingScheme.FREE_TEXT) &&
            !isNilOrBlank(nameFreeText) &&
            (!nameFreeText?.match(ALIGNMENT_NAME_REGEX) ||
                nameFreeText.length > ALIGNMENT_NAME_MAX_LENGTH)
                ? {
                      field: 'nameFreeText',
                      reason:
                          saveRequest.namingScheme === 'TRACK_NUMBER_TRACK'
                              ? 'invalid-operating-point-range-name'
                              : 'invalid-name',
                      type: FieldValidationIssueType.ERROR,
                  }
                : undefined,
            MANDATORY_NAME_SPECIFIER_SCHEMES.includes(
                namingScheme ?? LocationTrackNamingScheme.FREE_TEXT,
            ) && isNil(nameSpecifier)
                ? {
                      field: 'nameSpecifier',
                      reason: 'mandatory-field',
                      type: FieldValidationIssueType.ERROR,
                  }
                : undefined,
        ] satisfies (FieldValidationIssue<LocationTrackSaveRequest> | undefined)[]
    ).filter(filterNotEmpty);
};

export const validateLocationTrackDescriptionBase = (
    descriptionBase: string | undefined,
): FieldValidationIssue<{ descriptionBase?: string }>[] => {
    if (descriptionBase) {
        if (descriptionBase.length < 4 || descriptionBase.length > 256) {
            return [
                {
                    field: 'descriptionBase',
                    reason: 'invalid-description-length',
                    type: FieldValidationIssueType.ERROR,
                },
            ];
        } else if (!descriptionBase.match(ALIGNMENT_DESCRIPTION_REGEX)) {
            return [
                {
                    field: 'descriptionBase',
                    reason: 'invalid-description-characters',
                    type: FieldValidationIssueType.ERROR,
                },
            ];
        }
    }

    return [];
};
