import { PublicationGroup, PublicationId, PublicationStage } from 'publication/publication-model';
import { PreviewSelectType } from 'preview/preview-table';

export enum RevertRequestType {
    STAGE_CHANGES,
    CHANGES_WITH_DEPENDENCIES,
    PUBLICATION_GROUP,
}

export type RevertRequest =
    | RevertStageChanges
    | RevertChangesWithDependencies
    | RevertPublicationGroup;

export type RevertRequestSource = {
    id: PublicationId;
    type: PreviewSelectType;
    name: string;
};

export type RevertStageChanges = {
    type: RevertRequestType.STAGE_CHANGES;
    amount: number;
    stage: PublicationStage;
};

export type RevertChangesWithDependencies = {
    type: RevertRequestType.CHANGES_WITH_DEPENDENCIES;
};

export type RevertPublicationGroup = {
    type: RevertRequestType.PUBLICATION_GROUP;
    amount: number;
    publicationGroup: PublicationGroup;
};
