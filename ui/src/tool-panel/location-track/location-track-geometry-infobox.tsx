import * as React from 'react';
import Infobox from 'tool-panel/infobox/infobox';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { LoaderStatus, useRateLimitedLoaderWithStatus } from 'utils/react-utils';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import { getLocationTrackSectionsByPlan } from 'track-layout/layout-location-track-api';
import { MapViewport } from 'map/map-model';
import {
    AlignmentPlanSectionInfoboxContent,
    HighlightedLocationTrack,
    OnHighlightSection,
} from 'tool-panel/alignment-plan-section-infobox-content';
import { useTranslation } from 'react-i18next';
import {
    ProgressIndicatorType,
    ProgressIndicatorWrapper,
} from 'vayla-design-lib/progress/progress-indicator-wrapper';
import { LayoutContext } from 'common/common-model';

type LocationTrackGeometryInfoboxProps = {
    layoutContext: LayoutContext;
    locationTrackId: LocationTrackId;
    viewport: MapViewport;
    contentVisible: boolean;
    onContentVisibilityChange: () => void;
    onHighlightItem: (item: HighlightedLocationTrack | undefined) => void;
};

export const LocationTrackGeometryInfobox: React.FC<LocationTrackGeometryInfoboxProps> = ({
    layoutContext,
    locationTrackId,
    viewport,
    contentVisible,
    onContentVisibilityChange,
    onHighlightItem,
}) => {
    const { t } = useTranslation();
    const [useBoundingBox, setUseBoundingBox] = React.useState(true);
    const viewportDep = useBoundingBox && viewport;
    const [sections, elementFetchStatus] = useRateLimitedLoaderWithStatus(
        () =>
            getLocationTrackSectionsByPlan(
                layoutContext,
                locationTrackId,
                useBoundingBox ? viewport.area : undefined,
            ),
        1000,
        [locationTrackId, layoutContext.publicationState, layoutContext.branch, viewportDep],
    );
    const onHighlightSection: OnHighlightSection = (section) =>
        onHighlightItem(
            section === undefined
                ? undefined
                : {
                      ...section,
                      id: locationTrackId,
                      type: 'LOCATION_TRACK',
                  },
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
                    {sections && sections.length === 0 ? (
                        <p className={'infobox__text'}>
                            {t(
                                'tool-panel.alignment-plan-sections.no-geometries-for-location-track',
                            )}
                        </p>
                    ) : (
                        <AlignmentPlanSectionInfoboxContent
                            onHighlightSection={onHighlightSection}
                            sections={sections || []}
                        />
                    )}
                </ProgressIndicatorWrapper>
            </InfoboxContent>
        </Infobox>
    );
};
