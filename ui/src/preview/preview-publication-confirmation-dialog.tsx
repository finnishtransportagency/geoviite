import * as React from 'react';
import styles from './preview-view.scss';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { TextArea } from 'vayla-design-lib/text-area/text-area';
import { useTranslation } from 'react-i18next';

export type PreviewPublicationDialogProps = {
    isPublishing: boolean;
    onCancel: () => void;
    candidateCount: number;
    publish: (message: string) => void;
};

export const PreviewPublicationConfirmationDialog: React.FC<PreviewPublicationDialogProps> = ({
    isPublishing,
    onCancel,
    candidateCount,
    publish,
}) => {
    const { t } = useTranslation();
    const [message, setMessage] = React.useState('');

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
                value={
                    <TextArea
                        qa-id={'publication-message'}
                        value={message}
                        wide
                        onChange={(e) => setMessage(e.currentTarget.value)}
                    />
                }
            />
        </Dialog>
    );
};
