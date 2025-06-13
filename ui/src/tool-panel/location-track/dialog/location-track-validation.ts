import { FieldValidationIssue, FieldValidationIssueType } from 'utils/validation-utils';

export const ALIGNMENT_NAME_REGEX = /^[A-Za-zÄÖÅäöå0-9 \-_]+$/g;
export const ALIGNMENT_DESCRIPTION_REGEX = /^[A-ZÄÖÅa-zäöå0-9 _\-–—+().,'"/\\<>:!?&]+$/g;
export const ALIGNMENT_NAME_MAX_LENGTH = 50;

// TODO: GVT-3080
export const validateLocationTrackName = (
    name: string,
): FieldValidationIssue<{ name: string }>[] => {
    if (name && (!name.match(ALIGNMENT_NAME_REGEX) || name.length > ALIGNMENT_NAME_MAX_LENGTH)) {
        return [
            {
                field: 'name',
                reason: `invalid-name`,
                type: FieldValidationIssueType.ERROR,
            },
        ];
    } else if (name.trim() === '') {
        return [
            {
                field: 'name',
                reason: `mandatory-field`,
                type: FieldValidationIssueType.ERROR,
            },
        ];
    } else {
        return [];
    }
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
