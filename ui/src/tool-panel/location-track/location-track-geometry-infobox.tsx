import * as React from 'react';
import Infobox from 'tool-panel/infobox/infobox';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { LoaderStatus, useRateLimitedLoaderWithStatus } from 'utils/react-utils';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import { getLocationTrackSectionsByPlan } from 'track-layout/layout-location-track-api';
import { PublishType } from 'common/common-model';
import { MapViewport } from 'map/map-model';
import {
    AlignmentPlanSectionInfoboxContent,
    HighlightedAlignment,
} from 'tool-panel/alignment-plan-section-infobox-content';
import { useTranslation } from 'react-i18next';
import {
    ProgressIndicatorType,
    ProgressIndicatorWrapper,
} from 'vayla-design-lib/progress/progress-indicator-wrapper';
import { BoundingBox } from 'model/geometry';

type LocationTrackGeometryInfoboxProps = {
    publishType: PublishType;
    locationTrackId: LocationTrackId;
    viewport: MapViewport;
    contentVisible: boolean;
    onContentVisibilityChange: () => void;
    onHighlightItem: (item: HighlightedAlignment | undefined) => void;
    showArea: (boundingBox: BoundingBox) => void;
};

export const LocationTrackGeometryInfobox: React.FC<LocationTrackGeometryInfoboxProps> = ({
    publishType,
    locationTrackId,
    viewport,
    contentVisible,
    onContentVisibilityChange,
    onHighlightItem,
    showArea,
}) => {
    const { t } = useTranslation();
    const [useBoundingBox, setUseBoundingBox] = React.useState(true);
    const viewportDep = useBoundingBox && viewport;
    const [sections, elementFetchStatus] = useRateLimitedLoaderWithStatus(
        () =>
            getLocationTrackSectionsByPlan(
                publishType,
                locationTrackId,
                useBoundingBox ? viewport.area : undefined,
            ),
        1000,
        [locationTrackId, publishType, viewportDep],
    );

    return (
        <Infobox
            title={t('tool-panel.alignment-plan-sections.location-track-geometries')}
            contentVisible={contentVisible}
            onContentVisibilityChange={onContentVisibilityChange}>
            <InfoboxContent>
                <InfoboxField
                    label={t('tool-panel.alignment-plan-sections.bounding-box-geometries')}
                    value={
                        <Checkbox
                            checked={useBoundingBox}
                            onChange={(e) => setUseBoundingBox(e.target.checked)}
                        />
                    }
                />
                <ProgressIndicatorWrapper
                    indicator={ProgressIndicatorType.Area}
                    inProgress={elementFetchStatus !== LoaderStatus.Ready}
                    inline={false}>
                    {sections && sections.length == 0 ? (
                        <p className={'infobox__text'}>
                            {t(
                                'tool-panel.alignment-plan-sections.no-geometries-for-location-track',
                            )}
                        </p>
                    ) : (
                        <AlignmentPlanSectionInfoboxContent
                            id={locationTrackId}
                            sections={sections || []}
                            onHighlightItem={onHighlightItem}
                            showArea={showArea}
                            type={'LOCATION_TRACK'}
                        />
                    )}
                </ProgressIndicatorWrapper>
            </InfoboxContent>
        </Infobox>
    );
};
