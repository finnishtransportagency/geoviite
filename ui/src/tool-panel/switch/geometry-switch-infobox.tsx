import * as React from 'react';
import Infobox from 'tool-panel/infobox/infobox';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { makeSwitchImage } from 'geoviite-design-lib/switch/switch-icons';
import { IconColor, IconSize } from 'vayla-design-lib/icon/Icon';
import { useTranslation } from 'react-i18next';
import SwitchHand from 'geoviite-design-lib/switch/switch-hand';
import { GeometryPlanHeader, GeometrySwitch } from 'geometry/geometry-model';
import GeometryPlanInfobox from 'tool-panel/geometry-plan/geometry-plan-infobox';
import { LinkingState } from 'linking/linking-model';
import GeometrySwitchLinkingContainer from 'tool-panel/switch/geometry-switch-linking-container';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Point } from 'model/geometry';
import { GeometrySwitchInfoboxVisibilities } from 'track-layout/track-layout-slice';
import { first } from 'utils/array-utils';
import { ChangeTimes } from 'common/common-slice';
import { SwitchStructure } from 'common/common-model';
import { LayoutSwitch } from 'track-layout/track-layout-model';

type GeometrySwitchInfoboxProps = {
    geometrySwitch: GeometrySwitch | undefined;
    geometrySwitchLayout: LayoutSwitch | undefined;
    planHeader: GeometryPlanHeader | undefined;
    switchStructure: SwitchStructure | undefined;
    linkingState: LinkingState | undefined;
    changeTimes: ChangeTimes;
    onShowOnMap: (location: Point) => void;
    visibilities: GeometrySwitchInfoboxVisibilities;
    onVisibilityChange: (visibilities: GeometrySwitchInfoboxVisibilities) => void;
};

const GeometrySwitchInfobox: React.FC<GeometrySwitchInfoboxProps> = ({
    geometrySwitch,
    geometrySwitchLayout,
    planHeader,
    switchStructure,
    linkingState,
    changeTimes,
    onShowOnMap,
    visibilities,
    onVisibilityChange,
}: GeometrySwitchInfoboxProps) => {
    const { t } = useTranslation();
    const switchLocation = geometrySwitchLayout && first(geometrySwitchLayout.joints)?.location;

    const SwitchImage =
        switchStructure && makeSwitchImage(switchStructure.baseType, switchStructure.hand);

    const visibilityChange = (key: keyof GeometrySwitchInfoboxVisibilities) => {
        onVisibilityChange({ ...visibilities, [key]: !visibilities[key] });
    };

    return (
        <React.Fragment>
            <Infobox
                contentVisible={visibilities.basic}
                onContentVisibilityChange={() => visibilityChange('basic')}
                title={t('tool-panel.switch.geometry.geometry-title')}
                qa-id="geometry-switch-infobox">
                <InfoboxContent>
                    <InfoboxField
                        qaId="geometry-switch-name"
                        label={t('tool-panel.switch.geometry.name')}
                        value={geometrySwitch?.name}
                    />
                    <p>{switchStructure ? switchStructure.type : ''}</p>
                    {SwitchImage && (
                        <SwitchImage size={IconSize.ORIGINAL} color={IconColor.INHERIT} />
                    )}
                    {switchStructure && (
                        <InfoboxField
                            qaId="geometry-switch-hand"
                            label={t('tool-panel.switch.geometry.hand')}
                            value={<SwitchHand hand={switchStructure.hand} />}
                        />
                    )}
                    <InfoboxButtons>
                        <Button
                            size={ButtonSize.SMALL}
                            variant={ButtonVariant.SECONDARY}
                            qa-id="zoom-to-geometry-switch"
                            disabled={!switchLocation}
                            onClick={() => switchLocation && onShowOnMap(switchLocation)}>
                            {t('tool-panel.switch.layout.show-on-map')}
                        </Button>
                    </InfoboxButtons>
                </InfoboxContent>
            </Infobox>
            {planHeader && (
                <React.Fragment>
                    {geometrySwitch && (
                        <GeometrySwitchLinkingContainer
                            visibilities={{
                                linking: visibilities.linking,
                                suggestedSwitch: visibilities.suggestedSwitch,
                            }}
                            onVisibilityChange={(v) =>
                                onVisibilityChange({ ...visibilities, ...v })
                            }
                            linkingState={linkingState}
                            geometrySwitch={geometrySwitch}
                            switchChangeTime={changeTimes.layoutSwitch}
                            locationTrackChangeTime={changeTimes.layoutLocationTrack}
                            geometryPlanId={planHeader.id}
                        />
                    )}
                    <GeometryPlanInfobox
                        planHeader={planHeader}
                        visibilities={{
                            plan: visibilities.plan,
                            planQuality: visibilities.planQuality,
                        }}
                        onVisibilityChange={(v) => onVisibilityChange({ ...visibilities, ...v })}
                        changeTimes={changeTimes}
                    />
                </React.Fragment>
            )}
        </React.Fragment>
    );
};

export default GeometrySwitchInfobox;
