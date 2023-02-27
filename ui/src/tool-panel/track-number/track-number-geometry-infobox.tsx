import * as React from 'react';
import Infobox from 'tool-panel/infobox/infobox';
import { ReferenceLineId } from 'track-layout/track-layout-model';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import { PublishType } from 'common/common-model';
import { MapViewport } from 'map/map-model';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { AlignmentPlanSectionInfoboxContent } from 'tool-panel/alignment-plan-section-infobox-content';
import { getReferenceLineSectionsByPlan } from 'track-layout/layout-reference-line-api';
import { useTranslation } from 'react-i18next';

type TrackNumberGeometryInfoboxProps = {
    publishType: PublishType;
    referenceLineId: ReferenceLineId;
    viewport: MapViewport;
};

export const TrackNumberGeometryInfobox: React.FC<TrackNumberGeometryInfoboxProps> = ({
    publishType,
    referenceLineId,
    viewport,
}) => {
    const { t } = useTranslation();
    const [useBoungingBox, setUseBoundingBox] = React.useState(true);
    const viewportDep = useBoungingBox && viewport;
    const [sections, elementFetchStatus] = useLoaderWithStatus(
        () =>
            getReferenceLineSectionsByPlan(
                publishType,
                referenceLineId,
                useBoungingBox ? viewport.area : undefined,
            ),
        [referenceLineId, viewportDep],
    );

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
                {elementFetchStatus === LoaderStatus.Ready ? (
                    <AlignmentPlanSectionInfoboxContent sections={sections || []} />
                ) : (
                    <Spinner />
                )}
            </InfoboxContent>
        </Infobox>
    );
};
