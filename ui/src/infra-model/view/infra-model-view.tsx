import React from 'react';
import MapView from 'map/map-view';
import { MapViewport } from 'map/map-model';
import styles from './form/infra-model-form.module.scss';
import InfraModelForm from 'infra-model/view/form/infra-model-form';
import {
    ExtraInfraModelParameters,
    InfraModelState,
    OverrideInfraModelParameters,
} from 'infra-model/infra-model-slice';
import {
    OnClickLocationFunction,
    OnHighlightItemsFunction,
    OnHoverLocationFunction,
    OnSelectFunction,
} from 'selection/selection-model';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Dialog } from 'vayla-design-lib/dialog/dialog';
import { Prop } from 'utils/type-utils';
import { ValidationErrorType } from 'utils/validation-utils';
import { useTranslation } from 'react-i18next';
import { FileMenuOption, InfraModelToolbar } from 'infra-model/view/infra-model-toolbar';
import dialogStyles from 'vayla-design-lib/dialog/dialog.scss';
import InfraModelValidationErrorList from 'infra-model/view/infra-model-validation-error-list';
import { ChangeTimes } from 'common/common-slice';
import { WriteRoleRequired } from 'user/write-role-required';
import { Item } from 'vayla-design-lib/dropdown/dropdown';
import { CharsetSelectDialog } from './dialogs/charset-select-dialog';

export type InfraModelBaseProps = InfraModelState & {
    onExtraParametersChange: <TKey extends keyof ExtraInfraModelParameters>(
        parameters: Prop<ExtraInfraModelParameters, TKey>,
    ) => void;
    onOverrideParametersChange: (parameters: OverrideInfraModelParameters) => void;
    onViewportChange: (viewport: MapViewport) => void;
    onHoverLocation: OnHoverLocationFunction;
    onClickLocation: OnClickLocationFunction;
    onSelect: OnSelectFunction;
    changeTimes: ChangeTimes;
    onHighlightItems: OnHighlightItemsFunction;
    onCommitField: (fieldName: string) => void;
    isLoading: boolean;
    onClose: () => void;
};
export type InfraModelViewProps = InfraModelBaseProps & {
    onSave: () => Promise<boolean>;
    onValidate: () => void;
};

export const InfraModelView: React.FC<InfraModelViewProps> = (props: InfraModelViewProps) => {
    const { t } = useTranslation();

    const [showCriticalWarning, setShowCriticalWarning] = React.useState(false);
    const [showChangeCharsetDialog, setShowChangeCharsetDialog] = React.useState(false);

    const geometryPlan = props.validationResponse?.geometryPlan;
    const planLayout = props.validationResponse?.planLayout;
    const isNewPlan = geometryPlan?.dataType !== 'STORED';

    const fileMenuItems: Item<FileMenuOption>[] = isNewPlan
        ? [{ value: 'fix-encoding', name: t('im-form.file-handling-failed.change-encoding') }]
        : [];
    const handleFileMenuItemChange = (item: string) => {
        if (item == 'fix-encoding') setShowChangeCharsetDialog(true);
    };

    const onSaveClick = async () => {
        if (await props.onSave()) props.onClose();
    };

    const onProgressClick = () => {
        getFieldValidationWarnings().length || !planLayout
            ? setShowCriticalWarning(true)
            : onSaveClick();
    };

    const fileHandlingFailedErrors =
        props.validationResponse?.validationErrors
            .filter((e) => e.errorType === 'PARSING_ERROR' || e.errorType === 'REQUEST_ERROR')
            .map((item) => item.localizationKey) || [];

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

    const fileName = geometryPlan?.fileName || '';
    const toolbarName = `${isNewPlan ? `${t('im-form.toolbar.upload')}: ` : ''}${fileName}`;
    const showMap = props.validationResponse?.planLayout != undefined;

    return (
        <div className={styles['infra-model-upload']}>
            <InfraModelToolbar
                fileName={toolbarName}
                fileMenuItems={fileMenuItems}
                fileMenuItemSelected={handleFileMenuItemChange}
            />
            <div className={styles['infra-model-upload__form-column']}>
                <div className={styles['infra-model-upload__form-container']}>
                    {geometryPlan && (
                        <InfraModelForm
                            changeTimes={props.changeTimes}
                            validationErrors={props.validationErrors}
                            upLoading={props.isLoading}
                            geometryPlan={geometryPlan}
                            onInfraModelOverrideParametersChange={props.onOverrideParametersChange}
                            onInfraModelExtraParametersChange={props.onExtraParametersChange}
                            overrideInfraModelParameters={props.overrideInfraModelParameters}
                            extraInframodelParameters={props.extraInfraModelParameters}
                            onCommitField={props.onCommitField}
                            committedFields={props.committedFields}
                            onSelect={props.onSelect}
                        />
                    )}
                </div>

                {props.validationResponse && (
                    <InfraModelValidationErrorList validationResponse={props.validationResponse} />
                )}

                <div className={styles['infra-model-upload__buttons-container']}>
                    {isNewPlan && (
                        <Button
                            onClick={props.onClose}
                            variant={ButtonVariant.WARNING}
                            disabled={props.isLoading}
                            icon={Icons.Delete}>
                            {t('button.cancel')}
                        </Button>
                    )}
                    {!isNewPlan && (
                        <Button
                            onClick={props.onClose}
                            variant={ButtonVariant.SECONDARY}
                            disabled={props.isLoading}>
                            {t('button.return')}
                        </Button>
                    )}
                    <WriteRoleRequired>
                        <Button
                            title={getVisibleErrors()}
                            onClick={() => onProgressClick()}
                            disabled={
                                props.isLoading ||
                                props.validationResponse == null ||
                                fileHandlingFailedErrors.length > 0 ||
                                getFieldValidationErrors().length > 0
                            }
                            icon={Icons.Tick}
                            isProcessing={props.isLoading}>
                            {t(isNewPlan ? 'button.save' : 'im-form.save-changes')}
                        </Button>
                    </WriteRoleRequired>
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
                                disabled={props.isLoading}
                                onClick={() => setShowCriticalWarning(false)}>
                                {t('button.cancel')}
                            </Button>
                            <Button
                                id="infra-model-upload-dialog-save-button"
                                onClick={() => onSaveClick()}
                                disabled={props.isLoading}
                                isProcessing={props.isLoading}>
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
                        {planLayout != null || (
                            <li>{t('im-form.critical-warnings-dialog.error-message')}</li>
                        )}
                    </ul>
                </Dialog>
            )}
            {showChangeCharsetDialog && (
                <CharsetSelectDialog
                    title={t('im-form.file-handling-failed.change-encoding')}
                    value={props.overrideInfraModelParameters.encoding}
                    onSelect={(charset) => {
                        setShowChangeCharsetDialog(false);
                        props.onOverrideParametersChange({
                            ...props.overrideInfraModelParameters,
                            encoding: charset,
                        });
                    }}
                    onCancel={() => setShowChangeCharsetDialog(false)}
                />
            )}
        </div>
    );
};
