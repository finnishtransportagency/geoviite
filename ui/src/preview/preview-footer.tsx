import * as React from 'react';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Switch } from 'vayla-design-lib/switch/switch';
import { useTranslation } from 'react-i18next';
import styles from './preview-view.scss';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { publishCandidates } from 'publication/publication-api';
import { filterNotEmpty } from 'utils/array-utils';
import {
    updateKmPostChangeTime,
    updateLocationTrackChangeTime,
    updateReferenceLineChangeTime,
    updateSwitchChangeTime,
    updateTrackNumberChangeTime,
} from 'common/change-time-api';
import { Dialog, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import {
    PublishCandidate,
    PublishResult,
    PublishValidationError,
} from 'publication/publication-model';
import { OnSelectFunction } from 'selection/selection-model';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { TextArea } from 'vayla-design-lib/text-area/text-area';
import { draftLayoutContext, LayoutContext, officialLayoutContext } from 'common/common-model';
import { pendingValidations } from 'preview/preview-view-filters';

type PreviewFooterProps = {
    onSelect: OnSelectFunction;
    onPublish: () => void;
    layoutContext: LayoutContext;
    onChangeLayoutContext: (context: LayoutContext) => void;
    stagedPublishCandidates: PublishCandidate[];
};

function previewChangesCanBePublished(publishCandidates: PublishCandidate[]) {
    return publishCandidates.length !== 0;
}

function describe(name: string, value: number | undefined): string | undefined {
    return value !== undefined && value > 0 ? `${name}: ${value}` : undefined;
}

function publishErrors(publishCandidates: PublishCandidate[]): PublishValidationError[] {
    return publishCandidates.flatMap((candidate) => candidate.errors);
}

export const PreviewFooter: React.FC<PreviewFooterProps> = (props: PreviewFooterProps) => {
    const allPublishErrors = publishErrors(props.stagedPublishCandidates).filter(
        (error) => error.type == 'ERROR',
    );
    const describeResult = (result: PublishResult | undefined): string => {
        return [
            describe(t('publish.track-numbers'), result?.trackNumbers),
            describe(t('publish.km-posts'), result?.kmPosts),
            describe(t('publish.reference-lines'), result?.referenceLines),
            describe(t('publish.location-tracks'), result?.locationTracks),
            describe(t('publish.switches'), result?.switches),
        ]
            .filter(filterNotEmpty)
            .join('\n');
    };

    const publishPreviewChanges = previewChangesCanBePublished(props.stagedPublishCandidates);

    const updateChangeTimes = (result: PublishResult | undefined) => {
        if (result?.trackNumbers || 0 > 0) updateTrackNumberChangeTime();
        if (result?.kmPosts || 0 > 0) updateKmPostChangeTime();
        if (result?.referenceLines || 0 > 0) updateReferenceLineChangeTime();
        if (result?.locationTracks || 0 > 0) updateLocationTrackChangeTime();
        if (result?.switches || 0 > 0) updateSwitchChangeTime();
    };

    const { t } = useTranslation();
    const [publishConfirmVisible, setPublishConfirmVisible] = React.useState(false);

    const [isPublishing, setPublishing] = React.useState(false);
    const [message, setMessage] = React.useState('');

    const publish = () => {
        setPublishing(true);
        publishCandidates(props.stagedPublishCandidates, message)
            .then((r) => {
                Snackbar.success('publish.publish-success', describeResult(r));
                updateChangeTimes(r);
                props.onPublish();
            })
            .finally(() => {
                setPublishConfirmVisible(false);
                setPublishing(false);
            });
    };

    const candidateCount = props.stagedPublishCandidates.length;

    return (
        <footer className={styles['preview-footer']}>
            <div className={styles['preview-footer__action-buttons']}>
                <Button
                    onClick={() => setPublishConfirmVisible(true)}
                    variant={ButtonVariant.PRIMARY}
                    disabled={
                        candidateCount === 0 ||
                        publishConfirmVisible ||
                        pendingValidations(props.stagedPublishCandidates) ||
                        (allPublishErrors && allPublishErrors?.length > 0) ||
                        !publishPreviewChanges
                    }>
                    {t('preview-footer.publish-changes')}
                </Button>
            </div>
            <div className={styles['preview-footer__map-toggle']}>
                <Switch
                    onCheckedChange={(check) =>
                        props.onChangeLayoutContext(
                            check
                                ? draftLayoutContext(props.layoutContext)
                                : officialLayoutContext(props.layoutContext),
                        )
                    }
                    checked={props.layoutContext.publicationState === 'DRAFT'}>
                    {t('preview-footer.publish-tracklayout')}
                </Switch>
            </div>
            {publishConfirmVisible && (
                <Dialog
                    title={t('publish.publish-confirm.title')}
                    variant={DialogVariant.LIGHT}
                    allowClose={!isPublishing}
                    onClose={() => setPublishConfirmVisible(false)}
                    footerContent={
                        <div className={dialogStyles['dialog__footer-content--centered']}>
                            <Button
                                onClick={() => setPublishConfirmVisible(false)}
                                disabled={candidateCount === 0 || isPublishing}
                                variant={ButtonVariant.SECONDARY}>
                                {t('publish.publish-confirm.cancel')}
                            </Button>
                            <Button
                                qa-id={'publication-confirm'}
                                disabled={isPublishing || message.length === 0}
                                isProcessing={isPublishing}
                                onClick={publish}>
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
            )}
        </footer>
    );
};
