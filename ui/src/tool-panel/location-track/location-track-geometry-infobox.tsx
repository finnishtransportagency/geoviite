import * as React from 'react';
import Infobox from 'tool-panel/infobox/infobox';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import { getLocationTrackSectionsByPlan } from 'track-layout/layout-location-track-api';
import { PublishType } from 'common/common-model';
import { MapViewport } from 'map/map-model';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { AlignmentPlanSectionInfoboxContent } from 'tool-panel/alignment-plan-section-infobox-content';
import { useTranslation } from 'react-i18next';

type LocationTrackGeometryInfoboxProps = {
    publishType: PublishType;
    locationTrackId: LocationTrackId;
    viewport: MapViewport;
};

export const LocationTrackGeometryInfobox: React.FC<LocationTrackGeometryInfoboxProps> = ({
    publishType,
    locationTrackId,
    viewport,
}) => {
    const { t } = useTranslation();
    const [useBoungingBox, setUseBoundingBox] = React.useState(true);
    const viewportDep = useBoungingBox && viewport;
    const [sections, elementFetchStatus] = useLoaderWithStatus(
        () =>
            getLocationTrackSectionsByPlan(
                publishType,
                locationTrackId,
                useBoungingBox ? viewport.area : undefined,
            ),
        [locationTrackId, viewportDep],
    );

    return (
        <Infobox title={t('tool-panel.alignment-plan-sections.location-track-geometries')}>
            <InfoboxContent>
                <InfoboxField
                    label={t('tool-panel.alignment-plan-sections.bounding-box-geometries')}
                    value={
                        <Checkbox
                            checked={useBoungingBox}
                            onChange={(e) => setUseBoundingBox(e.target.checked)}
                        />
                    }
                />
                {elementFetchStatus === LoaderStatus.Ready ? (
                    <AlignmentPlanSectionInfoboxContent sections={sections || []} />
                ) : (
                    <Spinner />
                )}
            </InfoboxContent>
        </Infobox>
    );
};
