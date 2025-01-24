import { PublicationCandidate, PublicationStage } from 'publication/publication-model';
import { filterNotEmpty, filterUnique, filterUniqueById } from 'utils/array-utils';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { createAreaSelectTool, SelectMode } from 'map/tools/area-select-tool';

export const previewViewAreaSelectTool = (
    publicationCandidates: PublicationCandidate[],
    setStageForChanges: (candidates: PublicationCandidate[], stage: PublicationStage) => void,
    t: (key: string, params?: Record<string, unknown>) => string,
) =>
    createAreaSelectTool((items, mode) => {
        const newStage =
            mode === SelectMode.Add ? PublicationStage.STAGED : PublicationStage.UNSTAGED;

        const selectedCandidates = [
            ...(items.locationTrackPublicationCandidates || []),
            ...(items.referenceLinePublicationCandidates || []),
            ...(items.trackNumberPublicationCandidates || []),
            ...(items.switchPublicationCandidates || []),
            ...(items.kmPostPublicationCandidates || []),
        ].flat();

        const groups = selectedCandidates
            .map((candidate) => candidate.publicationGroup)
            .filter(filterNotEmpty)
            .filter(filterUniqueById((group) => group.id));

        const allCandidatesFromSelectedGroups = publicationCandidates.filter((candidate) =>
            groups.some((group) => candidate.publicationGroup?.id === group.id),
        );
        const refinedCandidateCollection = [
            ...selectedCandidates,
            ...allCandidatesFromSelectedGroups,
        ].filter(filterUnique);

        if (groups.length > 0) {
            Snackbar.info(
                t('preview-view.publication-group-selected', {
                    groupAmount: groups.length,
                    amount: refinedCandidateCollection.length,
                }),
            );
        }

        if (refinedCandidateCollection.length) {
            setStageForChanges(refinedCandidateCollection, newStage);
        }
    });
