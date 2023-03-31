import * as React from 'react';
import Infobox from 'tool-panel/infobox/infobox';
import { LayoutTrackNumberId } from 'track-layout/track-layout-model';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import { PublishType } from 'common/common-model';
import { MapViewport } from 'map/map-model';
import { AlignmentPlanSectionInfoboxContent } from 'tool-panel/alignment-plan-section-infobox-content';
import { useTranslation } from 'react-i18next';
import {
    ProgressIndicatorType,
    ProgressIndicatorWrapper,
} from 'vayla-design-lib/progress/progress-indicator-wrapper';
import { getTrackNumberReferenceLineSectionsByPlan } from 'track-layout/layout-track-number-api';
import { AlignmentPlanSection } from 'track-layout/layout-location-track-api';

type TrackNumberGeometryInfoboxProps = {
    publishType: PublishType;
    trackNumberId: LayoutTrackNumberId;
    viewport: MapViewport;
};

export const TrackNumberGeometryInfobox: React.FC<TrackNumberGeometryInfoboxProps> = ({
    publishType,
    trackNumberId,
    viewport,
}) => {
    const { t } = useTranslation();
    const [useBoungingBox, setUseBoundingBox] = React.useState(true);
    const viewportDep = useBoungingBox && viewport;
    const [sections, elementFetchStatus] = useLoaderWithStatus(
        () =>
            getTrackNumberReferenceLineSectionsByPlan(
                publishType,
                trackNumberId,
                useBoungingBox ? viewport.area : undefined,
            ),
        [trackNumberId, publishType, viewportDep],
    );

    function highlightReferenceLineSection(
        trackNumberId: LayoutTrackNumberId,
        section: AlignmentPlanSection,
    ) {
        // TODO
        console.log('highlight reference line section', trackNumberId, section);
    }

    return (
        <Infobox title={t('tool-panel.alignment-plan-sections.reference-line-geometries')}>
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
                            sections={sections || []}
                            highlightSection={(section) =>
                                highlightReferenceLineSection(trackNumberId, section)
                            }
                        />
                    )}
                </ProgressIndicatorWrapper>
            </InfoboxContent>
        </Infobox>
    );
};
