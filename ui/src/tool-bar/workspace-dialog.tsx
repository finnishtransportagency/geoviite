import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { Dialog } from 'geoviite-design-lib/dialog/dialog';
import { FormLayout, FormLayoutColumn } from 'geoviite-design-lib/form-layout/form-layout';
import { Heading, HeadingSize } from 'vayla-design-lib/heading/heading';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import {
    DatePicker,
    END_OF_DECAMILLENNIUM,
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

type WorkspaceDialogProps = {
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

export const WorkspaceDialog: React.FC<WorkspaceDialogProps> = ({
    existingDesign,
    onCancel,
    onSave,
    saving,
}) => {
    const { t } = useTranslation();

    const [selectedDate, setSelectedDate] = React.useState<Date | undefined>(
        existingDesign ? new Date(existingDesign?.estimatedCompletion) : undefined,
    );
    const [name, setName] = React.useState<string>(existingDesign?.name ?? '');

    const [allDesigns, allDesignsFetchStatus] = useLoaderWithStatus(
        () => getLayoutDesigns(getChangeTimes().layoutDesign),
        [getChangeTimes().layoutDesign],
    );
    const designNameNotUnique =
        allDesignsFetchStatus !== LoaderStatus.Ready ||
        allDesigns?.some(
            (design) => isEqualIgnoreCase(design.name, name) && design.id !== existingDesign?.id,
        );

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
                            disabled={!name || !selectedDate || designNameNotUnique || saving}
                            qa-id={'workspace-dialog-save'}
                            isProcessing={saving}
                            onClick={() => {
                                if (name && selectedDate) {
                                    onSave(existingDesign?.id, saveRequest(name, selectedDate));
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
                            />
                        }
                        errors={designNameNotUnique ? [t('workspace-dialog.name-not-unique')] : []}
                    />
                    <FieldLayout
                        label={`${t('workspace-dialog.completion-date')} *`}
                        value={
                            <DatePicker
                                value={selectedDate}
                                onChange={(date) => setSelectedDate(date)}
                                wide={true}
                                minDate={START_OF_MILLENNIUM}
                                maxDate={END_OF_DECAMILLENNIUM}
                                qa-id={'workspace-dialog-date'}
                            />
                        }
                    />
                </FormLayoutColumn>
            </FormLayout>
        </Dialog>
    );
};
