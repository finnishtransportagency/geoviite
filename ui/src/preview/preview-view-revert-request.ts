import { PublishableObjectId } from 'preview/preview-table';
import { DraftChangeType, PublicationGroup, PublicationStage } from 'publication/publication-model';

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
    id: PublishableObjectId;
    type: DraftChangeType;
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
