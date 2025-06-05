import * as React from 'react';
import { LayoutLocationTrack, LayoutReferenceLine } from 'track-layout/track-layout-model';
import Infobox from 'tool-panel/infobox/infobox';
import { useTranslation } from 'react-i18next';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { GeometryPlanId } from 'geometry/geometry-model';
import { usePlanHeader } from 'track-layout/track-layout-react-utils';
import GeometryPlanInfobox from 'tool-panel/geometry-plan/geometry-plan-infobox';
import GeometryAlignmentLinkingInfobox from 'tool-panel/geometry-alignment/geometry-alignment-linking-infobox';
import {
    GeometryLinkingAlignmentLockParameters,
    GeometryPreliminaryLinkingParameters,
    LinkingState,
    LinkingType,
} from 'linking/linking-model';
import { OnSelectOptions, OptionalUnselectableItemCollections } from 'selection/selection-model';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { BoundingBox } from 'model/geometry';
import { GeometryAlignmentHeader } from 'track-layout/layout-map-api';
import { GeometryAlignmentInfoboxVisibilities } from 'track-layout/track-layout-slice';
import { GeometryAlignmentVerticalGeometryInfobox } from 'tool-panel/geometry-alignment/geometry-alignment-vertical-geometry-infobox';
import { ChangeTimes } from 'common/common-slice';
import { LayoutContext } from 'common/common-model';

type GeometryAlignmentInfoboxProps = {
    onSelect: (options: OnSelectOptions) => void;
    onUnselect: (items: OptionalUnselectableItemCollections) => void;
    geometryAlignment: GeometryAlignmentHeader;
    selectedLayoutLocationTrack?: LayoutLocationTrack;
    selectedLayoutReferenceLine?: LayoutReferenceLine;
    planId: GeometryPlanId;
    changeTimes: ChangeTimes;
    linkingState?: LinkingState;
    onLinkingStart: (startParams: GeometryPreliminaryLinkingParameters) => void;
    onLockAlignment: (lockParameters: GeometryLinkingAlignmentLockParameters) => void;
    onStopLinking: () => void;
    resolution: number;
    layoutContext: LayoutContext;
    showArea: (area: BoundingBox) => void;
    visibilities: GeometryAlignmentInfoboxVisibilities;
    onVisibilityChange: (visibilities: GeometryAlignmentInfoboxVisibilities) => void;
    onVerticalGeometryDiagramVisibilityChange: (visibility: boolean) => void;
    verticalGeometryDiagramVisible: boolean;
};

const GeometryAlignmentInfobox: React.FC<GeometryAlignmentInfoboxProps> = ({
    onSelect,
    onUnselect,
    geometryAlignment,
    selectedLayoutLocationTrack,
    selectedLayoutReferenceLine,
    planId,
    changeTimes,
    linkingState,
    onLinkingStart,
    onStopLinking,
    onLockAlignment,
    resolution,
    layoutContext,
    showArea,
    visibilities,
    onVisibilityChange,
    onVerticalGeometryDiagramVisibilityChange,
    verticalGeometryDiagramVisible,
}: GeometryAlignmentInfoboxProps) => {
    const { t } = useTranslation();
    const planHeader = usePlanHeader(planId);

    const visibilityChange = (key: keyof GeometryAlignmentInfoboxVisibilities) => {
        onVisibilityChange({ ...visibilities, [key]: !visibilities[key] });
    };

    return (
        <React.Fragment>
            <Infobox
                contentVisible={visibilities.basic}
                onContentVisibilityChange={() => visibilityChange('basic')}
                title={t('tool-panel.alignment.geometry.title')}
                qa-id="geometry-alignment-infobox">
                <InfoboxContent>
                    <InfoboxField
                        qaId="geometry-alignment-name"
                        label={t('tool-panel.alignment.geometry.name')}
                        value={geometryAlignment.name}
                    />
                    <InfoboxField
                        qaId="geometry-alignment-track-number"
                        label={t('tool-panel.alignment.geometry.reference-line')}
                        value={t(
                            geometryAlignment.alignmentType === 'REFERENCE_LINE' ? 'yes' : 'no',
                        )}
                    />
                    <InfoboxButtons>
                        <Button
                            size={ButtonSize.SMALL}
                            variant={ButtonVariant.SECONDARY}
                            qa-id="zoom-to-geometry-alignment"
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
                    contentVisible={visibilities.linking}
                    onContentVisibilityChange={() => visibilityChange('linking')}
                    onSelect={onSelect}
                    onUnselect={onUnselect}
                    geometryAlignment={geometryAlignment}
                    selectedLayoutLocationTrack={selectedLayoutLocationTrack}
                    selectedLayoutReferenceLine={selectedLayoutReferenceLine}
                    planId={planId}
                    changeTimes={changeTimes}
                    linkingState={linkingState}
                    onLinkingStart={onLinkingStart}
                    onStopLinking={onStopLinking}
                    onLockAlignment={onLockAlignment}
                    resolution={resolution}
                    layoutContext={layoutContext}
                />
            )}

            {planHeader && (
                <GeometryPlanInfobox
                    planHeader={planHeader}
                    visibilities={{
                        plan: visibilities.plan,
                        planQuality: visibilities.planQuality,
                    }}
                    onVisibilityChange={(v) => {
                        onVisibilityChange({ ...visibilities, ...v });
                    }}
                    changeTimes={changeTimes}
                />
            )}
            <GeometryAlignmentVerticalGeometryInfobox
                contentVisible={visibilities.verticalGeometry}
                onContentVisibilityChange={() => visibilityChange('verticalGeometry')}
                onVerticalGeometryDiagramVisibilityChange={
                    onVerticalGeometryDiagramVisibilityChange
                }
                verticalGeometryDiagramVisible={verticalGeometryDiagramVisible}
            />
        </React.Fragment>
    );
};

export default React.memo(GeometryAlignmentInfobox);
