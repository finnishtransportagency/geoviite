import * as React from 'react';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import styles from './preview-view.scss';
import { useTranslation } from 'react-i18next';
import { useLoader, useTwoPartEffectWithStatus } from 'utils/react-utils';
import {
    getCalculatedChanges,
    getPublicationCandidates,
    getRevertRequestDependencies,
    revertPublicationCandidates,
    validateMergeToMain,
    validatePublicationCandidates,
} from 'publication/publication-api';
import { DesignPublicationMode, PreviewToolBar } from 'preview/preview-tool-bar';
import { OnSelectFunction } from 'selection/selection-model';
import { CalculatedChangesView } from './calculated-changes-view';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import {
    emptyValidatedPublicationCandidates,
    PublicationCandidate,
    PublicationCandidateReference,
    PublicationGroup,
    PublicationStage,
    PublicationValidationState,
} from 'publication/publication-model';
import PreviewTable from 'preview/preview-table';
import { PreviewConfirmRevertChangesDialog } from 'preview/preview-confirm-revert-changes-dialog';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import { getOwnUser } from 'user/user-api';
import { ChangeTimes } from 'common/common-slice';
import { BoundingBox } from 'model/geometry';
import { MapContext } from 'map/map-store';
import { MapViewContainer } from 'map/map-view-container';
import {
    addValidationState,
    conditionallyUpdateCandidates,
    countPublicationGroupAmounts,
    noCalculatedChanges,
    PublicationAssetChangeAmounts,
    setValidationStateToApiError,
    stageTransform,
} from 'publication/publication-utils';
import {
    RevertRequest,
    RevertRequestSource,
    RevertRequestType,
} from 'preview/preview-view-revert-request';
import { updateAllChangeTimes } from 'common/change-time-api';
import { PreviewFooter } from 'preview/preview-footer';
import {
    candidateIdAndTypeMatches,
    filterByPublicationGroup,
    filterByPublicationStage,
    filterByUser,
} from 'preview/preview-view-filters';
import {
    draftLayoutContext,
    draftMainLayoutContext,
    LayoutBranch,
    LayoutContext,
    officialLayoutContext,
} from 'common/common-model';
import { debounceAsync } from 'utils/async-utils';
import { DesignDraftsExistError } from 'preview/preview-view-design-drafts-exist-error';
import { createClassName } from 'vayla-design-lib/utils';
import { createAreaSelectTool, SelectMode } from 'map/tools/area-select-tool';

export type PreviewProps = {
    layoutContext: LayoutContext;
    changeTimes: ChangeTimes;
    stagedPublicationCandidateReferences: PublicationCandidateReference[];
    setStagedPublicationCandidateReferences: (publicationCandidate: PublicationCandidate[]) => void;
    showOnlyOwnUnstagedChanges: boolean;
    setShowOnlyOwnUnstagedChanges: (checked: boolean) => void;
    onSelect: OnSelectFunction;
    onPublish: () => void;
    onClosePreview: () => void;
    onShowOnMap: (bbox: BoundingBox) => void;
};

export type PreviewOperations = {
    setPublicationStage: {
        forSpecificChanges: (
            publicationCandidatesToBeUpdated: PublicationCandidate[],
            newStage: PublicationStage,
        ) => void;
        forAllShownChanges: (currentStage: PublicationStage, newStage: PublicationStage) => void;
        forPublicationGroup: (
            publicationGroup: PublicationGroup,
            newStage: PublicationStage,
        ) => void;
    };

    revert: {
        stageChanges: (stage: PublicationStage, revertRequestSource: RevertRequestSource) => void;
        changesWithDependencies: (
            publicationCandidates: PublicationCandidate[],
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
    changeIncludingDependencies: PublicationCandidateReference[];
};

export type MapDisplayTransitionSide = 'BASE_CONTEXT' | 'WITH_CHANGES';

const validateDebounced = debounceAsync(
    (
        designPublicationMode: DesignPublicationMode,
        layoutBranch: LayoutBranch,
        candidates: PublicationCandidateReference[],
    ) =>
        designPublicationMode === 'MERGE_TO_MAIN'
            ? validateMergeToMain(layoutBranch, candidates)
            : validatePublicationCandidates(layoutBranch, candidates),
    1000,
);

const getCalculatedChangesDebounced = debounceAsync(
    (layoutBranch: LayoutBranch, candidates: PublicationCandidateReference[]) =>
        getCalculatedChanges(layoutBranch, candidates),
    1000,
);

export const PreviewView: React.FC<PreviewProps> = (props: PreviewProps) => {
    const { t } = useTranslation();
    const user = useLoader(getOwnUser, []);

    const [showPreview, setShowPreview] = React.useState<boolean>(true);

    const latestValidationIdRef = React.useRef<number>(0);
    const [publicationValidationState, setPublicationValidationState] =
        React.useState<PublicationValidationState>('IN_PROGRESS');

    const [publicationCandidates, setPublicationCandidates] = React.useState<
        PublicationCandidate[]
    >([]);

    const [designPublicationMode, setDesignPublicationMode] =
        React.useState<DesignPublicationMode>('PUBLISH_CHANGES');

    const showCalculatedChanges = props.layoutContext.branch == 'MAIN';

    const onChangeDesignPublicationMode = (newMode: DesignPublicationMode) => {
        setDesignPublicationMode(newMode);
    };

    const canRevertChanges =
        props.layoutContext.branch === 'MAIN' || designPublicationMode === 'PUBLISH_CHANGES';

    const [mapDisplayTransitionSide, setMapDisplayTransitionSide] =
        React.useState<MapDisplayTransitionSide>('WITH_CHANGES');

    const hasDesignDraftsInDesign =
        useLoader(
            () =>
                props.layoutContext.branch === 'MAIN' || designPublicationMode === 'PUBLISH_CHANGES'
                    ? Promise.resolve(false)
                    : getPublicationCandidates(props.layoutContext.branch, 'DRAFT').then(
                          (candidates) => candidates.length > 0,
                      ),
            [props.changeTimes, props.layoutContext.branch, designPublicationMode],
        ) ?? false;

    useTwoPartEffectWithStatus(
        () =>
            getPublicationCandidates(
                props.layoutContext.branch,
                designPublicationMode === 'PUBLISH_CHANGES' ? 'DRAFT' : 'OFFICIAL',
            ),
        (candidates) => {
            const candidatesWithUpdatedStage = candidates.map((candidate) => {
                const candidateIsStaged = props.stagedPublicationCandidateReferences.some(
                    (stagedCandidate) => candidateIdAndTypeMatches(stagedCandidate, candidate),
                );

                return candidateIsStaged
                    ? {
                          ...candidate,
                          stage: PublicationStage.STAGED,
                      }
                    : candidate;
            });

            setPublicationCandidates(candidatesWithUpdatedStage);
            props.setStagedPublicationCandidateReferences(candidatesWithUpdatedStage);

            setShowPreview(true);
        },
        [props.changeTimes, props.layoutContext.branch, designPublicationMode],
    );

    const validatedPublicationCandidates =
        useLoader(() => {
            const validationId = latestValidationIdRef.current + 1;
            latestValidationIdRef.current = validationId;
            setPublicationValidationState('IN_PROGRESS');

            return validateDebounced(
                designPublicationMode,
                props.layoutContext.branch,
                props.stagedPublicationCandidateReferences,
            )
                .then((result) => {
                    if (latestValidationIdRef.current === validationId) {
                        setPublicationValidationState('API_CALL_OK');
                    }

                    return result;
                })
                .catch((_err) => {
                    setPublicationValidationState('API_CALL_ERROR');

                    return Promise.resolve({
                        validatedAsPublicationUnit: stagedPublicationCandidates.map(
                            setValidationStateToApiError,
                        ),
                        allChangesValidated: publicationCandidates.map(
                            setValidationStateToApiError,
                        ),
                    });
                });
        }, [props.stagedPublicationCandidateReferences, publicationCandidates]) ??
        emptyValidatedPublicationCandidates();

    const calculatedChanges = useLoader(
        () =>
            showCalculatedChanges
                ? getCalculatedChangesDebounced(
                      props.layoutContext.branch,
                      props.stagedPublicationCandidateReferences,
                  )
                : Promise.resolve(noCalculatedChanges),
        [props.stagedPublicationCandidateReferences],
    );

    const unstagedPublicationCandidates = addValidationState(
        filterByPublicationStage(publicationCandidates, PublicationStage.UNSTAGED),
        validatedPublicationCandidates.allChangesValidated,
    );

    const stagedPublicationCandidates = addValidationState(
        filterByPublicationStage(publicationCandidates, PublicationStage.STAGED),
        validatedPublicationCandidates.validatedAsPublicationUnit,
    );

    const displayedUnstagedPublicationCandidates =
        user && props.showOnlyOwnUnstagedChanges
            ? filterByUser(unstagedPublicationCandidates, user)
            : unstagedPublicationCandidates;

    const diplayedOnMapPublicationCandidates =
        user && props.showOnlyOwnUnstagedChanges
            ? filterByUser(publicationCandidates, user)
            : publicationCandidates;

    const [changesBeingReverted, setChangesBeingReverted] = React.useState<ChangesBeingReverted>();

    const publicationAssetChangeAmounts: PublicationAssetChangeAmounts = {
        total: props.stagedPublicationCandidateReferences.length ?? 0,
        staged: stagedPublicationCandidates.length,
        unstaged: unstagedPublicationCandidates.length,
        groupAmounts: countPublicationGroupAmounts(publicationCandidates ?? []),
        ownUnstaged: user ? filterByUser(unstagedPublicationCandidates, user).length : 0,
    };

    const onConfirmRevert = () => {
        if (changesBeingReverted === undefined) {
            return;
        }

        revertPublicationCandidates(
            props.layoutContext.branch,
            changesBeingReverted.changeIncludingDependencies,
        )
            .then((r) => {
                if (r.isOk()) {
                    const updatedCandidates = publicationCandidates.filter(
                        (candidate) =>
                            !changesBeingReverted.changeIncludingDependencies.some(
                                (revertedCandidate) =>
                                    candidateIdAndTypeMatches(revertedCandidate, candidate),
                            ),
                    );

                    setPublicationCandidates(updatedCandidates);
                    props.setStagedPublicationCandidateReferences(updatedCandidates);
                    Snackbar.success('publish.revert-success');
                }
            })
            .finally(() => {
                setChangesBeingReverted(undefined);
                void updateAllChangeTimes();
            });
    };

    const setStageForSpecificChanges = (
        publishCandidatesToBeUpdated: PublicationCandidate[],
        newStage: PublicationStage,
    ) => {
        const updatedCandidates = conditionallyUpdateCandidates(
            publicationCandidates,
            (candidate) =>
                publishCandidatesToBeUpdated.some((candidateToBeUpdated) =>
                    candidateIdAndTypeMatches(candidateToBeUpdated, candidate),
                ),
            stageTransform(newStage),
        );

        setPublicationCandidates(updatedCandidates);
        props.setStagedPublicationCandidateReferences(updatedCandidates);
    };

    const setPublicationGroupStage = (
        publicationGroup: PublicationGroup,
        newStage: PublicationStage,
    ) => {
        const updatedCandidates = conditionallyUpdateCandidates(
            publicationCandidates,
            (candidate) => candidate.publicationGroup?.id === publicationGroup.id,
            stageTransform(newStage),
        );

        setPublicationCandidates(updatedCandidates);
        props.setStagedPublicationCandidateReferences(updatedCandidates);
    };

    const setNewStageForStageChanges = (
        currentStage: PublicationStage,
        newStage: PublicationStage,
    ) => {
        const updateCondition = (candidate: PublicationCandidate): boolean => {
            const userFilter =
                currentStage === PublicationStage.UNSTAGED && props.showOnlyOwnUnstagedChanges
                    ? candidate.userName === user?.details.userName
                    : true;

            return candidate.stage === currentStage && userFilter;
        };

        const updatedCandidates = conditionallyUpdateCandidates(
            publicationCandidates,
            updateCondition,
            stageTransform(newStage),
        );

        setPublicationCandidates(updatedCandidates);
        props.setStagedPublicationCandidateReferences(updatedCandidates);
    };

    const revertChangesWithDependencies = (
        publishCandidates: PublicationCandidate[],
        revertRequestSource: RevertRequestSource,
    ) => {
        getRevertRequestDependencies(props.layoutContext.branch, publishCandidates).then(
            (changeIncludingDependencies) => {
                setChangesBeingReverted({
                    requestedRevertChange: {
                        type: RevertRequestType.CHANGES_WITH_DEPENDENCIES,
                        source: revertRequestSource,
                    },
                    changeIncludingDependencies,
                });
            },
        );
    };

    const revertStageChanges = (
        stage: PublicationStage,
        revertRequestSource: RevertRequestSource,
    ) => {
        const candidatesToRevert =
            stage === PublicationStage.STAGED
                ? stagedPublicationCandidates
                : displayedUnstagedPublicationCandidates;

        setChangesBeingReverted({
            requestedRevertChange: {
                type: RevertRequestType.STAGE_CHANGES,
                source: revertRequestSource,
                amount: candidatesToRevert.length,
                stage: stage,
            },
            changeIncludingDependencies: candidatesToRevert,
        });
    };

    const revertPublicationGroup = (
        publicationGroup: PublicationGroup,
        revertRequestSource: RevertRequestSource,
    ) => {
        setChangesBeingReverted({
            requestedRevertChange: {
                type: RevertRequestType.PUBLICATION_GROUP,
                source: revertRequestSource,
                amount: publicationAssetChangeAmounts.groupAmounts[publicationGroup.id] ?? 0,
                publicationGroup: publicationGroup,
            },
            changeIncludingDependencies: filterByPublicationGroup(
                publicationCandidates,
                publicationGroup,
            ),
        });
    };

    const clearStagedCandidates = () => {
        const updatedCandidates = publicationCandidates.filter(
            (candidate) => candidate.stage === PublicationStage.UNSTAGED,
        );

        setPublicationCandidates(updatedCandidates);
        props.setStagedPublicationCandidateReferences(updatedCandidates);
    };

    const previewOperations: PreviewOperations = {
        setPublicationStage: {
            forSpecificChanges: setStageForSpecificChanges,
            forAllShownChanges: setNewStageForStageChanges,
            forPublicationGroup: setPublicationGroupStage,
        },

        revert: {
            stageChanges: revertStageChanges,
            changesWithDependencies: revertChangesWithDependencies,
            publicationGroup: revertPublicationGroup,
        },
    };

    const publishCandidateSelectTool = React.useMemo(
        () =>
            createAreaSelectTool((items, mode) => {
                const selectedCandidates = [
                    ...(items.locationTrackPublicationCandidates || []),
                    ...(items.switchPublicationCandidates || []),
                ].flat();
                if (selectedCandidates.length) {
                    setStageForSpecificChanges(
                        selectedCandidates,
                        mode == SelectMode.Add
                            ? PublicationStage.STAGED
                            : PublicationStage.UNSTAGED,
                    );
                }
            }),
        [publicationCandidates],
    );

    return (
        <React.Fragment>
            <div className={styles['preview-view']} qa-id="preview-content">
                <PreviewToolBar
                    onClosePreview={props.onClosePreview}
                    layoutBranch={props.layoutContext.branch}
                    designPublicationMode={designPublicationMode}
                    onChangeDesignPublicationMode={onChangeDesignPublicationMode}
                />
                <div
                    className={createClassName(
                        styles['preview-view__changes'],
                        !showCalculatedChanges &&
                            styles['preview-view__changes--no-calculated-changes'],
                    )}>
                    {(showPreview && (
                        <>
                            <section
                                qa-id={'unstaged-changes'}
                                className={styles['preview-section']}>
                                {hasDesignDraftsInDesign && (
                                    <DesignDraftsExistError
                                        goToPublishChangesMode={() =>
                                            setDesignPublicationMode('PUBLISH_CHANGES')
                                        }
                                    />
                                )}
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
                                    layoutContext={props.layoutContext}
                                    changesBeingReverted={changesBeingReverted}
                                    publicationCandidates={displayedUnstagedPublicationCandidates}
                                    staged={false}
                                    onShowOnMap={props.onShowOnMap}
                                    changeTimes={props.changeTimes}
                                    publicationGroupAmounts={
                                        publicationAssetChangeAmounts.groupAmounts
                                    }
                                    displayedTotalPublicationAssetAmount={
                                        props.showOnlyOwnUnstagedChanges
                                            ? publicationAssetChangeAmounts.ownUnstaged
                                            : publicationAssetChangeAmounts.unstaged
                                    }
                                    previewOperations={previewOperations}
                                    tableValidationState={publicationValidationState}
                                    tableEntryValidationState={(item) => {
                                        return publicationValidationState === 'API_CALL_ERROR'
                                            ? 'API_CALL_ERROR'
                                            : item.validationState;
                                    }}
                                    canRevertChanges={canRevertChanges}
                                />
                            </section>
                            <section qa-id={'staged-changes'} className={styles['preview-section']}>
                                <div className={styles['preview-view__changes-title']}>
                                    <h3>
                                        {t(
                                            designPublicationMode === 'PUBLISH_CHANGES'
                                                ? 'preview-view.staged-changes-title'
                                                : 'preview-view.merging-to-main-changes-title',
                                            {
                                                amount: publicationAssetChangeAmounts.staged,
                                            },
                                        )}
                                    </h3>
                                </div>
                                <PreviewTable
                                    layoutContext={props.layoutContext}
                                    changesBeingReverted={changesBeingReverted}
                                    publicationCandidates={stagedPublicationCandidates}
                                    staged={true}
                                    onShowOnMap={props.onShowOnMap}
                                    changeTimes={props.changeTimes}
                                    publicationGroupAmounts={
                                        publicationAssetChangeAmounts.groupAmounts
                                    }
                                    displayedTotalPublicationAssetAmount={
                                        publicationAssetChangeAmounts.staged
                                    }
                                    previewOperations={previewOperations}
                                    tableValidationState={publicationValidationState}
                                    tableEntryValidationState={() => publicationValidationState}
                                    canRevertChanges={canRevertChanges}
                                />
                            </section>
                            <div className={styles['preview-section']}>
                                {showCalculatedChanges &&
                                    calculatedChanges &&
                                    designPublicationMode !== 'MERGE_TO_MAIN' && (
                                        <CalculatedChangesView
                                            layoutContext={props.layoutContext}
                                            calculatedChanges={calculatedChanges}
                                        />
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
                    <MapViewContainer
                        layoutContext={
                            designPublicationMode === 'MERGE_TO_MAIN'
                                ? mapDisplayTransitionSide === 'WITH_CHANGES'
                                    ? officialLayoutContext(props.layoutContext)
                                    : draftMainLayoutContext()
                                : mapDisplayTransitionSide === 'WITH_CHANGES'
                                  ? draftLayoutContext(props.layoutContext)
                                  : officialLayoutContext(props.layoutContext)
                        }
                        publicationCandidates={diplayedOnMapPublicationCandidates}
                        customActiveMapTool={publishCandidateSelectTool}
                    />
                </MapContext.Provider>
                <PreviewFooter
                    onSelect={props.onSelect}
                    layoutContext={props.layoutContext}
                    onChangeMapDisplayTransitionSide={setMapDisplayTransitionSide}
                    mapDisplayTransitionSide={mapDisplayTransitionSide}
                    onPublish={() => {
                        props.onPublish();
                        clearStagedCandidates();
                    }}
                    stagedPublicationCandidates={stagedPublicationCandidates}
                    disablePublication={
                        publicationValidationState !== 'API_CALL_OK' || hasDesignDraftsInDesign
                    }
                    designPublicationMode={designPublicationMode}
                />
            </div>
            {changesBeingReverted !== undefined && (
                <PreviewConfirmRevertChangesDialog
                    layoutContext={props.layoutContext}
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
