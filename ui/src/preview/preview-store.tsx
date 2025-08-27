import { PayloadAction } from '@reduxjs/toolkit';
import {
    PublicationCandidate,
    PublicationCandidateReference,
    PublicationStage,
} from 'publication/publication-model';
import { asPublicationCandidateReferences } from 'publication/publication-utils';
import { filterByPublicationStage } from 'preview/preview-view-filters';
import { reuseListElements } from 'utils/array-utils';

export type PreviewState = {
    showOnlyOwnUnstagedChanges: boolean;
    stagedPublicationCandidateReferences: PublicationCandidateReference[];
};

export const previewReducers = {
    setShowOnlyOwnUnstagedChanges: function (
        state: PreviewState,
        action: PayloadAction<boolean>,
    ): void {
        state.showOnlyOwnUnstagedChanges = action.payload;
    },

    setStagedPublicationCandidateReferences: function (
        state: PreviewState,
        action: PayloadAction<PublicationCandidate[]>,
    ): void {
        const stagedCandidateReferences = asPublicationCandidateReferences(
            filterByPublicationStage(action.payload, PublicationStage.STAGED),
        );

        state.stagedPublicationCandidateReferences = reuseListElements(
            stagedCandidateReferences,
            state.stagedPublicationCandidateReferences,
            (candidate) => candidate.id,
        );
    },
};
