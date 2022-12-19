import * as React from 'react';
import styles from './preview-view.scss';
import { useTranslation } from 'react-i18next';
import { useLoader } from 'utils/react-utils';
import {
    getCalculatedChanges,
    getPublishCandidates,
    PublishRequest,
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
import { PublishCandidates, ValidatedPublishCandidates } from 'publication/publication-model';
import {
    LayoutKmPostId,
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import PreviewTable from 'preview/preview-table';

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

const initialCandidates = {
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
    kmPosts: publishCandidates.kmPosts.filter((trackNumber) =>
        filterStaged(stagedChangeIds.kmPosts, trackNumber),
    ),
    referenceLines: publishCandidates.referenceLines.filter((trackNumber) =>
        filterStaged(stagedChangeIds.referenceLines, trackNumber),
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
    kmPosts: publishCandidates.kmPosts.filter((trackNumber) =>
        filterUnstaged(stagedChangeIds.kmPosts, trackNumber),
    ),
    referenceLines: publishCandidates.referenceLines.filter((trackNumber) =>
        filterUnstaged(stagedChangeIds.referenceLines, trackNumber),
    ),
});

export const PreviewView: React.FC<PreviewProps> = (props: PreviewProps) => {
    const { t } = useTranslation();
    const allChanges = useLoader(() => getPublishCandidates(), []);
    const allValidatedPublishCandidates: ValidatedPublishCandidates | null | undefined = useLoader(
        () =>
            (allChanges && validatePublishCandidates(publishCandidateIds(allChanges))) ?? undefined,
        [allChanges],
    );
    // GVT-1510 currently all changes are fetched above and hence all validated as a single publication unit
    const publishCandidates: PublishCandidates | undefined =
        allValidatedPublishCandidates?.validatedAsPublicationUnit;
    const unstagedChanges = publishCandidates
        ? getUnstagedChanges(publishCandidates, props.selectedPublishCandidateIds)
        : initialCandidates;
    const stagedChanges = publishCandidates
        ? getStagedChanges(publishCandidates, props.selectedPublishCandidateIds)
        : initialCandidates;

    const [selectedChanges, setSelectedChanges] = React.useState<SelectedChanges>({
        trackNumbers: [],
        referenceLines: [],
        locationTracks: [],
        switches: [],
        kmPosts: [],
    });
    React.useEffect(() => {
        setSelectedChanges({
            trackNumbers: publishCandidates
                ? publishCandidates.trackNumbers.map((tn) => tn.id)
                : [],
            referenceLines: publishCandidates
                ? publishCandidates.referenceLines.map((a) => a.id)
                : [],
            locationTracks: publishCandidates
                ? publishCandidates.locationTracks.map((a) => a.id)
                : [],
            switches: publishCandidates ? publishCandidates.switches.map((s) => s.id) : [],
            kmPosts: publishCandidates ? publishCandidates.kmPosts.map((kmp) => kmp.id) : [],
        });
    }, [publishCandidates]);
    const calculatedChanges = useLoader(
        () => getCalculatedChanges(selectedChanges),
        [selectedChanges],
    );
    const [mapMode, setMapMode] = React.useState<PublishType>('DRAFT');

    return (
        <React.Fragment>
            <div className={styles['preview-view']} qa-id="preview-content">
                <PreviewToolBar onClosePreview={props.onClosePreview} />
                <div className={styles['preview-view__changes']}>
                    {(unstagedChanges && stagedChanges && (
                        <>
                            <section className={styles['preview-section']}>
                                <div className={styles['preview-view__changes-title']}>
                                    <h3>{t('preview-view.unstaged-changes-title')}</h3>
                                </div>
                                <PreviewTable
                                    onPreviewSelect={props.onPreviewSelect}
                                    previewChanges={unstagedChanges}
                                    staged={false}
                                />
                            </section>

                            <section className={styles['preview-section']}>
                                <div className={styles['preview-view__changes-title']}>
                                    <h3>{t('preview-view.publish-candidates-title')}</h3>
                                </div>
                                <PreviewTable
                                    onPreviewSelect={props.onPublishPreviewRemove}
                                    previewChanges={stagedChanges}
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
                    request={selectedChanges}
                    onClosePreview={props.onClosePreview}
                    mapMode={mapMode}
                    onChangeMapMode={setMapMode}
                    previewChanges={unstagedChanges == null ? undefined : unstagedChanges}
                    onPublishPreviewRevert={props.onPublishPreviewRevert}
                />
            </div>
        </React.Fragment>
    );
};
