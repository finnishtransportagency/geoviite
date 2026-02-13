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
import { LayoutSwitchSaveRequestBase, LayoutSwitchUpdateRequest } from 'linking/linking-model';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import {
    draftLayoutContext,
    LayoutContext,
    officialLayoutContext,
    SwitchOwner,
    SwitchOwnerId,
    SwitchStructure,
    SwitchStructureId,
    TimeStamp,
} from 'common/common-model';
import { getSwitchOwners, getSwitchStructures } from 'common/common-api';
import { layoutStateCategories, switchTrapPoints } from 'utils/enum-localization-utils';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import {
    getSwitch,
    getSwitchesByName,
    getSwitchJointConnections,
    insertSwitch,
    updateSwitch,
} from 'track-layout/layout-switch-api';
import styles from './switch-edit-dialog.scss';
import { useLoader, useLoaderWithStatus } from 'utils/react-utils';
import { getSaveDisabledReasons, useSwitch } from 'track-layout/track-layout-react-utils';
import SwitchRevertConfirmationDialog from './switch-revert-confirmation-dialog';
import SwitchDeleteConfirmationDialog from './switch-delete-confirmation-dialog';
import { first } from 'utils/array-utils';
import { useCommonDataAppSelector, useTrackLayoutAppSelector } from 'store/hooks';
import {
    SwitchDraftOidField,
    validateDraftOid,
} from 'tool-panel/switch/dialog/switch-draft-oid-field';
import { TFunction } from 'i18next';
import { AnchorLink } from 'geoviite-design-lib/link/anchor-link';
import { getLocationTracksForJointConnections } from 'linking/linking-utils';

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
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);
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
            changeTime={changeTimes.layoutSwitch}
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
    changeTime: TimeStamp;
};

export const SwitchEditDialog = ({
    layoutContext,
    switchId,
    prefilledSwitchStructureId,
    onClose,
    onSave,
    onEdit,
    changeTime,
}: SwitchDialogProps) => {
    const { t } = useTranslation();
    const [showStructureChangeConfirmationDialog, setShowStructureChangeConfirmationDialog] =
        React.useState(false);
    const [showDeleteDraftConfirmDialog, setShowDeleteDraftConfirmDialog] = React.useState(false);
    const [showDeleteOfficialConfirmDialog, setShowDeleteOfficialConfirmDialog] =
        React.useState(false);
    const [switchStateCategory, setSwitchStateCategory] =
        React.useState<LayoutStateCategory>('EXISTING');
    const [switchDraftOidExistsInRatko, setSwitchDraftOidExistsInRatko] = React.useState(false);
    const [switchDraftOid, setSwitchDraftOid] = React.useState<string>('');
    const [switchName, setSwitchName] = React.useState<string>('');
    const [trapPoint, setTrapPoint] = React.useState<TrapPoint>(TrapPoint.UNKNOWN);
    const [switchStructureId, setSwitchStructureId] = React.useState<SwitchStructureId | undefined>(
        prefilledSwitchStructureId,
    );
    const [visitedFields, setVisitedFields] = React.useState<string[]>([]);
    const [isSaving, setIsSaving] = React.useState(false);
    const [switchStructures, setSwitchStructures] = React.useState<SwitchStructure[]>([]);
    const [switchOwners, setSwitchOwners] = React.useState<SwitchOwner[]>([]);
    const [switchOwnerId, setSwitchOwnerId] = React.useState<SwitchOwnerId>();
    const [existingSwitch, setExistingSwitch] = React.useState<LayoutSwitch>();
    const [editingOid, setEditingOid] = React.useState(false);
    const firstInputRef = React.useRef<HTMLInputElement>(null);
    const isExistingSwitch = !!switchId;

    const switchStructureChanged =
        isExistingSwitch && switchStructureId !== existingSwitch?.switchStructureId;

    const hasExistingOfficial =
        useSwitch(existingSwitch?.id, officialLayoutContext(layoutContext), changeTime) !==
        undefined;
    const canSetDeleted = isExistingSwitch && hasExistingOfficial;
    const stateCategoryOptions = layoutStateCategories
        .map((s) => (s.value !== 'NOT_EXISTING' || canSetDeleted ? s : { ...s, disabled: true }))
        .map((sc) => ({ ...sc, qaId: sc.value }));

    const conflictingSwitch = useLoader(async () => {
        if (validateSwitchName(switchName).length === 0) {
            const switches = await getSwitchesByName(
                draftLayoutContext(layoutContext),
                switchName.trim(),
            );
            return switches.find((s) => s.id !== existingSwitch?.id);
        } else {
            return undefined;
        }
    }, [switchName, existingSwitch?.id]);

    const [tracks, loaderStatus] = useLoaderWithStatus(
        () =>
            switchId
                ? getSwitchJointConnections(layoutContext, switchId).then((jointConnections) =>
                      getLocationTracksForJointConnections(layoutContext, jointConnections),
                  )
                : Promise.resolve([]),
        [layoutContext.branch, layoutContext.publicationState, switchId],
    );
    const linkedTracks = tracks ?? [];

    React.useEffect(() => {
        if (isExistingSwitch) {
            getSwitch(switchId, draftLayoutContext(layoutContext)).then((s) => {
                if (s) {
                    setExistingSwitch(s);
                    setSwitchStateCategory(s.stateCategory);
                    setSwitchName(s.name);
                    setSwitchStructureId(s.switchStructureId);
                    setTrapPoint(booleanToTrapPoint(s.trapPoint));
                    if (s.draftOid) {
                        setSwitchDraftOid(s.draftOid);
                        setEditingOid(true);
                    }
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
        } else if (isExistingSwitch) {
            updateExistingSwitch(false);
        } else {
            createNewSwitch();
        }
    }

    function createSwitchSaveRequestBase(
        name: string,
        structureId: SwitchStructureId,
        ownerId: SwitchOwnerId,
        stateCategory: LayoutStateCategory,
        draftOid: string | undefined,
    ): LayoutSwitchSaveRequestBase {
        return {
            name: name.trim(),
            switchStructureId: structureId,
            stateCategory: stateCategory,
            ownerId: ownerId,
            trapPoint: trapPointToBoolean(trapPoint),
            draftOid: draftOid,
        };
    }

    function createNewSwitch() {
        if (switchName && switchStateCategory && switchStructureId && switchOwnerId) {
            const switchSaveRequest = createSwitchSaveRequestBase(
                switchName,
                switchStructureId,
                switchOwnerId,
                switchStateCategory,
                editingOid ? switchDraftOid : undefined,
            );

            saveNewSwitch(switchSaveRequest);
        }
    }

    function updateExistingSwitch(removeSwitchLinks: boolean) {
        if (
            switchName &&
            switchStructureId &&
            switchOwnerId &&
            switchStateCategory &&
            existingSwitch
        ) {
            const switchSaveRequest = createSwitchSaveRequestBase(
                switchName,
                switchStructureId,
                switchOwnerId,
                switchStateCategory,
                editingOid ? switchDraftOid : undefined,
            );
            const saveRequestWithLinkRemoveInfo = { ...switchSaveRequest, removeSwitchLinks };

            saveUpdatedSwitch(existingSwitch, saveRequestWithLinkRemoveInfo);
        }
    }

    function saveNewSwitch(newSwitch: LayoutSwitchSaveRequestBase) {
        setIsSaving(true);
        insertSwitch(newSwitch, layoutContext)
            .then(
                (switchId) => {
                    onSave && onSave(switchId);
                    onClose();
                    Snackbar.success('switch-dialog.new-switch-added');
                },
                () => Snackbar.error('switch-dialog.adding-switch-failed'),
            )
            .finally(() => setIsSaving(false));
    }

    function saveUpdatedSwitch(
        existingSwitch: LayoutSwitch,
        updatedSwitch: LayoutSwitchUpdateRequest,
    ) {
        setIsSaving(true);
        updateSwitch(existingSwitch.id, updatedSwitch, layoutContext)
            .then(
                () => {
                    onSave && onSave(existingSwitch.id);
                    onClose();
                    Snackbar.success('switch-dialog.modified-successfully');
                },
                () => Snackbar.error('switch-dialog.modify-failed'),
            )
            .finally(() => setIsSaving(false));
    }

    const validationIssues = [
        ...(editingOid ? validateDraftOid(switchDraftOid) : []),
        ...validateSwitchName(switchName),
        ...validateSwitchStateCategory(switchStateCategory),
        ...validateSwitchStructureId(switchStructureId),
        ...validateSwitchOwnerId(switchOwnerId),
    ];

    function hasErrors(prop: keyof LayoutSwitchSaveRequestBase) {
        return getVisibleErrorsByProp(prop).length > 0;
    }

    function getVisibleErrorsByProp(prop: keyof LayoutSwitchSaveRequestBase) {
        if (visitedFields.includes(prop)) {
            return validationIssues
                .filter((error) => error.field === prop)
                .map(({ reason }) => t(`switch-dialog.${reason}`));
        }
        return [];
    }

    function handleOnDelete() {
        onSave && switchId && onSave(switchId);
        onClose();
    }

    const canSave =
        validationIssues.length === 0 && !isSaving && (!editingOid || switchDraftOidExistsInRatko);

    return (
        <React.Fragment>
            <Dialog
                title={
                    isExistingSwitch ? t('switch-dialog.title-edit') : t('switch-dialog.title-new')
                }
                onClose={onClose}
                footerContent={
                    <React.Fragment>
                        {isExistingSwitch && (
                            <Button
                                disabled={!existingSwitch?.isDraft}
                                onClick={() => setShowDeleteDraftConfirmDialog(true)}
                                variant={ButtonVariant.WARNING}>
                                {t('button.revert-draft')}
                            </Button>
                        )}
                        <div
                            className={
                                isExistingSwitch
                                    ? dialogStyles['dialog__footer-content--right-aligned']
                                    : dialogStyles['dialog__footer-content--centered']
                            }>
                            <Button variant={ButtonVariant.SECONDARY} onClick={onClose}>
                                {t('button.cancel')}
                            </Button>
                            <Button
                                qa-id="save-switch-changes"
                                disabled={!canSave}
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
                        {layoutContext.branch === 'MAIN' && (
                            <SwitchDraftOidField
                                switchId={existingSwitch?.id}
                                changeTime={changeTime}
                                draftOid={switchDraftOid}
                                setDraftOid={setSwitchDraftOid}
                                setDraftOidExistsInRatko={setSwitchDraftOidExistsInRatko}
                                errors={getVisibleErrorsByProp('draftOid')}
                                visitField={() => visitField('draftOid')}
                                isVisited={visitedFields.includes('draftOid')}
                                editingOid={editingOid}
                                setEditingOid={setEditingOid}
                                onEdit={onEdit}
                            />
                        )}
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
                                    <AnchorLink
                                        className={styles['switch-edit-dialog__alert']}
                                        onClick={() => onEdit(conflictingSwitch.id)}>
                                        {moveToEditLinkText(t, conflictingSwitch)}
                                    </AnchorLink>
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
                            label={`${t('switch-dialog.trap-point')} *`}
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
                            label={`${t('switch-dialog.owner')} *`}
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
                    title={t('switch-dialog.structure-change-confirmation-title')}
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
                            <Button
                                disabled={isSaving}
                                isProcessing={isSaving}
                                onClick={() =>
                                    isExistingSwitch
                                        ? updateExistingSwitch(false)
                                        : createNewSwitch()
                                }>
                                {t('button.save')}
                            </Button>
                        </div>
                    }>
                    <p>
                        <span className={styles['switch-edit-dialog__warning']}>
                            <Icons.StatusError color={IconColor.INHERIT} />
                        </span>{' '}
                        {t('switch-dialog.structure-change-unlink')}
                    </p>
                    <p>{t('switch-dialog.confirm-switch-save')}</p>
                </Dialog>
            )}
            {showDeleteOfficialConfirmDialog && switchId && (
                <SwitchDeleteConfirmationDialog
                    linkedLocationTracks={linkedTracks}
                    linkedTracksLoaderStatus={loaderStatus}
                    onConfirm={(deleteSwitchLinking) => updateExistingSwitch(deleteSwitchLinking)}
                    onClose={() => setShowDeleteOfficialConfirmDialog(false)}
                    isSaving={isSaving}
                />
            )}
            {showDeleteDraftConfirmDialog && switchId && (
                <SwitchRevertConfirmationDialog
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

function validateSwitchName(name: string): FieldValidationIssue<LayoutSwitchSaveRequestBase>[] {
    const errors: FieldValidationIssue<LayoutSwitchSaveRequestBase>[] = [];
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
): FieldValidationIssue<LayoutSwitchSaveRequestBase>[] {
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
): FieldValidationIssue<LayoutSwitchSaveRequestBase>[] {
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
): FieldValidationIssue<LayoutSwitchSaveRequestBase>[] {
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

export const moveToEditLinkText = (
    t: TFunction<'translation', undefined>,
    s: { stateCategory: LayoutStateCategory; name: string },
) => {
    return s.stateCategory === 'NOT_EXISTING'
        ? t('switch-dialog.move-to-edit-deleted')
        : t('switch-dialog.move-to-edit', { name: s.name });
};
