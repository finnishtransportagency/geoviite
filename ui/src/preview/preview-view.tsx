import * as React from 'react';
import styles from './preview-view.scss';
import { useTranslation } from 'react-i18next';
import { useLoader } from 'utils/react-utils';
import {
    getCalculatedChanges,
    getPublishCandidates,
    PublishRequest,
    revertCandidates,
    validatePublishCandidates,
} from 'publication/publication-api';

import { PreviewFooter } from 'preview/preview-footer';
import { PreviewToolBar } from 'preview/preview-tool-bar';
import MapView from 'map/map-view';
import { Map, MapViewport, OptionalShownItems } from 'map/map-model';
import {
    OnClickLocationFunction,
    OnHighlightItemsFunction,
    OnHoverLocationFunction,
    OnSelectFunction,
    Selection,
} from 'selection/selection-model';
import { ChangeTimes, SelectedPublishChange } from 'track-layout/track-layout-store';
import { PublishType } from 'common/common-model';
import { CalculatedChangesView } from './calculated-changes-view';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import {
    KmPostPublishCandidate,
    LocationTrackPublishCandidate,
    PublicationId,
    PublishCandidate,
    PublishCandidates,
    ReferenceLinePublishCandidate,
    SwitchPublishCandidate,
    TrackNumberPublishCandidate,
} from 'publication/publication-model';
import {
    LayoutKmPostId,
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import PreviewTable, { PreviewSelectType, PreviewTableEntry } from 'preview/preview-table';
import { updateAllChangeTimes } from 'common/change-time-api';
import { Dialog, DialogVariant } from 'vayla-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import dialogStyles from '../vayla-design-lib/dialog/dialog.scss';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';

type CandidateId =
    | LocationTrackId
    | LayoutTrackNumberId
    | ReferenceLineId
    | LayoutSwitchId
    | LayoutKmPostId;
type Candidate = {
    id: CandidateId;
};

export type SelectedChanges = {
    trackNumbers: LayoutTrackNumberId[];
    referenceLines: ReferenceLineId[];
    locationTracks: LocationTrackId[];
    switches: LayoutSwitchId[];
    kmPosts: LayoutKmPostId[];
};

type PendingValidation = {
    pendingValidation: boolean;
};

export type PreviewCandidates = {
    trackNumbers: (TrackNumberPublishCandidate & PendingValidation)[];
    referenceLines: (ReferenceLinePublishCandidate & PendingValidation)[];
    locationTracks: (LocationTrackPublishCandidate & PendingValidation)[];
    switches: (SwitchPublishCandidate & PendingValidation)[];
    kmPosts: (KmPostPublishCandidate & PendingValidation)[];
};

type PreviewProps = {
    map: Map;
    selection: Selection;
    changeTimes: ChangeTimes;
    selectedPublishCandidateIds: SelectedChanges;
    onViewportChange: (viewport: MapViewport) => void;
    onSelect: OnSelectFunction;
    onHighlightItems: OnHighlightItemsFunction;
    onHoverLocation: OnHoverLocationFunction;
    onClickLocation: OnClickLocationFunction;
    onShownItemsChange: (shownItems: OptionalShownItems) => void;
    onPublish: () => void;
    onClosePreview: () => void;
    onPreviewSelect: (selectedChange: SelectedPublishChange) => void;
    onPublishPreviewRemove: (selectedChange: SelectedPublishChange) => void;
    onPublishPreviewRevert: () => void;
};

const publishCandidateIds = (candidates: PublishCandidates): PublishRequest => ({
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

const filterStaged = (stagedIds: CandidateId[], candidate: Candidate) =>
    stagedIds.includes(candidate.id);
const filterUnstaged = (stagedIds: CandidateId[], candidate: Candidate) =>
    !stagedIds.includes(candidate.id);

const getStagedChanges = (
    publishCandidates: PublishCandidates,
    stagedChangeIds: SelectedChanges,
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
    stagedChangeIds: SelectedChanges,
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
const pendingValidation = (
    allStaged: CandidateId[],
    allValidated: CandidateId[],
    id: CandidateId,
) => allStaged.includes(id) && !allValidated.includes(id);

const previewChanges = (
    stagedValidatedChanges: PublishCandidates,
    allSelectedChanges: PublishRequest,
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

const typeTranslationKey = (type: PreviewSelectType | undefined) => {
    switch (type) {
        case PreviewSelectType.trackNumber:
            return 'track-number';
        case PreviewSelectType.referenceLine:
            return 'reference-line';
        case PreviewSelectType.locationTrack:
            return 'location-track';
        case PreviewSelectType.switch:
            return 'switch';
        case PreviewSelectType.kmPost:
            return 'km-post';
        default:
            return '';
    }
};

export const PreviewView: React.FC<PreviewProps> = (props: PreviewProps) => {
    const { t } = useTranslation();

    const entireChangeset = useLoader(() => getPublishCandidates(), [props.changeTimes]);
    const entireChangesetValidation = useLoader(
        () =>
            validatePublishCandidates(
                publishCandidateIds(entireChangeset ? entireChangeset : emptyChanges),
            ),
        [entireChangeset],
    );
    const stagedValidation = useLoader(
        () => validatePublishCandidates(props.selectedPublishCandidateIds),
        [props.selectedPublishCandidateIds, props.changeTimes],
    );
    const unstagedChanges = entireChangesetValidation
        ? getUnstagedChanges(
              entireChangesetValidation?.validatedAsPublicationUnit,
              props.selectedPublishCandidateIds,
          )
        : undefined;
    const stagedChangesValidated = stagedValidation
        ? getStagedChanges(
              stagedValidation.validatedAsPublicationUnit,
              props.selectedPublishCandidateIds,
          )
        : undefined;

    const unstagedPreviewChanges: PreviewCandidates = unstagedChanges
        ? {
              trackNumbers: unstagedChanges.trackNumbers.map(nonPendingCandidate),
              referenceLines: unstagedChanges.referenceLines.map(nonPendingCandidate),
              locationTracks: unstagedChanges.locationTracks.map(nonPendingCandidate),
              switches: unstagedChanges.switches.map(nonPendingCandidate),
              kmPosts: unstagedChanges.kmPosts.map(nonPendingCandidate),
          }
        : emptyChanges;

    const stagedPreviewChanges: PreviewCandidates =
        stagedChangesValidated && entireChangeset
            ? previewChanges(
                  stagedChangesValidated,
                  props.selectedPublishCandidateIds,
                  entireChangeset,
              )
            : emptyChanges;

    const calculatedChanges = useLoader(
        () => getCalculatedChanges(props.selectedPublishCandidateIds),
        [props.selectedPublishCandidateIds],
    );
    const [mapMode, setMapMode] = React.useState<PublishType>('DRAFT');
    const [isReverting, setIsReverting] = React.useState(false);
    const [revertConfirmVisible, setRevertConfirmVisible] = React.useState(false);
    const [entryBeingReverted, setEntryBeingReverted] = React.useState<PreviewTableEntry>();

    const onRevertRow = (id: PublicationId, type: PreviewSelectType) => {
        const selectedChange = {
            trackNumber: type === PreviewSelectType.trackNumber ? id : undefined,
            referenceLine: type === PreviewSelectType.referenceLine ? id : undefined,
            locationTrack: type === PreviewSelectType.locationTrack ? id : undefined,
            switch: type === PreviewSelectType.switch ? id : undefined,
            kmPost: type === PreviewSelectType.kmPost ? id : undefined,
        };
        const request = {
            trackNumbers: type === PreviewSelectType.trackNumber ? [id] : [],
            referenceLines: type === PreviewSelectType.referenceLine ? [id] : [],
            locationTracks: type === PreviewSelectType.locationTrack ? [id] : [],
            switches: type === PreviewSelectType.switch ? [id] : [],
            kmPosts: type === PreviewSelectType.kmPost ? [id] : [],
        };
        revertCandidates(request)
            .then((r) => {
                if (r.isOk()) {
                    Snackbar.success(t('publish.revert-success'));
                    props.onPublishPreviewRemove(selectedChange);
                }
            })
            .finally(() => {
                setRevertConfirmVisible(false);
                setIsReverting(false);
                updateAllChangeTimes();
            });
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
                                </div>
                                <PreviewTable
                                    onPreviewSelect={props.onPreviewSelect}
                                    onRevert={(entry) => {
                                        setEntryBeingReverted(entry);
                                        setRevertConfirmVisible(true);
                                    }}
                                    previewChanges={unstagedPreviewChanges}
                                    staged={false}
                                />
                            </section>

                            <section qa-id={'staged-changes'} className={styles['preview-section']}>
                                <div className={styles['preview-view__changes-title']}>
                                    <h3>{t('preview-view.staged-changes-title')}</h3>
                                </div>
                                <PreviewTable
                                    onPreviewSelect={props.onPublishPreviewRemove}
                                    onRevert={(entry) => {
                                        setEntryBeingReverted(entry);
                                        setRevertConfirmVisible(true);
                                    }}
                                    previewChanges={stagedPreviewChanges}
                                    staged={true}
                                />
                            </section>

                            <div className={styles['preview-section']}>
                                {calculatedChanges && (
                                    <CalculatedChangesView calculatedChanges={calculatedChanges} />
                                )}
                                {!calculatedChanges && <Spinner />}
                            </div>
                        </>
                    )) || <Spinner />}
                </div>

                <MapView
                    map={props.map}
                    onViewportUpdate={props.onViewportChange}
                    selection={props.selection}
                    publishType={mapMode}
                    changeTimes={props.changeTimes}
                    onSelect={props.onSelect}
                    onHighlightItems={props.onHighlightItems}
                    onHoverLocation={props.onHoverLocation}
                    onClickLocation={props.onClickLocation}
                    onShownLayerItemsChange={props.onShownItemsChange}
                />

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
                    onPublishPreviewRevert={props.onPublishPreviewRevert}
                />
            </div>
            {revertConfirmVisible && (
                <Dialog
                    title={t('publish.revert-confirm.title')}
                    variant={DialogVariant.LIGHT}
                    allowClose={false}
                    className={dialogStyles['dialog--wide']}
                    footerContent={
                        <React.Fragment>
                            <Button
                                onClick={() => setRevertConfirmVisible(false)}
                                disabled={isReverting}
                                variant={ButtonVariant.SECONDARY}>
                                {t('publish.revert-confirm.cancel')}
                            </Button>
                            <Button
                                icon={Icons.Delete}
                                disabled={isReverting}
                                isProcessing={isReverting}
                                variant={ButtonVariant.WARNING}
                                onClick={() => {
                                    if (entryBeingReverted) {
                                        setIsReverting(true);
                                        onRevertRow(entryBeingReverted.id, entryBeingReverted.type);
                                    }
                                }}>
                                {t('publish.revert-confirm.confirm')}
                            </Button>
                        </React.Fragment>
                    }>
                    <div>{`${t('publish.revert-confirm.description')} ${t(
                        `publish.revert-confirm.${typeTranslationKey(entryBeingReverted?.type)}`,
                    )} ${entryBeingReverted?.name}?`}</div>
                </Dialog>
            )}
        </React.Fragment>
    );
};
