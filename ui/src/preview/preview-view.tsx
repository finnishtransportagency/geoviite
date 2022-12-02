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
import {
    LayoutKmPostId,
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
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
import { ChangeTimes } from 'track-layout/track-layout-store';
import { PublishType } from 'common/common-model';
import PublicationTable from 'publication/publication-table';
import { CalculatedChangesView } from './calculated-changes-view';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { PublishCandidates } from 'publication/publication-model';

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
    onViewportChange: (viewport: MapViewport) => void;
    onSelect: OnSelectFunction;
    onHighlightItems: OnHighlightItemsFunction;
    onHoverLocation: OnHoverLocationFunction;
    onClickLocation: OnClickLocationFunction;
    onShownItemsChange: (shownItems: OptionalShownItems) => void;
    onClosePreview: () => void;
};

const publishCandidateIds = (candidates: PublishCandidates): PublishRequest => ({
    trackNumbers: candidates.trackNumbers.map(tn => tn.id),
    locationTracks: candidates.locationTracks.map(lt => lt.id),
    referenceLines: candidates.referenceLines.map(rl => rl.id),
    switches: candidates.switches.map(s => s.id),
    kmPosts: candidates.kmPosts.map(s => s.id),
});

export const PreviewView: React.FC<PreviewProps> = (props: PreviewProps) => {
    const {t} = useTranslation();
    const allChanges = useLoader(() => getPublishCandidates(), []);
    const allPreviewChanges = useLoader(() =>
            (allChanges && validatePublishCandidates(publishCandidateIds(allChanges))) ?? undefined,
        [allChanges]);
    // GVT-1510 currently all changes are fetched above and hence all validated as a single publication unit
    const previewChanges = allPreviewChanges?.validatedAsPublicationUnit

    const [selectedChanges, setSelectedChanges] = React.useState<SelectedChanges>({
        trackNumbers: [],
        referenceLines: [],
        locationTracks: [],
        switches: [],
        kmPosts: [],
    });
    React.useEffect(() => {
        setSelectedChanges({
            trackNumbers: previewChanges ? previewChanges.trackNumbers.map((tn) => tn.id) : [],
            referenceLines: previewChanges ? previewChanges.referenceLines.map((a) => a.id) : [],
            locationTracks: previewChanges ? previewChanges.locationTracks.map((a) => a.id) : [],
            switches: previewChanges ? previewChanges.switches.map((s) => s.id) : [],
            kmPosts: previewChanges ? previewChanges.kmPosts.map((kmp) => kmp.id) : [],
        });
    }, [previewChanges]);
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

                    {(previewChanges && (
                        <>
                            <section className={styles['preview-section']}>
                                <div className={styles['preview-view__changes-title']}>
                                    <h3>{t('preview-view.other-changes-title')}</h3>
                                </div>
                                <PublicationTable previewChanges={previewChanges}/>
                            </section>

                            <section className={styles['preview-section']}>
                                <div className={styles['preview-view__changes-title']}>
                                    <h3>{t('preview-view.publish-candidates-title')}</h3>
                                </div>
                                <PublicationTable previewChanges={previewChanges}/>
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
                />
            </div>
        </React.Fragment>
    );
};
