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
} from 'infra-model/infra-model-api';
import InfraModelForm from 'infra-model/view/form/infra-model-form';
import {
    ExtraInfraModelParameters,
    InfraModelState,
    InfraModelViewType,
    OnPlanFetchReady,
    OverrideInfraModelParameters,
    XmlCharset,
} from 'infra-model/infra-model-slice';
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
import { Prop } from 'utils/type-utils';
import { ValidationErrorType } from 'utils/validation-utils';
import { useTranslation } from 'react-i18next';
import { InfraModelToolbar } from 'infra-model/view/infra-model-toolbar';
import { Dropdown, Item } from 'vayla-design-lib/dropdown/dropdown';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import { Menu } from 'vayla-design-lib/menu/menu';
import dialogStyles from 'vayla-design-lib/dialog/dialog.scss';
import InfraModelValidationErrorList from 'infra-model/view/infra-model-validation-error-list';
import { useAppNavigate } from 'common/navigate';
import { ChangeTimes } from 'common/common-slice';
import { useCommonDataAppSelector } from 'store/hooks';

// For now use whole state and some extras as params
export type InfraModelViewProps = InfraModelState & {
    viewType: InfraModelViewType;
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
    getGeometryElement: (geomElemId: GeometryElementId) => Promise<GeometryElement | null>;
    getGeometrySwitch: (geomSwitchId: GeometrySwitchId) => Promise<GeometrySwitch | null>;
    onCommitField: (fieldName: string) => void;
};

const xmlEncodingOptions: Item<XmlCharset>[] = [
    { name: 'ISO-8859-1', value: 'ISO_8859_1' },
    { name: 'UTF-8', value: 'UTF_8' },
    { name: 'UTF-16', value: 'UTF_16' },
    { name: 'US ASCII', value: 'US_ASCII' },
];

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
    const { t } = useTranslation();
    const navigate = useAppNavigate();

    const [file, setFile] = React.useState<File>();
    const [loadingInProgress, setLoadingInProgress] = React.useState(false);

    const [infraModelValidationResponse, setInfraModelValidationResponse] =
        React.useState<ValidationResponse | null>(null);

    const [fileHandlingFailedErrors, setFileHandlingFailedErrors] = React.useState<string[]>([]);
    const [fileMenuVisible, setFileMenuVisible] = React.useState(false);

    const [showCriticalWarning, setShowCriticalWarning] = React.useState(false);
    const [showFileHandlingFailed, setShowFileHandlingFailed] = React.useState(false);
    const [showChangeCharsetDialog, setShowChangeCharsetDialog] = React.useState(false);
    const [showCharsetPicker, setShowCharsetPicker] = React.useState(false);
    const [charsetOverride, setCharsetOverride] = React.useState<XmlCharset | undefined>(undefined);
    const userHasWriteRole = useCommonDataAppSelector((state) => state.userHasWriteRole);

    const fileMenuItems = [
        { value: 'fix-encoding', name: t('im-form.file-handling-failed.change-encoding') },
    ];
    const handleFileMenuItemChange = (item: string) => {
        if (item == 'fix-encoding') setShowChangeCharsetDialog(true);
        setFileMenuVisible(false);
    };

    const onSaveClick = async () => {
        setShowCriticalWarning(false);
        setLoadingInProgress(true);

        const overrideParameters = charsetOverride
            ? { ...props.overrideInfraModelParameters, encoding: charsetOverride }
            : props.overrideInfraModelParameters;

        const extraParams = {
            ...props.extraInfraModelParameters,
            oid: props.extraInfraModelParameters.oid || undefined,
        };
        const formData = getFormFile(file, extraParams, overrideParameters);

        const succeed =
            props.viewType === InfraModelViewType.EDIT
                ? (await updateGeometryPlan((props.plan as GeometryPlan).id, formData)) != null
                : (await saveInfraModelFile(formData)) != null;

        setLoadingInProgress(false);

        if (succeed) {
            navigate('inframodel-list');
        }
    };

    const onProgressClick = () => {
        getFieldValidationWarnings().length || !props.planLayout
            ? setShowCriticalWarning(true)
            : onSaveClick();
    };

    const validateFile = async () => {
        const overrideParameters = charsetOverride
            ? { ...props.overrideInfraModelParameters, encoding: charsetOverride }
            : props.overrideInfraModelParameters;

        const response =
            props.viewType === InfraModelViewType.UPLOAD
                ? await getValidationResponseForFile(file, overrideParameters)
                : props.plan
                ? await getValidationResponseGeometryPlan(props.plan.id, overrideParameters)
                : null;

        setInfraModelValidationResponse(response);

        const processingErrors =
            response?.validationErrors
                .filter((e) => e.errorType === 'PARSING_ERROR' || e.errorType === 'REQUEST_ERROR')
                .map((item) => item.localizationKey) || [];
        setFileHandlingFailedErrors(processingErrors);
        setShowFileHandlingFailed(processingErrors.length > 0);

        props.onPlanFetchReady({
            plan: response?.geometryPlan || null,
            planLayout: response?.planLayout || null,
        });
    };

    const getFieldValidationWarnings = () => {
        return props.validationErrors.filter((error) => error.type === ValidationErrorType.WARNING);
    };

    const getFieldValidationErrors = () => {
        return props.validationErrors.filter((error) => error.type === ValidationErrorType.ERROR);
    };

    const getVisibleErrors = () => {
        const fieldValidationErrors = getFieldValidationErrors().map((error) =>
            t(`im-form.${error.reason}`),
        );
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

    const navigateToList = () => navigate('inframodel-list');
    const showMap = infraModelValidationResponse?.planLayout != undefined;

    return (
        <div className={styles['infra-model-upload']}>
            <InfraModelToolbar
                fileName={props.plan?.fileName}
                viewType={props.viewType}
                navigateToList={navigateToList}
            />
            <div className={styles['infra-model-upload__form-column']}>
                <div className={styles['infra-model-upload__file-info-container']}>
                    <Title>{file && file.name}</Title>
                    {file && (
                        <div className={styles['infra-model-upload__title-menu-container']}>
                            <Button
                                onClick={() => setFileMenuVisible(!fileMenuVisible)}
                                variant={ButtonVariant.SECONDARY}
                                icon={Icons.More}
                            />
                            {fileMenuVisible && (
                                <div className={styles['infra-model-upload__title-menu']}>
                                    <Menu
                                        items={fileMenuItems}
                                        onChange={(item) =>
                                            item && handleFileMenuItemChange(item)
                                        }></Menu>
                                </div>
                            )}
                        </div>
                    )}
                </div>

                <div className={styles['infra-model-upload__form-container']}>
                    {props.plan && (
                        <InfraModelForm
                            changeTimes={props.changeTimes}
                            validationErrors={props.validationErrors}
                            upLoading={loadingInProgress}
                            geometryPlan={props.plan}
                            onInfraModelOverrideParametersChange={
                                props.onInfraModelOverrideParametersChange
                            }
                            onInfraModelExtraParametersChange={
                                props.onInfraModelExtraParametersChange
                            }
                            overrideInfraModelParameters={props.overrideInfraModelParameters}
                            extraInframodelParameters={props.extraInfraModelParameters}
                            onCommitField={props.onCommitField}
                            committedFields={props.committedFields}
                            onSelect={props.onSelect}
                        />
                    )}
                </div>

                {infraModelValidationResponse && (
                    <InfraModelValidationErrorList
                        validationResponse={infraModelValidationResponse}
                    />
                )}

                <div className={styles['infra-model-upload__buttons-container']}>
                    {props.viewType === InfraModelViewType.UPLOAD && (
                        <Button
                            onClick={navigateToList}
                            variant={ButtonVariant.WARNING}
                            disabled={loadingInProgress}
                            icon={Icons.Delete}>
                            {t('button.cancel')}
                        </Button>
                    )}
                    {props.viewType === InfraModelViewType.EDIT && userHasWriteRole && (
                        <Button
                            onClick={navigateToList}
                            variant={ButtonVariant.SECONDARY}
                            disabled={loadingInProgress}>
                            {t('button.return')}
                        </Button>
                    )}
                    {userHasWriteRole && (
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
                    )}
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
                {!showMap && <div className={styles['infra-model-upload__error-photo']} />}
            </div>
            {showCriticalWarning && (
                <Dialog
                    title={t('im-form.critical-warnings-dialog.title')}
                    onClose={() => setShowCriticalWarning(false)}
                    className={dialogStyles['dialog--wide']}
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
                        {props.planLayout != null || (
                            <li>{t('im-form.critical-warnings-dialog.error-message')}</li>
                        )}
                    </ul>
                </Dialog>
            )}
            {showFileHandlingFailed && (
                <Dialog
                    title={t('im-form.file-handling-failed.title')}
                    scrollable={false}
                    className={dialogStyles['dialog--wide']}
                    onClose={() => setShowFileHandlingFailed(false)}
                    footerContent={
                        <React.Fragment>
                            {showCharsetPicker && (
                                <React.Fragment>
                                    <Button
                                        variant={ButtonVariant.SECONDARY}
                                        onClick={() => setShowFileHandlingFailed(false)}>
                                        {t('button.cancel')}
                                    </Button>
                                    <Button
                                        onClick={() => {
                                            setShowCharsetPicker(false);
                                            setShowFileHandlingFailed(false);
                                            validateFile();
                                        }}>
                                        {t('im-form.file-handling-failed.try-again')}
                                    </Button>
                                </React.Fragment>
                            )}
                            {!showCharsetPicker && (
                                <Button
                                    onClick={() => {
                                        setShowFileHandlingFailed(false);
                                    }}>
                                    {t('button.ok')}
                                </Button>
                            )}
                        </React.Fragment>
                    }>
                    <ul className={styles['infra-model-upload-failed__errors']}>
                        {fileHandlingFailedErrors.map((error) => (
                            <li key={error}>{t(error)}</li>
                        ))}
                    </ul>
                    <Checkbox
                        checked={showCharsetPicker}
                        onChange={(e) => setShowCharsetPicker(e.target.checked)}>
                        {t('im-form.file-handling-failed.change-encoding')}
                    </Checkbox>
                    {showCharsetPicker && (
                        <div className={styles['infra-model-upload-failed__encode-container']}>
                            <label className={styles['infra-model-upload-failed__checkbox-label']}>
                                {t('im-form.file-handling-failed.encoding')}
                            </label>
                            <Dropdown
                                options={xmlEncodingOptions}
                                value={charsetOverride}
                                onChange={setCharsetOverride}
                            />
                        </div>
                    )}
                </Dialog>
            )}
            {showChangeCharsetDialog && (
                <Dialog
                    title={t('im-form.file-handling-failed.change-encoding')}
                    scrollable={false}
                    className={dialogStyles['dialog--wide']}
                    onClose={() => setShowChangeCharsetDialog(false)}
                    footerContent={
                        <React.Fragment>
                            <Button
                                variant={ButtonVariant.SECONDARY}
                                onClick={() => setShowChangeCharsetDialog(false)}>
                                {t('button.cancel')}
                            </Button>
                            <Button
                                onClick={() => {
                                    setShowChangeCharsetDialog(false);
                                    validateFile();
                                }}>
                                {t('im-form.file-handling-failed.try-again')}
                            </Button>
                        </React.Fragment>
                    }>
                    <div className={styles['infra-model-upload-failed__encode-container']}>
                        <label className={styles['infra-model-upload-failed__checkbox-label']}>
                            {t('im-form.file-handling-failed.encoding')}
                        </label>
                        <Dropdown
                            options={xmlEncodingOptions}
                            value={charsetOverride}
                            onChange={setCharsetOverride}
                        />
                    </div>
                </Dialog>
            )}
        </div>
    );
};
