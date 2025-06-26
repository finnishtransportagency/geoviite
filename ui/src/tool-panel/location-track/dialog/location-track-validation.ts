import { FieldValidationIssue, FieldValidationIssueType } from 'utils/validation-utils';
import { LocationTrackNamingScheme } from 'track-layout/track-layout-model';
import { filterNotEmpty } from 'utils/array-utils';
import { LocationTrackSaveRequest } from 'linking/linking-model';
import { isNilOrBlank } from 'utils/string-utils';
import { isNil } from 'utils/type-utils';

export const ALIGNMENT_NAME_REGEX = /^[A-Za-zÄÖÅäöå0-9 \-_]+$/g;
export const ALIGNMENT_DESCRIPTION_REGEX = /^[A-ZÄÖÅa-zäöå0-9 _\-–—+().,'"/\\<>:!?&]+$/g;
export const ALIGNMENT_NAME_MAX_LENGTH = 50;

const freeTextSchemes = [
    LocationTrackNamingScheme.FREE_TEXT,
    LocationTrackNamingScheme.WITHIN_OPERATING_POINT,
    LocationTrackNamingScheme.TRACK_NUMBER_TRACK,
];
const specifierSchemes = [
    LocationTrackNamingScheme.TRACK_NUMBER_TRACK,
    LocationTrackNamingScheme.BETWEEN_OPERATING_POINTS,
];

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

    const errors: {
        field: keyof LocationTrackSaveRequest;
        reason: string;
        type: FieldValidationIssueType;
    }[] = [
        isNil(namingScheme)
            ? {
                  field: 'namingScheme' as keyof LocationTrackSaveRequest,
                  reason: 'mandatory-field',
                  type: FieldValidationIssueType.ERROR,
              }
            : undefined,
        freeTextSchemes.includes(namingScheme ?? LocationTrackNamingScheme.FREE_TEXT) &&
        isNilOrBlank(nameFreeText)
            ? {
                  field: 'nameFreeText' as keyof LocationTrackSaveRequest,
                  reason: 'mandatory-field',
                  type: FieldValidationIssueType.ERROR,
              }
            : undefined,
        freeTextSchemes.includes(namingScheme ?? LocationTrackNamingScheme.FREE_TEXT) &&
        !isNilOrBlank(nameFreeText) &&
        (!nameFreeText?.match(ALIGNMENT_NAME_REGEX) ||
            nameFreeText.length > ALIGNMENT_NAME_MAX_LENGTH)
            ? {
                  field: 'nameFreeText' as keyof LocationTrackSaveRequest,
                  reason: `invalid-name`,
                  type: FieldValidationIssueType.ERROR,
              }
            : undefined,
        specifierSchemes.includes(namingScheme ?? LocationTrackNamingScheme.FREE_TEXT) &&
        isNil(nameSpecifier)
            ? {
                  field: 'nameSpecifier' as keyof LocationTrackSaveRequest,
                  reason: 'mandatory-field',
                  type: FieldValidationIssueType.ERROR,
              }
            : undefined,
    ].filter(filterNotEmpty);
    return errors;
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
