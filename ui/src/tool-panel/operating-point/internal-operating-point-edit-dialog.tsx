import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { Dialog } from 'geoviite-design-lib/dialog/dialog';
import { FormLayout, FormLayoutColumn } from 'geoviite-design-lib/form-layout/form-layout';
import { Heading, HeadingSize } from 'vayla-design-lib/heading/heading';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { OperatingPointDeleteDraftConfirmDialog } from 'tool-panel/operating-point/operating-point-delete-draft-confirm-dialog';

type InternalOperatingPointEditDialogProps = {
    onSave: () => void;
    onClose: () => void;
};

export const InternalOperatingPointEditDialog: React.FC<InternalOperatingPointEditDialogProps> = ({
    onClose,
    onSave: _s,
}) => {
    const { t } = useTranslation();
    const [deleteDraftConfirmDialogOpen, setShowDeleteDraftConfirmDialog] = React.useState(false);

    return (
        <React.Fragment>
            <Dialog
                title={t('operating-point-dialog.title-edit')}
                onClose={onClose}
                footerContent={
                    <React.Fragment>
                        <Button
                            /*disabled={!existingSwitch?.isDraft}*/
                            onClick={() => setShowDeleteDraftConfirmDialog(true)}
                            variant={ButtonVariant.WARNING}>
                            {t('button.revert-draft')}
                        </Button>

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
                            value={'Nimi lol'}
                        />
                        <FieldLayout
                            label={`${t('operating-point-dialog.abbreviation')} *`}
                            value={'NL'}
                        />
                        <FieldLayout
                            label={`${t('operating-point-dialog.type-raide')} *`}
                            value={'Liikennepaikka (LP)'}
                        />
                        <FieldLayout
                            label={`${t('operating-point-dialog.type-rinf')} *`}
                            value={<Dropdown value={'Pieni asema (koodi 20)'} wide />}
                        />
                        <FieldLayout
                            label={`${t('operating-point-dialog.state')} *`}
                            value={'Nimi lol'}
                        />
                        <FieldLayout
                            label={`${t('operating-point-dialog.uic-code')} *`}
                            value={'1337'}
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
