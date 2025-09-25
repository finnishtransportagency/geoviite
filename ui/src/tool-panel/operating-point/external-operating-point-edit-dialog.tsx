import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { Dialog } from 'geoviite-design-lib/dialog/dialog';
import { FormLayout, FormLayoutColumn } from 'geoviite-design-lib/form-layout/form-layout';
import { Heading, HeadingSize } from 'vayla-design-lib/heading/heading';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { layoutStates } from 'utils/enum-localization-utils';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';

type ExternalOperatingPointEditDialogProps = {
    onSave: () => void;
    onClose: () => void;
};

export const ExternalOperatingPointEditDialog: React.FC<ExternalOperatingPointEditDialogProps> = ({
    onClose,
    onSave: _s,
}) => {
    const { t } = useTranslation();

    const isNew = true;

    const stateOptions = layoutStates.map((s) =>
        s.value !== 'DELETED' || !isNew ? s : { ...s, disabled: true },
    );

    return (
        <Dialog
            title={
                isNew
                    ? t('operating-point-dialog.title-new')
                    : t('operating-point-dialog.title-edit')
            }
            onClose={onClose}
            footerContent={
                <React.Fragment>
                    {!isNew && (
                        <Button
                            /*disabled={!existingSwitch?.isDraft}
                        onClick={() => setShowDeleteDraftConfirmDialog(true)}*/
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
                        value={<TextField value={'Nimi lol'} wide />}
                    />
                    <FieldLayout
                        label={`${t('operating-point-dialog.abbreviation')} *`}
                        value={<TextField value={'Nimi lol'} wide />}
                    />
                    <FieldLayout
                        label={`${t('operating-point-dialog.type-rinf')} *`}
                        value={<Dropdown value={'Nimi lol'} wide />}
                    />
                    <FieldLayout
                        label={`${t('operating-point-dialog.state')} *`}
                        value={<Dropdown options={stateOptions} value={'Nimi lol'} wide />}
                    />
                    <FieldLayout
                        label={`${t('operating-point-dialog.uic-code')} *`}
                        value={<TextField value={'1337'} wide />}
                    />
                </FormLayoutColumn>
            </FormLayout>
        </Dialog>
    );
};
