import * as React from 'react';
import { Dialog, DialogVariant } from 'vayla-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { FormLayout, FormLayoutColumn } from 'geoviite-design-lib/form-layout/form-layout';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { useTranslation } from 'react-i18next';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { createTrackNumber, updateTrackNumber } from 'track-layout/track-layout-api';
import {
    useReferenceLineStartAndEnd,
    useTrackNumberReferenceLine,
    useTrackNumbers,
} from 'track-layout/track-layout-react-utils';
import { Heading, HeadingSize } from 'vayla-design-lib/heading/heading';
import {
    actions,
    getErrors,
    initialTrackNumberEditState,
    reducer,
    TrackNumberSaveRequest,
} from './track-number-edit-store';
import { createDelegates } from 'store/store-utils';
import { ValidationErrorType } from 'utils/validation-utils';
import {
    LayoutReferenceLine,
    LayoutTrackNumber,
    LayoutTrackNumberId,
} from 'track-layout/track-layout-model';
import { formatTrackMeter } from 'utils/geography-utils';
import {
    Precision,
    roundToPrecision,
} from 'utils/rounding';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { layoutStates } from 'utils/enum-localization-utils';
import styles from 'vayla-design-lib/dialog/dialog.scss';
import { Icons } from 'vayla-design-lib/icon/Icon';
import TrackNumberDeleteConfirmationDialog from 'tool-panel/track-number/dialog/track-number-delete-confirmation-dialog';
import { createClassName } from 'vayla-design-lib/utils';

type TrackNumberEditDialogContainerProps = {
    editTrackNumberId?: LayoutTrackNumberId;
    onClose: () => void;
    onSave?: (trackNumberId: LayoutTrackNumberId) => void;
};

type TrackNumberEditDialogProps = {
    inEditTrackNumber?: LayoutTrackNumber;
    inEditReferenceLine?: LayoutReferenceLine;
    isNewDraft: boolean;
    trackNumbers: LayoutTrackNumber[];
    onClose: () => void;
    onSave?: (trackNumberId: LayoutTrackNumberId) => void;
};

export const TrackNumberEditDialogContainer: React.FC<TrackNumberEditDialogContainerProps> = ({
    editTrackNumberId,
    onClose,
    onSave,
}: TrackNumberEditDialogContainerProps) => {
    const trackNumbers = useTrackNumbers('DRAFT');
    const editReferenceLine = useTrackNumberReferenceLine(editTrackNumberId, 'DRAFT');
    const officialTrackNumbers = useTrackNumbers('OFFICIAL');
    const isDeletable =
        officialTrackNumbers &&
        !officialTrackNumbers.some(
            (officialTrackNumber) => officialTrackNumber.id === editTrackNumberId,
        );

    if (trackNumbers !== undefined && (!editTrackNumberId || editReferenceLine)) {
        return (
            <TrackNumberEditDialog
                inEditTrackNumber={trackNumbers.find((tn) => tn.id == editTrackNumberId)}
                inEditReferenceLine={editReferenceLine}
                trackNumbers={trackNumbers}
                isNewDraft={!!isDeletable}
                onClose={onClose}
                onSave={onSave}
            />
        );
    } else {
        return <div />;
    }
};

export const TrackNumberEditDialog: React.FC<TrackNumberEditDialogProps> = ({
    inEditTrackNumber,
    inEditReferenceLine,
    trackNumbers,
    isNewDraft,
    onClose,
    onSave,
}: TrackNumberEditDialogProps) => {
    const { t } = useTranslation();

    const [state, dispatcher] = React.useReducer(
        reducer,
        initialTrackNumberEditState(inEditTrackNumber, inEditReferenceLine, trackNumbers),
    );
    const stateActions = createDelegates(dispatcher, actions);
    const startAndEndPoints = inEditReferenceLine
        ? useReferenceLineStartAndEnd(inEditReferenceLine.id, 'DRAFT')
        : undefined;

    const [saveInProgress, setSaveInProgress] = React.useState<boolean>(false);
    const [draftDeleteConfirmationVisible, setDraftDeleteConfirmationVisible] =
        React.useState<boolean>();
    const [nonDraftDeleteConfirmationVisible, setNonDraftDeleteConfirmationVisible] =
        React.useState<boolean>(false);

    const trackNumberStateOptions = layoutStates.filter((s) => s.value !== 'PLANNED');

    const confirmNewDraftDelete = () => {
        setDraftDeleteConfirmationVisible(true);
    };
    const closeDraftDeleteConfirmation = () => {
        setDraftDeleteConfirmationVisible(false);
    };
    const closeNonDraftDeleteConfirmation = () => {
        setNonDraftDeleteConfirmationVisible(false);
    };
    const onTrackNumberDeleted = () => {
        closeDraftDeleteConfirmation();
        onClose && onClose();
    };

    const saveOrConfirm = () => {
        if (state.request?.state === 'DELETED') {
            setNonDraftDeleteConfirmationVisible(true);
        } else {
            saveTrackNumber();
        }
    };

    const saveTrackNumber = () => {
        setSaveInProgress(true);
        const operation = inEditTrackNumber
            ? updateTrackNumber(inEditTrackNumber.id, state.request)
            : createTrackNumber(state.request);
        operation
            .then((tn) => {
                if (tn) {
                    Snackbar.success(t('track-number-edit.result.succeeded'));
                    if (onSave) onSave(tn);
                    onClose();
                } else {
                    Snackbar.error(t('track-number-edit.result.failed'));
                }
            })
            .finally(() => {
                setNonDraftDeleteConfirmationVisible(false);
                setSaveInProgress(false);
            });
    };

    const hasErrors =
        state.validationErrors.filter((e) => e.type === ValidationErrorType.ERROR).length > 0;
    const numberErrors = getErrors(state, 'number');
    const stateErrors = getErrors(state, 'state');
    const descriptionErrors = getErrors(state, 'description');
    const startAddressErrors = getErrors(state, 'startAddress');

    return (
        <Dialog
            title={t(
                inEditTrackNumber
                    ? 'track-number-edit.title.edit'
                    : 'track-number-edit.title.create',
            )}
            onClose={onClose}
            style={{ minWidth: '700px' }}
            scrollable={false}
            footerClassName={'dialog-footer'}
            footerContent={
                <React.Fragment>
                    <div className={styles['dialog-footer__content-area']}>
                        {isNewDraft && inEditTrackNumber && (
                            <div className={styles['dialog-footer__content--shrink']}>
                                <Button
                                    onClick={() => {
                                        inEditTrackNumber ? confirmNewDraftDelete() : undefined;
                                    }}
                                    icon={Icons.Delete}
                                    variant={ButtonVariant.WARNING}>
                                    {t('track-number-edit.title.delete-draft')}
                                </Button>
                            </div>
                        )}
                        <div
                            className={createClassName(
                                styles['dialog-footer__content--grow'],
                                styles['dialog-footer__content--centered'],
                                styles['dialog-footer__content--padded'],
                            )}>
                            <Button
                                variant={ButtonVariant.SECONDARY}
                                disabled={saveInProgress}
                                onClick={onClose}>
                                {t('track-number-edit.action.cancel')}
                            </Button>
                            <Button
                                disabled={hasErrors || saveInProgress}
                                isProcessing={saveInProgress}
                                onClick={saveOrConfirm}>
                                {t('track-number-edit.action.save')}
                            </Button>
                        </div>
                    </div>
                </React.Fragment>
            }>
            <FormLayout isProcessing={saveInProgress}>
                <FormLayoutColumn>
                    <Heading size={HeadingSize.SUB}>
                        {t('track-number-edit.title.track-number')}
                    </Heading>
                    <FieldLayout
                        label={`${t('track-number-edit.field.number')} *`}
                        value={
                            <TextField
                                value={state.request.number}
                                onChange={(e) =>
                                    stateActions.onUpdateProp({
                                        key: 'number' as keyof TrackNumberSaveRequest,
                                        value: e.target.value,
                                        editingExistingValue: !!inEditTrackNumber,
                                    })
                                }
                                onBlur={() => stateActions.onCommitField('number')}
                                disabled={saveInProgress}
                                hasError={numberErrors.length > 0}
                                wide
                            />
                        }
                        errors={numberErrors.map((e) => t(e.reason))}
                    />
                    <FieldLayout
                        label={`${t('track-number-edit.field.state')} *`}
                        value={
                            <Dropdown
                                value={state.request.state}
                                canUnselect={false}
                                options={trackNumberStateOptions}
                                onChange={(state) =>
                                    stateActions.onUpdateProp({
                                        key: 'state' as keyof TrackNumberSaveRequest,
                                        value: state || 'NOT_IN_USE',
                                        editingExistingValue: !!inEditTrackNumber,
                                    })
                                }
                                onBlur={() => stateActions.onCommitField('state')}
                                hasError={stateErrors.length > 0}
                                wide
                                searchable
                            />
                        }
                        errors={stateErrors.map((e) => t(e.reason))}
                    />
                    <FieldLayout
                        label={`${t('track-number-edit.field.description')} *`}
                        value={
                            <TextField
                                value={state.request.description}
                                onChange={(e) =>
                                    stateActions.onUpdateProp({
                                        key: 'description' as keyof TrackNumberSaveRequest,
                                        value: e.target.value,
                                        editingExistingValue: !!inEditTrackNumber,
                                    })
                                }
                                onBlur={() => stateActions.onCommitField('description')}
                                disabled={saveInProgress}
                                hasError={descriptionErrors.length > 0}
                                wide
                            />
                        }
                        errors={descriptionErrors.map((e) => t(e.reason))}
                    />
                </FormLayoutColumn>
                <FormLayoutColumn>
                    <Heading size={HeadingSize.SUB}>
                        {t('track-number-edit.title.reference-line')}
                    </Heading>
                    <FieldLayout
                        label={`${t('track-number-edit.field.start-location')} *`}
                        value={
                            <TextField
                                value={state.request.startAddress}
                                onChange={(e) =>
                                    stateActions.onUpdateProp({
                                        key: 'startAddress' as keyof TrackNumberSaveRequest,
                                        value: e.target.value,
                                        editingExistingValue: !!inEditTrackNumber,
                                    })
                                }
                                onBlur={() => stateActions.onCommitField('startAddress')}
                                disabled={saveInProgress}
                                hasError={startAddressErrors.length > 0}
                                wide
                            />
                        }
                        errors={startAddressErrors.map((e) => t(e.reason))}
                    />
                    {startAndEndPoints && (
                        <FieldLayout
                            label={t('track-number-edit.field.end-location')}
                            value={
                                <TextField
                                    value={formatTrackMeter(startAndEndPoints.end.address)}
                                    wide
                                    disabled
                                />
                            }
                        />
                    )}
                    {inEditReferenceLine && (
                        <FieldLayout
                            label={t('track-number-edit.field.true-length')}
                            value={
                                <TextField
                                    value={roundToPrecision(
                                        inEditReferenceLine.length,
                                        Precision.alignmentLengthMeters,
                                    )}
                                    wide
                                    disabled
                                />
                            }
                        />
                    )}
                </FormLayoutColumn>
            </FormLayout>
            {nonDraftDeleteConfirmationVisible && (
                <Dialog
                    title={t('track-number-edit.title.delete-non-draft')}
                    variant={DialogVariant.DARK}
                    allowClose={false}
                    style={{ minWidth: '300px' }}
                    footerContent={
                        <React.Fragment>
                            <Button
                                onClick={closeNonDraftDeleteConfirmation}
                                variant={ButtonVariant.SECONDARY}>
                                {t('button.cancel')}
                            </Button>
                            <Button onClick={saveTrackNumber}>{t('button.delete')}</Button>
                        </React.Fragment>
                    }>
                    <div className={'dialog__text'}>
                        {t('track-number-edit.dialog.deleted-track-numbers-not-allowed')}
                    </div>
                    <div className={'dialog__text'}>
                        {t('track-number-edit.dialog.confirm-track-number-delete')}
                    </div>
                </Dialog>
            )}
            {inEditTrackNumber && draftDeleteConfirmationVisible && (
                <TrackNumberDeleteConfirmationDialog
                    id={inEditTrackNumber.id}
                    onCancel={closeDraftDeleteConfirmation}
                    onClose={onTrackNumberDeleted}
                />
            )}
        </Dialog>
    );
};

export default TrackNumberEditDialog;
