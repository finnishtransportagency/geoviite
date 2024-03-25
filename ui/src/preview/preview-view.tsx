import * as React from 'react';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
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
import { PreviewToolBar } from 'preview/preview-tool-bar';
import { OnSelectFunction } from 'selection/selection-model';
import { CalculatedChangesView } from './calculated-changes-view';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import {
    PublicationGroup,
    PublicationStage,
    PublishCandidate,
    PublishCandidateReference,
    emptyValidatedPublishCandidates,
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
    PublicationAssetChangeAmounts,
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
import { draftLayoutContext, LayoutContext } from 'common/common-model';
import { debounceAsync } from 'utils/async-utils';

export type PreviewProps = {
    layoutContext: LayoutContext;
    changeTimes: ChangeTimes;
    stagedPublishCandidateReferences: PublishCandidateReference[];
    setStagedPublicationCandidateReferences: (publicationCandidate: PublishCandidate[]) => void;
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
            publishCandidatesToBeUpdated: PublishCandidate[],
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
            publishCandidates: PublishCandidate[],
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
    changeIncludingDependencies: PublishCandidateReference[];
};

const validateDebounced = debounceAsync(
    (candidates: PublishCandidateReference[]) => validatePublishCandidates(candidates),
    1000,
);
const getCalculatedChangesDebounced = debounceAsync(
    (candidates: PublishCandidateReference[]) => getCalculatedChanges(candidates),
    1000,
);

export const PreviewView: React.FC<PreviewProps> = (props: PreviewProps) => {
    const { t } = useTranslation();
    const user = useLoader(getOwnUser, []);

    const [publishCandidates, setPublishCandidates] = React.useState<PublishCandidate[]>([]);
    useLoader(
        () =>
            getPublishCandidates().then((candidates) => {
                const candidatesWithUpdatedStage = candidates.map((candidate) => {
                    const candidateIsStaged = props.stagedPublishCandidateReferences.some(
                        (stagedCandidate) => candidateIdAndTypeMatches(stagedCandidate, candidate),
                    );

                    return candidateIsStaged
                        ? {
                              ...candidate,
                              stage: PublicationStage.STAGED,
                          }
                        : candidate;
                });

                setPublishCandidates(candidatesWithUpdatedStage);
                props.setStagedPublicationCandidateReferences(candidatesWithUpdatedStage);
            }),
        [props.changeTimes],
    );

    const [showValidationStatusSpinner, setShowValidationStatusSpinner] =
        React.useState<boolean>(true);

    const validatedCandidates =
        useLoader(() => {
            setShowValidationStatusSpinner(true);

            return validateDebounced(props.stagedPublishCandidateReferences).finally(() =>
                setShowValidationStatusSpinner(false),
            );
        }, [props.stagedPublishCandidateReferences]) ?? emptyValidatedPublishCandidates();

    const calculatedChanges = useLoader(
        () => getCalculatedChangesDebounced(props.stagedPublishCandidateReferences),
        [props.stagedPublishCandidateReferences],
    );

    const [layoutContext, setLayoutContext] = React.useState<LayoutContext>(
        draftLayoutContext(props.layoutContext),
    );

    const unstagedPublishCandidates = addValidationState(
        filterByPublicationStage(publishCandidates, PublicationStage.UNSTAGED),
        validatedCandidates.allChangesValidated,
    );

    const stagedPublishCandidates = addValidationState(
        filterByPublicationStage(publishCandidates, PublicationStage.STAGED),
        validatedCandidates.validatedAsPublicationUnit,
    );

    const displayedUnstagedPublishCandidates =
        user && props.showOnlyOwnUnstagedChanges
            ? filterByUser(unstagedPublishCandidates, user)
            : unstagedPublishCandidates;

    const [changesBeingReverted, setChangesBeingReverted] = React.useState<ChangesBeingReverted>();

    const publicationAssetChangeAmounts: PublicationAssetChangeAmounts = {
        total: props.stagedPublishCandidateReferences.length ?? 0,
        staged: stagedPublishCandidates.length,
        unstaged: unstagedPublishCandidates.length,
        groupAmounts: countPublicationGroupAmounts(publishCandidates ?? []),
        ownUnstaged: user ? filterByUser(unstagedPublishCandidates, user).length : 0,
    };

    const onConfirmRevert = () => {
        if (changesBeingReverted === undefined) {
            return;
        }

        revertCandidates(changesBeingReverted.changeIncludingDependencies)
            .then((r) => {
                if (r.isOk()) {
                    const updatedCandidates = publishCandidates.filter(
                        (candidate) =>
                            !changesBeingReverted.changeIncludingDependencies.some(
                                (revertedCandidate) =>
                                    candidateIdAndTypeMatches(revertedCandidate, candidate),
                            ),
                    );

                    setPublishCandidates(updatedCandidates);
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
        publishCandidatesToBeUpdated: PublishCandidate[],
        newStage: PublicationStage,
    ) => {
        const updatedCandidates = conditionallyUpdateCandidates(
            publishCandidates,
            (candidate) =>
                publishCandidatesToBeUpdated.some((candidateToBeUpdated) =>
                    candidateIdAndTypeMatches(candidateToBeUpdated, candidate),
                ),
            stageTransform(newStage),
        );

        setPublishCandidates(updatedCandidates);
        props.setStagedPublicationCandidateReferences(updatedCandidates);
    };

    const setPublicationGroupStage = (
        publicationGroup: PublicationGroup,
        newStage: PublicationStage,
    ) => {
        const updatedCandidates = conditionallyUpdateCandidates(
            publishCandidates,
            (candidate) => candidate.publicationGroup?.id === publicationGroup.id,
            stageTransform(newStage),
        );

        setPublishCandidates(updatedCandidates);
        props.setStagedPublicationCandidateReferences(updatedCandidates);
    };

    const setNewStageForStageChanges = (
        currentStage: PublicationStage,
        newStage: PublicationStage,
    ) => {
        const updatedCandidates = conditionallyUpdateCandidates(
            publishCandidates,
            (candidate) => candidate.stage === currentStage,
            stageTransform(newStage),
        );

        setPublishCandidates(updatedCandidates);
        props.setStagedPublicationCandidateReferences(updatedCandidates);
    };

    const revertChangesWithDependencies = (
        publishCandidates: PublishCandidate[],
        revertRequestSource: RevertRequestSource,
    ) => {
        getRevertRequestDependencies(publishCandidates).then((changeIncludingDependencies) => {
            setChangesBeingReverted({
                requestedRevertChange: {
                    type: RevertRequestType.CHANGES_WITH_DEPENDENCIES,
                    source: revertRequestSource,
                },
                changeIncludingDependencies,
            });
        });
    };

    const revertStageChanges = (
        stage: PublicationStage,
        revertRequestSource: RevertRequestSource,
    ) => {
        const candidatesToRevert =
            stage === PublicationStage.STAGED ? stagedPublishCandidates : unstagedPublishCandidates;

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
                publishCandidates,
                publicationGroup,
            ),
        });
    };

    const clearStagedCandidates = () => {
        const updatedCandidates = publishCandidates.filter(
            (candidate) => candidate.stage === PublicationStage.UNSTAGED,
        );

        setPublishCandidates(updatedCandidates);
        props.setStagedPublicationCandidateReferences(updatedCandidates);
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
                    {(displayedUnstagedPublishCandidates && (
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
                                    layoutContext={layoutContext}
                                    changesBeingReverted={changesBeingReverted}
                                    publishCandidates={displayedUnstagedPublishCandidates}
                                    staged={false}
                                    onShowOnMap={props.onShowOnMap}
                                    changeTimes={props.changeTimes}
                                    publicationAssetChangeAmounts={publicationAssetChangeAmounts}
                                    previewOperations={previewOperations}
                                    showStatusSpinner={showValidationStatusSpinner}
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
                                    layoutContext={layoutContext}
                                    changesBeingReverted={changesBeingReverted}
                                    publishCandidates={stagedPublishCandidates}
                                    staged={true}
                                    onShowOnMap={props.onShowOnMap}
                                    changeTimes={props.changeTimes}
                                    publicationAssetChangeAmounts={publicationAssetChangeAmounts}
                                    previewOperations={previewOperations}
                                    showStatusSpinner={showValidationStatusSpinner}
                                />
                            </section>

                            <div className={styles['preview-section']}>
                                {calculatedChanges && (
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
                    <MapViewContainer layoutContext={layoutContext} />
                </MapContext.Provider>

                <PreviewFooter
                    onSelect={props.onSelect}
                    layoutContext={layoutContext}
                    onChangeLayoutContext={setLayoutContext}
                    onPublish={() => {
                        props.onPublish();
                        clearStagedCandidates();
                    }}
                    stagedPublishCandidates={stagedPublishCandidates}
                />
            </div>
            {changesBeingReverted !== undefined && (
                <PreviewConfirmRevertChangesDialog
                    layoutContext={layoutContext}
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
