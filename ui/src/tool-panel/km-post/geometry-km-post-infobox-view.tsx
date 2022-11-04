import * as React from 'react';
import Infobox from 'tool-panel/infobox/infobox';
import { LayoutKmPost } from 'track-layout/track-layout-model';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { useTranslation } from 'react-i18next';
import { GeometryKmPostId, GeometryPlanId } from 'geometry/geometry-model';
import { usePlanHeader } from 'track-layout/track-layout-react-utils';
import GeometryPlanInfobox from 'tool-panel/geometry-plan-infobox';
import { PublishType, TimeStamp } from 'common/common-model';
import GeometryKmPostLinkingInfobox from 'tool-panel/km-post/geometry-km-post-linking-infobox';
import { LinkingKmPost } from 'linking/linking-model';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';

type GeometryKmPostInfoboxProps = {
    geometryKmPost: LayoutKmPost;
    planId: GeometryPlanId;
    layoutKmPost?: LayoutKmPost;
    kmPostChangeTime: TimeStamp;
    linkingState: LinkingKmPost | undefined;
    startLinking: (geometryKmPostId: GeometryKmPostId) => void;
    stopLinking: () => void;
    onKmPostSelect: (kmPost: LayoutKmPost) => void;
    publishType: PublishType;
    onShowOnMap: () => void;
};

const GeometryKmPostInfoboxView: React.FC<GeometryKmPostInfoboxProps> = ({
    geometryKmPost,
    planId,
    layoutKmPost,
    kmPostChangeTime,
    linkingState,
    startLinking,
    stopLinking,
    onKmPostSelect,
    publishType,
    onShowOnMap,
}: GeometryKmPostInfoboxProps) => {
    const { t } = useTranslation();
    const plan = usePlanHeader(planId);

    return (
        <React.Fragment>
            <Infobox
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
                            disabled={!geometryKmPost.location}
                            variant={ButtonVariant.SECONDARY}
                            onClick={() => onShowOnMap()}>
                            {t('tool-panel.km-post.geometry.show-on-map')}
                        </Button>
                    </InfoboxButtons>
                </InfoboxContent>
            </Infobox>

            <GeometryKmPostLinkingInfobox
                geometryKmPost={geometryKmPost}
                planId={planId}
                layoutKmPost={layoutKmPost}
                kmPostChangeTime={kmPostChangeTime}
                linkingState={linkingState}
                startLinking={startLinking}
                stopLinking={stopLinking}
                onKmPostSelect={onKmPostSelect}
                publishType={publishType}
            />

            {plan && <GeometryPlanInfobox planHeader={plan} />}
        </React.Fragment>
    );
};

export default GeometryKmPostInfoboxView;
