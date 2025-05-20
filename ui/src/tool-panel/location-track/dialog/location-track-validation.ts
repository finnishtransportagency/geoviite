import { FieldValidationIssue, FieldValidationIssueType } from 'utils/validation-utils';
import { LocationTrackNaming } from 'track-layout/track-layout-model';

export const ALIGNMENT_NAME_REGEX = /^[A-Za-zÄÖÅäöå0-9 \-_]+$/g;
export const ALIGNMENT_DESCRIPTION_REGEX = /^[A-ZÄÖÅa-zäöå0-9 _\-–—+().,'"/\\<>:!?&]+$/g;
export const ALIGNMENT_NAME_MAX_LENGTH = 50;

export const validateLocationTrackName = (
    name: LocationTrackNaming,
): FieldValidationIssue<{ namingScheme: LocationTrackNaming }>[] => {
    if (
        !name.freeText ||
        name.freeText.match(
            ALIGNMENT_NAME_REGEX || name.freeText.length > ALIGNMENT_NAME_MAX_LENGTH,
        )
    ) {
        return [
            {
                field: 'namingScheme',
                reason: `invalid-name`,
                type: FieldValidationIssueType.ERROR,
            },
        ];
    } else if (name.freeText && name.freeText.trim() === '') {
        return [
            {
                field: 'namingScheme',
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
