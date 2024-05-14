import * as React from 'react';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import styles from './preview-view.scss';
import { useTranslation } from 'react-i18next';
import { useLoader } from 'utils/react-utils';
import {
    getCalculatedChanges,
    getPublicationCandidates,
    getRevertRequestDependencies,
    revertPublicationCandidates,
    validatePublicationCandidates,
} from 'publication/publication-api';
import { PreviewToolBar } from 'preview/preview-tool-bar';
import { OnSelectFunction } from 'selection/selection-model';
import { CalculatedChangesView } from './calculated-changes-view';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import {
    emptyValidatedPublicationCandidates,
    PublicationCandidate,
    PublicationCandidateReference,
    PublicationGroup,
    PublicationStage,
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

const validateDebounced = debounceAsync(
    (candidates: PublicationCandidateReference[]) => validatePublicationCandidates(candidates),
    1000,
);
const getCalculatedChangesDebounced = debounceAsync(
    (candidates: PublicationCandidateReference[]) => getCalculatedChanges(candidates),
    1000,
);

export const PreviewView: React.FC<PreviewProps> = (props: PreviewProps) => {
    const { t } = useTranslation();
    const user = useLoader(getOwnUser, []);

    const [showPreview, setShowPreview] = React.useState<boolean>(true);

    const [showValidationStatusSpinner, setShowValidationStatusSpinner] =
        React.useState<boolean>(true);

    const [publicationCandidates, setPublicationCandidates] = React.useState<
        PublicationCandidate[]
    >([]);

    useLoader(
        () =>
            getPublicationCandidates().then((candidates) => {
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
            }),
        [props.changeTimes],
    );

    const validatedPublicationCandidates =
        useLoader(() => {
            setShowValidationStatusSpinner(true);

            return validateDebounced(props.stagedPublicationCandidateReferences).finally(() =>
                setShowValidationStatusSpinner(false),
            );
        }, [props.stagedPublicationCandidateReferences]) ?? emptyValidatedPublicationCandidates();

    const calculatedChanges = useLoader(
        () => getCalculatedChangesDebounced(props.stagedPublicationCandidateReferences),
        [props.stagedPublicationCandidateReferences],
    );

    const [layoutContext, setLayoutContext] = React.useState<LayoutContext>(
        draftLayoutContext(props.layoutContext),
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

        revertPublicationCandidates(changesBeingReverted.changeIncludingDependencies)
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

    return (
        <React.Fragment>
            <div className={styles['preview-view']} qa-id="preview-content">
                <PreviewToolBar
                    onClosePreview={props.onClosePreview}
                    designId={props.layoutContext.designId}
                />
                <div className={styles['preview-view__changes']}>
                    {(showPreview && (
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
                                    validationInProgress={showValidationStatusSpinner}
                                    isRowValidating={(item) => item.pendingValidation}
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
                                    validationInProgress={showValidationStatusSpinner}
                                    isRowValidating={() => showValidationStatusSpinner}
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
                    stagedPublicationCandidates={stagedPublicationCandidates}
                    validating={showValidationStatusSpinner}
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
