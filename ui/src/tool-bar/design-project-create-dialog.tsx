import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { Dialog } from 'geoviite-design-lib/dialog/dialog';
import { FormLayout, FormLayoutColumn } from 'geoviite-design-lib/form-layout/form-layout';
import { Heading, HeadingSize } from 'vayla-design-lib/heading/heading';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { DatePicker } from 'vayla-design-lib/datepicker/datepicker';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import styles from './design-project-create-dialog.scss';

type DesignProjectCreateDialogProps = {
    onCancel: () => void;
    onSave: () => void;
};

export const DesignProjectCreateDialog: React.FC<DesignProjectCreateDialogProps> = ({
    onCancel,
    onSave,
}) => {
    const { t } = useTranslation();

    // TODO Add data bindings and such once design projects have a data model
    return (
        <Dialog
            className={styles['design-project-create-dialog']}
            title={t('design-project-dialog.title-new')}
            onClose={onCancel}
            footerContent={
                <React.Fragment>
                    <div className={dialogStyles['dialog__footer-content--centered']}>
                        <Button variant={ButtonVariant.SECONDARY} onClick={onCancel}>
                            {t('button.cancel')}
                        </Button>
                        <Button onClick={onSave}>{t('button.save')}</Button>
                    </div>
                </React.Fragment>
            }>
            <FormLayout>
                <FormLayoutColumn>
                    <Heading size={HeadingSize.SUB}>
                        {t('design-project-dialog.basic-info')}
                    </Heading>
                    <FieldLayout
                        label={`${t('design-project-dialog.name')} *`}
                        value={<TextField wide />}
                    />
                    <FieldLayout
                        label={`${t('design-project-dialog.completion-date')} *`}
                        value={<DatePicker value={new Date()} />}
                    />
                </FormLayoutColumn>
            </FormLayout>
        </Dialog>
    );
};
