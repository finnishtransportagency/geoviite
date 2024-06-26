import React from 'react';
import styles from './form/infra-model-form.module.scss';
import InfraModelForm from 'infra-model/view/form/infra-model-form';
import {
    ExtraInfraModelParameters,
    InfraModelState,
    OverrideInfraModelParameters,
} from 'infra-model/infra-model-slice';
import { OnSelectFunction } from 'selection/selection-model';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Dialog } from 'geoviite-design-lib/dialog/dialog';
import { Prop } from 'utils/type-utils';
import { FieldValidationIssueType } from 'utils/validation-utils';
import { useTranslation } from 'react-i18next';
import { FileMenuOption, InfraModelToolbar } from 'infra-model/view/infra-model-toolbar';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import InfraModelValidationIssueList from 'infra-model/view/infra-model-validation-issue-list';
import { ChangeTimes } from 'common/common-slice';
import { Item } from 'vayla-design-lib/dropdown/dropdown';
import { CharsetSelectDialog } from './dialogs/charset-select-dialog';
import { MapContext } from 'map/map-store';
import { MapViewContainer } from 'map/map-view-container';
import { PrivilegeRequired } from 'user/privilege-required';
import { EDIT_GEOMETRY_FILE } from 'user/user-model';

export type InfraModelBaseProps = InfraModelState & {
    onExtraParametersChange: <TKey extends keyof ExtraInfraModelParameters>(
        parameters: Prop<ExtraInfraModelParameters, TKey>,
    ) => void;
    onOverrideParametersChange: (parameters: OverrideInfraModelParameters) => void;
    onSelect: OnSelectFunction;
    changeTimes: ChangeTimes;
    isLoading: boolean;
    onClose: () => void;
};
export type InfraModelViewProps = InfraModelBaseProps & {
    onSave: () => Promise<boolean>;
};

export const InfraModelView: React.FC<InfraModelViewProps> = (props: InfraModelViewProps) => {
    const { t } = useTranslation();

    const [showCriticalWarning, setShowCriticalWarning] = React.useState(false);
    const [showChangeCharsetDialog, setShowChangeCharsetDialog] = React.useState(false);

    const geometryPlan = props.validationResponse?.geometryPlan;
    const planLayout = props.validationResponse?.planLayout;
    const isNewPlan = geometryPlan?.dataType !== 'STORED';

    const fileMenuItems: Item<FileMenuOption>[] = isNewPlan
        ? [
              {
                  value: 'fix-encoding',
                  name: t('im-form.file-handling-failed.change-encoding'),
                  qaId: `fix-encoding`,
              },
          ]
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
        props.validationResponse?.geometryValidationIssues
            .filter((e) => e.issueType === 'PARSING_ERROR' || e.issueType === 'REQUEST_ERROR')
            .map((item) => item.localizationKey) || [];

    const getFieldValidationWarnings = () => {
        return props.validationIssues.filter(
            (error) => error.type === FieldValidationIssueType.WARNING,
        );
    };

    const getFieldValidationIssues = () => {
        return props.validationIssues.filter(
            (error) => error.type === FieldValidationIssueType.ERROR,
        );
    };

    const getVisibleErrors = () => {
        const fieldValidationIssues = getFieldValidationIssues().map((error) =>
            t(`im-form.${error.reason}`),
        );
        return fieldValidationIssues.length > 0 ? fieldValidationIssues.join(', ') : '';
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
                            validationIssues={props.validationIssues}
                            upLoading={props.isLoading}
                            geometryPlan={geometryPlan}
                            onInfraModelOverrideParametersChange={props.onOverrideParametersChange}
                            onInfraModelExtraParametersChange={props.onExtraParametersChange}
                            overrideInfraModelParameters={props.overrideInfraModelParameters}
                            extraInframodelParameters={props.extraInfraModelParameters}
                            committedFields={props.committedFields}
                        />
                    )}
                </div>

                {props.validationResponse && (
                    <InfraModelValidationIssueList validationResponse={props.validationResponse} />
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
                    <PrivilegeRequired privilege={EDIT_GEOMETRY_FILE}>
                        <Button
                            qa-id="infra-model-save-button"
                            title={getVisibleErrors()}
                            onClick={() => onProgressClick()}
                            disabled={
                                props.isLoading ||
                                !props.validationResponse ||
                                fileHandlingFailedErrors.length > 0 ||
                                getFieldValidationIssues().length > 0
                            }
                            icon={Icons.Tick}
                            isProcessing={props.isLoading}>
                            {t(isNewPlan ? 'button.save' : 'im-form.save-changes')}
                        </Button>
                    </PrivilegeRequired>
                </div>
            </div>
            <div className={styles['infra-model-upload__map-container']}>
                {showMap && (
                    <MapContext.Provider value="infra-model">
                        <MapViewContainer manuallySetPlan={planLayout ?? undefined} />
                    </MapContext.Provider>
                )}
                {!showMap && <div className={styles['infra-model-upload__error-photo']} />}
            </div>
            {showCriticalWarning && (
                <Dialog
                    title={t('im-form.critical-warnings-dialog.title')}
                    onClose={() => setShowCriticalWarning(false)}
                    footerContent={
                        <div className={dialogStyles['dialog__footer-content--centered']}>
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
                        </div>
                    }>
                    <span>{t('im-form.critical-warnings-dialog.sub-title')}</span>
                    <ul>
                        {getFieldValidationWarnings().map((warning) => (
                            <li key={warning.field}>
                                {t(`im-form.critical-warnings-dialog.${warning.field}`)}
                            </li>
                        ))}
                        {!!planLayout || (
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
