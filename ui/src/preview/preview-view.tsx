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
import { SelectedPublishChange } from 'track-layout/track-layout-slice';
import { AssetId, PublishType } from 'common/common-model';
import { CalculatedChangesView } from './calculated-changes-view';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import {
    KmPostPublishCandidate,
    LocationTrackPublishCandidate,
    PublicationId,
    PublishCandidate,
    PublishCandidates,
    PublishRequestIds,
    ReferenceLinePublishCandidate,
    SwitchPublishCandidate,
    TrackNumberPublishCandidate,
} from 'publication/publication-model';
import PreviewTable, { PreviewSelectType, PreviewTableEntry } from 'preview/preview-table';
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

export type ChangesBeingReverted = {
    requestedRevertChange: { type: PreviewSelectType; name: string; id: string };
    changeIncludingDependencies: PublishRequestIds;
};

export type PreviewProps = {
    changeTimes: ChangeTimes;
    selectedPublishCandidateIds: PublishRequestIds;
    showOnlyOwnUnstagedChanges: boolean;
    setShowOnlyOwnUnstagedChanges: (checked: boolean) => void;
    onSelect: OnSelectFunction;
    onPublish: () => void;
    onClosePreview: () => void;
    onPreviewSelect: (selectedChange: SelectedPublishChange) => void;
    onPublishPreviewRemove: (selectedChangesWithDependencies: PublishRequestIds) => void;
    onShowOnMap: (bbox: BoundingBox) => void;
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
};

const filterStaged = (stagedIds: AssetId[], candidate: Candidate) =>
    stagedIds.includes(candidate.id);
const filterUnstaged = (stagedIds: AssetId[], candidate: Candidate) =>
    !stagedIds.includes(candidate.id);

const getStagedChanges = (
    publishCandidates: PublishCandidates,
    stagedChangeIds: PublishRequestIds,
) => ({
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
) => ({
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
) => {
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

const singleRowPublishRequestOfPreviewTableEntry = (
    id: PublicationId,
    type: PreviewSelectType,
): PublishRequestIds => ({
    trackNumbers: type === 'trackNumber' ? [id] : [],
    referenceLines: type === 'referenceLine' ? [id] : [],
    locationTracks: type === 'locationTrack' ? [id] : [],
    switches: type === 'switch' ? [id] : [],
    kmPosts: type === 'kmPost' ? [id] : [],
});

const singleRowPublishRequestOfSelectedPublishChange = (
    change: SelectedPublishChange,
): PublishRequestIds => ({
    trackNumbers: change.trackNumber ? [change.trackNumber] : [],
    referenceLines: change.referenceLine ? [change.referenceLine] : [],
    locationTracks: change.locationTrack ? [change.locationTrack] : [],
    switches: change.switch ? [change.switch] : [],
    kmPosts: change.kmPost ? [change.kmPost] : [],
});

const filterPreviewCandidateArrayByUser = <T extends PreviewCandidate>(
    user: User,
    candidates: T[],
) => candidates.filter((candidate) => candidate.userName === user.details.userName);

const previewCandidatesByUser = (
    user: User,
    publishCandidates: PreviewCandidates,
): PreviewCandidates => ({
    trackNumbers: filterPreviewCandidateArrayByUser(user, publishCandidates.trackNumbers),
    referenceLines: filterPreviewCandidateArrayByUser(user, publishCandidates.referenceLines),
    locationTracks: filterPreviewCandidateArrayByUser(user, publishCandidates.locationTracks),
    switches: filterPreviewCandidateArrayByUser(user, publishCandidates.switches),
    kmPosts: filterPreviewCandidateArrayByUser(user, publishCandidates.kmPosts),
});

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

    const unstagedPreviewChanges: PreviewCandidates = (() => {
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

    const onRequestRevert = (requestedRevertChange: PreviewTableEntry) => {
        getRevertRequestDependencies(
            singleRowPublishRequestOfPreviewTableEntry(
                requestedRevertChange.id,
                requestedRevertChange.type,
            ),
        ).then((changeIncludingDependencies) => {
            setChangesBeingReverted({
                requestedRevertChange,
                changeIncludingDependencies,
            });
        });
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

    const onPublishPreviewRemove = (selectedChange: SelectedPublishChange): void => {
        props.onPublishPreviewRemove(
            singleRowPublishRequestOfSelectedPublishChange(selectedChange),
        );
    };

    const onPreviewSelect = (selectedChange: SelectedPublishChange): void => {
        props.onPreviewSelect(selectedChange);
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
                                    <h3>{t('preview-view.unstaged-changes-title')}</h3>
                                    <Checkbox
                                        checked={props.showOnlyOwnUnstagedChanges}
                                        onChange={(e) => {
                                            props.setShowOnlyOwnUnstagedChanges(e.target.checked);
                                        }}>
                                        {t('preview-view.show-only-mine')}
                                    </Checkbox>
                                </div>
                                <PreviewTable
                                    onPreviewSelect={onPreviewSelect}
                                    onRevert={onRequestRevert}
                                    changesBeingReverted={changesBeingReverted}
                                    previewChanges={unstagedPreviewChanges}
                                    staged={false}
                                    onShowOnMap={props.onShowOnMap}
                                    changeTimes={props.changeTimes}
                                />
                            </section>

                            <section qa-id={'staged-changes'} className={styles['preview-section']}>
                                <div className={styles['preview-view__changes-title']}>
                                    <h3>{t('preview-view.staged-changes-title')}</h3>
                                </div>
                                <PreviewTable
                                    onPreviewSelect={onPublishPreviewRemove}
                                    onRevert={onRequestRevert}
                                    changesBeingReverted={changesBeingReverted}
                                    previewChanges={stagedPreviewChanges}
                                    staged={true}
                                    onShowOnMap={props.onShowOnMap}
                                    changeTimes={props.changeTimes}
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
