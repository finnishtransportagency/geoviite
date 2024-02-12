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
import { PublishType } from 'common/common-model';
import { CalculatedChangesView } from './calculated-changes-view';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import {
    PublicationGroup,
    PublicationStage,
    PublishRequestIds,
} from 'publication/publication-model';
import PreviewTable from 'preview/preview-table';
import { updateAllChangeTimes } from 'common/change-time-api';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { PreviewConfirmRevertChangesDialog } from 'preview/preview-confirm-revert-changes-dialog';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
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
import {
    RevertRequest,
    RevertRequestSource,
    RevertRequestType,
} from 'preview/preview-view-revert-request';
import {
    getStagedChanges,
    getUnstagedChanges,
    idsByPublicationGroup,
    previewCandidatesByUser,
    previewChanges,
    publishCandidateIds,
} from 'preview/preview-view-filters';
import {
    countPublicationGroupAmounts,
    countPublishCandidates,
    emptyChanges,
    nonPendingCandidate,
    PreviewCandidates,
    PublicationAssetChangeAmounts,
} from 'preview/preview-view-data';

export type PreviewProps = {
    changeTimes: ChangeTimes;
    selectedPublishCandidateIds: PublishRequestIds;
    showOnlyOwnUnstagedChanges: boolean;
    setShowOnlyOwnUnstagedChanges: (checked: boolean) => void;
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

export type ChangesBeingReverted = {
    requestedRevertChange: { source: RevertRequestSource } & RevertRequest;
    changeIncludingDependencies: PublishRequestIds;
};

const validateDebounced = debounceAsync(
    (request: PublishRequestIds) => validatePublishCandidates(request),
    1000,
);
const getCalculatedChangesDebounced = debounceAsync(
    (request: PublishRequestIds) => getCalculatedChanges(request),
    1000,
);

export const PreviewView: React.FC<PreviewProps> = (props: PreviewProps) => {
    const { t } = useTranslation();

    const revertedCandidatesSoFar = useRef(publishCandidateIds(emptyChanges));
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
        return user && props.showOnlyOwnUnstagedChanges
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
                                        checked={props.showOnlyOwnUnstagedChanges}
                                        onChange={(e) => {
                                            props.setShowOnlyOwnUnstagedChanges(e.target.checked);
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
