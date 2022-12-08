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
import {
    ChangeTimes,
    SelectedPublishChange,
} from 'track-layout/track-layout-store';
import { PublishType } from 'common/common-model';
import PublicationTable from 'publication/publication-table';
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


type Candidate = string;

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
    trackNumbers: candidates.trackNumbers.map(tn => tn.id),
    locationTracks: candidates.locationTracks.map(lt => lt.id),
    referenceLines: candidates.referenceLines.map(rl => rl.id),
    switches: candidates.switches.map(s => s.id),
    kmPosts: candidates.kmPosts.map(s => s.id),
});


const initialCandidates = {
    trackNumbers: [],
    locationTracks: [],
    referenceLines: [],
    switches: [],
    kmPosts: [],
};

function isIncluded(candidate: Candidate, candidateIds: Candidate[], reversed: boolean) {
    return reversed ? !candidateIds.includes(candidate) : candidateIds.includes(candidate);
}

const getPublishCandidate = (previewChanges: PublishCandidates | undefined, publishPreviewChangesIds: SelectedChanges, reversed: boolean) => {
    return {
        trackNumbers: previewChanges?.trackNumbers.filter(tn => isIncluded(tn.id, publishPreviewChangesIds.trackNumbers, reversed)) || [],
        locationTracks: previewChanges?.locationTracks.filter(lt => isIncluded(lt.id, publishPreviewChangesIds.locationTracks, reversed)) || [],
        referenceLines: previewChanges?.referenceLines.filter(rl => isIncluded(rl.id, publishPreviewChangesIds.referenceLines, reversed)) || [],
        switches: previewChanges?.switches.filter(s => isIncluded(s.id, publishPreviewChangesIds.switches, reversed)) || [],
        kmPosts: previewChanges?.kmPosts.filter(km => isIncluded(km.id, publishPreviewChangesIds.kmPosts, reversed)) || [],
    };
};

const getPublishPreviewChanges = (publishPreviewChangesIds: SelectedChanges, previewChanges: PublishCandidates | undefined): PublishCandidates[] => {
    return publishPreviewChangesIds ? [
            getPublishCandidate(previewChanges, publishPreviewChangesIds, true),
            getPublishCandidate(previewChanges, publishPreviewChangesIds, false)]
        : [(previewChanges || initialCandidates), initialCandidates];
};


export const PreviewView: React.FC<PreviewProps> = (props: PreviewProps) => {
    const {t} = useTranslation();
    const allChanges = useLoader(() => getPublishCandidates(), []);
    const allValidatedPreviewChanges: ValidatedPublishCandidates | null | undefined = useLoader(() =>
            (allChanges && validatePublishCandidates(publishCandidateIds(allChanges))) ?? undefined,
        [allChanges]);
    // GVT-1510 currently all changes are fetched above and hence all validated as a single publication unit
    const allPreviewChanges: PublishCandidates | undefined = allValidatedPreviewChanges?.validatedAsPublicationUnit;
    const [previewChanges, publishPreviewChanges]: PublishCandidates[] = getPublishPreviewChanges(props.selectedPublishCandidateIds, allPreviewChanges);

    const [selectedChanges, setSelectedChanges] = React.useState<SelectedChanges>({
        trackNumbers: [],
        referenceLines: [],
        locationTracks: [],
        switches: [],
        kmPosts: [],
    });
    React.useEffect(() => {
        setSelectedChanges({
            trackNumbers: allPreviewChanges ? allPreviewChanges.trackNumbers.map((tn) => tn.id) : [],
            referenceLines: allPreviewChanges ? allPreviewChanges.referenceLines.map((a) => a.id) : [],
            locationTracks: allPreviewChanges ? allPreviewChanges.locationTracks.map((a) => a.id) : [],
            switches: allPreviewChanges ? allPreviewChanges.switches.map((s) => s.id) : [],
            kmPosts: allPreviewChanges ? allPreviewChanges.kmPosts.map((kmp) => kmp.id) : [],
        });
    }, [allPreviewChanges]);
    const calculatedChanges = useLoader(
        () => getCalculatedChanges(selectedChanges),
        [selectedChanges],
    );
    const [mapMode, setMapMode] = React.useState<PublishType>('DRAFT');

    return (
        <React.Fragment>
            <div className={styles['preview-view']} qa-id="preview-content">
                <PreviewToolBar onClosePreview={props.onClosePreview}/>
                <div className={styles['preview-view__changes']}>

                    {(previewChanges && publishPreviewChanges && (
                        <>
                            <section className={styles['preview-section']}>
                                <div className={styles['preview-view__changes-title']}>
                                    <h3>{t('preview-view.other-changes-title')}</h3>
                                </div>
                                <PublicationTable
                                    onPreviewSelect={props.onPreviewSelect}
                                    previewChanges={previewChanges}
                                />
                            </section>

                            <section className={styles['preview-section']}>
                                <div className={styles['preview-view__changes-title']}>
                                    <h3>{t('preview-view.publish-candidates-title')}</h3>
                                </div>
                                <PublicationTable
                                    onPreviewSelect={props.onPublishPreviewRemove}
                                    previewChanges={publishPreviewChanges}
                                    publish={true}
                                />
                            </section>

                            <div className={styles['preview-section']}>
                                {calculatedChanges && (
                                    <CalculatedChangesView calculatedChanges={calculatedChanges}/>
                                )}
                                {!calculatedChanges && <Spinner/>}
                            </div>
                        </>
                    )) || <Spinner/>}
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
                    previewChanges={previewChanges == null ? undefined : previewChanges}
                    onPublishPreviewRevert={props.onPublishPreviewRevert}
                />
            </div>
        </React.Fragment>
    );
};
