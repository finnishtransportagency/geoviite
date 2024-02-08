import * as React from 'react';
import { useRef } from 'react';
import styles from './preview-view.scss';
import { useTranslation } from 'react-i18next';
import { useLoader } from 'utils/react-utils';
import {
    getCalculatedChanges,
    getPublishCandidates,
    getRevertRequestDependencies,
    revertCandidates,
    validatePublishCandidates,
} from 'publication/publication-api';

import { PreviewFooter } from 'preview/preview-footer';
import { PreviewToolBar } from 'preview/preview-tool-bar';
import { OnSelectFunction } from 'selection/selection-model';
import { AssetId, PublishType } from 'common/common-model';
import { CalculatedChangesView } from './calculated-changes-view';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import {
    KmPostPublishCandidate,
    LocationTrackPublishCandidate,
    PublicationGroup,
    PublicationGroupId,
    PublicationId,
    PublicationStage,
    PublishCandidate,
    PublishCandidates,
    PublishRequestIds,
    ReferenceLinePublishCandidate,
    SwitchPublishCandidate,
    TrackNumberPublishCandidate,
    WithId,
} from 'publication/publication-model';
import PreviewTable, { PreviewSelectType } from 'preview/preview-table';
import { updateAllChangeTimes } from 'common/change-time-api';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { PreviewConfirmRevertChangesDialog } from 'preview/preview-confirm-revert-changes-dialog';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import { User } from 'user/user-model';
import { getOwnUser } from 'user/user-api';
import { ChangeTimes } from 'common/common-slice';
import { debounceAsync } from 'utils/async-utils';
import { BoundingBox } from 'model/geometry';
import { MapContext } from 'map/map-store';
import { MapViewContainer } from 'map/map-view-container';
import {
    addPublishRequestIds,
    dropIdsFromPublishCandidates,
    intersectPublishRequestIds,
} from 'publication/publication-utils';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

type Candidate = {
    id: AssetId;
};

type PendingValidation = {
    pendingValidation: boolean;
};

type PreviewCandidate = PublishCandidate & PendingValidation;

export type PreviewCandidates = {
    trackNumbers: (TrackNumberPublishCandidate & PendingValidation)[];
    referenceLines: (ReferenceLinePublishCandidate & PendingValidation)[];
    locationTracks: (LocationTrackPublishCandidate & PendingValidation)[];
    switches: (SwitchPublishCandidate & PendingValidation)[];
    kmPosts: (KmPostPublishCandidate & PendingValidation)[];
};

export type RevertRequestSource = {
    id: PublicationId;
    type: PreviewSelectType;
    name: string;
};

export enum RevertRequestType {
    STAGE_CHANGES,
    CHANGES_WITH_DEPENDENCIES,
    PUBLICATION_GROUP,
}

export type RevertRequest =
    | RevertStageChanges
    | RevertChangesWithDependencies
    | RevertPublicationGroup;

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

export type ChangesBeingReverted = {
    requestedRevertChange: { source: RevertRequestSource } & RevertRequest;
    changeIncludingDependencies: PublishRequestIds;
};

export type PreviewProps = {
    changeTimes: ChangeTimes;
    selectedPublishCandidateIds: PublishRequestIds;
    onSelect: OnSelectFunction;
    onPublish: () => void;
    onClosePreview: () => void;
    onPublishPreviewSelect: (selectedChanges: PublishRequestIds) => void;
    onPublishPreviewRemove: (selectedChangesWithDependencies: PublishRequestIds) => void;
    onShowOnMap: (bbox: BoundingBox) => void;
};

export type PreviewOperations = {
    setPublicationStage: {
        forSpecificChanges: (
            publishRequestIds: PublishRequestIds,
            newStage: PublicationStage,
        ) => void;
        forAllStageChanges: (currentStage: PublicationStage, newStage: PublicationStage) => void;
        forPublicationGroup: (
            publicationGroup: PublicationGroup,
            newStage: PublicationStage,
        ) => void;
    };

    revert: {
        stageChanges: (stage: PublicationStage, revertRequestSource: RevertRequestSource) => void;
        changesWithDependencies: (
            publishRequestIds: PublishRequestIds,
            revertRequestSource: RevertRequestSource,
        ) => void;
        publicationGroup: (
            publicationGroup: PublicationGroup,
            revertRequestSource: RevertRequestSource,
        ) => void;
    };
};

const publishCandidateIds = (candidates: PublishCandidates): PublishRequestIds => ({
    trackNumbers: candidates.trackNumbers.map((tn) => tn.id),
    locationTracks: candidates.locationTracks.map((lt) => lt.id),
    referenceLines: candidates.referenceLines.map((rl) => rl.id),
    switches: candidates.switches.map((s) => s.id),
    kmPosts: candidates.kmPosts.map((s) => s.id),
});

const emptyChanges = {
    trackNumbers: [],
    locationTracks: [],
    referenceLines: [],
    switches: [],
    kmPosts: [],
} satisfies PreviewCandidates | PublishRequestIds;

const filterStaged = (stagedIds: AssetId[], candidate: Candidate) =>
    stagedIds.includes(candidate.id);
const filterUnstaged = (stagedIds: AssetId[], candidate: Candidate) =>
    !stagedIds.includes(candidate.id);

const getStagedChanges = (
    publishCandidates: PublishCandidates,
    stagedChangeIds: PublishRequestIds,
): PublishCandidates => ({
    trackNumbers: publishCandidates.trackNumbers.filter((trackNumber) =>
        filterStaged(stagedChangeIds.trackNumbers, trackNumber),
    ),
    locationTracks: publishCandidates.locationTracks.filter((locationTrack) =>
        filterStaged(stagedChangeIds.locationTracks, locationTrack),
    ),
    switches: publishCandidates.switches.filter((layoutSwitch) =>
        filterStaged(stagedChangeIds.switches, layoutSwitch),
    ),
    kmPosts: publishCandidates.kmPosts.filter((kmPost) =>
        filterStaged(stagedChangeIds.kmPosts, kmPost),
    ),
    referenceLines: publishCandidates.referenceLines.filter((referenceLine) =>
        filterStaged(stagedChangeIds.referenceLines, referenceLine),
    ),
});

const getUnstagedChanges = (
    publishCandidates: PublishCandidates,
    stagedChangeIds: PublishRequestIds,
): PublishCandidates => ({
    trackNumbers: publishCandidates.trackNumbers.filter((trackNumber) =>
        filterUnstaged(stagedChangeIds.trackNumbers, trackNumber),
    ),
    locationTracks: publishCandidates.locationTracks.filter((locationTrack) =>
        filterUnstaged(stagedChangeIds.locationTracks, locationTrack),
    ),
    switches: publishCandidates.switches.filter((layoutSwitch) =>
        filterUnstaged(stagedChangeIds.switches, layoutSwitch),
    ),
    kmPosts: publishCandidates.kmPosts.filter((kmPost) =>
        filterUnstaged(stagedChangeIds.kmPosts, kmPost),
    ),
    referenceLines: publishCandidates.referenceLines.filter((referenceLine) =>
        filterUnstaged(stagedChangeIds.referenceLines, referenceLine),
    ),
});

// Validating the change set takes time. After a change is staged, it should be regarded as staged, but pending
// validation until validation is complete
const pendingValidation = (allStaged: AssetId[], allValidated: AssetId[], id: AssetId) =>
    allStaged.includes(id) && !allValidated.includes(id);

const previewChanges = (
    stagedValidatedChanges: PublishCandidates,
    allSelectedChanges: PublishRequestIds,
    entireChangeset: PublishCandidates,
): PreviewCandidates => {
    const validatedIds = publishCandidateIds(stagedValidatedChanges);

    return {
        trackNumbers: [
            ...stagedValidatedChanges.trackNumbers.map(nonPendingCandidate),
            ...entireChangeset.trackNumbers
                .filter((change) =>
                    pendingValidation(
                        allSelectedChanges.trackNumbers,
                        validatedIds.trackNumbers,
                        change.id,
                    ),
                )
                .map(pendingCandidate),
        ],
        referenceLines: [
            ...stagedValidatedChanges.referenceLines.map(nonPendingCandidate),
            ...entireChangeset.referenceLines
                .filter((change) =>
                    pendingValidation(
                        allSelectedChanges.referenceLines,
                        validatedIds.referenceLines,
                        change.id,
                    ),
                )
                .map(pendingCandidate),
        ],
        locationTracks: [
            ...stagedValidatedChanges.locationTracks.map(nonPendingCandidate),
            ...entireChangeset.locationTracks
                .filter((change) =>
                    pendingValidation(
                        allSelectedChanges.locationTracks,
                        validatedIds.locationTracks,
                        change.id,
                    ),
                )
                .map(pendingCandidate),
        ],
        switches: [
            ...stagedValidatedChanges.switches.map(nonPendingCandidate),
            ...entireChangeset.switches
                .filter((change) =>
                    pendingValidation(
                        allSelectedChanges.switches,
                        validatedIds.switches,
                        change.id,
                    ),
                )
                .map(pendingCandidate),
        ],
        kmPosts: [
            ...stagedValidatedChanges.kmPosts.map(nonPendingCandidate),
            ...entireChangeset.kmPosts
                .filter((change) =>
                    pendingValidation(allSelectedChanges.kmPosts, validatedIds.kmPosts, change.id),
                )
                .map(pendingCandidate),
        ],
    };
};

const nonPendingCandidate = <T extends PublishCandidate>(candidate: T) => ({
    ...candidate,
    pendingValidation: false,
});
const pendingCandidate = <T extends PublishCandidate>(candidate: T) => ({
    ...candidate,
    pendingValidation: true,
});

const filterPreviewCandidateArrayByUser = <T extends PreviewCandidate>(
    user: User,
    candidates: T[],
) => candidates.filter((candidate) => candidate.userName === user.details.userName);

const previewCandidatesByUser = (
    user: User,
    previewCandidates: PreviewCandidates,
): PreviewCandidates => ({
    trackNumbers: filterPreviewCandidateArrayByUser(user, previewCandidates.trackNumbers),
    referenceLines: filterPreviewCandidateArrayByUser(user, previewCandidates.referenceLines),
    locationTracks: filterPreviewCandidateArrayByUser(user, previewCandidates.locationTracks),
    switches: filterPreviewCandidateArrayByUser(user, previewCandidates.switches),
    kmPosts: filterPreviewCandidateArrayByUser(user, previewCandidates.kmPosts),
});

const validateDebounced = debounceAsync(
    (request: PublishRequestIds) => validatePublishCandidates(request),
    1000,
);
const getCalculatedChangesDebounced = debounceAsync(
    (request: PublishRequestIds) => getCalculatedChanges(request),
    1000,
);

const filterByPublicationGroup = (
    candidates: (PublishCandidate & WithId)[],
    publicationGroup: PublicationGroup,
) => candidates.filter((candidate) => candidate.publicationGroup?.id === publicationGroup.id);

const assetIdsByPublicationGroup = (
    candidates: (PublishCandidate & WithId)[],
    publicationGroup: PublicationGroup,
) => {
    return filterByPublicationGroup(candidates, publicationGroup).map((candidate) => candidate.id);
};

const idsByPublicationGroup = (
    candidates: PublishCandidates,
    publicationGroup: PublicationGroup,
): PublishRequestIds => ({
    trackNumbers: assetIdsByPublicationGroup(candidates.trackNumbers, publicationGroup),
    referenceLines: assetIdsByPublicationGroup(candidates.referenceLines, publicationGroup),
    locationTracks: assetIdsByPublicationGroup(candidates.locationTracks, publicationGroup),
    switches: assetIdsByPublicationGroup(candidates.switches, publicationGroup),
    kmPosts: assetIdsByPublicationGroup(candidates.kmPosts, publicationGroup),
});

export type PublicationAssetChangeAmounts = {
    total: number;
    staged: number;
    unstaged: number;
    groupAmounts: Record<PublicationGroupId, number>;
    ownUnstaged: number;
};

const countPublishCandidates = (publishCandidates: PublishCandidates | undefined): number => {
    if (!publishCandidates) {
        return 0;
    }

    return Object.values(publishCandidates)
        .filter((maybeAssetArray) => Array.isArray(maybeAssetArray))
        .reduce((amount, assetArray) => amount + assetArray.length, 0);
};

const countPublicationGroupAmounts = (
    changeSet: PublishCandidates | undefined,
): Record<PublicationGroupId, number> => {
    if (!changeSet) {
        return {};
    }

    return Object.values(changeSet)
        .filter((maybeAssetArray) => Array.isArray(maybeAssetArray))
        .flatMap((assetArray) => {
            return assetArray.map((asset) => asset.publicationGroup?.id);
        })
        .filter(
            (publicationGroupId): publicationGroupId is PublicationGroupId => !!publicationGroupId,
        )
        .reduce((groupSizes, publicationGroup) => {
            publicationGroup in groupSizes
                ? (groupSizes[publicationGroup] += 1)
                : (groupSizes[publicationGroup] = 1);

            return groupSizes;
        }, {} as Record<PublicationGroupId, number>);
};

export const PreviewView: React.FC<PreviewProps> = (props: PreviewProps) => {
    const { t } = useTranslation();

    const revertedCandidatesSoFar = useRef(publishCandidateIds(emptyChanges));

    const [onlyShowMine, setOnlyShowMine] = React.useState(false);
    const user = useLoader(getOwnUser, []);

    const entireChangeset = useLoader(() => getPublishCandidates(), [props.changeTimes]);
    const validatedChangeset = useLoader(
        () =>
            validateDebounced(props.selectedPublishCandidateIds).then((validated) => {
                if (validated !== undefined) {
                    // forget any reversions that the backend acknowledged, in case the user is working on multiple tabs
                    // and might re-draft an object after reverting it
                    revertedCandidatesSoFar.current = intersectPublishRequestIds(
                        revertedCandidatesSoFar.current,
                        publishCandidateIds(validated.allChangesValidated),
                    );
                }
                return validated;
            }),
        [props.selectedPublishCandidateIds],
    );
    const unstagedChanges = validatedChangeset
        ? getUnstagedChanges(
              dropIdsFromPublishCandidates(
                  validatedChangeset.allChangesValidated,
                  revertedCandidatesSoFar.current,
              ),
              props.selectedPublishCandidateIds,
          )
        : undefined;
    const stagedChangesValidated = validatedChangeset
        ? getStagedChanges(
              dropIdsFromPublishCandidates(
                  validatedChangeset.validatedAsPublicationUnit,
                  revertedCandidatesSoFar.current,
              ),
              props.selectedPublishCandidateIds,
          )
        : undefined;

    const unstagedPreviewChanges = ((): PreviewCandidates => {
        const allUnstagedChangesValidated = unstagedChanges
            ? {
                  trackNumbers: unstagedChanges.trackNumbers.map(nonPendingCandidate),
                  referenceLines: unstagedChanges.referenceLines.map(nonPendingCandidate),
                  locationTracks: unstagedChanges.locationTracks.map(nonPendingCandidate),
                  switches: unstagedChanges.switches.map(nonPendingCandidate),
                  kmPosts: unstagedChanges.kmPosts.map(nonPendingCandidate),
              }
            : emptyChanges;
        return user && onlyShowMine
            ? previewCandidatesByUser(user, allUnstagedChangesValidated)
            : allUnstagedChangesValidated;
    })();

    const stagedPreviewChanges: PreviewCandidates =
        stagedChangesValidated && entireChangeset
            ? previewChanges(
                  stagedChangesValidated,
                  props.selectedPublishCandidateIds,
                  entireChangeset,
              )
            : emptyChanges;

    const calculatedChanges = useLoader(
        () => getCalculatedChangesDebounced(props.selectedPublishCandidateIds),
        [props.selectedPublishCandidateIds],
    );
    const [mapMode, setMapMode] = React.useState<PublishType>('DRAFT');
    const [changesBeingReverted, setChangesBeingReverted] = React.useState<ChangesBeingReverted>();

    const publicationAssetChangeAmounts: PublicationAssetChangeAmounts = {
        total: countPublishCandidates(entireChangeset),
        staged: countPublishCandidates(stagedPreviewChanges),
        unstaged: countPublishCandidates(unstagedPreviewChanges),
        groupAmounts: countPublicationGroupAmounts(entireChangeset),
        ownUnstaged:
            user && entireChangeset
                ? countPublishCandidates(previewCandidatesByUser(user, unstagedPreviewChanges))
                : 0,
    };

    const onConfirmRevert = () => {
        if (changesBeingReverted === undefined) {
            return;
        }

        revertCandidates(changesBeingReverted.changeIncludingDependencies)
            .then((r) => {
                if (r.isOk()) {
                    Snackbar.success('publish.revert-success');
                    revertedCandidatesSoFar.current = addPublishRequestIds(
                        revertedCandidatesSoFar.current,
                        changesBeingReverted.changeIncludingDependencies,
                    );
                    props.onPublishPreviewRemove(changesBeingReverted.changeIncludingDependencies);
                }
            })
            .finally(() => {
                setChangesBeingReverted(undefined);
                void updateAllChangeTimes();
            });
    };

    const setStageForSpecificChanges = (
        publishRequestIds: PublishRequestIds,
        publicationStage: PublicationStage,
    ) => {
        switch (publicationStage) {
            case PublicationStage.STAGED:
                return props.onPublishPreviewSelect(publishRequestIds);

            case PublicationStage.UNSTAGED:
                return props.onPublishPreviewRemove(publishRequestIds);

            default:
                exhaustiveMatchingGuard(publicationStage);
        }
    };

    const setPublicationGroupStage = (
        publicationGroup: PublicationGroup,
        stage: PublicationStage,
    ) => {
        if (!entireChangeset) {
            return;
        }

        const groupedChanges = idsByPublicationGroup(entireChangeset, publicationGroup);
        setStageForSpecificChanges(groupedChanges, stage);
    };

    const revertChangesWithDependencies = (
        publishRequestIds: PublishRequestIds,
        revertRequestSource: RevertRequestSource,
    ) => {
        getRevertRequestDependencies(publishRequestIds).then((changeIncludingDependencies) => {
            setChangesBeingReverted({
                requestedRevertChange: {
                    type: RevertRequestType.CHANGES_WITH_DEPENDENCIES,
                    source: revertRequestSource,
                },
                changeIncludingDependencies,
            });
        });
    };

    const getStageCandidates = (stage: PublicationStage): [PreviewCandidates, number] => {
        switch (stage) {
            case PublicationStage.UNSTAGED:
                return [unstagedPreviewChanges, publicationAssetChangeAmounts.unstaged];

            case PublicationStage.STAGED:
                return [stagedPreviewChanges, publicationAssetChangeAmounts.staged];

            default: {
                return exhaustiveMatchingGuard(stage);
            }
        }
    };

    const revertStageChanges = (
        stage: PublicationStage,
        revertRequestSource: RevertRequestSource,
    ) => {
        const [stageCandidates, amountOfCandidates] = getStageCandidates(stage);
        if (!stageCandidates) {
            return;
        }

        setChangesBeingReverted({
            requestedRevertChange: {
                type: RevertRequestType.STAGE_CHANGES,
                source: revertRequestSource,
                amount: amountOfCandidates,
                stage: stage,
            },
            changeIncludingDependencies: publishCandidateIds(stageCandidates),
        });
    };

    const revertPublicationGroup = (
        publicationGroup: PublicationGroup,
        revertRequestSource: RevertRequestSource,
    ) => {
        if (!entireChangeset) {
            return;
        }

        setChangesBeingReverted({
            requestedRevertChange: {
                type: RevertRequestType.PUBLICATION_GROUP,
                source: revertRequestSource,
                amount: publicationAssetChangeAmounts.groupAmounts[publicationGroup.id],
                publicationGroup: publicationGroup,
            },
            changeIncludingDependencies: idsByPublicationGroup(entireChangeset, publicationGroup),
        });
    };

    const setNewStageForStageChanges = (
        currentStage: PublicationStage,
        newStage: PublicationStage,
    ) => {
        switch (currentStage) {
            case PublicationStage.STAGED:
                return setStageForSpecificChanges(
                    publishCandidateIds(stagedPreviewChanges),
                    newStage,
                );

            case PublicationStage.UNSTAGED:
                return setStageForSpecificChanges(
                    publishCandidateIds(unstagedPreviewChanges),
                    newStage,
                );

            default:
                exhaustiveMatchingGuard(currentStage);
        }
    };

    const previewOperations: PreviewOperations = {
        setPublicationStage: {
            forSpecificChanges: setStageForSpecificChanges,
            forAllStageChanges: setNewStageForStageChanges,
            forPublicationGroup: setPublicationGroupStage,
        },

        revert: {
            stageChanges: revertStageChanges,
            changesWithDependencies: revertChangesWithDependencies,
            publicationGroup: revertPublicationGroup,
        },
    };

    return (
        <React.Fragment>
            <div className={styles['preview-view']} qa-id="preview-content">
                <PreviewToolBar onClosePreview={props.onClosePreview} />
                <div className={styles['preview-view__changes']}>
                    {(unstagedChanges && stagedChangesValidated && (
                        <>
                            <section
                                qa-id={'unstaged-changes'}
                                className={styles['preview-section']}>
                                <div className={styles['preview-view__changes-title']}>
                                    <h3>
                                        {t('preview-view.unstaged-changes-title', {
                                            amount: publicationAssetChangeAmounts.unstaged,
                                        })}
                                    </h3>
                                    <Checkbox
                                        checked={onlyShowMine}
                                        onChange={(e) => {
                                            setOnlyShowMine(e.target.checked);
                                        }}>
                                        {t('preview-view.show-only-mine', {
                                            amount: publicationAssetChangeAmounts.ownUnstaged,
                                        })}
                                    </Checkbox>
                                </div>
                                <PreviewTable
                                    changesBeingReverted={changesBeingReverted}
                                    previewChanges={unstagedPreviewChanges}
                                    staged={false}
                                    onShowOnMap={props.onShowOnMap}
                                    changeTimes={props.changeTimes}
                                    publicationAssetChangeAmounts={publicationAssetChangeAmounts}
                                    previewOperations={previewOperations}
                                />
                            </section>

                            <section qa-id={'staged-changes'} className={styles['preview-section']}>
                                <div className={styles['preview-view__changes-title']}>
                                    <h3>
                                        {t('preview-view.staged-changes-title', {
                                            amount: publicationAssetChangeAmounts.staged,
                                        })}
                                    </h3>
                                </div>
                                <PreviewTable
                                    changesBeingReverted={changesBeingReverted}
                                    previewChanges={stagedPreviewChanges}
                                    staged={true}
                                    onShowOnMap={props.onShowOnMap}
                                    changeTimes={props.changeTimes}
                                    publicationAssetChangeAmounts={publicationAssetChangeAmounts}
                                    previewOperations={previewOperations}
                                />
                            </section>

                            <div className={styles['preview-section']}>
                                {calculatedChanges && (
                                    <CalculatedChangesView calculatedChanges={calculatedChanges} />
                                )}
                                {!calculatedChanges && <Spinner />}
                            </div>
                        </>
                    )) || (
                        <div className={styles['preview-section__spinner-container']}>
                            <Spinner />
                        </div>
                    )}
                </div>
                <MapContext.Provider value="track-layout">
                    <MapViewContainer publishType={mapMode} />
                </MapContext.Provider>

                <PreviewFooter
                    onSelect={props.onSelect}
                    request={
                        stagedChangesValidated
                            ? publishCandidateIds(stagedChangesValidated)
                            : emptyChanges
                    }
                    onPublish={props.onPublish}
                    mapMode={mapMode}
                    onChangeMapMode={setMapMode}
                    previewChanges={stagedPreviewChanges ? stagedPreviewChanges : emptyChanges}
                />
            </div>
            {changesBeingReverted !== undefined && (
                <PreviewConfirmRevertChangesDialog
                    changesBeingReverted={changesBeingReverted}
                    cancelRevertChanges={() => {
                        setChangesBeingReverted(undefined);
                    }}
                    confirmRevertChanges={onConfirmRevert}
                />
            )}
        </React.Fragment>
    );
};
