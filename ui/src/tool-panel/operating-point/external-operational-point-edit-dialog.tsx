import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { Dialog } from 'geoviite-design-lib/dialog/dialog';
import { FormLayout, FormLayoutColumn } from 'geoviite-design-lib/form-layout/form-layout';
import { Heading, HeadingSize } from 'vayla-design-lib/heading/heading';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Dropdown, DropdownOption, dropdownOption } from 'vayla-design-lib/dropdown/dropdown';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { OperatingPointDeleteDraftConfirmDialog } from 'tool-panel/operating-point/operating-point-delete-draft-confirm-dialog';
import { OperationalPoint, RinfType } from 'track-layout/track-layout-model';
import { rinfTypes } from 'utils/enum-localization-utils';
import { OperationalPointState } from 'geoviite-design-lib/operational-point-state/operational-point-state';
import { createDelegatesWithDispatcher } from 'store/store-utils';
import {
    actions,
    ExternalOperationalPointEditState,
    ExternalOperationalPointSaveRequest,
    initialExternalOperationalPointEditState,
    reducer,
} from 'tool-panel/operating-point/external-operational-point-edit-store';
import { UnknownAction } from 'redux';
import {
    hasErrors,
    getVisibleErrorsByProp as getVisibleErrorsByPropGeneric,
} from 'utils/validation-utils';

type ExternalOperationalPointEditDialogProps = {
    operationalPoint: OperationalPoint;
    onSave: () => void;
    onClose: () => void;
};

export const ExternalOperationalPointEditDialog: React.FC<
    ExternalOperationalPointEditDialogProps
> = ({ operationalPoint, onClose, onSave: _s }) => {
    const { t } = useTranslation();

    const [state, dispatcher] = React.useReducer<
        ExternalOperationalPointEditState,
        [action: UnknownAction]
    >(reducer, initialExternalOperationalPointEditState);
    const stateActions = createDelegatesWithDispatcher(dispatcher, actions);

    React.useEffect(() => {
        if (operationalPoint) stateActions.onOperationalPointLoaded(operationalPoint);
    }, [operationalPoint]);

    const [deleteDraftConfirmDialogOpen, setShowDeleteDraftConfirmDialog] = React.useState(false);

    const rinfTypeOptions: DropdownOption<RinfType>[] = rinfTypes.map((value) =>
        dropdownOption(value.value, value.name, `rinf-type-option-${value}`),
    );

    const getVisibleErrorsByProp = (prop: keyof ExternalOperationalPointSaveRequest) =>
        getVisibleErrorsByPropGeneric(state.committedFields, state.validationIssues, prop);

    return (
        <React.Fragment>
            <Dialog
                title={t('operating-point-dialog.title-edit')}
                onClose={onClose}
                footerContent={
                    <React.Fragment>
                        {
                            <Button
                                disabled={!operationalPoint?.isDraft}
                                onClick={() => setShowDeleteDraftConfirmDialog(true)}
                                variant={ButtonVariant.WARNING}>
                                {t('button.revert-draft')}
                            </Button>
                        }

                        <div className={dialogStyles['dialog__footer-content--right-aligned']}>
                            <Button variant={ButtonVariant.SECONDARY} onClick={onClose}>
                                {t('button.cancel')}
                            </Button>
                            <Button
                                qa-id="save-switch-changes"
                                /*disabled={!canSave}
                            isProcessing={isSaving}
                            onClick={saveOrConfirm}
                            title={getSaveDisabledReasons(
                                validationIssues.map((e) => e.reason),
                                isSaving,
                            )
                                .map((reason) => t(`switch-dialog.${reason}`))
                                .join(', ')}*/
                            >
                                {t('button.save')}
                            </Button>
                        </div>
                    </React.Fragment>
                }>
                <FormLayout>
                    <FormLayoutColumn>
                        <Heading size={HeadingSize.SUB}>
                            {t('operating-point-dialog.basic-info')}
                        </Heading>
                        <FieldLayout
                            label={`${t('operating-point-dialog.name')} *`}
                            value={state.existingOperationalPoint?.name}
                        />
                        <FieldLayout
                            label={`${t('operating-point-dialog.abbreviation')} *`}
                            value={state.existingOperationalPoint?.abbreviation}
                        />
                        <FieldLayout
                            label={`${t('operating-point-dialog.type-raide')} *`}
                            value={
                                state.existingOperationalPoint &&
                                t(`enum.RaideType.${state.existingOperationalPoint.raideType}`)
                            }
                        />
                        <FieldLayout
                            label={`${t('operating-point-dialog.type-rinf')} *`}
                            value={
                                <Dropdown
                                    options={rinfTypeOptions}
                                    value={
                                        rinfTypeOptions.find(
                                            (o) => o.value === state.operationalPoint.rinfType,
                                        )?.value
                                    }
                                    onChange={(value) =>
                                        value &&
                                        stateActions.onUpdateProp({
                                            key: 'rinfType',
                                            value: value,
                                            editingExistingValue: !!state.operationalPoint,
                                        })
                                    }
                                    onBlur={() => stateActions.onCommitField('rinfType')}
                                    hasError={hasErrors(
                                        state.committedFields,
                                        state.validationIssues,
                                        'rinfType',
                                    )}
                                    wide
                                />
                            }
                            errors={getVisibleErrorsByProp('rinfType')}
                        />
                        <FieldLayout
                            label={`${t('operating-point-dialog.state')} *`}
                            value={
                                state.existingOperationalPoint && (
                                    <OperationalPointState
                                        state={state.existingOperationalPoint?.state}
                                    />
                                )
                            }
                        />
                        <FieldLayout
                            label={`${t('operating-point-dialog.uic-code')} *`}
                            value={state.existingOperationalPoint?.uicCode}
                        />
                    </FormLayoutColumn>
                </FormLayout>
            </Dialog>
            {deleteDraftConfirmDialogOpen && (
                <OperatingPointDeleteDraftConfirmDialog
                    onClose={() => setShowDeleteDraftConfirmDialog(false)}
                    onRevert={() => setShowDeleteDraftConfirmDialog(false)}
                />
            )}
        </React.Fragment>
    );
};
