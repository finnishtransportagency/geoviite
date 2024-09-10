import React from 'react';
import styles from './form/infra-model-form.module.scss';
import { InfraModelBaseProps, InfraModelView } from 'infra-model/view/infra-model-view';
import {
    getGeometryValidationIssuesForInfraModelFile,
    saveInfraModelFile,
} from 'infra-model/infra-model-api';
import { convertToNativeFile, SerializableFile } from 'utils/file-utils';
import { useTranslation } from 'react-i18next';
import { CharsetSelectDialog } from './dialogs/charset-select-dialog';
import { ValidationResponse } from 'infra-model/infra-model-slice';

export type InfraModelUploadLoaderProps = InfraModelBaseProps & {
    file?: SerializableFile;
    onValidation: (validationResponse: ValidationResponse) => void;
};

export const InfraModelUploadLoader: React.FC<InfraModelUploadLoaderProps> = ({ ...props }) => {
    const { t } = useTranslation();

    const [file, setFile] = React.useState<File>();

    const extraParams = props.extraInfraModelParameters;
    const overrideParams = props.overrideInfraModelParameters;

    React.useEffect(() => {
        props.clearInfraModelState();

        // Convert serializable file to native file
        if (props.file) {
            const file = convertToNativeFile(props.file);
            setFile(file);
        }
    }, [props.file]);

    const onValidate: () => void = async () => {
        if (file) {
            props.setLoading(true);
            await getGeometryValidationIssuesForInfraModelFile(file, overrideParams)
                .then(props.onValidation)
                .finally(() => props.setLoading(false));
        }
    };
    // Automatically re-validate whenever the file or manually input data changes
    React.useEffect(() => {
        onValidate();
    }, [file, overrideParams]);

    const onSave: () => Promise<boolean> = async () => {
        if (file) {
            props.setSaving(true);
            return await saveInfraModelFile(file, extraParams, overrideParams)
                .then((response) => response != undefined)
                .finally(() => {
                    props.setSaving(false);
                });
        } else {
            return false;
        }
    };

    const fileHandlingFailedErrors =
        props.validationResponse?.geometryValidationIssues
            .filter((e) => e.issueType === 'PARSING_ERROR' || e.issueType === 'REQUEST_ERROR')
            .map((item) => item.localizationKey) || [];
    const showFileHandlingFailed = fileHandlingFailedErrors.length > 0;

    return (
        <>
            <InfraModelView {...props} fileSource={'FILE_UPLOAD'} onSave={onSave} />

            {showFileHandlingFailed && (
                <CharsetSelectDialog
                    title={t('im-form.file-handling-failed.title')}
                    value={props.overrideInfraModelParameters.encoding}
                    onCancel={props.onClose}
                    onSelect={(charset) => {
                        props.onOverrideParametersChange({
                            ...props.overrideInfraModelParameters,
                            encoding: charset,
                        });
                    }}>
                    <ul className={styles['infra-model-upload-failed__errors']}>
                        {fileHandlingFailedErrors.map((error) => (
                            <li key={error}>{t(error)}</li>
                        ))}
                    </ul>
                    <span>{t('im-form.file-handling-failed.try-change-encoding')}</span>
                </CharsetSelectDialog>
            )}
        </>
    );
};
