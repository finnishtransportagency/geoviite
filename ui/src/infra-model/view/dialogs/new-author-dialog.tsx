import * as React from 'react';
import { Dialog } from 'geoviite-design-lib/dialog/dialog';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { FormLayoutColumn } from 'geoviite-design-lib/form-layout/form-layout';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { useTranslation } from 'react-i18next';
import { Author } from 'geometry/geometry-model';
import { debounce } from 'ts-debounce';
import { createAuthor } from 'geometry/geometry-api';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { isEqualWithoutWhitespace } from 'utils/string-utils';

type NewAuthorDialogProps = {
    authors?: Author[];
    onClose: () => void;
    onSave: (author: Author) => void;
};

export const NewAuthorDialog: React.FC<NewAuthorDialogProps> = ({ authors, onClose, onSave }) => {
    const { t } = useTranslation();
    const [authorName, setAuthorName] = React.useState<string>('');
    const [canSave, setCanSave] = React.useState<boolean>(false);
    const [duplicateName, setDuplicateName] = React.useState<boolean>(false);
    const [saveInProgress, setSaveInProgress] = React.useState<boolean>(false);

    const debouncer = React.useCallback(
        debounce((newName: string) => {
            const existingAuthor = authors?.find((author) =>
                isEqualWithoutWhitespace(author.companyName, newName),
            );

            if (existingAuthor) setDuplicateName(true);
            else if (newName) setCanSave(true);
        }, 300),
        [authors],
    );

    const getErrorMessage = () => (duplicateName ? [t('im-form.duplicate-author-name')] : []);

    const onNameChange = (name: string) => {
        setAuthorName(name);
        setCanSave(false);
        setDuplicateName(false);

        debouncer(name);
    };

    const saveAuthor = () => {
        setSaveInProgress(true);
        createAuthor({ companyName: authorName } as Author).then((a) => {
            setCanSave(false);
            setSaveInProgress(false);

            Snackbar.success('im-form.new-author-created');
            onSave(a);
        });
    };

    return (
        <Dialog
            title={t('im-form.new-author-dialog-title')}
            onClose={onClose}
            footerContent={
                <div className={dialogStyles['dialog__footer-content--centered']}>
                    <Button
                        variant={ButtonVariant.SECONDARY}
                        icon={Icons.Delete}
                        disabled={saveInProgress}
                        onClick={onClose}>
                        {t('im-form.dialog-cancel-button')}
                    </Button>
                    <Button
                        disabled={!canSave || saveInProgress}
                        isProcessing={saveInProgress}
                        onClick={saveAuthor}>
                        {t('im-form.dialog-create-button')}
                    </Button>
                </div>
            }>
            <FormLayoutColumn>
                <FieldLayout
                    label={t('im-form.company')}
                    value={
                        <TextField
                            value={authorName}
                            onChange={(e) => onNameChange(e.target.value)}
                            disabled={saveInProgress}
                            hasError={duplicateName}
                            wide
                        />
                    }
                    errors={getErrorMessage()}
                />
            </FormLayoutColumn>
        </Dialog>
    );
};

export default NewAuthorDialog;
