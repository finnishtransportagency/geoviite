import * as React from 'react';
import Infobox from 'tool-panel/infobox/infobox';
import { LayoutKmPost } from 'track-layout/track-layout-model';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { useTranslation } from 'react-i18next';
import { GeometryPlanId } from 'geometry/geometry-model';
import { usePlanHeader } from 'track-layout/track-layout-react-utils';
import GeometryPlanInfobox from 'tool-panel/geometry-plan-infobox';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { GeometryKmPostInfoboxVisibilities } from 'track-layout/track-layout-slice';
import GeometryKmPostLinkingContainer from 'tool-panel/km-post/geometry-km-post-infobox-linking-container';
import { ChangeTimes } from 'common/common-slice';

type GeometryKmPostInfoboxProps = {
    geometryKmPost: LayoutKmPost;
    planId: GeometryPlanId;
    onShowOnMap: () => void;
    visibilities: GeometryKmPostInfoboxVisibilities;
    onVisibilityChange: (visibilities: GeometryKmPostInfoboxVisibilities) => void;
    changeTimes: ChangeTimes;
};

const GeometryKmPostInfobox: React.FC<GeometryKmPostInfoboxProps> = ({
    geometryKmPost,
    planId,
    onShowOnMap,
    visibilities,
    onVisibilityChange,
    changeTimes,
}: GeometryKmPostInfoboxProps) => {
    const { t } = useTranslation();
    const plan = usePlanHeader(planId);

    const visibilityChange = (key: keyof GeometryKmPostInfoboxVisibilities) => {
        onVisibilityChange({ ...visibilities, [key]: !visibilities[key] });
    };

    return (
        <React.Fragment>
            <Infobox
                contentVisible={visibilities.basic}
                onContentVisibilityChange={() => visibilityChange('basic')}
                title={t('tool-panel.km-post.geometry.general-title')}
                qa-id="geometry-km-post-infobox">
                <InfoboxContent>
                    <InfoboxField
                        label={t('tool-panel.km-post.geometry.km-post')}
                        value={geometryKmPost.kmNumber}
                    />
                    <InfoboxButtons>
                        <Button
                            size={ButtonSize.SMALL}
                            disabled={!geometryKmPost.layoutLocation}
                            variant={ButtonVariant.SECONDARY}
                            onClick={() => onShowOnMap()}>
                            {t('tool-panel.km-post.geometry.show-on-map')}
                        </Button>
                    </InfoboxButtons>
                </InfoboxContent>
            </Infobox>
            <GeometryKmPostLinkingContainer
                geometryKmPost={geometryKmPost}
                planId={planId}
                contentVisible={visibilities.linking}
                onContentVisibilityChange={() => visibilityChange('linking')}
            />
            {plan && (
                <GeometryPlanInfobox
                    planHeader={plan}
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
        </React.Fragment>
    );
};

export default GeometryKmPostInfobox;
