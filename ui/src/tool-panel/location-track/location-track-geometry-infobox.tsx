import * as React from 'react';
import Infobox from 'tool-panel/infobox/infobox';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { LoaderStatus, useRateLimitedLoaderWithStatus } from 'utils/react-utils';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
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
import { InfoboxList, InfoboxListRow } from 'tool-panel/infobox/infobox-list';

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
    const onHighlightSection: OnHighlightSection = React.useCallback(
        (section) =>
            onHighlightItem(
                section === undefined
                    ? undefined
                    : {
                          ...section,
                          id: locationTrackId,
                          type: 'LOCATION_TRACK',
                      },
            ),
        [onHighlightItem, locationTrackId],
    );

    return (
        <Infobox
            title={t('tool-panel.alignment-plan-sections.location-track-geometries')}
            contentVisible={contentVisible}
            onContentVisibilityChange={onContentVisibilityChange}>
            <InfoboxContent>
                <InfoboxList>
                    <InfoboxListRow
                        label={t('tool-panel.alignment-plan-sections.bounding-box-geometries')}
                        content={
                            <Checkbox
                                extraClassName="alignment-plan-section-infobox__navigation-checkbox"
                                checked={useBoundingBox}
                                onChange={(e) => setUseBoundingBox(e.target.checked)}
                            />
                        }
                    />
                </InfoboxList>
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
