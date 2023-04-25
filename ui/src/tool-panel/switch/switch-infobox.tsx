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
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { makeSwitchImage } from 'geoviite-design-lib/switch/switch-icons';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { useTranslation } from 'react-i18next';
import SwitchHand from 'geoviite-design-lib/switch/switch-hand';
import { formatToTM35FINString } from 'utils/geography-utils';
import { useLoader } from 'utils/react-utils';
import { getSwitchOwners, getSwitchStructures, pointString } from 'common/common-api';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { SwitchEditDialog } from './dialog/switch-edit-dialog';
import SwitchJointInfobox from 'tool-panel/switch/switch-joint-infobox';
import { JointNumber, PublishType, SwitchOwnerId, TrackMeter } from 'common/common-model';
import SwitchDeleteDialog from 'tool-panel/switch/dialog/switch-delete-dialog';
import LayoutStateCategoryLabel from 'geoviite-design-lib/layout-state-category/layout-state-category-label';
import { Point } from 'model/geometry';
import { PlacingSwitch } from 'linking/linking-model';
import { MessageBox } from 'geoviite-design-lib/message-box/message-box';
import { translateSwitchTrapPoint } from 'utils/enum-localization-utils';
import { filterNotEmpty } from 'utils/array-utils';
import { SwitchInfoboxTrackMeters } from 'tool-panel/switch/switch-infobox-track-meters';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { getLocationTrack } from 'track-layout/layout-location-track-api';
import { getTrackMeter } from 'track-layout/layout-map-api';
import { getSwitch, getSwitchJointConnections } from 'track-layout/layout-switch-api';
import { asyncCache } from 'cache/cache';
import { AssetValidationInfoboxContainer } from 'tool-panel/asset-validation-infobox-container';
import { ChangeTimes } from 'common/common-slice';
import { SwitchInfoboxVisibilities } from 'track-layout/track-layout-slice';
import { useCommonDataAppSelector } from 'store/hooks';

const switchJointTrackMeterCache = asyncCache<string, TrackMeter | undefined>();

type SwitchInfoboxProps = {
    switchId: LayoutSwitchId;
    onShowOnMap: (location: Point) => void;
    onDataChange: () => void;
    changeTimes: ChangeTimes;
    publishType: PublishType;
    onUnselect: (switchId: LayoutSwitchId) => void;
    placingSwitchLinkingState?: PlacingSwitch;
    startSwitchPlacing: (layoutSwitch: LayoutSwitch) => void;
    stopLinking: () => void;
    visibilities: SwitchInfoboxVisibilities;
    onVisibilityChange: (visibilities: SwitchInfoboxVisibilities) => void;
};

const mapToSwitchJointTrackMeter = (
    jointNumber: JointNumber,
    locationTrack: LayoutLocationTrack,
    trackMeter: TrackMeter,
): SwitchJointTrackMeter => {
    return {
        locationTrackId: locationTrack.id,
        locationTrackName: locationTrack.name,
        trackMeter: trackMeter,
        jointNumber: jointNumber,
    };
};

const getTrackMeterForPoint = async (
    jointNumber: JointNumber,
    locationTrackId: LocationTrackId,
    location: Point,
    publishType: PublishType,
    changeTimes: ChangeTimes,
) => {
    const locationTrack = await getLocationTrack(
        locationTrackId,
        publishType,
        changeTimes.layoutLocationTrack,
    );

    if (!locationTrack) return undefined;

    const cacheKey = `${locationTrack.trackNumberId}_${publishType}_${pointString(location)}`;
    const trackMeter = await switchJointTrackMeterCache.get(
        changeTimes.layoutTrackNumber,
        cacheKey,
        () => getTrackMeter(locationTrack.trackNumberId, publishType, location),
    );

    return trackMeter
        ? mapToSwitchJointTrackMeter(jointNumber, locationTrack, trackMeter)
        : undefined;
};

const getSwitchJointTrackMeters = async (
    switchJointConnections: LayoutSwitchJointConnection[],
    publishType: PublishType,
    changeTimes: ChangeTimes,
): Promise<SwitchJointTrackMeter[]> => {
    const jointTrackMeters = await Promise.all(
        switchJointConnections.flatMap((connection) => {
            return connection.accurateMatches.map(async ({ locationTrackId, location }) =>
                getTrackMeterForPoint(
                    connection.number,
                    locationTrackId,
                    location,
                    publishType,
                    changeTimes,
                ),
            );
        }),
    );

    return jointTrackMeters.filter(filterNotEmpty);
};

const SwitchInfobox: React.FC<SwitchInfoboxProps> = ({
    switchId,
    onShowOnMap,
    onDataChange,
    changeTimes,
    publishType,
    onUnselect,
    placingSwitchLinkingState,
    startSwitchPlacing,
    visibilities,
    onVisibilityChange,
    stopLinking,
}: SwitchInfoboxProps) => {
    const { t } = useTranslation();
    const switchOwners = useLoader(() => getSwitchOwners(), []);
    const switchStructures = useLoader(() => getSwitchStructures(), []);
    const layoutSwitch = useLoader(
        () => getSwitch(switchId, publishType),
        [switchId, changeTimes.layoutSwitch, publishType],
    );
    const switchStructure = switchStructures?.find(
        (structure) => structure.id === layoutSwitch?.switchStructureId,
    );
    const switchJointConnections = useLoader(
        () => getSwitchJointConnections(publishType, switchId),
        [publishType, layoutSwitch],
    );

    const switchJointTrackMeters = useLoader(() => {
        return switchJointConnections
            ? getSwitchJointTrackMeters(switchJointConnections, publishType, changeTimes)
            : undefined;
    }, [switchJointConnections, publishType, changeTimes]);

    const SwitchImage =
        switchStructure && makeSwitchImage(switchStructure.baseType, switchStructure.hand);
    const switchLocation =
        switchStructure &&
        layoutSwitch &&
        getSwitchPresentationJoint(layoutSwitch, switchStructure.presentationJointNumber)?.location;

    const [showEditDialog, setShowEditDialog] = React.useState(false);
    const [showDeleteDialog, setShowDeleteDialog] = React.useState(false);
    const canStartPlacing = placingSwitchLinkingState == undefined && layoutSwitch != undefined;
    const userHarWriteRole = useCommonDataAppSelector((state) => state.userHasWriteRole);

    function isOfficial(): boolean {
        return publishType === 'OFFICIAL';
    }

    function openEditSwitchDialog() {
        setShowEditDialog(true);
        onDataChange();
    }

    function closeEditSwitchDialog() {
        setShowEditDialog(false);
    }

    function handleSwitchDelete() {
        setShowDeleteDialog(false);
        setShowEditDialog(false);
        onUnselect(switchId);
    }

    function getOwnerName(ownerId: SwitchOwnerId | null | undefined) {
        const name = switchOwners?.find((o) => o.id == ownerId)?.name;
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

    return (
        <React.Fragment>
            {layoutSwitch && (
                <Infobox
                    contentVisible={visibilities.basic}
                    onContentVisibilityChange={() => visibilityChange('basic')}
                    title={t('tool-panel.switch.layout.general-heading')}
                    qa-id="switch-infobox">
                    <InfoboxContent>
                        <InfoboxField
                            label={t('tool-panel.switch.layout.km-m')}
                            value={
                                (switchJointTrackMeters && (
                                    <SwitchInfoboxTrackMeters
                                        jointTrackMeters={switchJointTrackMeters}
                                        presentationJoint={switchStructure?.presentationJointNumber}
                                    />
                                )) || <Spinner />
                            }
                        />
                        <InfoboxField
                            label={t('tool-panel.switch.layout.oid')}
                            value={
                                layoutSwitch.externalId || t('tool-panel.switch.layout.unpublished')
                            }
                        />
                        <InfoboxField
                            label={t('tool-panel.switch.layout.name')}
                            value={layoutSwitch.name}
                            onEdit={openEditSwitchDialog}
                            iconDisabled={isOfficial()}
                        />
                        <InfoboxField
                            label={t('tool-panel.switch.layout.state-category')}
                            value={
                                <LayoutStateCategoryLabel category={layoutSwitch.stateCategory} />
                            }
                            onEdit={openEditSwitchDialog}
                            iconDisabled={isOfficial()}
                        />
                        <InfoboxButtons>
                            <Button
                                size={ButtonSize.SMALL}
                                disabled={!switchLocation}
                                variant={ButtonVariant.SECONDARY}
                                onClick={() => switchLocation && onShowOnMap(switchLocation)}>
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
                    <p>{switchStructure ? switchStructure.type : ''}</p>
                    {SwitchImage && (
                        <SwitchImage size={IconSize.ORIGINAL} color={IconColor.INHERIT} />
                    )}
                    <InfoboxField
                        label={t('tool-panel.switch.layout.hand')}
                        value={switchStructure && <SwitchHand hand={switchStructure.hand} />}
                    />
                    <InfoboxField
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
                        label={t('tool-panel.switch.layout.coordinates')}
                        value={switchLocation ? formatToTM35FINString(switchLocation) : '-'}
                    />
                    {switchStructure && switchJointConnections && (
                        <SwitchJointInfobox
                            switchAlignments={switchStructure.alignments}
                            jointConnections={switchJointConnections}
                            publishType={publishType}
                        />
                    )}
                    {userHarWriteRole && (
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
                                disabled={!canStartPlacing}
                                onClick={tryToStartSwitchPlacing}>
                                {t('tool-panel.switch.layout.start-switch-placing')}
                            </Button>
                        </InfoboxButtons>
                    )}
                    {placingSwitchLinkingState && (
                        <MessageBox>{t('tool-panel.switch.layout.switch-placing-help')}</MessageBox>
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
                        label={t('tool-panel.switch.layout.owner')}
                        value={getOwnerName(layoutSwitch?.ownerId)}
                    />
                </InfoboxContent>
            </Infobox>
            {layoutSwitch && layoutSwitch.draftType !== 'NEW_DRAFT' && (
                <AssetValidationInfoboxContainer
                    contentVisible={visibilities.validation}
                    onContentVisibilityChange={() => visibilityChange('validation')}
                    id={layoutSwitch.id}
                    type={'SWITCH'}
                    publishType={publishType}
                    changeTime={changeTimes.layoutSwitch}
                />
            )}
            <Infobox
                contentVisible={visibilities.log}
                onContentVisibilityChange={() => visibilityChange('log')}
                title={t('tool-panel.switch.layout.change-info-heading')}
                qa-id="switch-heading-infobox">
                <InfoboxContent>
                    {layoutSwitch?.draftType === 'NEW_DRAFT' && (
                        <InfoboxButtons>
                            <Button
                                onClick={() => setShowDeleteDialog(true)}
                                icon={Icons.Delete}
                                variant={ButtonVariant.WARNING}
                                size={ButtonSize.SMALL}>
                                {t('tool-panel.switch.layout.delete-draft')}
                            </Button>
                        </InfoboxButtons>
                    )}
                </InfoboxContent>
            </Infobox>
            {showDeleteDialog && (
                <SwitchDeleteDialog
                    switchId={switchId}
                    onDelete={handleSwitchDelete}
                    onCancel={() => setShowDeleteDialog(false)}
                />
            )}

            {showEditDialog && (
                <SwitchEditDialog
                    switchId={layoutSwitch?.id}
                    onClose={closeEditSwitchDialog}
                    onUpdate={closeEditSwitchDialog}
                    onDelete={handleSwitchDelete}
                />
            )}
        </React.Fragment>
    );
};

export default React.memo(SwitchInfobox);
