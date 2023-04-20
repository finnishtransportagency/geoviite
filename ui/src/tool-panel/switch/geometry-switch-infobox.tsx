import * as React from 'react';
import Infobox from 'tool-panel/infobox/infobox';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { makeSwitchImage } from 'geoviite-design-lib/switch/switch-icons';
import { IconColor, IconSize } from 'vayla-design-lib/icon/Icon';
import { useTranslation } from 'react-i18next';
import SwitchHand from 'geoviite-design-lib/switch/switch-hand';
import { GeometryPlanId, GeometrySwitchId } from 'geometry/geometry-model';
import { usePlanHeader } from 'track-layout/track-layout-react-utils';
import GeometryPlanInfobox from 'tool-panel/geometry-plan-infobox';
import { useLoader } from 'utils/react-utils';
import { getGeometrySwitch, getGeometrySwitchLayout } from 'geometry/geometry-api';
import { LinkingState, LinkingType, SuggestedSwitch } from 'linking/linking-model';
import GeometrySwitchLinkingContainer from 'tool-panel/switch/geometry-switch-linking-container';
import { TimeStamp } from 'common/common-model';
import { LayoutSwitch } from 'track-layout/track-layout-model';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Point } from 'model/geometry';
import { getSwitchStructure } from 'common/common-api';

type GeometrySwitchInfoboxProps = {
    switchId?: GeometrySwitchId;
    planId?: GeometryPlanId;
    linkingState?: LinkingState;
    suggestedSwitch?: SuggestedSwitch;
    switchChangeTime: TimeStamp;
    locationTrackChangeTime: TimeStamp;
    layoutSwitch?: LayoutSwitch;
    onShowOnMap: (location: Point) => void;
};

const GeometrySwitchInfobox: React.FC<GeometrySwitchInfoboxProps> = ({
    switchId,
    planId,
    linkingState,
    suggestedSwitch,
    switchChangeTime,
    locationTrackChangeTime,
    layoutSwitch,
    onShowOnMap,
}: GeometrySwitchInfoboxProps) => {
    const { t } = useTranslation();
    const switchItem = useLoader(
        () => (switchId ? getGeometrySwitch(switchId) : undefined),
        [switchId],
    );
    const geometrySwitchLayout = useLoader(
        () => (switchId ? getGeometrySwitchLayout(switchId) : undefined),
        [switchId],
    );
    const switchStructure = useLoader(
        () =>
            switchItem && switchItem.switchStructureId
                ? getSwitchStructure(switchItem.switchStructureId)
                : undefined,
        [switchItem],
    );
    const switchLocation = geometrySwitchLayout && geometrySwitchLayout.joints[0]?.location;

    const SwitchImage =
        switchStructure && makeSwitchImage(switchStructure.baseType, switchStructure.hand);
    const planHeader = usePlanHeader(planId);
    const isGeometrySwitchLinking = !!switchId;

    return (
        <React.Fragment>
            {isGeometrySwitchLinking && (
                <Infobox
                    title={t('tool-panel.switch.geometry.geometry-title')}
                    qa-id="geometry-switch-infobox">
                    <InfoboxContent>
                        <InfoboxField
                            label={t('tool-panel.switch.geometry.name')}
                            value={switchItem?.name}
                        />
                        <p>{switchStructure ? switchStructure.type : ''}</p>
                        {SwitchImage && (
                            <SwitchImage size={IconSize.ORIGINAL} color={IconColor.INHERIT} />
                        )}
                        <InfoboxField
                            label={t('tool-panel.switch.geometry.hand')}
                            value={<SwitchHand hand={switchStructure && switchStructure.hand} />}
                        />
                        <InfoboxButtons>
                            <Button
                                size={ButtonSize.SMALL}
                                variant={ButtonVariant.SECONDARY}
                                disabled={!switchLocation}
                                onClick={() => switchLocation && onShowOnMap(switchLocation)}>
                                {t('tool-panel.switch.layout.show-on-map')}
                            </Button>
                        </InfoboxButtons>
                    </InfoboxContent>
                </Infobox>
            )}
            {(!linkingState || linkingState.type === LinkingType.LinkingSwitch) && (
                <GeometrySwitchLinkingContainer
                    linkingState={linkingState}
                    switchId={switchId ?? undefined}
                    suggestedSwitch={suggestedSwitch}
                    switchChangeTime={switchChangeTime}
                    locationTrackChangeTime={locationTrackChangeTime}
                    layoutSwitch={layoutSwitch}
                    planId={planId ?? undefined}
                />
            )}
            {planHeader && (
                <GeometryPlanInfobox
                    planHeader={planHeader}
                    visibilities={{ plan: true, planQuality: true }}
                    onVisibilityChange={() => ''}
                />
            )}
        </React.Fragment>
    );
};

export default GeometrySwitchInfobox;
