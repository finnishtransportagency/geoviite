import * as React from 'react';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Switch } from 'vayla-design-lib/switch/switch';
import { useTranslation } from 'react-i18next';
import styles from './preview-view.scss';
import dialogStyles from '../vayla-design-lib/dialog/dialog.scss';
import { publishCandidates, PublishRequest, PublishResult } from 'publication/publication-api';
import { filterNotEmpty } from 'utils/array-utils';
import {
    updateKmPostChangeTime,
    updateLocationTrackChangeTime,
    updateReferenceLineChangeTime,
    updateSwitchChangeTime,
    updateTrackNumberChangeTime,
} from 'common/change-time-api';
import { Dialog, DialogVariant } from 'vayla-design-lib/dialog/dialog';
import { PublishType } from 'common/common-model';
import { PublishCandidates, PublishValidationError } from 'publication/publication-model';
import { OnSelectFunction } from 'selection/selection-model';
import { PreviewCandidates } from 'preview/preview-view';

type PreviewFooterProps = {
    onSelect: OnSelectFunction;
    request: PublishRequest;
    onClosePreview: () => void;
    mapMode: PublishType;
    onChangeMapMode: (type: PublishType) => void;
    previewChanges: PreviewCandidates;
    onPublishPreviewRevert: () => void;
};

function describe(name: string, value: number | undefined): string | undefined {
    return value !== undefined && value > 0 ? `${name}: ${value}` : undefined;
}

function publishErrors(previewChanges: PublishCandidates): PublishValidationError[] {
    return previewChanges.locationTracks
        .flatMap((l) => l.errors)
        .concat(
            previewChanges.kmPosts.flatMap((k) => k.errors),
            previewChanges.referenceLines.flatMap((r) => r.errors),
            previewChanges.switches.flatMap((s) => s.errors),
            previewChanges.trackNumbers.flatMap((t) => t.errors),
        );
}

export const PreviewFooter: React.FC<PreviewFooterProps> = (props: PreviewFooterProps) => {
    const allPublishErrors =
        props.previewChanges &&
        publishErrors(props.previewChanges).filter((error) => error.type == 'ERROR');
    const describeResult = (result: PublishResult | null): string => {
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

    const updateChangeTimes = (result: PublishResult | null) => {
        if (result?.trackNumbers || 0 > 0) updateTrackNumberChangeTime();
        if (result?.kmPosts || 0 > 0) updateKmPostChangeTime();
        if (result?.referenceLines || 0 > 0) updateReferenceLineChangeTime();
        if (result?.locationTracks || 0 > 0) updateLocationTrackChangeTime();
        if (result?.switches || 0 > 0) updateSwitchChangeTime();
    };

    const { t } = useTranslation();
    const [publishConfirmVisible, setPublishConfirmVisible] = React.useState(false);

    const [isPublishing, setPublishing] = React.useState(false);

    const emptyRequest =
        props.request.trackNumbers.length == 0 &&
        props.request.kmPosts.length == 0 &&
        props.request.referenceLines.length == 0 &&
        props.request.locationTracks.length == 0 &&
        props.request.switches.length == 0;

    const validationPending =
        props.previewChanges.trackNumbers.some((tn) => tn.pendingValidation) ||
        props.previewChanges.referenceLines.some((rl) => rl.pendingValidation) ||
        props.previewChanges.locationTracks.some((lt) => lt.pendingValidation) ||
        props.previewChanges.switches.some((sw) => sw.pendingValidation) ||
        props.previewChanges.kmPosts.some((km) => km.pendingValidation);

    const publish = () => {
        setPublishing(true);
        publishCandidates(props.request)
            .then((r) => {
                if (r.isOk()) {
                    const result = r.unwrapOr(null);
                    Snackbar.success(t('publish.publish-success'), describeResult(result));
                    updateChangeTimes(result);
                    props.onClosePreview();
                }
            })
            .finally(() => {
                setPublishConfirmVisible(false);
                setPublishing(false);
            });
    };

    return (
        <footer className={styles['preview-footer']}>
            <div className={styles['preview-footer__action-buttons']}>
                <Button
                    onClick={() => setPublishConfirmVisible(true)}
                    variant={ButtonVariant.PRIMARY}
                    disabled={
                        emptyRequest ||
                        publishConfirmVisible ||
                        validationPending ||
                        (allPublishErrors && allPublishErrors?.length > 0)
                    }>
                    {t('preview-footer.publish-changes')}
                </Button>
            </div>
            <div className={styles['preview-footer__map-toggle']}>
                <Switch
                    onCheckedChange={(check) => props.onChangeMapMode(check ? 'DRAFT' : 'OFFICIAL')}
                    checked={props.mapMode == 'DRAFT'}>
                    {t('preview-footer.publish-tracklayout')}
                </Switch>
            </div>
            {publishConfirmVisible && (
                <Dialog
                    title={t('publish.publish-confirm.title')}
                    variant={DialogVariant.LIGHT}
                    allowClose={false}
                    className={dialogStyles['dialog--normal']}
                    footerContent={
                        <React.Fragment>
                            <Button
                                onClick={() => setPublishConfirmVisible(false)}
                                disabled={emptyRequest || isPublishing}
                                variant={ButtonVariant.SECONDARY}>
                                {t('publish.publish-confirm.cancel')}
                            </Button>
                            <Button
                                disabled={isPublishing}
                                isProcessing={isPublishing}
                                onClick={publish}>
                                {t('publish.publish-confirm.confirm')}
                            </Button>
                        </React.Fragment>
                    }>
                    <div>{t('publish.publish-confirm.description')}</div>
                </Dialog>
            )}
        </footer>
    );
};
