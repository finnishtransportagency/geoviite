import * as React from 'react';
import {
    booleanToTrapPoint,
    getSwitchPresentationJoint,
    LayoutLocationTrack,
    LayoutSwitch,
    LayoutSwitchId,
    LayoutSwitchJointConnection,
    LocationTrackId,
    SwitchJointTrackMeter,
} from 'track-layout/track-layout-model';
import Infobox from 'tool-panel/infobox/infobox';
import InfoboxContent, { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { makeSwitchImage } from 'geoviite-design-lib/switch/switch-icons';
import { IconColor, IconSize } from 'vayla-design-lib/icon/Icon';
import { useTranslation } from 'react-i18next';
import SwitchHand from 'geoviite-design-lib/switch/switch-hand';
import { formatToTM35FINString } from 'utils/geography-utils';
import { useLoader } from 'utils/react-utils';
import { getSwitchOwners, getSwitchStructures } from 'common/common-api';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { SwitchEditDialogContainer } from './dialog/switch-edit-dialog';
import SwitchJointInfobox from 'tool-panel/switch/switch-joint-infobox';
import {
    draftLayoutContext,
    JointNumber,
    LayoutContext,
    SwitchOwnerId,
    TrackMeter,
} from 'common/common-model';
import LayoutStateCategoryLabel from 'geoviite-design-lib/layout-state-category/layout-state-category-label';
import { BoundingBox, Point } from 'model/geometry';
import { PlacingSwitch } from 'linking/linking-model';
import { MessageBox } from 'geoviite-design-lib/message-box/message-box';
import { translateSwitchTrapPoint } from 'utils/enum-localization-utils';
import { filterNotEmpty } from 'utils/array-utils';
import { SwitchInfoboxTrackMeters } from 'tool-panel/switch/switch-infobox-track-meters';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { getLocationTrack } from 'track-layout/layout-location-track-api';
import { getTrackMeter } from 'track-layout/layout-map-api';
import { getSwitch, getSwitchJointConnections } from 'track-layout/layout-switch-api';
import { AssetValidationInfoboxContainer } from 'tool-panel/asset-validation-infobox-container';
import { ChangeTimes } from 'common/common-slice';
import { SwitchInfoboxVisibilities } from 'track-layout/track-layout-slice';
import { formatDateShort } from 'utils/date-utils';
import {
    refreshSwitchSelection,
    useSwitchChangeTimes,
} from 'track-layout/track-layout-react-utils';
import { OnSelectOptions, OptionalUnselectableItemCollections } from 'selection/selection-model';
import { calculateBoundingBoxToShowAroundLocation } from 'map/map-utils';
import { PrivilegeRequired } from 'user/privilege-required';
import { EDIT_LAYOUT } from 'user/user-model';
import { SwitchOid } from 'track-layout/oid';

type SwitchInfoboxProps = {
    switchId: LayoutSwitchId;
    showArea: (boundingBox: BoundingBox) => void;
    onDataChange: () => void;
    changeTimes: ChangeTimes;
    layoutContext: LayoutContext;
    onSelect: (options: OnSelectOptions) => void;
    onUnselect: (items: OptionalUnselectableItemCollections) => void;
    placingSwitchLinkingState?: PlacingSwitch;
    startSwitchPlacing: (layoutSwitch: LayoutSwitch) => void;
    stopLinking: () => void;
    visibilities: SwitchInfoboxVisibilities;
    onVisibilityChange: (visibilities: SwitchInfoboxVisibilities) => void;
    onSelectLocationTrackBadge: (locationTrackId: LocationTrackId) => void;
};

const mapToSwitchJointTrackMeter = (
    jointNumber: JointNumber,
    locationTrack: LayoutLocationTrack,
    trackMeter: TrackMeter | undefined,
    location: Point,
): SwitchJointTrackMeter => {
    return {
        locationTrackId: locationTrack.id,
        locationTrackName: locationTrack.name,
        trackMeter: trackMeter,
        jointNumber: jointNumber,
        location,
    };
};

const getTrackMeterForPoint = async (
    jointNumber: JointNumber,
    locationTrackId: LocationTrackId,
    location: Point,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
) => {
    const locationTrack = await getLocationTrack(
        locationTrackId,
        layoutContext,
        changeTimes.layoutLocationTrack,
    );

    if (!locationTrack) return undefined;

    const trackMeter = await getTrackMeter(
        locationTrack.trackNumberId,
        layoutContext,
        changeTimes.layoutTrackNumber,
        location,
    );

    return mapToSwitchJointTrackMeter(jointNumber, locationTrack, trackMeter, location);
};

export const getSwitchJointTrackMeters = async (
    switchJointConnections: LayoutSwitchJointConnection[],
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
): Promise<SwitchJointTrackMeter[]> => {
    const jointTrackMeters = await Promise.all(
        switchJointConnections.flatMap((connection) => {
            return connection.accurateMatches.map(async ({ locationTrackId, location }) =>
                getTrackMeterForPoint(
                    connection.number,
                    locationTrackId,
                    location,
                    layoutContext,
                    changeTimes,
                ),
            );
        }),
    );

    return jointTrackMeters.filter(filterNotEmpty);
};

const SwitchInfobox: React.FC<SwitchInfoboxProps> = ({
    switchId,
    showArea,
    onDataChange,
    changeTimes,
    layoutContext,
    onSelect,
    onUnselect,
    placingSwitchLinkingState,
    startSwitchPlacing,
    visibilities,
    onVisibilityChange,
    stopLinking,
    onSelectLocationTrackBadge,
}: SwitchInfoboxProps) => {
    const { t } = useTranslation();
    const switchOwners = useLoader(() => getSwitchOwners(), []);
    const switchStructures = useLoader(() => getSwitchStructures(), []);
    const layoutSwitch = useLoader(
        () => getSwitch(switchId, layoutContext),
        [switchId, changeTimes.layoutSwitch, layoutContext.branch, layoutContext.publicationState],
    );
    const structure = switchStructures?.find(
        (structure) => structure.id === layoutSwitch?.switchStructureId,
    );
    const switchJointConnections = useLoader(
        () => getSwitchJointConnections(layoutContext, switchId),
        [layoutContext.branch, layoutContext.publicationState, layoutSwitch],
    );
    const switchChangeTimes = useSwitchChangeTimes(layoutSwitch?.id, layoutContext);

    const switchJointTrackMeters = useLoader(() => {
        return switchJointConnections
            ? getSwitchJointTrackMeters(switchJointConnections, layoutContext, changeTimes)
            : undefined;
    }, [switchJointConnections, layoutContext.branch, layoutContext.publicationState, changeTimes]);

    const SwitchImage = structure && makeSwitchImage(structure.baseType, structure.hand);
    const switchLocation =
        structure &&
        layoutSwitch &&
        getSwitchPresentationJoint(layoutSwitch, structure.presentationJointNumber)?.location;

    const [showEditDialog, setShowEditDialog] = React.useState(false);
    const canStartPlacing = placingSwitchLinkingState === undefined && layoutSwitch !== undefined;

    function isOfficial(): boolean {
        return layoutContext.publicationState === 'OFFICIAL';
    }

    function openEditSwitchDialog() {
        setShowEditDialog(true);
        onDataChange();
    }

    function getOwnerName(ownerId: SwitchOwnerId | undefined) {
        const name = switchOwners?.find((o) => o.id === ownerId)?.name;
        return name ?? '-';
    }

    function tryToStartSwitchPlacing() {
        if (layoutSwitch) {
            startSwitchPlacing(layoutSwitch);
        }
    }

    const visibilityChange = (key: keyof SwitchInfoboxVisibilities) => {
        onVisibilityChange({ ...visibilities, [key]: !visibilities[key] });
    };
    const handleSwitchSave = refreshSwitchSelection(
        draftLayoutContext(layoutContext),
        onSelect,
        onUnselect,
    );

    return (
        <React.Fragment>
            {layoutSwitch && (
                <Infobox
                    contentVisible={visibilities.basic}
                    onContentVisibilityChange={() => visibilityChange('basic')}
                    title={t('tool-panel.switch.layout.general-heading')}
                    qa-id="switch-infobox"
                    onEdit={openEditSwitchDialog}
                    iconDisabled={isOfficial()}>
                    <InfoboxContent>
                        <InfoboxField
                            label={t('tool-panel.switch.layout.km-m')}
                            value={
                                (switchJointTrackMeters && (
                                    <SwitchInfoboxTrackMeters
                                        jointTrackMeters={switchJointTrackMeters}
                                        presentationJoint={structure?.presentationJointNumber}
                                    />
                                )) || <Spinner />
                            }
                        />
                        <InfoboxField
                            qaId="switch-oid"
                            label={t('tool-panel.switch.layout.oid')}
                            value={
                                <SwitchOid
                                    id={layoutSwitch.id}
                                    branch={layoutContext.branch}
                                    changeTimes={changeTimes}
                                    getFallbackTextIfNoOid={() =>
                                        t('tool-panel.switch.layout.unpublished')
                                    }
                                />
                            }
                        />
                        <InfoboxField
                            qaId="switch-name"
                            label={t('tool-panel.switch.layout.name')}
                            value={layoutSwitch.name}
                        />
                        <InfoboxField
                            qaId="switch-state-category"
                            label={t('tool-panel.switch.layout.state-category')}
                            value={
                                <LayoutStateCategoryLabel category={layoutSwitch.stateCategory} />
                            }
                        />
                        <InfoboxButtons>
                            <Button
                                size={ButtonSize.SMALL}
                                title={
                                    !switchLocation ? t('tool-panel.switch.layout.no-location') : ''
                                }
                                disabled={!switchLocation}
                                variant={ButtonVariant.SECONDARY}
                                qa-id="zoom-to-switch"
                                onClick={() =>
                                    switchLocation &&
                                    showArea(
                                        calculateBoundingBoxToShowAroundLocation(switchLocation),
                                    )
                                }>
                                {t('tool-panel.switch.layout.show-on-map')}
                            </Button>
                        </InfoboxButtons>
                    </InfoboxContent>
                </Infobox>
            )}
            <Infobox
                contentVisible={visibilities.structure}
                onContentVisibilityChange={() => visibilityChange('structure')}
                title={t('tool-panel.switch.layout.structure-heading')}
                qa-id="switch-structure-infobox">
                <InfoboxContent>
                    <p>{structure ? structure.type : ''}</p>
                    {SwitchImage && (
                        <SwitchImage size={IconSize.ORIGINAL} color={IconColor.INHERIT} />
                    )}
                    <InfoboxField
                        qaId="switch-hand"
                        label={t('tool-panel.switch.layout.hand')}
                        value={structure && <SwitchHand hand={structure.hand} />}
                    />
                    <InfoboxField
                        qaId="switch-trap-point"
                        label={t('tool-panel.switch.layout.trap-point')}
                        value={
                            layoutSwitch &&
                            translateSwitchTrapPoint(booleanToTrapPoint(layoutSwitch.trapPoint))
                        }
                    />
                </InfoboxContent>
            </Infobox>
            <Infobox
                contentVisible={visibilities.location}
                onContentVisibilityChange={() => visibilityChange('location')}
                title={t('tool-panel.switch.layout.location-heading')}
                qa-id="switch-location-infobox">
                <InfoboxContent>
                    <InfoboxField
                        qaId="switch-coordinates"
                        label={t('tool-panel.switch.layout.coordinates')}
                        value={switchLocation ? formatToTM35FINString(switchLocation) : '-'}
                    />
                    {structure && switchJointConnections && (
                        <SwitchJointInfobox
                            switchAlignments={structure.alignments}
                            jointConnections={switchJointConnections}
                            layoutContext={layoutContext}
                            onSelectLocationTrackBadge={onSelectLocationTrackBadge}
                        />
                    )}
                    <PrivilegeRequired privilege={EDIT_LAYOUT}>
                        <InfoboxButtons>
                            {!canStartPlacing && (
                                <Button
                                    size={ButtonSize.SMALL}
                                    variant={ButtonVariant.SECONDARY}
                                    onClick={stopLinking}>
                                    {t('button.cancel')}
                                </Button>
                            )}

                            <Button
                                size={ButtonSize.SMALL}
                                variant={ButtonVariant.SECONDARY}
                                title={
                                    layoutContext.publicationState !== 'DRAFT'
                                        ? t(
                                              'tool-panel.disabled.activity-disabled-in-official-mode',
                                          )
                                        : ''
                                }
                                disabled={
                                    !canStartPlacing || layoutContext.publicationState !== 'DRAFT'
                                }
                                onClick={tryToStartSwitchPlacing}>
                                {t('tool-panel.switch.layout.start-switch-placing')}
                            </Button>
                        </InfoboxButtons>
                    </PrivilegeRequired>
                    {placingSwitchLinkingState && (
                        <InfoboxContentSpread>
                            <MessageBox>
                                {t('tool-panel.switch.layout.switch-placing-help')}
                            </MessageBox>
                        </InfoboxContentSpread>
                    )}
                </InfoboxContent>
            </Infobox>
            <Infobox
                contentVisible={visibilities.additionalInfo}
                onContentVisibilityChange={() => visibilityChange('additionalInfo')}
                title={t('tool-panel.switch.layout.additional-heading')}
                qa-id="switch-additional-infobox">
                <InfoboxContent>
                    <InfoboxField
                        qaId="switch-owner"
                        label={t('tool-panel.switch.layout.owner')}
                        value={getOwnerName(layoutSwitch?.ownerId)}
                    />
                </InfoboxContent>
            </Infobox>
            {layoutSwitch && (
                <AssetValidationInfoboxContainer
                    contentVisible={visibilities.validation}
                    onContentVisibilityChange={() => visibilityChange('validation')}
                    idAndType={{ id: layoutSwitch.id, type: 'SWITCH' }}
                    layoutContext={layoutContext}
                    changeTime={changeTimes.layoutSwitch}
                />
            )}
            <Infobox
                contentVisible={visibilities.log}
                onContentVisibilityChange={() => visibilityChange('log')}
                title={t('tool-panel.switch.layout.change-info-heading')}
                qa-id="switch-heading-infobox">
                <InfoboxContent>
                    {switchChangeTimes && (
                        <React.Fragment>
                            <InfoboxField
                                label={t('tool-panel.created')}
                                value={formatDateShort(switchChangeTimes.created)}
                            />
                            <InfoboxField
                                label={t('tool-panel.changed')}
                                value={
                                    switchChangeTimes.changed &&
                                    formatDateShort(switchChangeTimes.changed)
                                }
                            />
                        </React.Fragment>
                    )}
                </InfoboxContent>
            </Infobox>

            {showEditDialog && (
                <SwitchEditDialogContainer
                    switchId={layoutSwitch?.id}
                    onClose={() => setShowEditDialog(false)}
                    onSave={handleSwitchSave}
                />
            )}
        </React.Fragment>
    );
};

export default React.memo(SwitchInfobox);
