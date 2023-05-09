import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import * as React from 'react';
import { useMemo } from 'react';
import {
    debouncedGetGeometryPlanHeaders,
    debouncedSearchTracks,
    getGeometryPlanOptions,
    getLocationTrackOptions,
} from 'data-products/data-products-utils';
import { LayoutLocationTrack } from 'track-layout/track-layout-model';
import { useTranslation } from 'react-i18next';
import VerticalGeometryDiagram from 'vertical-geometry/vertical-geometry-diagram';
import { GeometryAlignment, GeometryPlanHeader, PlanSource } from 'geometry/geometry-model';
import { getGeometryPlan } from 'geometry/geometry-api';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import { OnSelectOptions } from 'selection/selection-model';
import { useCommonDataAppSelector } from 'store/hooks';

const VerticalGeometryDiagramDemoPage: React.FC = () => {
    const { t } = useTranslation();
    const [locationTrack, setLocationTrack] = React.useState<LayoutLocationTrack>();
    const [planSource, setPlanSource] = React.useState<PlanSource>('PAIKANNUSPALVELU');
    const [geometryPlanHeader, setGeometryPlanHeader] = React.useState<GeometryPlanHeader>();
    const [selectedAlignment, setSelectedAlignment] = React.useState<GeometryAlignment>();
    const [geometryAlignments, setGeometryAlignments] = React.useState<GeometryAlignment[]>([]);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);

    const getLocationTracks = React.useCallback(
        (searchTerm) =>
            debouncedSearchTracks(searchTerm, 'OFFICIAL', 10).then((locationTracks) =>
                getLocationTrackOptions(locationTracks, locationTrack),
            ),
        [locationTrack],
    );
    const getGeometryPlanHeaders = React.useCallback(
        (searchTerm) =>
            debouncedGetGeometryPlanHeaders('PAIKANNUSPALVELU', searchTerm).then(
                (geometryPlanHeaders) =>
                    getGeometryPlanOptions(geometryPlanHeaders, geometryPlanHeader),
            ),
        [geometryPlanHeader],
    );

    const selectGeometrySource = (source: PlanSource) => {
        setGeometryPlanHeader(undefined);
        setGeometryAlignments([]);
        setSelectedAlignment(undefined);
        setPlanSource(source);
    };

    const selectGeometryPlan = (header: GeometryPlanHeader) => {
        setGeometryPlanHeader(header);
        setGeometryAlignments([]);
        setSelectedAlignment(undefined);
        getGeometryPlan(header.id).then((plan) => setGeometryAlignments(plan?.alignments ?? []));
    };

    const locationTrackAlignmentId = useMemo(
        () =>
            locationTrack != undefined && {
                locationTrackId: locationTrack.id,
                publishType: 'OFFICIAL' as const,
            },
        [locationTrack],
    );

    const planAlignmentId = useMemo(
        () =>
            geometryPlanHeader != undefined &&
            selectedAlignment != undefined && {
                planId: geometryPlanHeader.id,
                alignmentId: selectedAlignment.id,
            },
        [geometryPlanHeader, selectedAlignment],
    );

    const noopActions = {
        onSelect: (options: OnSelectOptions) => {
            console.log(options);
        },
    };

    return (
        <div style={{ width: '100%' }}>
            Sijaintiraide:
            <Dropdown
                value={locationTrack}
                getName={(item) => item.name}
                placeholder={t('data-products.search.search')}
                options={getLocationTracks}
                searchable
                onChange={setLocationTrack}
                unselectText={t('data-products.search.not-selected')}
                wideList
                wide
            />
            {locationTrackAlignmentId && (
                <VerticalGeometryDiagram
                    alignmentId={locationTrackAlignmentId}
                    {...noopActions}
                    changeTimes={changeTimes}
                />
            )}
            <br />
            Suunnitelma:
            <Checkbox
                checked={planSource === 'PAIKANNUSPALVELU'}
                onChange={() => selectGeometrySource('PAIKANNUSPALVELU')}>
                Paikannuspalvelu
            </Checkbox>
            <Checkbox
                checked={planSource === 'GEOMETRIAPALVELU'}
                onChange={() => selectGeometrySource('GEOMETRIAPALVELU')}>
                Geometriapalvelu
            </Checkbox>
            <Dropdown
                value={geometryPlanHeader}
                getName={(item: GeometryPlanHeader) => item.fileName}
                placeholder={t('data-products.search.search')}
                options={getGeometryPlanHeaders}
                searchable
                onChange={selectGeometryPlan}
                unselectText={t('data-products.search.not-selected')}
                wideList
                wide
            />
            Suunnitelman raide:
            <Dropdown
                value={selectedAlignment}
                getName={(item: GeometryAlignment) => item.name}
                options={geometryAlignments.map((a) => ({ name: a.name, value: a }))}
                onChange={setSelectedAlignment}
            />
            {planAlignmentId && (
                <VerticalGeometryDiagram
                    alignmentId={planAlignmentId}
                    {...noopActions}
                    changeTimes={changeTimes}
                />
            )}
        </div>
    );
};

export default VerticalGeometryDiagramDemoPage;
