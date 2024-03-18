import { createListenerMiddleware } from '@reduxjs/toolkit';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import { createDelegates } from 'store/store-utils';
import {
    emptyCalculatedChanges,
    PublicationStage,
    PublishCandidate,
} from 'publication/publication-model';
import { getCalculatedChanges, validatePublishCandidates } from 'publication/publication-api';
import {
    candidateIdAndTypeMatches,
    filterByPublicationStage,
    pendingValidations,
} from 'preview/preview-view-filters';

export const publishCandidateValidationMiddleware = createListenerMiddleware();

publishCandidateValidationMiddleware.startListening({
    actionCreator: trackLayoutActionCreators.setPublishCandidates,
    effect: async (action, listenerApi) => {
        const delegates = createDelegates(trackLayoutActionCreators);
        const storedPublishCandidates = action.payload;

        if (!pendingValidations(storedPublishCandidates)) {
            if (storedPublishCandidates.length === 0) {
                delegates.setCalculatedChanges(emptyCalculatedChanges());
            }

            return;
        }

        // Cancel other running instances of validation, this is now the most up-to-date validation run.
        listenerApi.cancelActiveListeners();
        delegates.setCalculatedChanges(undefined);

        // Don't send the API call immediately in case the requested validation state is being modified fast.
        // If this listener is cancelled during the delay, the API calls won't be made.
        await listenerApi.delay(1000);

        const stagedCandidates = filterByPublicationStage(
            storedPublishCandidates,
            PublicationStage.STAGED,
        );

        const [publishCandidateValidationResponse, calculatedChanges] = await Promise.all([
            validatePublishCandidates(stagedCandidates),
            getCalculatedChanges(stagedCandidates),
        ]);

        // If this listener was aborted, no state updates should be made as there's already a new validation running.
        if (listenerApi.signal.aborted) {
            return;
        }

        const validatedPublishCandidates: PublishCandidate[] = storedPublishCandidates.map(
            (storedCandidate): PublishCandidate => {
                const validationGroup =
                    storedCandidate.stage === PublicationStage.UNSTAGED
                        ? publishCandidateValidationResponse.allChangesValidated
                        : publishCandidateValidationResponse.validatedAsPublicationUnit;

                const validatedCandidate = validationGroup.find((candidate) =>
                    candidateIdAndTypeMatches(candidate, storedCandidate),
                );

                return validatedCandidate
                    ? {
                          ...storedCandidate,
                          errors: validatedCandidate.errors,
                          pendingValidation: false,
                      }
                    : storedCandidate;
            },
        );

        delegates.setPublishCandidates(validatedPublishCandidates);
        delegates.setCalculatedChanges(calculatedChanges);
    },
});
