import * as React from 'react';
import Infobox from 'tool-panel/infobox/infobox';
import { LayoutTrackNumberId } from 'track-layout/track-layout-model';
import { LoaderStatus, useRateLimitedLoaderWithStatus } from 'utils/react-utils';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import { LayoutContext, TimeStamp } from 'common/common-model';
import { MapViewport } from 'map/map-model';
import {
    AlignmentPlanSectionInfoboxContent,
    HighlightedReferenceLine,
    OnHighlightSection,
} from 'tool-panel/alignment-plan-section-infobox-content';
import { useTranslation } from 'react-i18next';
import {
    ProgressIndicatorType,
    ProgressIndicatorWrapper,
} from 'vayla-design-lib/progress/progress-indicator-wrapper';
import { getTrackNumberReferenceLineSectionsByPlan } from 'track-layout/layout-track-number-api';

type TrackNumberGeometryInfoboxProps = {
    layoutContext: LayoutContext;
    trackNumberId: LayoutTrackNumberId;
    viewport: MapViewport;
    contentVisible: boolean;
    onContentVisibilityChange: () => void;
    onHighlightItem: (item: HighlightedReferenceLine | undefined) => void;
    changeTime: TimeStamp;
};

export const TrackNumberGeometryInfobox: React.FC<TrackNumberGeometryInfoboxProps> = ({
    layoutContext,
    trackNumberId,
    viewport,
    contentVisible,
    onContentVisibilityChange,
    onHighlightItem,
    changeTime,
}) => {
    const { t } = useTranslation();
    const [useBoundingBox, setUseBoundingBox] = React.useState(true);
    const viewportDep = useBoundingBox && viewport;
    const [sections, elementFetchStatus] = useRateLimitedLoaderWithStatus(
        () =>
            getTrackNumberReferenceLineSectionsByPlan(
                layoutContext,
                trackNumberId,
                useBoundingBox ? viewport.area : undefined,
            ),
        1000,
        [
            trackNumberId,
            layoutContext.publicationState,
            layoutContext.branch,
            viewportDep,
            changeTime,
        ],
    );
    const onHighlightSection: OnHighlightSection = (section) =>
        onHighlightItem(
            section === undefined
                ? undefined
                : {
                      ...section,
                      id: trackNumberId,
                      type: 'REFERENCE_LINE',
                  },
        );
    return (
        <Infobox
            title={t('tool-panel.alignment-plan-sections.reference-line-geometries')}
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
                                'tool-panel.alignment-plan-sections.no-geometries-for-reference-line',
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
