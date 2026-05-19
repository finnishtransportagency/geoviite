import { PublishableObjectId } from 'preview/preview-table';
import {
    DraftChangeType,
    PublicationCandidate,
    PublicationGroup,
    PublicationStage,
} from 'publication/publication-model';

export enum RevertRequestType {
    STAGE_CHANGES,
    STAGE_CHANGES_WITH_PARTIAL_SPLITS,
    CHANGES_WITH_DEPENDENCIES,
    PUBLICATION_GROUP,
}

export type RevertRequest =
    | RevertStageChanges
    | RevertStageChangesWithPartialSplits
    | RevertChangesWithDependencies
    | RevertPublicationGroup;

export type RevertRequestSource = {
    id: PublishableObjectId;
    type: DraftChangeType;
    name: string;
};

export type RevertStageChanges = {
    type: RevertRequestType.STAGE_CHANGES;
    amount: number;
    stage: PublicationStage;
};

export type RevertStageChangesWithPartialSplits = {
    type: RevertRequestType.STAGE_CHANGES_WITH_PARTIAL_SPLITS;
    stage: PublicationStage;
    nonSplitCandidates: PublicationCandidate[];
    fullSplitCandidates: PublicationCandidate[];
};

export type RevertChangesWithDependencies = {
    type: RevertRequestType.CHANGES_WITH_DEPENDENCIES;
};

export type RevertPublicationGroup = {
    type: RevertRequestType.PUBLICATION_GROUP;
    amount: number;
    publicationGroup: PublicationGroup;
};
