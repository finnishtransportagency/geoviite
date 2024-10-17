import * as React from 'react';
import { Dialog } from 'geoviite-design-lib/dialog/dialog';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { FormLayoutColumn } from 'geoviite-design-lib/form-layout/form-layout';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { useTranslation } from 'react-i18next';
import { Project, ProjectId } from 'geometry/geometry-model';
import { debounce } from 'ts-debounce';
import { createProject, getProjects } from 'geometry/geometry-api';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { isEqualWithoutWhitespace } from 'utils/string-utils';
import { useLoader } from 'utils/react-utils';

type NewProjectDialogProps = {
    onClose: () => void;
    onSave: (projectId: ProjectId) => void;
};

export const NewProjectDialog: React.FC<NewProjectDialogProps> = ({ onClose, onSave }) => {
    const { t } = useTranslation();
    const [projectName, setProjectName] = React.useState<string>('');
    const [canSave, setCanSave] = React.useState<boolean>(false);
    const [duplicateName, setDuplicateName] = React.useState<boolean>(false);
    const [saveInProgress, setSaveInProgress] = React.useState<boolean>(false);
    const projects = useLoader(getProjects, []);

    const debouncer = React.useCallback(
        debounce((newName: string) => {
            const existingProject = projects?.find((project) =>
                isEqualWithoutWhitespace(project.name, newName),
            );

            if (existingProject) setDuplicateName(true);
            else if (newName) setCanSave(true);
        }, 300),
        [projects],
    );

    const getErrorMessage = () => (duplicateName ? [t('im-form.duplicate-project-name')] : []);

    const onNameChange = (name: string) => {
        setProjectName(name);
        setCanSave(false);
        setDuplicateName(false);
        debouncer(name);
    };

    const saveProject = () => {
        setSaveInProgress(true);
        createProject({ name: projectName } as Project).then((p) => {
            setCanSave(false);
            setSaveInProgress(false);

            Snackbar.success('im-form.new-project-created');
            onSave(p);
        });
    };

    return (
        <Dialog
            title={t('im-form.new-project-dialog-title')}
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
                        onClick={saveProject}>
                        {t('im-form.dialog-create-button')}
                    </Button>
                </div>
            }>
            <FormLayoutColumn>
                <FieldLayout
                    label={t('im-form.name-field')}
                    value={
                        <TextField
                            value={projectName}
                            maxLength={100}
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

export default NewProjectDialog;
