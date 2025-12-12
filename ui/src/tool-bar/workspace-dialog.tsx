import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { Dialog } from 'geoviite-design-lib/dialog/dialog';
import { FormLayout, FormLayoutColumn } from 'geoviite-design-lib/form-layout/form-layout';
import { Heading, HeadingSize } from 'vayla-design-lib/heading/heading';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import {
    DatePicker,
    END_OF_CENTURY,
    START_OF_MILLENNIUM,
} from 'vayla-design-lib/datepicker/datepicker';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import styles from './workspace-dialog.scss';
import {
    getLayoutDesigns,
    LayoutDesign,
    LayoutDesignSaveRequest,
} from 'track-layout/layout-design-api';
import { LayoutDesignId } from 'common/common-model';
import { formatISODate } from 'utils/date-utils';
import { getChangeTimes } from 'common/change-time-api';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { isEqualIgnoreCase } from 'utils/string-utils';
import { filterNotEmpty } from 'utils/array-utils';

type WorkspaceDialogProps = {
    nameSuggestion?: string;
    existingDesign?: LayoutDesign;
    onCancel: () => void;
    onSave: (id: LayoutDesignId | undefined, saveRequest: LayoutDesignSaveRequest) => void;
    saving: boolean;
};

const saveRequest = (name: string, estimatedCompletion: Date): LayoutDesignSaveRequest => ({
    name,
    estimatedCompletion: formatISODate(estimatedCompletion),
    designState: 'ACTIVE',
});

export const DESIGN_NAME_REGEX = /^[A-Za-zÄÖÅäöå0-9 \-+_!?.,"'\\/()[\]<>:;&*#€$]*$/g;

function validateDesignName(name: string, committed: boolean): string[] {
    return [
        committed && name.length < 2 ? 'name-too-short' : undefined,
        name.length > 100 ? 'name-too-long' : undefined,
        !name.match(DESIGN_NAME_REGEX) ? 'name-invalid' : undefined,
    ].filter(filterNotEmpty);
}

export const WorkspaceDialog: React.FC<WorkspaceDialogProps> = ({
    nameSuggestion,
    existingDesign,
    onCancel,
    onSave,
    saving,
}) => {
    const { t } = useTranslation();

    const [selectedDate, setSelectedDate] = React.useState<Date | undefined>(
        existingDesign ? new Date(existingDesign?.estimatedCompletion) : undefined,
    );
    const [name, setName] = React.useState<string>(nameSuggestion ?? existingDesign?.name ?? '');
    const [nameCommitted, setNameCommitted] = React.useState<boolean>(false);
    const nameInputRef = React.useRef<HTMLInputElement>(null);

    const [allDesigns, allDesignsFetchStatus] = useLoaderWithStatus(
        () => getLayoutDesigns(false, false, getChangeTimes().layoutDesign),
        [getChangeTimes().layoutDesign],
    );
    const designNameErrors = validateDesignName(name.trim(), nameCommitted);
    if (!nameCommitted && name.trim().length > 1 && designNameErrors.length === 0) {
        setNameCommitted(true);
    }
    const designNameNotUnique =
        allDesignsFetchStatus !== LoaderStatus.Ready ||
        allDesigns?.some(
            (design) =>
                isEqualIgnoreCase(design.name, name.trim()) && design.id !== existingDesign?.id,
        );
    const allErrors = designNameErrors
        .map((e) => t(`workspace-dialog.${e}`))
        .concat(designNameNotUnique ? [t('workspace-dialog.name-not-unique')] : []);

    React.useEffect(() => nameInputRef?.current?.focus(), []);

    return (
        <Dialog
            className={styles['workspace-dialog']}
            title={
                existingDesign ? t('workspace-dialog.title-edit') : t('workspace-dialog.title-new')
            }
            onClose={onCancel}
            footerContent={
                <React.Fragment>
                    <div className={dialogStyles['dialog__footer-content--centered']}>
                        <Button
                            variant={ButtonVariant.SECONDARY}
                            onClick={onCancel}
                            disabled={saving}
                            qa-id={'workspace-dialog-cancel'}>
                            {t('button.cancel')}
                        </Button>
                        <Button
                            disabled={!name || !selectedDate || allErrors.length > 0 || saving}
                            qa-id={'workspace-dialog-save'}
                            isProcessing={saving}
                            onClick={() => {
                                if (name && selectedDate) {
                                    onSave(
                                        existingDesign?.id,
                                        saveRequest(name.trim(), selectedDate),
                                    );
                                }
                            }}>
                            {t('button.save')}
                        </Button>
                    </div>
                </React.Fragment>
            }>
            <FormLayout>
                <FormLayoutColumn>
                    <Heading size={HeadingSize.SUB}>{t('workspace-dialog.basic-info')}</Heading>
                    <FieldLayout
                        label={`${t('workspace-dialog.name')} *`}
                        value={
                            <TextField
                                wide
                                value={name}
                                onChange={(evt) => setName(evt.target.value)}
                                qa-id={'workspace-dialog-name'}
                                hasError={allErrors.length > 0}
                                onBlur={() => setNameCommitted(true)}
                                ref={nameInputRef}
                            />
                        }
                        errors={allErrors}
                    />
                    <FieldLayout
                        label={`${t('workspace-dialog.completion-date')} *`}
                        value={
                            <DatePicker
                                value={selectedDate}
                                onChange={(date) => setSelectedDate(date)}
                                wide={true}
                                minDate={START_OF_MILLENNIUM}
                                maxDate={END_OF_CENTURY}
                                qa-id={'workspace-dialog-date'}
                            />
                        }
                    />
                </FormLayoutColumn>
            </FormLayout>
        </Dialog>
    );
};
