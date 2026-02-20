import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import { FormLayout, FormLayoutColumn } from 'geoviite-design-lib/form-layout/form-layout';
import { Heading, HeadingSize } from 'vayla-design-lib/heading/heading';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { Dropdown, dropdownOption, DropdownOption } from 'vayla-design-lib/dropdown/dropdown';
import { operationalPointStates, rinfTypes } from 'utils/enum-localization-utils';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { OperationalPointDeleteDraftConfirmDialog } from 'tool-panel/operational-point/dialog/operational-point-delete-draft-confirm-dialog';
import {
    OperationalPoint,
    OperationalPointId,
    OperationalPointState,
    OperationalPointRinfType,
} from 'track-layout/track-layout-model';
import {
    actions,
    initialInternalOperationalPointEditState,
    InternalOperationalPointEditState,
    InternalOperationalPointSaveRequest,
    reducer,
} from 'tool-panel/operational-point/dialog/internal-operational-point-edit-store';
import { createDelegatesWithDispatcher } from 'store/store-utils';
import { UnknownAction } from 'redux';
import {
    FieldValidationIssueType,
    getVisibleErrorsByProp as getVisibleErrorsByPropGeneric,
    hasErrors as hasErrorsGeneric,
} from 'utils/validation-utils';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import {
    deleteDraftOperationalPoint,
    insertOperationalPoint,
    updateInternalOperationalPoint,
} from 'track-layout/layout-operational-point-api';
import { LayoutContext } from 'common/common-model';
import { isEqualIgnoreCase } from 'utils/string-utils';
import { filterNotEmpty } from 'utils/array-utils';
import { AnchorLink } from 'geoviite-design-lib/link/anchor-link';
import { OperationalPointRinfCodeField } from './operational-point-rinf-code-field';
import { withConditionalRinfCodeOverride } from 'tool-panel/operational-point/operational-point-utils';

type InternalOperationalPointEditDialogProps = {
    operationalPoint: OperationalPoint | undefined;
    layoutContext: LayoutContext;
    allOtherOperationalPoints: OperationalPoint[];
    isDraftOnly: boolean;
    onSave: (id: OperationalPointId) => void;
    onClose: () => void;
    onEditOperationalPoint: (operationalPoint: OperationalPointId) => void;
};

export const InternalOperationalPointEditDialog: React.FC<
    InternalOperationalPointEditDialogProps
> = ({
    operationalPoint,
    layoutContext,
    allOtherOperationalPoints,
    isDraftOnly,
    onClose,
    onSave,
    onEditOperationalPoint,
}) => {
    const { t } = useTranslation();

    const [state, dispatcher] = React.useReducer<
        InternalOperationalPointEditState,
        [action: UnknownAction]
    >(reducer, initialInternalOperationalPointEditState);
    const stateActions = createDelegatesWithDispatcher(dispatcher, actions);

    const isNew = !operationalPoint;

    React.useEffect(() => {
        stateActions.onInit(operationalPoint);
    }, [operationalPoint]);

    React.useEffect(() => {
        stateActions.setEditingRinfCode(!isNew && !!operationalPoint.rinfCodeOverride);
    }, [isNew, operationalPoint?.rinfCodeOverride]);

    const [deleteDraftConfirmDialogOpen, setShowDeleteDraftConfirmDialog] = React.useState(false);
    const [deleteOperationalPointConfirmDialogOpen, setDeleteOperationalPointConfirmDialogOpen] =
        React.useState(false);
    const [isSaving, setIsSaving] = React.useState(false);

    const stateOptions = operationalPointStates.map((s) =>
        s.value !== 'DELETED' || !isDraftOnly ? s : { ...s, disabled: true },
    );
    const rinfTypeOptions: DropdownOption<OperationalPointRinfType>[] = rinfTypes.map((value) =>
        dropdownOption(value.value, value.name, `rinf-type-option-${value}`),
    );

    function updateProp<TKey extends keyof InternalOperationalPointSaveRequest>(
        key: TKey,
        value: InternalOperationalPointSaveRequest[TKey],
    ) {
        stateActions.onUpdateProp({
            key: key,
            value: value,
            editingExistingValue: !!state.operationalPoint,
        });
    }

    const duplicateAbbreviationPoint = allOtherOperationalPoints?.find(
        (operationalPoint) =>
            operationalPoint.abbreviation &&
            state.operationalPoint?.abbreviation &&
            isEqualIgnoreCase(operationalPoint.abbreviation, state.operationalPoint.abbreviation),
    );
    const duplicateNamePoint = allOtherOperationalPoints?.find(
        (operationalPoint) =>
            operationalPoint.state !== 'DELETED' &&
            !!state.operationalPoint?.name &&
            isEqualIgnoreCase(operationalPoint.name, state.operationalPoint.name),
    );
    const duplicateUicCodePoint = allOtherOperationalPoints?.find(
        (operationalPoint) =>
            operationalPoint.state !== 'DELETED' &&
            !!state.operationalPoint.uicCode &&
            isEqualIgnoreCase(operationalPoint.uicCode, state.operationalPoint.uicCode),
    );
    const duplicateRinfCodePoint = allOtherOperationalPoints?.find(
        (operationalPoint) =>
            operationalPoint.state !== 'DELETED' &&
            !!state.operationalPoint?.rinfCodeOverride &&
            !!operationalPoint.rinfCode &&
            isEqualIgnoreCase(operationalPoint.rinfCode, state.operationalPoint.rinfCodeOverride),
    );

    const hasErrors = (fieldName: keyof InternalOperationalPointSaveRequest) =>
        hasErrorsGeneric(state.committedFields, state.validationIssues, fieldName);
    const getVisibleErrorsByProp = (prop: keyof InternalOperationalPointSaveRequest) =>
        getVisibleErrorsByPropGeneric(state.committedFields, state.validationIssues, prop);

    const visibleNameErrors = [
        ...getVisibleErrorsByProp('name'),
        duplicateNamePoint !== undefined ? 'name-in-use' : undefined,
    ].filter(filterNotEmpty);

    const visibleAbbreviationErrors = [
        ...getVisibleErrorsByProp('abbreviation'),
        duplicateAbbreviationPoint !== undefined ? 'abbreviation-in-use' : undefined,
    ].filter(filterNotEmpty);

    const visibleUicCodeErrors = [
        ...getVisibleErrorsByProp('uicCode'),
        duplicateUicCodePoint !== undefined ? 'uic-code-in-use' : undefined,
    ].filter(filterNotEmpty);

    const visibleRinfCodeErrors = [
        ...getVisibleErrorsByProp('rinfCodeOverride'),
        duplicateRinfCodePoint !== undefined ? 'rinf-code-in-use' : undefined,
    ].filter(filterNotEmpty);

    const translateErrors = (errors: string[]): string[] =>
        errors.map((err) => t(`operational-point-dialog.validation.${err}`));

    function saveNewOperationalPoint(
        newOperationalPoint: InternalOperationalPointSaveRequest,
        allowRinfCodeOverride: boolean,
    ) {
        setIsSaving(true);
        const saveRequest = withConditionalRinfCodeOverride(
            newOperationalPoint,
            allowRinfCodeOverride,
        );
        insertOperationalPoint(saveRequest, layoutContext)
            .then(
                (opId) => {
                    onSave(opId);
                    onClose();
                    Snackbar.success('operational-point-dialog.new-added');
                },
                () => Snackbar.error('operational-point-dialog.adding-failed'),
            )
            .finally(() => setIsSaving(false));
    }

    function saveUpdatedOperationalPoint(
        id: OperationalPointId,
        updatedOperationalPoint: InternalOperationalPointSaveRequest,
        allowRinfCodeOverride: boolean,
    ) {
        setIsSaving(true);
        const saveRequest = withConditionalRinfCodeOverride(
            updatedOperationalPoint,
            allowRinfCodeOverride,
        );
        updateInternalOperationalPoint(id, saveRequest, layoutContext)
            .then(
                () => {
                    onSave(id);
                    onClose();
                    Snackbar.success('operational-point-dialog.modified-successfully');
                },
                () => Snackbar.error('operational-point-dialog.modify-failed'),
            )
            .finally(() => setIsSaving(false));
    }

    const save = () =>
        isNew
            ? saveNewOperationalPoint(state.operationalPoint, state.editingRinfCode)
            : saveUpdatedOperationalPoint(
                  operationalPoint.id,
                  state.operationalPoint,
                  state.editingRinfCode,
              );

    const saveOrConfirm = () =>
        state.operationalPoint.state === 'DELETED'
            ? setDeleteOperationalPointConfirmDialogOpen(true)
            : save();

    const deleteOperationalPoint = () => {
        save();
        setDeleteOperationalPointConfirmDialogOpen(false);
    };

    const revertDraft = () => {
        if (operationalPoint) {
            deleteDraftOperationalPoint(layoutContext, operationalPoint.id);
            setShowDeleteDraftConfirmDialog(false);
            onClose();
        }
    };

    const moveToEditLinkText = (state: OperationalPointState, name: string) => {
        return state === 'DELETED'
            ? t('operational-point-dialog.move-to-edit-deleted')
            : t('operational-point-dialog.move-to-edit', { name });
    };

    const canSave =
        !isSaving &&
        !duplicateNamePoint &&
        !duplicateAbbreviationPoint &&
        !duplicateUicCodePoint &&
        !duplicateRinfCodePoint &&
        !state.validationIssues.some((issue) => issue.type === FieldValidationIssueType.ERROR);

    const onUpdateRinfCode = (rinfCode: string) => {
        stateActions.onUpdateProp({
            key: 'rinfCodeOverride',
            value: rinfCode,
            editingExistingValue: !isNew && !!state.existingOperationalPoint?.rinfCodeOverride,
        });
    };

    return (
        <React.Fragment>
            <Dialog
                title={
                    isNew
                        ? t('operational-point-dialog.title-new')
                        : t('operational-point-dialog.title-edit')
                }
                onClose={onClose}
                footerContent={
                    <React.Fragment>
                        {!isNew && (
                            <Button
                                disabled={!operationalPoint?.isDraft}
                                onClick={() => setShowDeleteDraftConfirmDialog(true)}
                                variant={ButtonVariant.WARNING}>
                                {t('button.revert-draft')}
                            </Button>
                        )}
                        <div
                            className={
                                isNew
                                    ? dialogStyles['dialog__footer-content--centered']
                                    : dialogStyles['dialog__footer-content--right-aligned']
                            }>
                            <Button variant={ButtonVariant.SECONDARY} onClick={onClose}>
                                {t('button.cancel')}
                            </Button>
                            <Button
                                qa-id="save-internal-operational-point-changes"
                                onClick={saveOrConfirm}
                                disabled={!canSave}
                                isProcessing={isSaving}>
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
                            onUpdateRinfCode={onUpdateRinfCode}
                            onCommitField={stateActions.onCommitField}
                            editingRinfCode={state.editingRinfCode}
                            onEditingRinfCodeChange={(editing) =>
                                stateActions.setEditingRinfCode(editing)
                            }
                            errors={visibleRinfCodeErrors}
                        />
                        <FieldLayout
                            label={`${t('operational-point-dialog.name')} *`}
                            value={
                                <TextField
                                    value={state.operationalPoint.name}
                                    onChange={(e) => updateProp('name', e.target.value)}
                                    onBlur={() => stateActions.onCommitField('name')}
                                    hasError={visibleNameErrors.length > 0}
                                    wide
                                />
                            }
                            errors={translateErrors(visibleNameErrors)}>
                            {duplicateNamePoint && (
                                <AnchorLink
                                    className={dialogStyles['dialog__alert']}
                                    onClick={() => onEditOperationalPoint(duplicateNamePoint.id)}>
                                    {moveToEditLinkText(
                                        duplicateNamePoint.state,
                                        duplicateNamePoint.name,
                                    )}
                                </AnchorLink>
                            )}
                        </FieldLayout>
                        <FieldLayout
                            label={t('operational-point-dialog.abbreviation')}
                            value={
                                <TextField
                                    value={state.operationalPoint.abbreviation ?? ''}
                                    onChange={(e) => updateProp('abbreviation', e.target.value)}
                                    onBlur={() => stateActions.onCommitField('abbreviation')}
                                    hasError={visibleAbbreviationErrors.length > 0}
                                    wide
                                />
                            }
                            errors={translateErrors(visibleAbbreviationErrors)}
                        />
                        <FieldLayout
                            label={`${t('operational-point-dialog.type-rinf')} *`}
                            value={
                                <Dropdown
                                    options={rinfTypeOptions}
                                    value={
                                        rinfTypeOptions.find(
                                            (o) => o.value === state.operationalPoint.rinfType,
                                        )?.value
                                    }
                                    onChange={(value) => value && updateProp('rinfType', value)}
                                    onBlur={() => stateActions.onCommitField('rinfType')}
                                    hasError={hasErrors('rinfType')}
                                    wide
                                />
                            }
                            errors={translateErrors(getVisibleErrorsByProp('rinfType'))}
                        />
                        <FieldLayout
                            label={`${t('operational-point-dialog.state')} *`}
                            value={
                                <Dropdown
                                    options={stateOptions}
                                    value={state.operationalPoint.state}
                                    onChange={(value) => value && updateProp('state', value)}
                                    onBlur={() => stateActions.onCommitField('state')}
                                    hasError={hasErrors('state')}
                                    wide
                                />
                            }
                            errors={translateErrors(getVisibleErrorsByProp('state'))}
                        />
                        <FieldLayout
                            label={`${t('operational-point-dialog.uic-code')} *`}
                            value={
                                <TextField
                                    value={state.operationalPoint.uicCode}
                                    onChange={(e) => updateProp('uicCode', e.target.value)}
                                    onBlur={() => stateActions.onCommitField('uicCode')}
                                    hasError={visibleUicCodeErrors.length > 0}
                                    wide
                                />
                            }
                            errors={translateErrors(visibleUicCodeErrors)}
                        />
                    </FormLayoutColumn>
                </FormLayout>
            </Dialog>
            {deleteDraftConfirmDialogOpen && (
                <OperationalPointDeleteDraftConfirmDialog
                    onClose={() => setShowDeleteDraftConfirmDialog(false)}
                    onRevert={revertDraft}
                />
            )}
            {deleteOperationalPointConfirmDialogOpen && (
                <Dialog
                    title={t('operational-point-dialog.delete-confirm-dialog.title')}
                    onClose={() => setDeleteOperationalPointConfirmDialogOpen(false)}
                    variant={DialogVariant.DARK}
                    allowClose={false}
                    footerContent={
                        <div className={dialogStyles['dialog__footer-content--centered']}>
                            <Button
                                variant={ButtonVariant.SECONDARY}
                                onClick={() => setDeleteOperationalPointConfirmDialogOpen(false)}>
                                {t('button.cancel')}
                            </Button>
                            <Button
                                variant={ButtonVariant.PRIMARY_WARNING}
                                onClick={deleteOperationalPoint}>
                                {t('button.delete')}
                            </Button>
                        </div>
                    }>
                    <div className={dialogStyles['dialog__text']}>
                        {t('operational-point-dialog.delete-confirm-dialog.deleted-op-not-allowed')}
                    </div>
                    <div className={dialogStyles['dialog__text']}>
                        {t('operational-point-dialog.delete-confirm-dialog.confirm')}
                    </div>
                </Dialog>
            )}
        </React.Fragment>
    );
};
