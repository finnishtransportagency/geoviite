import React from 'react';
import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
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
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';
import { FieldValidationIssue, FieldValidationIssueType } from 'utils/validation-utils';
import { TrackLayoutSwitchSaveRequest } from 'linking/linking-model';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import {
    draftLayoutContext,
    LayoutContext,
    SwitchOwner,
    SwitchOwnerId,
    SwitchStructure,
    SwitchStructureId,
} from 'common/common-model';
import { getSwitchOwners, getSwitchStructures } from 'common/common-api';
import { layoutStateCategories, switchTrapPoints } from 'utils/enum-localization-utils';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import {
    getSwitch,
    getSwitchesByName,
    insertSwitch,
    updateSwitch,
} from 'track-layout/layout-switch-api';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import styles from './switch-edit-dialog.scss';
import { useLoader } from 'utils/react-utils';
import { Link } from 'vayla-design-lib/link/link';
import { getSaveDisabledReasons } from 'track-layout/track-layout-react-utils';
import SwitchDeleteConfirmationDialog from './switch-delete-confirmation-dialog';
import { first } from 'utils/array-utils';
import { useTrackLayoutAppSelector } from 'store/hooks';

const SWITCH_NAME_REGEX = /^[A-ZÄÖÅa-zäöå0-9 \-_/]+$/g;

type SwitchDialogContainerProps = {
    switchId?: LayoutSwitchId;
    prefilledSwitchStructureId?: SwitchStructureId;
    onClose: () => void;
    onSave?: (switchId: LayoutSwitchId) => void;
};

export const SwitchEditDialogContainer = ({
    switchId,
    prefilledSwitchStructureId,
    onClose,
    onSave,
}: SwitchDialogContainerProps) => {
    const [editSwitchId, setEditSwitchId] = React.useState<LayoutSwitchId | undefined>(switchId);
    const layoutContext = useTrackLayoutAppSelector((state) => state.layoutContext);
    return (
        <SwitchEditDialog
            layoutContext={layoutContext}
            switchId={editSwitchId}
            prefilledSwitchStructureId={
                switchId === editSwitchId ? prefilledSwitchStructureId : undefined
            }
            onClose={onClose}
            onSave={onSave}
            onEdit={(id) => setEditSwitchId(id)}
        />
    );
};

type SwitchDialogProps = {
    layoutContext: LayoutContext;
    switchId?: LayoutSwitchId;
    prefilledSwitchStructureId?: SwitchStructureId;
    onClose: () => void;
    onSave?: (switchId: LayoutSwitchId) => void;
    onEdit: (id: LayoutSwitchId) => void;
};

export const SwitchEditDialog = ({
    layoutContext,
    switchId,
    prefilledSwitchStructureId,
    onClose,
    onSave,
    onEdit,
}: SwitchDialogProps) => {
    const { t } = useTranslation();
    const [showStructureChangeConfirmationDialog, setShowStructureChangeConfirmationDialog] =
        React.useState(false);
    const [showDeleteOfficialConfirmDialog, setShowDeleteOfficialConfirmDialog] =
        React.useState(false);
    const [showDeleteDraftConfirmDialog, setShowDeleteDraftConfirmDialog] = React.useState(false);
    const [switchStateCategory, setSwitchStateCategory] = React.useState<LayoutStateCategory>();
    const [switchName, setSwitchName] = React.useState<string>('');
    const [trapPoint, setTrapPoint] = React.useState<TrapPoint>(TrapPoint.Unknown);
    const [switchStructureId, setSwitchStructureId] = React.useState<SwitchStructureId | undefined>(
        prefilledSwitchStructureId,
    );
    const [visitedFields, setVisitedFields] = React.useState<string[]>([]);
    const [isSaving, setIsSaving] = React.useState(false);
    const [switchStructures, setSwitchStructures] = React.useState<SwitchStructure[]>([]);
    const [switchOwners, setSwitchOwners] = React.useState<SwitchOwner[]>([]);
    const [switchOwnerId, setSwitchOwnerId] = React.useState<SwitchOwnerId>();
    const [existingSwitch, setExistingSwitch] = React.useState<LayoutSwitch>();
    const firstInputRef = React.useRef<HTMLInputElement>(null);
    const isExistingSwitch = !!switchId;

    const switchStructureChanged =
        isExistingSwitch && switchStructureId != existingSwitch?.switchStructureId;

    const stateCategoryOptions = layoutStateCategories
        .filter((sc) => isExistingSwitch || sc.value != 'NOT_EXISTING')
        .map((sc) => ({ ...sc, qaId: sc.value }));

    const conflictingSwitch = useLoader(async () => {
        if (validateSwitchName(switchName).length == 0) {
            const switches = await getSwitchesByName(draftLayoutContext(layoutContext), switchName);
            return switches.find((s) => s.id != existingSwitch?.id);
        } else {
            return undefined;
        }
    }, [switchName, existingSwitch?.id]);

    React.useEffect(() => {
        if (isExistingSwitch) {
            getSwitch(switchId, draftLayoutContext(layoutContext)).then((s) => {
                if (s) {
                    setExistingSwitch(s);
                    setSwitchStateCategory(s.stateCategory);
                    setSwitchName(s.name);
                    setSwitchStructureId(s.switchStructureId);
                    setTrapPoint(booleanToTrapPoint(s.trapPoint));
                    firstInputRef.current?.focus();
                }
            });
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
            setSwitchOwners(owners);
        });
    }, []);

    React.useEffect(() => {
        if (existingSwitch) {
            setSwitchOwnerId(existingSwitch.ownerId ?? undefined);
        } else if (switchOwners.length > 0) {
            const vayla = switchOwners.find((o) => o.name === 'Väylävirasto');
            setSwitchOwnerId(vayla ? vayla.id : first(switchOwners)?.id);
        }
    }, [switchOwners, existingSwitch]);

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
        setSwitchStructureId(structureId);
        visitField('switchStructureId');
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
        if (
            switchStateCategory === 'NOT_EXISTING' &&
            existingSwitch?.stateCategory !== 'NOT_EXISTING'
        ) {
            setShowDeleteOfficialConfirmDialog(true);
        } else if (switchStructureChanged) {
            setShowStructureChangeConfirmationDialog(true);
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

            insertSwitch(newSwitch, layoutContext).then(
                (switchId) => {
                    onSave && onSave(switchId);
                    onClose();
                    Snackbar.success('switch-dialog.new-switch-added');
                },
                () => Snackbar.error('switch-dialog.adding-switch-failed'),
            );
        }
        //save updated switch here
        if (
            switchName &&
            switchStateCategory &&
            switchStructureId &&
            switchOwnerId &&
            existingSwitch
        ) {
            setIsSaving(true);
            const updatedSwitch: TrackLayoutSwitchSaveRequest = {
                name: switchName,
                switchStructureId: switchStructureId,
                stateCategory: switchStateCategory,
                ownerId: switchOwnerId,
                trapPoint: trapPointToBoolean(trapPoint),
            };
            updateSwitch(existingSwitch.id, updatedSwitch, layoutContext).then(
                () => {
                    onSave && onSave(existingSwitch.id);
                    onClose();
                    Snackbar.success('switch-dialog.modified-successfully');
                },
                () => Snackbar.error('switch-dialog.modify-failed'),
            );
        }
    }

    const validationIssues = [
        ...validateSwitchName(switchName),
        ...validateSwitchStateCategory(switchStateCategory),
        ...validateSwitchStructureId(switchStructureId),
        ...validateSwitchOwnerId(switchOwnerId),
    ];

    function hasErrors(prop: keyof TrackLayoutSwitchSaveRequest) {
        return getVisibleErrorsByProp(prop).length > 0;
    }

    function getVisibleErrorsByProp(prop: keyof TrackLayoutSwitchSaveRequest) {
        if (visitedFields.includes(prop)) {
            return validationIssues
                .filter((error) => error.field == prop)
                .map(({ reason }) => t(`switch-dialog.${reason}`));
        }
        return [];
    }

    function handleOnDelete() {
        onSave && switchId && onSave(switchId);
        onClose();
    }

    const moveToEditLinkText = (s: LayoutSwitch) => {
        return s.stateCategory === 'NOT_EXISTING'
            ? t('switch-dialog.move-to-edit-deleted')
            : t('switch-dialog.move-to-edit', { name: s.name });
    };

    return (
        <React.Fragment>
            <Dialog
                title={
                    isExistingSwitch ? t('switch-dialog.title-edit') : t('switch-dialog.title-new')
                }
                onClose={onClose}
                footerContent={
                    <React.Fragment>
                        {existingSwitch?.editState === 'CREATED' && isExistingSwitch && (
                            <Button
                                onClick={() => setShowDeleteDraftConfirmDialog(true)}
                                icon={Icons.Delete}
                                variant={ButtonVariant.WARNING}>
                                {t('button.delete-draft')}
                            </Button>
                        )}
                        <div
                            className={
                                existingSwitch?.editState === 'CREATED'
                                    ? dialogStyles['dialog__footer-content--right-aligned']
                                    : dialogStyles['dialog__footer-content--centered']
                            }>
                            <Button variant={ButtonVariant.SECONDARY} onClick={onClose}>
                                {t('button.cancel')}
                            </Button>
                            <Button
                                qa-id="save-switch-changes"
                                disabled={validationIssues.length > 0 || isSaving}
                                isProcessing={isSaving}
                                onClick={saveOrConfirm}
                                title={getSaveDisabledReasons(
                                    validationIssues.map((e) => e.reason),
                                    isSaving,
                                )
                                    .map((reason) => t(`switch-dialog.${reason}`))
                                    .join(', ')}>
                                {t('button.save')}
                            </Button>
                        </div>
                    </React.Fragment>
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
                                    qa-id="switch-name"
                                    value={switchName}
                                    onChange={(e) => updateName(e.target.value)}
                                    hasError={hasErrors('name')}
                                    onBlur={() => visitField('name')}
                                    ref={firstInputRef}
                                    wide
                                />
                            }
                            errors={getVisibleErrorsByProp('name')}>
                            {conflictingSwitch && (
                                <>
                                    <div className={styles['switch-edit-dialog__alert-color']}>
                                        {conflictingSwitch.stateCategory === 'NOT_EXISTING'
                                            ? t('switch-dialog.name-in-use-deleted')
                                            : t('switch-dialog.name-in-use')}
                                    </div>
                                    <Link
                                        className={styles['switch-edit-dialog__alert']}
                                        onClick={() => onEdit(conflictingSwitch.id)}>
                                        {moveToEditLinkText(conflictingSwitch)}
                                    </Link>
                                </>
                            )}
                        </FieldLayout>
                        <FieldLayout
                            label={`${t('switch-dialog.state-category')} *`}
                            value={
                                <Dropdown
                                    qaId="switch-state"
                                    value={switchStateCategory}
                                    options={stateCategoryOptions}
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
                                        qaId: `type-${type.id}`,
                                    }))}
                                    onChange={(value) => value && updateStructureId(value)}
                                    onBlur={() => visitField('switchStructureId')}
                                    hasError={hasErrors('switchStructureId')}
                                    wide
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
                                        qaId: `owner-${o.id}`,
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
                </FormLayout>
            </Dialog>
            {showStructureChangeConfirmationDialog && (
                <Dialog
                    title={t('switch-dialog.confirmation-title')}
                    variant={DialogVariant.DARK}
                    allowClose={false}
                    footerContent={
                        <div className={dialogStyles['dialog__footer-content--centered']}>
                            <Button
                                onClick={() => setShowStructureChangeConfirmationDialog(false)}
                                variant={ButtonVariant.SECONDARY}
                                disabled={isSaving}>
                                {t('button.cancel')}
                            </Button>
                            {isSaving ? (
                                <Spinner />
                            ) : (
                                <Button onClick={save}>{t('button.save')}</Button>
                            )}
                        </div>
                    }>
                    <p>{t('switch-dialog.changed-switch-structure-warning')}</p>
                    <p>
                        <span className={styles['switch-edit-dialog__warning']}>
                            <Icons.StatusError color={IconColor.INHERIT} />
                        </span>{' '}
                        {t('switch-dialog.switch-will-be-unlinked')}{' '}
                        {t('switch-dialog.confirm-switch-save')}
                    </p>
                </Dialog>
            )}
            {showDeleteOfficialConfirmDialog && existingSwitch?.editState === 'CREATED' && (
                <Dialog
                    title={t('switch-dialog.confirmation-save-as-deleted-new')}
                    variant={DialogVariant.DARK}
                    allowClose={false}
                    footerContent={
                        <div className={dialogStyles['dialog__footer-content--centered']}>
                            <Button
                                onClick={() => setShowDeleteOfficialConfirmDialog(false)}
                                variant={ButtonVariant.SECONDARY}
                                disabled={isSaving}>
                                {t('button.cancel')}
                            </Button>
                            {isSaving ? (
                                <Spinner />
                            ) : (
                                <Button variant={ButtonVariant.PRIMARY_WARNING} onClick={save}>
                                    {t('button.delete')}
                                </Button>
                            )}
                        </div>
                    }>
                    <p>{t('switch-dialog.deleted-state-warning-new')}</p>
                    <div>
                        <div className={'dialog__text'}>
                            {t('switch-dialog.confirm-switch-save-deleted-new')}
                        </div>
                    </div>
                </Dialog>
            )}
            {showDeleteOfficialConfirmDialog && existingSwitch?.editState !== 'CREATED' && (
                <Dialog
                    title={t('switch-dialog.confirmation-delete-title')}
                    variant={DialogVariant.DARK}
                    allowClose={false}
                    footerContent={
                        <div className={dialogStyles['dialog__footer-content--centered']}>
                            <Button
                                onClick={() => setShowDeleteOfficialConfirmDialog(false)}
                                variant={ButtonVariant.SECONDARY}
                                disabled={isSaving}>
                                {t('button.cancel')}
                            </Button>
                            {isSaving ? (
                                <Spinner />
                            ) : (
                                <Button variant={ButtonVariant.PRIMARY_WARNING} onClick={save}>
                                    {t('button.delete')}
                                </Button>
                            )}
                        </div>
                    }>
                    <p>{t('switch-dialog.deleted-state-warning')}</p>
                    <div>
                        <div className={styles['switch-edit-dialog__warning']}>
                            <Icons.StatusError color={IconColor.INHERIT} />{' '}
                            {t('switch-dialog.switch-will-be-unlinked')}
                        </div>
                        <div className={'dialog__text'}>
                            {t('switch-dialog.confirm-switch-delete')}
                        </div>
                    </div>
                </Dialog>
            )}
            {showDeleteDraftConfirmDialog && switchId && (
                <SwitchDeleteConfirmationDialog
                    layoutContext={layoutContext}
                    switchId={switchId}
                    onSave={() => {
                        handleOnDelete();
                        onClose();
                    }}
                    onClose={() => setShowDeleteDraftConfirmDialog(false)}
                />
            )}
        </React.Fragment>
    );
};

function validateSwitchName(name: string): FieldValidationIssue<TrackLayoutSwitchSaveRequest>[] {
    const errors: FieldValidationIssue<TrackLayoutSwitchSaveRequest>[] = [];
    if (!name) {
        errors.push({
            field: 'name',
            reason: 'mandatory-field',
            type: FieldValidationIssueType.ERROR,
        });
    }
    if (name.length > 20) {
        errors.push({
            field: 'name',
            reason: 'name-max-limit',
            type: FieldValidationIssueType.ERROR,
        });
    }
    if (!name.match(SWITCH_NAME_REGEX)) {
        errors.push({
            field: 'name',
            reason: 'invalid-name',
            type: FieldValidationIssueType.ERROR,
        });
    }
    return errors;
}

function validateSwitchStateCategory(
    stateCategory?: LayoutStateCategory,
): FieldValidationIssue<TrackLayoutSwitchSaveRequest>[] {
    if (!stateCategory)
        return [
            {
                field: 'stateCategory',
                reason: 'mandatory-field',
                type: FieldValidationIssueType.ERROR,
            },
        ];
    else return [];
}

function validateSwitchStructureId(
    structureId?: SwitchStructureId,
): FieldValidationIssue<TrackLayoutSwitchSaveRequest>[] {
    if (!structureId)
        return [
            {
                field: 'switchStructureId',
                reason: 'mandatory-field',
                type: FieldValidationIssueType.ERROR,
            },
        ];
    else return [];
}

function validateSwitchOwnerId(
    ownerId?: SwitchStructureId,
): FieldValidationIssue<TrackLayoutSwitchSaveRequest>[] {
    if (!ownerId)
        return [
            {
                field: 'ownerId',
                reason: 'mandatory-field',
                type: FieldValidationIssueType.ERROR,
            },
        ];
    else return [];
}
