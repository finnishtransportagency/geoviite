import React from 'react';
import MapView from 'map/map-view';
import { MapViewport } from 'map/map-model';
import styles from './form/infra-model-form.module.scss';
import {
    EMPTY_VALIDATION_RESPONSE,
    getValidationErrorsForGeometryPlan,
    getValidationErrorsForInfraModelFile,
    saveInfraModelFile,
    updateGeometryPlan,
    ValidationResponse,
} from 'api/inframodel-api';
import InfraModelForm from 'infra-model/view/form/infra-model-form';
import {
    ExtraInfraModelParameters,
    InfraModelState,
    InfraModelViewType,
    OnPlanFetchReady,
    OverrideInfraModelParameters,
} from 'infra-model/infra-model-store';
import {
    GeometryElement,
    GeometryElementId,
    GeometryPlan,
    GeometryPlanId,
    GeometrySwitch,
    GeometrySwitchId,
} from 'geometry/geometry-model';
import {
    OnClickLocationFunction,
    OnHighlightItemsFunction,
    OnHoverLocationFunction,
    OnSelectFunction,
} from 'selection/selection-model';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { convertToNativeFile } from 'utils/file-utils';
import { Title } from 'vayla-design-lib/title/title';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Dialog } from 'vayla-design-lib/dialog/dialog';
import { ChangeTimes } from 'track-layout/track-layout-store';
import { Prop } from 'utils/type-utils';
import { ValidationErrorType } from 'utils/validation-utils';
import { useTranslation } from 'react-i18next';
import { InfraModelToolbar } from 'infra-model/view/infra-model-toolbar';

// For now use whole state and some extras as params
export type InfraModelViewProps = InfraModelState & {
    onInfraModelExtraParametersChange: <TKey extends keyof ExtraInfraModelParameters>(
        infraModelExtraParameters: Prop<ExtraInfraModelParameters, TKey>,
    ) => void;
    onInfraModelOverrideParametersChange: (
        overrideInfraModelParameters: OverrideInfraModelParameters,
    ) => void;
    onPlanUpdate: () => void;
    onPlanFetchReady: (plan: OnPlanFetchReady) => void;
    onViewportChange: (viewport: MapViewport) => void;
    onHoverLocation: OnHoverLocationFunction;
    onClickLocation: OnClickLocationFunction;
    onSelect: OnSelectFunction;
    changeTimes: ChangeTimes;
    onHighlightItems: OnHighlightItemsFunction;
    onInfraModelViewChange: (viewType: InfraModelViewType) => void;
    getGeometryElement: (geomElemId: GeometryElementId) => Promise<GeometryElement | null>;
    getGeometrySwitch: (geomSwitchId: GeometrySwitchId) => Promise<GeometrySwitch | null>;
    onCommitField: (fieldName: string) => void;
};

const getFormFile = (
    file?: Blob,
    extraParameters?: ExtraInfraModelParameters,
    overrideParameters?: OverrideInfraModelParameters,
) => {
    const formData = new FormData();

    if (file) {
        formData.set('file', file);
    }

    if (overrideParameters) {
        const jsonOverrideBlob = new Blob([JSON.stringify(overrideParameters)], {
            type: 'application/json',
        });

        formData.set('override-parameters', jsonOverrideBlob);
    }

    if (extraParameters) {
        const jsonExtraBlob = new Blob([JSON.stringify(extraParameters)], {
            type: 'application/json',
        });

        formData.set('extrainfo-parameters', jsonExtraBlob);
    }

    return formData;
};

const getValidationResponseForFile = async (
    file?: File,
    overrideParameters?: OverrideInfraModelParameters,
): Promise<ValidationResponse> => {
    if (file) {
        const formData = getFormFile(file, undefined, overrideParameters);
        return getValidationErrorsForInfraModelFile(formData);
    } else {
        return Promise.resolve({
            ...EMPTY_VALIDATION_RESPONSE,
            message: 'No file',
        });
    }
};

const getValidationResponseGeometryPlan = async (
    planId: GeometryPlanId,
    overrideParameters: OverrideInfraModelParameters,
): Promise<ValidationResponse> => {
    const formData = getFormFile(undefined, undefined, overrideParameters);
    return getValidationErrorsForGeometryPlan(planId, formData);
};

export const InfraModelView: React.FC<InfraModelViewProps> = (props: InfraModelViewProps) => {
    const {t} = useTranslation();

    const [file, setFile] = React.useState<File>();
    const [loadingInProgress, setLoadingInProgress] = React.useState(false);
    const [showMap, setShowMap] = React.useState<boolean>();

    const [infraModelValidationResponse, setInfraModelValidationResponse] =
        React.useState<ValidationResponse | null>(null);

    const [fileHandlingFailedErrors, setFileHandlingFailedErrors] = React.useState<string[]>([]);

    const [showCriticalWarning, setShowCriticalWarning] = React.useState(false);
    const [showFileHandlingFailed, setShowFileHandlingFailed] = React.useState(false);

    const onSaveClick = async () => {
        setShowCriticalWarning(false);
        setLoadingInProgress(true);

        const extraParams = {
            ...props.extraInframodelParameters,
            oid: props.extraInframodelParameters.oid || undefined,
        };
        const formData = getFormFile(file, extraParams, props.overrideInfraModelParameters);

        const succeed =
            props.viewType === InfraModelViewType.EDIT
                ? (await updateGeometryPlan((props.plan as GeometryPlan).id, formData)) != null
                : (await saveInfraModelFile(formData)) != null;

        setLoadingInProgress(false);

        if (succeed) {
            props.onInfraModelViewChange(InfraModelViewType.LIST);
        }
    };

    const onProgressClick = () => {
        getFieldValidationWarnings().length || !props.planLayout
            ? setShowCriticalWarning(true)
            : onSaveClick();
    };

    const validateFile = async () => {
        const response =
            props.viewType === InfraModelViewType.UPLOAD
                ? await getValidationResponseForFile(file, props.overrideInfraModelParameters)
                : await getValidationResponseGeometryPlan(
                (props.plan as GeometryPlan).id,
                props.overrideInfraModelParameters,
                );

        setInfraModelValidationResponse(response);

        const processingErrors = response.validationErrors
            .filter((e) => e.errorType === 'PARSING_ERROR' || e.errorType === 'REQUEST_ERROR')
            .map((item) => item.localizationKey);
        setFileHandlingFailedErrors(processingErrors);
        setShowFileHandlingFailed(processingErrors.length > 0);

        props.onPlanFetchReady({
            plan: response.geometryPlan,
            planLayout: response.planLayout,
        });

        setShowMap(response.planLayout != undefined);
    };

    const getFieldValidationWarnings = () => {
        return props.validationErrors.filter((error) => error.type === ValidationErrorType.WARNING);
    };

    const getFieldValidationErrors = () => {
        return props.validationErrors.filter((error) => error.type === ValidationErrorType.ERROR);
    };

    const getVisibleErrors = () => {
        const fieldValidationErrors = getFieldValidationErrors().map(error => t(`im-form.${error.reason}`));
        return fieldValidationErrors.length > 0 ? fieldValidationErrors.join(', ') : '';
    };

    React.useEffect(() => {
        props.onPlanUpdate();
    }, [props.plan]);

    // In first render convert serializable file to native file
    React.useEffect(() => {
        if (props.viewType === InfraModelViewType.UPLOAD && props.file) {
            const file = convertToNativeFile(props.file);
            setFile(file);
        }
    }, []);

    // Automatically re-validate whenever the file or manually input data changes
    React.useEffect(() => {
        setLoadingInProgress(true);
        validateFile().finally(() => setLoadingInProgress(false));
    }, [file, props.overrideInfraModelParameters]);

    return (
        <div className={styles['infra-model-upload']}>
            <InfraModelToolbar
                onInfraModelViewChange={props.onInfraModelViewChange}
                fileName={props.plan?.fileName}
                viewType={props.viewType}
            />
            <div className={styles['infra-model-upload__form-column']}>
                <div className={styles['infra-model-upload__file-info-container']}>
                    <Title>{file && file.name}</Title>
                </div>

                <div className={styles['infra-model-upload__form-container']}>
                    {props.plan && (
                        <InfraModelForm
                            changeTimes={props.changeTimes}
                            validationErrors={props.validationErrors}
                            upLoading={loadingInProgress}
                            validationResponse={infraModelValidationResponse}
                            geometryPlan={props.plan}
                            onInfraModelOverrideParametersChange={
                                props.onInfraModelOverrideParametersChange
                            }
                            onInfraModelExtraParametersChange={
                                props.onInfraModelExtraParametersChange
                            }
                            overrideInfraModelParameters={props.overrideInfraModelParameters}
                            extraInframodelParameters={props.extraInframodelParameters}
                            onCommitField={props.onCommitField}
                            committedFields={props.committedFields}
                        />
                    )}
                </div>

                <div className={styles['infra-model-upload__buttons-container']}>
                    {props.viewType === InfraModelViewType.UPLOAD && (
                        <Button
                            onClick={() => props.onInfraModelViewChange(InfraModelViewType.LIST)}
                            variant={ButtonVariant.WARNING}
                            disabled={loadingInProgress}
                            icon={Icons.Delete}>
                            {t('button.cancel')}
                        </Button>
                    )}
                    {props.viewType === InfraModelViewType.EDIT && (
                        <Button
                            onClick={() => props.onInfraModelViewChange(InfraModelViewType.LIST)}
                            variant={ButtonVariant.SECONDARY}
                            disabled={loadingInProgress}>
                            {t('button.return')}
                        </Button>
                    )}

                    <Button
                        title={getVisibleErrors()}
                        onClick={() => onProgressClick()}
                        disabled={
                            loadingInProgress ||
                            infraModelValidationResponse == null ||
                            fileHandlingFailedErrors.length > 0 ||
                            getFieldValidationErrors().length > 0
                        }
                        icon={Icons.Tick}
                        isProcessing={loadingInProgress}>
                        {t(
                            props.viewType === InfraModelViewType.EDIT
                                ? 'im-form.save-changes'
                                : 'button.save',
                        )}
                    </Button>
                </div>
            </div>
            <div className={styles['infra-model-upload__map-container']}>
                {showMap && (
                    <MapView
                        map={props.map}
                        onViewportUpdate={props.onViewportChange}
                        selection={props.selection}
                        publishType="OFFICIAL"
                        onSelect={props.onSelect}
                        changeTimes={props.changeTimes}
                        onHighlightItems={props.onHighlightItems}
                        onHoverLocation={props.onHoverLocation}
                        onClickLocation={props.onClickLocation}
                    />
                )}
                {showMap === false && <div className={styles['infra-model-upload__error-photo']}/>}
            </div>
            {showCriticalWarning && (
                <div>
                    <Dialog
                        title={t('im-form.critical-warnings-dialog.title')}
                        onClose={() => setShowCriticalWarning(false)}
                        footerContent={
                            <React.Fragment>
                                <Button
                                    variant={ButtonVariant.SECONDARY}
                                    disabled={loadingInProgress}
                                    onClick={() => setShowCriticalWarning(false)}>
                                    {t('button.cancel')}
                                </Button>
                                <Button
                                    id="infra-model-upload-dialog-save-button"
                                    onClick={() => onSaveClick()}
                                    disabled={loadingInProgress}
                                    isProcessing={loadingInProgress}>
                                    {t('button.save')}
                                </Button>
                            </React.Fragment>
                        }>
                        <span>{t('im-form.critical-warnings-dialog.sub-title')}</span>
                        <ul>
                            {getFieldValidationWarnings().map((warning) => (
                                <li key={warning.field}>
                                    {t(`im-form.critical-warnings-dialog.${warning.field}`)}
                                </li>
                            ))}
                            {props.planLayout != null ||
                            `${t('im-form.critical-warnings-dialog.error-message')}`}
                        </ul>
                    </Dialog>
                </div>
            )}
            {showFileHandlingFailed && (
                <div>
                    <Dialog
                        title={t('im-form.file-handling-failed.title')}
                        onClose={() => setShowFileHandlingFailed(false)}>
                        <div>
                            {fileHandlingFailedErrors &&
                            fileHandlingFailedErrors.map((error) => (
                                <div key={error}>{t(error)}</div>
                            ))}
                        </div>
                    </Dialog>
                </div>
            )}
        </div>
    );
};
