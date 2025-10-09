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
import { OperationalPointDeleteDraftConfirmDialog } from 'tool-panel/operational-point/operational-point-delete-draft-confirm-dialog';
import { OperationalPoint, OperationalPointId, RinfType } from 'track-layout/track-layout-model';
import {
    actions,
    initialInternalOperationalPointEditState,
    InternalOperationalPointEditState,
    InternalOperationalPointSaveRequest,
    reducer,
} from 'tool-panel/operational-point/internal-operational-point-edit-store';
import { createDelegatesWithDispatcher } from 'store/store-utils';
import { UnknownAction } from 'redux';
import {
    hasErrors as hasErrorsGeneric,
    getVisibleErrorsByProp as getVisibleErrorsByPropGeneric,
} from 'utils/validation-utils';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import {
    deleteDraftOperationalPoint,
    insertOperationalPoint,
    updateInternalOperationalPoint,
} from 'track-layout/layout-operational-point-api';
import { LayoutContext } from 'common/common-model';

type InternalOperationalPointEditDialogProps = {
    operationalPoint: OperationalPoint | undefined;
    layoutContext: LayoutContext;
    onSave: (id: OperationalPointId) => void;
    onClose: () => void;
};

export const InternalOperationalPointEditDialog: React.FC<
    InternalOperationalPointEditDialogProps
> = ({ operationalPoint, layoutContext, onClose, onSave }) => {
    const { t } = useTranslation();

    const [state, dispatcher] = React.useReducer<
        InternalOperationalPointEditState,
        [action: UnknownAction]
    >(reducer, initialInternalOperationalPointEditState);
    const stateActions = createDelegatesWithDispatcher(dispatcher, actions);

    React.useEffect(() => {
        if (operationalPoint) stateActions.onOperationalPointLoaded(operationalPoint);
    }, [operationalPoint]);

    const [deleteDraftConfirmDialogOpen, setShowDeleteDraftConfirmDialog] = React.useState(false);
    const [deleteOperationalPointConfirmDialogOpen, setDeleteOperationalPointConfirmDialogOpen] =
        React.useState(false);
    const [isSaving, setIsSaving] = React.useState(false);

    const isNew = !operationalPoint;

    const stateOptions = operationalPointStates.map((s) =>
        s.value !== 'DELETED' || !isNew ? s : { ...s, disabled: true },
    );
    const rinfTypeOptions: DropdownOption<RinfType>[] = rinfTypes.map((value) =>
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

    const hasErrors = (fieldName: keyof InternalOperationalPointSaveRequest) =>
        hasErrorsGeneric(state.committedFields, state.validationIssues, fieldName);
    const getVisibleErrorsByProp = (prop: keyof InternalOperationalPointSaveRequest) =>
        getVisibleErrorsByPropGeneric(state.committedFields, state.validationIssues, prop).map(
            (err) => t(`operational-point-dialog.validation.${err}`),
        );

    function saveNewOperationalPoint(newOperationalPoint: InternalOperationalPointSaveRequest) {
        setIsSaving(true);
        insertOperationalPoint(newOperationalPoint, layoutContext)
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
    ) {
        setIsSaving(true);
        updateInternalOperationalPoint(id, updatedOperationalPoint, layoutContext)
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
            ? saveNewOperationalPoint(state.operationalPoint)
            : saveUpdatedOperationalPoint(operationalPoint.id, state.operationalPoint);

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
            setShowDeleteDraftConfirmDialog(true);
        }
    };

    const canSave = !isSaving;

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
                        <FieldLayout
                            label={`${t('operational-point-dialog.name')} *`}
                            value={
                                <TextField
                                    value={state.operationalPoint.name}
                                    onChange={(e) => updateProp('name', e.target.value)}
                                    onBlur={() => stateActions.onCommitField('name')}
                                    hasError={hasErrors('name')}
                                    wide
                                />
                            }
                            errors={getVisibleErrorsByProp('name')}
                        />
                        <FieldLayout
                            label={`${t('operational-point-dialog.abbreviation')} *`}
                            value={
                                <TextField
                                    value={state.operationalPoint.abbreviation}
                                    onChange={(e) => updateProp('abbreviation', e.target.value)}
                                    onBlur={() => stateActions.onCommitField('abbreviation')}
                                    hasError={hasErrors('abbreviation')}
                                    wide
                                />
                            }
                            errors={getVisibleErrorsByProp('abbreviation')}
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
                            errors={getVisibleErrorsByProp('rinfType')}
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
                            errors={getVisibleErrorsByProp('state')}
                        />
                        <FieldLayout
                            label={`${t('operational-point-dialog.uic-code')} *`}
                            value={
                                <TextField
                                    value={state.operationalPoint.uicCode}
                                    onChange={(e) => updateProp('uicCode', e.target.value)}
                                    onBlur={() => stateActions.onCommitField('uicCode')}
                                    hasError={hasErrors('uicCode')}
                                    wide
                                />
                            }
                            errors={getVisibleErrorsByProp('uicCode')}
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
