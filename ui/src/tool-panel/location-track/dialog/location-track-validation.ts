import { FieldValidationIssue, FieldValidationIssueType } from 'utils/validation-utils';

export const ALIGNMENT_NAME_REGEX = /^[A-Za-zÄÖÅäöå0-9 \-_]+$/g;
export const ALIGNMENT_NAME_MAX_LENGTH = 50;

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
    return descriptionBase && (descriptionBase.length < 4 || descriptionBase.length > 256)
        ? [
              {
                  field: 'descriptionBase',
                  reason: 'invalid-description',
                  type: FieldValidationIssueType.ERROR,
              },
          ]
        : [];
};
