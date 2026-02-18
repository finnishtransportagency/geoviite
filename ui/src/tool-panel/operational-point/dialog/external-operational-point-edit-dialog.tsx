import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { Dialog } from 'geoviite-design-lib/dialog/dialog';
import { FormLayout, FormLayoutColumn } from 'geoviite-design-lib/form-layout/form-layout';
import { Heading, HeadingSize } from 'vayla-design-lib/heading/heading';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Dropdown, DropdownOption, dropdownOption } from 'vayla-design-lib/dropdown/dropdown';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { OperationalPointDeleteDraftConfirmDialog } from 'tool-panel/operational-point/dialog/operational-point-delete-draft-confirm-dialog';
import {
    OperationalPoint,
    OperationalPointId,
    OperationalPointRinfType,
} from 'track-layout/track-layout-model';
import { rinfTypes } from 'utils/enum-localization-utils';
import { OperationalPointState } from 'geoviite-design-lib/operational-point-state/operational-point-state';
import { createDelegatesWithDispatcher } from 'store/store-utils';
import {
    actions,
    ExternalOperationalPointEditState,
    ExternalOperationalPointSaveRequest,
    initialExternalOperationalPointEditState,
    reducer,
} from 'tool-panel/operational-point/dialog/external-operational-point-edit-store';
import { UnknownAction } from 'redux';
import {
    getVisibleErrorsByProp as getVisibleErrorsByPropGeneric,
    hasErrors,
} from 'utils/validation-utils';
import { LayoutContext } from 'common/common-model';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import {
    deleteDraftOperationalPoint,
    updateExternalOperationalPoint,
} from 'track-layout/layout-operational-point-api';
import { OperationalPointRinfCodeField } from './operational-point-rinf-code-field';
import { withConditionalRinfCodeOverride } from 'tool-panel/operational-point/operational-point-utils';

type ExternalOperationalPointEditDialogProps = {
    operationalPoint: OperationalPoint;
    layoutContext: LayoutContext;
    isDraftOnly: boolean;
    onSave: (id: OperationalPointId) => void;
    onClose: () => void;
};

export const ExternalOperationalPointEditDialog: React.FC<
    ExternalOperationalPointEditDialogProps
> = ({ operationalPoint, layoutContext, isDraftOnly, onClose, onSave }) => {
    const { t } = useTranslation();

    const [state, dispatcher] = React.useReducer<
        ExternalOperationalPointEditState,
        [action: UnknownAction]
    >(reducer, initialExternalOperationalPointEditState);
    const stateActions = createDelegatesWithDispatcher(dispatcher, actions);

    React.useEffect(() => {
        if (operationalPoint) stateActions.onOperationalPointLoaded(operationalPoint);
    }, [operationalPoint]);
    React.useEffect(() => {
        stateActions.setEditingRinfCode(isDraftOnly);
    }, [isDraftOnly]);

    const [deleteDraftConfirmDialogOpen, setdeleteDraftConfirmDialogOpen] = React.useState(false);
    const [isSaving, setIsSaving] = React.useState(false);

    const isOlp = state.existingOperationalPoint?.raideType === 'OLP';

    const rinfTypeOptions: DropdownOption<OperationalPointRinfType>[] = rinfTypes.map((value) =>
        dropdownOption(value.value, value.name, `rinf-type-option-${value}`),
    );

    const getVisibleErrorsByProp = (prop: keyof ExternalOperationalPointSaveRequest) =>
        getVisibleErrorsByPropGeneric(state.committedFields, state.validationIssues, prop).map(
            (err) => t(`operational-point-dialog.validation.${err}`),
        );

    const updateProp = <K extends keyof ExternalOperationalPointSaveRequest>(
        key: K,
        value: ExternalOperationalPointSaveRequest[K],
    ) =>
        stateActions.onUpdateProp({
            key,
            value,
            editingExistingValue: !!state.operationalPoint,
        });

    const saveUpdatedOperationalPoint = (
        id: OperationalPointId,
        updatedOperationalPoint: ExternalOperationalPointSaveRequest,
        allowRinfCodeOverride: boolean,
    ) => {
        setIsSaving(true);
        const saveRequest = withConditionalRinfCodeOverride(
            updatedOperationalPoint,
            allowRinfCodeOverride,
        );
        updateExternalOperationalPoint(id, saveRequest, layoutContext)
            .then(
                () => {
                    onSave(id);
                    onClose();
                    Snackbar.success('operational-point-dialog.modified-successfully');
                },
                () => Snackbar.error('operational-point-dialog.modify-failed'),
            )
            .finally(() => setIsSaving(false));
    };

    const revertDraft = () => {
        deleteDraftOperationalPoint(layoutContext, operationalPoint.id);
        setdeleteDraftConfirmDialogOpen(false);
        onClose();
    };

    return (
        <React.Fragment>
            <Dialog
                title={t('operational-point-dialog.title-edit')}
                onClose={onClose}
                footerContent={
                    <React.Fragment>
                        <Button
                            disabled={!operationalPoint?.isDraft || isSaving}
                            onClick={() => setdeleteDraftConfirmDialogOpen(true)}
                            variant={ButtonVariant.WARNING}>
                            {t('button.revert-draft')}
                        </Button>
                        <div className={dialogStyles['dialog__footer-content--right-aligned']}>
                            <Button variant={ButtonVariant.SECONDARY} onClick={onClose}>
                                {t('button.cancel')}
                            </Button>
                            <Button
                                isProcessing={isSaving}
                                disabled={isSaving || state.validationIssues.length > 0}
                                qa-id="save-external-operational-point-changes"
                                onClick={() =>
                                    saveUpdatedOperationalPoint(
                                        operationalPoint.id,
                                        state.operationalPoint,
                                        state.editingRinfCode,
                                    )
                                }>
                                {t('button.save')}
                            </Button>
                        </div>
                    </React.Fragment>
                }>
                <FormLayout>
                    <FormLayoutColumn>
                        <Heading size={HeadingSize.SUB}>
                            {t('operational-point-dialog.basic-info')}
                        </Heading>
                        <OperationalPointRinfCodeField
                            rinfCodeOverride={state.operationalPoint?.rinfCodeOverride ?? ''}
                            rinfCodeGenerated={
                                state.existingOperationalPoint?.rinfCodeGenerated ?? ''
                            }
                            onChange={(value) => updateProp('rinfCodeOverride', value)}
                            editingRinfCode={state.editingRinfCode}
                            onEditingRinfCodeChange={(editing) =>
                                stateActions.setEditingRinfCode(editing)
                            }
                            disabled={!isDraftOnly}
                            hasError={hasErrors(
                                state.committedFields,
                                state.validationIssues,
                                'rinfCodeOverride',
                            )}
                        />
                        <FieldLayout
                            label={t('operational-point-dialog.name')}
                            value={state.existingOperationalPoint?.name}
                        />
                        <FieldLayout
                            label={t('operational-point-dialog.abbreviation')}
                            value={state.existingOperationalPoint?.abbreviation}
                        />
                        <FieldLayout
                            label={t('operational-point-dialog.type-raide')}
                            value={
                                state.existingOperationalPoint &&
                                t(
                                    `enum.OperationalPointRaideType.${state.existingOperationalPoint.raideType}`,
                                )
                            }
                        />
                        <FieldLayout
                            label={`${t('operational-point-dialog.type-rinf')}${isOlp ? '' : ' *'}`}
                            value={
                                <Dropdown
                                    options={rinfTypeOptions}
                                    value={
                                        rinfTypeOptions.find(
                                            (o) => o.value === state.operationalPoint.rinfType,
                                        )?.value
                                    }
                                    onChange={(value) => updateProp('rinfType', value)}
                                    onBlur={() => stateActions.onCommitField('rinfType')}
                                    hasError={hasErrors(
                                        state.committedFields,
                                        state.validationIssues,
                                        'rinfType',
                                    )}
                                    canUnselect={isOlp}
                                    wide
                                />
                            }
                            errors={getVisibleErrorsByProp('rinfType')}
                        />
                        <FieldLayout
                            label={t('operational-point-dialog.state')}
                            value={
                                state.existingOperationalPoint && (
                                    <OperationalPointState
                                        state={state.existingOperationalPoint?.state}
                                    />
                                )
                            }
                        />
                        <FieldLayout
                            label={t('operational-point-dialog.uic-code')}
                            value={state.existingOperationalPoint?.uicCode}
                        />
                    </FormLayoutColumn>
                </FormLayout>
            </Dialog>
            {deleteDraftConfirmDialogOpen && (
                <OperationalPointDeleteDraftConfirmDialog
                    onClose={() => setdeleteDraftConfirmDialogOpen(false)}
                    onRevert={revertDraft}
                />
            )}
        </React.Fragment>
    );
};
