import * as React from 'react';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { useTranslation } from 'react-i18next';
import styles from './preview-view.scss';
import { mergeCandidatesToMain, publishPublicationCandidates } from 'publication/publication-api';
import { filterNotEmpty } from 'utils/array-utils';
import {
    updateKmPostChangeTime,
    updateLocationTrackChangeTime,
    updateReferenceLineChangeTime,
    updateSwitchChangeTime,
    updateTrackNumberChangeTime,
} from 'common/change-time-api';
import {
    LayoutValidationIssue,
    LayoutValidationIssueType,
    PublicationCandidate,
    PublicationResult,
    validationIssueIsError,
} from 'publication/publication-model';
import { OnSelectFunction } from 'selection/selection-model';
import { LayoutContext } from 'common/common-model';
import { PreviewPublicationConfirmationDialog } from 'preview/preview-publication-confirmation-dialog';
import { DesignPublicationMode } from 'preview/preview-tool-bar';
import { PreviewMergeToMainConfirmationDialog } from 'preview/preview-merge-to-main-confirmation-dialog';
import { MapDisplayTransitionSide } from 'preview/preview-view';

type PreviewFooterProps = {
    onSelect: OnSelectFunction;
    onPublish: () => void;
    layoutContext: LayoutContext;
    onChangeMapDisplayTransitionSide: (side: MapDisplayTransitionSide) => void;
    mapDisplayTransitionSide: MapDisplayTransitionSide;
    stagedPublicationCandidates: PublicationCandidate[];
    disablePublication: boolean;
    designPublicationMode: DesignPublicationMode;
};

function previewChangesCanBePublished(publishCandidates: PublicationCandidate[]) {
    return publishCandidates.length !== 0;
}

function describe(name: string, value: number | undefined): string | undefined {
    return value !== undefined && value > 0 ? `${name}: ${value}` : undefined;
}

function publishErrors(publishCandidates: PublicationCandidate[]): LayoutValidationIssue[] {
    return publishCandidates.flatMap((candidate) => candidate.issues);
}

function checkStagedCandidateValidation(
    stagedPublicationCandidates: PublicationCandidate[],
    designPublicationMode: DesignPublicationMode,
): boolean {
    const disablesPublication: (issue: LayoutValidationIssueType) => boolean =
        designPublicationMode === 'MERGE_TO_MAIN' ? (t) => t === 'FATAL' : validationIssueIsError;
    return (
        publishErrors(stagedPublicationCandidates)
            .map((error) => error.type)
            .filter(disablesPublication).length === 0
    );
}

export const PreviewFooter: React.FC<PreviewFooterProps> = (props: PreviewFooterProps) => {
    const stagedCandidateValidationOk = checkStagedCandidateValidation(
        props.stagedPublicationCandidates,
        props.designPublicationMode,
    );
    const describeResult = (result: PublicationResult | undefined): string => {
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

    const publishPreviewChanges = previewChangesCanBePublished(props.stagedPublicationCandidates);

    const updateChangeTimes = (result: PublicationResult | undefined) => {
        if (result?.trackNumbers || 0 > 0) updateTrackNumberChangeTime();
        if (result?.kmPosts || 0 > 0) updateKmPostChangeTime();
        if (result?.referenceLines || 0 > 0) updateReferenceLineChangeTime();
        if (result?.locationTracks || 0 > 0) updateLocationTrackChangeTime();
        if (result?.switches || 0 > 0) updateSwitchChangeTime();
    };

    const { t } = useTranslation();
    const [publishConfirmVisible, setPublishConfirmVisible] = React.useState(false);

    const [isPublishing, setPublishing] = React.useState(false);

    const publishWith = (publicationPromise: Promise<PublicationResult>) =>
        publicationPromise
            .then((r) => {
                Snackbar.success('publish.publish-success', describeResult(r));
                updateChangeTimes(r);
                props.onPublish();
            })
            .finally(() => {
                setPublishConfirmVisible(false);
                setPublishing(false);
            });

    const publish = (message: string) => {
        setPublishing(true);
        publishWith(
            publishPublicationCandidates(
                props.layoutContext.branch,
                props.stagedPublicationCandidates,
                message,
            ),
        );
    };

    const mergeToMain = () => {
        const branch = props.layoutContext.branch;
        if (branch === 'MAIN') return;
        setPublishing(true);
        publishWith(mergeCandidatesToMain(branch, props.stagedPublicationCandidates));
    };

    const candidateCount = props.stagedPublicationCandidates.length;

    return (
        <footer className={styles['preview-footer']}>
            <div className={styles['preview-footer__action-buttons']}>
                <Button
                    onClick={() => setPublishConfirmVisible(true)}
                    variant={ButtonVariant.PRIMARY}
                    disabled={
                        candidateCount === 0 ||
                        publishConfirmVisible ||
                        props.disablePublication ||
                        !stagedCandidateValidationOk ||
                        !publishPreviewChanges
                    }>
                    {t('preview-footer.publish-changes')}
                </Button>
            </div>
            {publishConfirmVisible &&
                (props.designPublicationMode === 'PUBLISH_CHANGES' ? (
                    <PreviewPublicationConfirmationDialog
                        isPublishing={isPublishing}
                        onCancel={() => setPublishConfirmVisible(false)}
                        candidateCount={candidateCount}
                        publish={publish}
                    />
                ) : (
                    props.layoutContext.branch !== 'MAIN' && (
                        <PreviewMergeToMainConfirmationDialog
                            designBranch={props.layoutContext.branch}
                            isPublishing={isPublishing}
                            onCancel={() => setPublishConfirmVisible(false)}
                            candidateCount={candidateCount}
                            mergeToMain={mergeToMain}
                        />
                    )
                ))}
        </footer>
    );
};
