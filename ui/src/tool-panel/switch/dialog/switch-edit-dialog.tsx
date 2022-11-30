import React from 'react';
import { Dialog, DialogVariant } from 'vayla-design-lib/dialog/dialog';
import { useTranslation } from 'react-i18next';
import {
    booleanToTrapPoint,
    LayoutStateCategory,
    LayoutSwitch,
    LayoutSwitchId,
    TrapPoint,
    trapPointToBoolean,
} from 'track-layout/track-layout-model';
import { FormLayout, FormLayoutColumn } from 'geoviite-design-lib/form-layout/form-layout';
import { Heading, HeadingSize } from 'vayla-design-lib/heading/heading';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { insertSwitch, updateSwitch } from 'linking/linking-api';
import { ValidationError, ValidationErrorType } from 'utils/validation-utils';
import { TrackLayoutSwitchSaveRequest } from 'linking/linking-model';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import {
    SwitchOwner,
    SwitchOwnerId,
    SwitchStructure,
    SwitchStructureId,
} from 'common/common-model';
import { getSwitchOwners, getSwitchStructures } from 'common/common-api';
import { layoutStateCategories, switchTrapPoints } from 'utils/enum-localization-utils';
import { getSwitch } from 'track-layout/track-layout-api';
import SwitchDeleteDialog from 'tool-panel/switch/dialog/switch-delete-dialog';
import styles from 'vayla-design-lib/dialog/dialog.scss';
import dialogStyles from 'vayla-design-lib/dialog/dialog.scss';
import { createClassName } from 'vayla-design-lib/utils';

const SWITCH_NAME_REGEX = /^[A-ZÄÖÅa-zäöå0-9 \-_/]+$/g;

export type SwitchDialogProps = {
    switchId?: LayoutSwitchId;
    prefilledSwitchStructureId?: SwitchStructureId;
    onClose: () => void;
    onInsert?: (switchId: LayoutSwitchId) => void;
    onUpdate?: () => void;
    onDelete?: () => void;
};

export const SwitchEditDialog = ({
    switchId,
    prefilledSwitchStructureId,
    onClose,
    onInsert,
    onUpdate,
    onDelete,
}: SwitchDialogProps) => {
    const { t } = useTranslation();
    const [showConfirmationDialog, setShowConfirmationDialog] = React.useState(false);
    const [showDeleteDialog, setShowDeleteDialog] = React.useState(false);
    const [switchStateCategory, setSwitchStateCategory] = React.useState<LayoutStateCategory>();
    const [switchName, setSwitchName] = React.useState<string>('');
    const [trapPoint, setTrapPoint] = React.useState<TrapPoint>(TrapPoint.Unknown);
    const [switchStructureId, setSwitchStructureId] = React.useState<SwitchStructureId | undefined>(
        prefilledSwitchStructureId,
    );
    const [validationErrors, setValidationErrors] = React.useState<
        ValidationError<TrackLayoutSwitchSaveRequest>[]
    >([]);
    const [visitedFields, setVisitedFields] = React.useState<string[]>([]);
    const [isSaving, setIsSaving] = React.useState(false);
    const [switchStructures, setSwitchStructures] = React.useState<SwitchStructure[]>([]);
    const [switchOwners, setSwitchOwners] = React.useState<SwitchOwner[]>([]);
    const [switchOwnerId, setSwitchOwnerId] = React.useState<SwitchOwnerId>();
    const [existingSwitch, setExistingSwitch] = React.useState<LayoutSwitch>();
    const [officialSwitch, setOfficialSwitch] = React.useState<LayoutSwitch>();
    const firstInputRef = React.useRef<HTMLInputElement>(null);
    const isExistingSwitch = !!switchId;
    const canEditSwitchStructure = !prefilledSwitchStructureId;

    const switchStateCategoryOptions = layoutStateCategories
        .filter((ls) => isExistingSwitch || ls.value != 'NOT_EXISTING')
        .map((sc) => ({ ...sc, disabled: sc.value === 'FUTURE_EXISTING' }));

    React.useEffect(() => {
        if (isExistingSwitch) {
            getSwitch(switchId, 'DRAFT').then((s) => {
                setExistingSwitch(s);
                setSwitchStateCategory(s.stateCategory);
                setSwitchName(s.name);
                setSwitchStructureId(s.switchStructureId);
                setTrapPoint(booleanToTrapPoint(s.trapPoint));
                firstInputRef.current?.focus();
            });

            getSwitch(switchId, 'OFFICIAL').then((r) => setOfficialSwitch(r));
        } else {
            firstInputRef.current?.focus();
        }
    }, [switchId]);

    React.useEffect(() => {
        getSwitchStructures().then((s) => {
            setSwitchStructures(s);
        });
    }, []);

    React.useEffect(() => {
        getSwitchOwners().then((owners) => {
            const vayla = owners.find((o) => o.name === 'Väylävirasto');
            setSwitchOwnerId(vayla ? vayla.id : owners[0].id);
            setSwitchOwners(owners);
        });
    }, []);

    React.useEffect(() => {
        validateLinkingSwitch();
    }, [switchName, switchStructureId, switchStateCategory, switchOwnerId, visitedFields]);

    function visitField(fieldName: string) {
        if (!visitedFields.includes(fieldName)) {
            setVisitedFields([...visitedFields, fieldName]);
        }
    }

    function updateName(value: string) {
        setSwitchName(value);
        visitField('name');
    }

    function updateStateCategory(stateCategory: LayoutStateCategory) {
        setSwitchStateCategory(stateCategory);
        visitField('stateCategory');
    }

    function updateStructureId(structureId: SwitchStructureId) {
        if (!prefilledSwitchStructureId) {
            setSwitchStructureId(structureId);
            visitField('switchStructureId');
        }
    }

    function updateTrapPoint(trapPoint: TrapPoint) {
        setTrapPoint(trapPoint);
        visitField('trapPoint');
    }

    function updateOwner(id: string) {
        setSwitchOwnerId(id);
        visitField('owner');
    }

    function saveOrConfirm() {
        if (switchStateCategory === 'NOT_EXISTING') {
            setShowConfirmationDialog(true);
        } else {
            save();
        }
    }

    function save() {
        if (
            switchName &&
            switchStateCategory &&
            switchStructureId &&
            switchOwnerId &&
            !existingSwitch
        ) {
            setIsSaving(true);
            const newSwitch: TrackLayoutSwitchSaveRequest = {
                name: switchName,
                switchStructureId: switchStructureId,
                stateCategory: switchStateCategory,
                ownerId: switchOwnerId,
                trapPoint: trapPointToBoolean(trapPoint),
            };

            insertSwitch(newSwitch)
                .then((result) => {
                    result
                        .map((switchId) => {
                            onInsert && onInsert(switchId);
                            Snackbar.success(t('switch-dialog.new-switch-added'));
                        })
                        .mapErr((_err) => {
                            Snackbar.error(t('switch-dialog.adding-switch-failed'));
                        });
                })
                .catch(() => {
                    Snackbar.error(t('switch-dialog.adding-switch-failed'));
                });
        }
        //save updated switch here
        if (
            switchName &&
            switchStateCategory &&
            switchStructureId &&
            switchOwnerId &&
            existingSwitch
        ) {
            const updatedSwitch: TrackLayoutSwitchSaveRequest = {
                name: switchName,
                switchStructureId: switchStructureId,
                stateCategory: switchStateCategory,
                ownerId: switchOwnerId,
                trapPoint: trapPointToBoolean(trapPoint),
            };
            updateSwitch(existingSwitch.id, updatedSwitch)
                .then((result) => {
                    result
                        .map(() => {
                            onUpdate && onUpdate();
                            Snackbar.success(t('switch-dialog.modified-successfully'));
                        })
                        .mapErr((_err) => {
                            Snackbar.error(t('switch-dialog.modify-failed'));
                        });
                })
                .catch(() => {
                    Snackbar.error(t('switch-dialog.modify-failed'));
                });
        }
    }

    function hasErrors(prop: keyof TrackLayoutSwitchSaveRequest) {
        return getVisibleErrorsByProp(prop).length > 0;
    }

    function getVisibleErrorsByProp(prop: keyof TrackLayoutSwitchSaveRequest) {
        if (visitedFields.includes(prop)) {
            return validationErrors
                .filter((error) => error.field == prop)
                .map((error) => error.reason);
        }
        return [];
    }

    function validateLinkingSwitch() {
        const errors: ValidationError<TrackLayoutSwitchSaveRequest>[] = [];
        if (!switchName) {
            errors.push({
                field: 'name',
                reason: t('switch-dialog.validation-error-mandatory-field'),
                type: ValidationErrorType.ERROR,
            });
        }
        if (switchName.length > 20) {
            errors.push({
                field: 'name',
                reason: t('switch-dialog.name-max-limit'),
                type: ValidationErrorType.ERROR,
            });
        }
        if (!switchName.match(SWITCH_NAME_REGEX)) {
            errors.push({
                field: 'name',
                reason: t('switch-dialog.invalid-name'),
                type: ValidationErrorType.ERROR,
            });
        }
        if (!switchStateCategory) {
            errors.push({
                field: 'stateCategory',
                reason: t('switch-dialog.validation-error-mandatory-field'),
                type: ValidationErrorType.ERROR,
            });
        }
        if (!switchStructureId) {
            errors.push({
                field: 'switchStructureId',
                reason: t('switch-dialog.validation-error-mandatory-field'),
                type: ValidationErrorType.ERROR,
            });
        }
        if (!switchOwnerId) {
            errors.push({
                field: 'ownerId',
                reason: t('switch-dialog.validation-error-mandatory-field'),
                type: ValidationErrorType.ERROR,
            });
        }
        setValidationErrors(errors);
    }

    function handleOnDelete() {
        setShowDeleteDialog(false);
        onDelete && onDelete();
    }

    return (
        <React.Fragment>
            <Dialog
                title={
                    isExistingSwitch ? t('switch-dialog.title-edit') : t('switch-dialog.title-new')
                }
                onClose={onClose}
                className={dialogStyles['dialog--ultrawide']}
                footerClassName={'dialog-footer'}
                footerContent={
                    <div className={styles['dialog-footer__content-area']}>
                        {officialSwitch === undefined && isExistingSwitch && (
                            <div className={styles['dialog-footer__content--shrink']}>
                                <Button
                                    onClick={() => setShowDeleteDialog(true)}
                                    icon={Icons.Delete}
                                    variant={ButtonVariant.WARNING}>
                                    {t('tool-panel.switch.layout.delete-draft')}
                                </Button>
                            </div>
                        )}
                        <div
                            className={createClassName(
                                styles['dialog-footer__content--grow'],
                                styles['dialog-footer__content--centered'],
                                styles['dialog-footer__content--padded'],
                            )}>
                            <Button variant={ButtonVariant.SECONDARY} onClick={onClose}>
                                {t('button.return')}
                            </Button>
                            <Button
                                disabled={validationErrors.length > 0 || isSaving}
                                isProcessing={isSaving}
                                onClick={saveOrConfirm}>
                                {t('button.save')}
                            </Button>
                        </div>
                    </div>
                }>
                <FormLayout>
                    <FormLayoutColumn>
                        <Heading size={HeadingSize.SUB}>
                            {t('switch-dialog.basic-info-heading')}
                        </Heading>
                        <FieldLayout
                            label={`${t('switch-dialog.switch-name')} *`}
                            value={
                                <TextField
                                    value={switchName}
                                    onChange={(e) => updateName(e.target.value)}
                                    hasError={hasErrors('name')}
                                    onBlur={() => visitField('name')}
                                    inputRef={firstInputRef}
                                    wide
                                />
                            }
                            errors={getVisibleErrorsByProp('name')}
                        />
                        <FieldLayout
                            label={`${t('switch-dialog.state-category')} *`}
                            value={
                                <Dropdown
                                    value={switchStateCategory}
                                    options={switchStateCategoryOptions}
                                    onChange={(value) => value && updateStateCategory(value)}
                                    onBlur={() => visitField('stateCategory')}
                                    hasError={hasErrors('stateCategory')}
                                    wide
                                    searchable
                                />
                            }
                            errors={getVisibleErrorsByProp('stateCategory')}
                        />
                        <FieldLayout
                            label={`${t('switch-dialog.switch-type')} *`}
                            value={
                                <Dropdown
                                    value={switchStructureId}
                                    options={switchStructures.map((type) => ({
                                        name: type.type,
                                        value: type.id,
                                    }))}
                                    onChange={(value) => value && updateStructureId(value)}
                                    onBlur={() => visitField('switchStructureId')}
                                    hasError={hasErrors('switchStructureId')}
                                    wide
                                    disabled={!canEditSwitchStructure}
                                    searchable
                                />
                            }
                            errors={getVisibleErrorsByProp('switchStructureId')}
                        />

                        <FieldLayout
                            label={`${t('switch-dialog.trap-point')}`}
                            value={
                                <Dropdown
                                    value={trapPoint}
                                    options={switchTrapPoints}
                                    onChange={(value) => value && updateTrapPoint(value)}
                                    onBlur={() => visitField('trapPoint')}
                                    wide
                                />
                            }
                        />

                        <Heading size={HeadingSize.SUB}>
                            {t('switch-dialog.extra-info-heading')}
                        </Heading>

                        <FieldLayout
                            label={t('switch-dialog.owner')}
                            value={
                                <Dropdown
                                    value={switchOwnerId}
                                    options={switchOwners.map((o) => ({
                                        name: o.name,
                                        value: o.id,
                                    }))}
                                    onChange={(value) => value && updateOwner(value)}
                                    onBlur={() => visitField('ownerId')}
                                    hasError={hasErrors('ownerId')}
                                    wide
                                    searchable
                                />
                            }
                            errors={getVisibleErrorsByProp('ownerId')}
                        />
                    </FormLayoutColumn>

                    <FormLayoutColumn>
                        <Heading size={HeadingSize.SUB}>
                            {t('switch-dialog.info-from-linking')}
                        </Heading>
                        <FieldLayout
                            label={t('switch-dialog.track-location')}
                            value={
                                <TextField
                                    value={'-'}
                                    onChange={(_value) => undefined}
                                    onBlur={() => undefined}
                                    wide
                                    disabled
                                />
                            }
                        />
                        <FieldLayout
                            label={t('switch-dialog.track-location')}
                            value={
                                <TextField
                                    value={'-'}
                                    onChange={(_value) => undefined}
                                    onBlur={() => undefined}
                                    wide
                                    disabled
                                />
                            }
                        />
                        <FieldLayout
                            label={t('switch-dialog.track-location')}
                            value={
                                <TextField
                                    value={'-'}
                                    onChange={(_value) => undefined}
                                    onBlur={() => undefined}
                                    wide
                                    disabled
                                />
                            }
                        />
                        <FieldLayout
                            label={t('switch-dialog.coordinate-system')}
                            value={
                                <TextField
                                    value={'-'}
                                    onChange={(_value) => undefined}
                                    onBlur={() => undefined}
                                    wide
                                    disabled
                                />
                            }
                        />
                        <FieldLayout
                            label={t('switch-dialog.coordinates')}
                            value={
                                <TextField
                                    value={'-'}
                                    onChange={(_value) => undefined}
                                    onBlur={() => undefined}
                                    wide
                                    disabled
                                />
                            }
                        />
                    </FormLayoutColumn>
                </FormLayout>
            </Dialog>
            {showConfirmationDialog && (
                <Dialog
                    title={t('switch-dialog.deleted-confirmation-title')}
                    variant={DialogVariant.DARK}
                    allowClose={false}
                    className={dialogStyles['dialog--normal']}
                    footerContent={
                        <>
                            <Button
                                onClick={() => setShowConfirmationDialog(false)}
                                variant={ButtonVariant.SECONDARY}>
                                {t('button.cancel')}
                            </Button>
                            <Button onClick={save}>{t('button.delete')}</Button>
                        </>
                    }>
                    <p>
                        {t('switch-dialog.deleted-category-warning')}
                        <br />
                        <br />
                        {t('switch-dialog.confirm-switch-delete')}
                    </p>
                </Dialog>
            )}
            {showDeleteDialog && switchId && (
                <SwitchDeleteDialog
                    switchId={switchId}
                    onDelete={handleOnDelete}
                    onCancel={() => setShowDeleteDialog(false)}
                />
            )}
        </React.Fragment>
    );
};
