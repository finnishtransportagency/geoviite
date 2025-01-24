import { PublicationCandidate, PublicationStage } from 'publication/publication-model';
import { filterNotEmpty, filterUniqueById, first } from 'utils/array-utils';
import { expectDefined } from 'utils/type-utils';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { createAreaSelectTool, SelectMode } from 'map/tools/area-select-tool';

export const previewViewAreaSelectTool = (
    publicationCandidates: PublicationCandidate[],
    setStageForChanges: (candidates: PublicationCandidate[], stage: PublicationStage) => void,
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
        const refinedCandidateCollection =
            groups.length === 1
                ? publicationCandidates.filter(
                      (candidate) =>
                          candidate.publicationGroup?.id == expectDefined(first(groups)).id,
                  )
                : selectedCandidates;
        if (
            newStage == PublicationStage.STAGED &&
            groups.length == 1 &&
            selectedCandidates.length !== refinedCandidateCollection.length
        ) {
            Snackbar.info(
                'Valittu kaikki samaan kokonaisuuteen kuuluvat muutokset ' +
                    refinedCandidateCollection.length +
                    ' kpl',
            );
        }

        if (refinedCandidateCollection.length) {
            setStageForChanges(refinedCandidateCollection, newStage);
        }
    });
