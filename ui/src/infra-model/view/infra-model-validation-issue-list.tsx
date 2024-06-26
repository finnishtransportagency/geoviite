import React from 'react';
import styles from './form/infra-model-form.module.scss';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { useTranslation } from 'react-i18next';
import {
    CustomGeometryValidationIssue,
    GeometryValidationIssueType,
    ValidationResponse,
} from 'infra-model/infra-model-slice';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

type InframodelValidationIssueListProps = {
    validationResponse?: ValidationResponse;
};

const getIcon = (issueType: GeometryValidationIssueType) => {
    switch (issueType) {
        case 'REQUEST_ERROR':
        case 'PARSING_ERROR':
        case 'VALIDATION_ERROR':
        case 'TRANSFORMATION_ERROR':
            return Icons.StatusError;
        case 'OBSERVATION_MAJOR':
        case 'OBSERVATION_MINOR':
            return Icons.StatusError;
        default:
            return exhaustiveMatchingGuard(issueType);
    }
};

const createErrorRow = (issueType: GeometryValidationIssueType, message: string, index: number) => {
    const Icon = getIcon(issueType);
    return (
        <li key={index}>
            <div className={styles['infra-model-upload__error']}>
                <Icon color={IconColor.INHERIT} size={IconSize.MEDIUM} />
                <span>{message}</span>
            </div>
        </li>
    );
};

const InfraModelValidationIssueList: React.FC<InframodelValidationIssueListProps> = ({
    validationResponse,
}: InframodelValidationIssueListProps) => {
    const { t } = useTranslation();
    const errorListDiv = (errorList: ValidationResponse) => {
        const errors = errorList.geometryValidationIssues.filter(
            (e) =>
                e.issueType === 'PARSING_ERROR' ||
                e.issueType === 'VALIDATION_ERROR' ||
                e.issueType === 'TRANSFORMATION_ERROR',
        );
        const major = errorList.geometryValidationIssues.filter(
            (e) => e.issueType === 'OBSERVATION_MAJOR',
        );
        const minor = errorList.geometryValidationIssues.filter(
            (e) => e.issueType === 'OBSERVATION_MINOR',
        );

        return (
            <div className={styles['infra-model-upload__validation-errors']}>
                {errors.length > 0 && (
                    <section>
                        <h2 className={styles['infra-model-upload__validation-error-title']}>
                            {t('im-form.validation-errors')}
                        </h2>
                        <ul className={styles['infra-model-upload__errors']}>
                            {errors.map((error: CustomGeometryValidationIssue, index: number) =>
                                createErrorRow(
                                    error.issueType,
                                    t(error.localizationKey, error),
                                    index,
                                ),
                            )}
                        </ul>
                    </section>
                )}
                {(major.length > 0 || minor.length > 0) && (
                    <section>
                        <h2 className={styles['infra-model-upload__validation-error-title']}>
                            {t('im-form.validation-warnings')}
                        </h2>
                        <ol className={styles['infra-model-upload__errors']}>
                            {major.map((error, index) =>
                                createErrorRow(
                                    error.issueType,
                                    t(error.localizationKey, error),
                                    index,
                                ),
                            )}

                            {minor.map((error, index) =>
                                createErrorRow(
                                    error.issueType,
                                    t(error.localizationKey, error),
                                    index,
                                ),
                            )}
                        </ol>
                    </section>
                )}
            </div>
        );
    };

    const noFileSelected = () => {
        return <div>No file selected</div>;
    };

    return (
        <React.Fragment>
            {validationResponse ? errorListDiv(validationResponse) : noFileSelected()}
        </React.Fragment>
    );
};

export default InfraModelValidationIssueList;
