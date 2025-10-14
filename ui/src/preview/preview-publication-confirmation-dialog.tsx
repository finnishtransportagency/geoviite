import * as React from 'react';
import styles from './preview-view.scss';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { TextArea } from 'vayla-design-lib/text-area/text-area';
import { useTranslation } from 'react-i18next';
import { FieldValidationIssue, FieldValidationIssueType } from 'utils/validation-utils';
import { PublicationDetails } from 'publication/publication-model';
import { filterNotEmpty } from 'utils/array-utils';

export type PreviewPublicationDialogProps = {
    isPublishing: boolean;
    onCancel: () => void;
    candidateCount: number;
    publish: (message: string) => void;
};

const validateMessage = (message: string): FieldValidationIssue<PublicationDetails>[] =>
    [
        message.length > 500
            ? ({
                  field: 'message',
                  type: FieldValidationIssueType.ERROR,
                  reason: 'publish.publish-confirm.message-too-long',
              } satisfies FieldValidationIssue<PublicationDetails>)
            : undefined,
    ].filter(filterNotEmpty);

export const PreviewPublicationConfirmationDialog: React.FC<PreviewPublicationDialogProps> = ({
    isPublishing,
    onCancel,
    candidateCount,
    publish,
}) => {
    const { t } = useTranslation();
    const textAreaRef = React.useRef<HTMLTextAreaElement>(null);

    const [message, setMessage] = React.useState('');
    const messageValidationErrors = validateMessage(message).map((err) => t(err.reason));

    React.useEffect(() => {
        if (textAreaRef.current) {
            textAreaRef.current?.focus();
        }
    }, []);

    return (
        <Dialog
            title={t('publish.publish-confirm.title')}
            variant={DialogVariant.LIGHT}
            allowClose={!isPublishing}
            onClose={onCancel}
            footerContent={
                <div className={dialogStyles['dialog__footer-content--centered']}>
                    <Button
                        onClick={onCancel}
                        disabled={candidateCount === 0 || isPublishing}
                        variant={ButtonVariant.SECONDARY}>
                        {t('publish.publish-confirm.cancel')}
                    </Button>
                    <Button
                        qa-id={'publication-confirm'}
                        disabled={isPublishing || message.length === 0}
                        isProcessing={isPublishing}
                        onClick={() => publish(message)}>
                        {t('publish.publish-confirm.confirm', {
                            candidates: candidateCount,
                        })}
                    </Button>
                </div>
            }>
            <div className={styles['preview-confirm__description']}>
                {t('publish.publish-confirm.description')}
            </div>
            <FieldLayout
                label={`${t('publish.publish-confirm.message')} *`}
                errors={messageValidationErrors}
                value={
                    <TextArea
                        qa-id={'publication-message'}
                        value={message}
                        wide
                        hasError={messageValidationErrors.length > 0}
                        onChange={(e) => setMessage(e.currentTarget.value)}
                        ref={textAreaRef}
                    />
                }
            />
        </Dialog>
    );
};
