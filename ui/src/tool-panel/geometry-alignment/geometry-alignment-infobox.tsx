import * as React from 'react';
import {
    LayoutLocationTrack,
    LayoutReferenceLine,
    MapAlignment,
    MapSegment,
} from 'track-layout/track-layout-model';
import Infobox from 'tool-panel/infobox/infobox';
import { useTranslation } from 'react-i18next';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { GeometryPlanId } from 'geometry/geometry-model';
import { usePlanHeader } from 'track-layout/track-layout-react-utils';
import GeometryPlanInfobox from 'tool-panel/geometry-plan-infobox';
import GeometryAlignmentLinkingInfobox from 'tool-panel/geometry-alignment/geometry-alignment-linking-infobox';
import { PublishType, TimeStamp } from 'common/common-model';
import {
    GeometryLinkingAlignmentLockParameters,
    GeometryPreliminaryLinkingParameters,
    LinkingState,
    LinkingType,
} from 'linking/linking-model';
import GeometrySegmentInfobox from 'tool-panel/geometry-alignment/geometry-segment/geometry-segment-infobox';
import { OnSelectOptions } from 'selection/selection-model';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { BoundingBox } from 'model/geometry';
import InfoboxText from 'tool-panel/infobox/infobox-text';

type GeometryAlignmentInfoboxProps = {
    onSelect: (options: OnSelectOptions) => void;
    geometryAlignment: MapAlignment;
    selectedLayoutLocationTrack?: LayoutLocationTrack;
    selectedLayoutReferenceLine?: LayoutReferenceLine;
    segment?: MapSegment;
    planId: GeometryPlanId;
    locationTrackChangeTime: TimeStamp;
    trackNumberChangeTime: TimeStamp;
    linkingState?: LinkingState;
    onLinkingStart: (startParams: GeometryPreliminaryLinkingParameters) => void;
    onLockAlignment: (lockParameters: GeometryLinkingAlignmentLockParameters) => void;
    onStopLinking: () => void;
    resolution: number;
    publishType: PublishType;
    showArea: (area: BoundingBox) => void;
};

const GeometryAlignmentInfobox: React.FC<GeometryAlignmentInfoboxProps> = ({
    onSelect,
    geometryAlignment,
    selectedLayoutLocationTrack,
    selectedLayoutReferenceLine,
    segment,
    planId,
    locationTrackChangeTime,
    trackNumberChangeTime,
    linkingState,
    onLinkingStart,
    onStopLinking,
    onLockAlignment,
    resolution,
    publishType,
    showArea,
}: GeometryAlignmentInfoboxProps) => {
    const { t } = useTranslation();
    const planHeader = usePlanHeader(planId);

    return (
        <React.Fragment>
            <Infobox
                title={t('tool-panel.alignment.geometry.title')}
                qa-id="geometry-alignment-infobox">
                <InfoboxContent>
                    <InfoboxField
                        label={t('tool-panel.alignment.geometry.name')}
                        value={geometryAlignment.name}
                    />
                    <InfoboxField
                        label={t('tool-panel.alignment.geometry.reference-line')}
                        value={t(
                            geometryAlignment.alignmentType === 'REFERENCE_LINE' ? 'yes' : 'no',
                        )}
                    />
                    <InfoboxButtons>
                        <Button
                            size={ButtonSize.SMALL}
                            variant={ButtonVariant.SECONDARY}
                            onClick={() =>
                                geometryAlignment.boundingBox &&
                                showArea(geometryAlignment.boundingBox)
                            }>
                            {t('tool-panel.alignment.geometry.show-on-map')}
                        </Button>
                    </InfoboxButtons>
                </InfoboxContent>
            </Infobox>
            {(!linkingState ||
                linkingState.type === LinkingType.UnknownAlignment ||
                linkingState.type === LinkingType.LinkingGeometryWithEmptyAlignment ||
                linkingState.type === LinkingType.LinkingGeometryWithAlignment) && (
                <GeometryAlignmentLinkingInfobox
                    onSelect={onSelect}
                    geometryAlignment={geometryAlignment}
                    selectedLayoutLocationTrack={selectedLayoutLocationTrack}
                    selectedLayoutReferenceLine={selectedLayoutReferenceLine}
                    planId={planId}
                    locationTrackChangeTime={locationTrackChangeTime}
                    trackNumberChangeTime={trackNumberChangeTime}
                    linkingState={linkingState}
                    onLinkingStart={onLinkingStart}
                    onStopLinking={onStopLinking}
                    onLockAlignment={onLockAlignment}
                    resolution={resolution}
                    publishType={publishType}
                />
            )}

            {planHeader && <GeometryPlanInfobox planHeader={planHeader} />}
            {planHeader &&
                (segment ? (
                    <GeometrySegmentInfobox chosenSegment={segment} planHeader={planHeader} />
                ) : (
                    <Infobox title={t('tool-panel.alignment.geometry-segment.title')}>
                        <InfoboxContent>
                            <InfoboxText
                                value={t(
                                    'tool-panel.alignment.geometry-segment.no-segment-selected',
                                )}
                            />
                        </InfoboxContent>
                    </Infobox>
                ))}
        </React.Fragment>
    );
};

export default React.memo(GeometryAlignmentInfobox);
