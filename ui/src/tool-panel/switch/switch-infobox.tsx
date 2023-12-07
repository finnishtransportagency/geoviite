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
import { getSwitchOwners, getSwitchStructures } from 'common/common-api';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { SwitchEditDialogContainer } from './dialog/switch-edit-dialog';
import SwitchJointInfobox from 'tool-panel/switch/switch-joint-infobox';
import { JointNumber, PublishType, SwitchOwnerId, TrackMeter } from 'common/common-model';
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
import { AssetValidationInfoboxContainer } from 'tool-panel/asset-validation-infobox-container';
import { ChangeTimes } from 'common/common-slice';
import { SwitchInfoboxVisibilities } from 'track-layout/track-layout-slice';
import { WriteAccessRequired } from 'user/write-access-required';
import { formatDateShort } from 'utils/date-utils';
import {
    refreshSwitchSelection,
    useSwitchChangeTimes,
} from 'track-layout/track-layout-react-utils';
import { OnSelectOptions, OptionalUnselectableItemCollections } from 'selection/selection-model';
import SwitchDeleteConfirmationDialog from './dialog/switch-delete-confirmation-dialog';

type SwitchInfoboxProps = {
    switchId: LayoutSwitchId;
    onShowOnMap: (location: Point) => void;
    onDataChange: () => void;
    changeTimes: ChangeTimes;
    publishType: PublishType;
    onSelect: (options: OnSelectOptions) => void;
    onUnselect: (items: OptionalUnselectableItemCollections) => void;
    placingSwitchLinkingState?: PlacingSwitch;
    startSwitchPlacing: (layoutSwitch: LayoutSwitch) => void;
    stopLinking: () => void;
    visibilities: SwitchInfoboxVisibilities;
    onVisibilityChange: (visibilities: SwitchInfoboxVisibilities) => void;
};

const mapToSwitchJointTrackMeter = (
    jointNumber: JointNumber,
    locationTrack: LayoutLocationTrack,
    trackMeter: TrackMeter | undefined,
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

    const trackMeter = await getTrackMeter(
        locationTrack.trackNumberId,
        publishType,
        changeTimes.layoutTrackNumber,
        location,
    );

    return mapToSwitchJointTrackMeter(jointNumber, locationTrack, trackMeter);
};

export const getSwitchJointTrackMeters = async (
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
    onSelect,
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
    const structure = switchStructures?.find(
        (structure) => structure.id === layoutSwitch?.switchStructureId,
    );
    const switchJointConnections = useLoader(
        () => getSwitchJointConnections(publishType, switchId),
        [publishType, layoutSwitch],
    );
    const switchChangeTimes = useSwitchChangeTimes(layoutSwitch?.id, publishType);

    const switchJointTrackMeters = useLoader(() => {
        return switchJointConnections
            ? getSwitchJointTrackMeters(switchJointConnections, publishType, changeTimes)
            : undefined;
    }, [switchJointConnections, publishType, changeTimes]);

    const SwitchImage = structure && makeSwitchImage(structure.baseType, structure.hand);
    const switchLocation =
        structure &&
        layoutSwitch &&
        getSwitchPresentationJoint(layoutSwitch, structure.presentationJointNumber)?.location;

    const [showEditDialog, setShowEditDialog] = React.useState(false);
    const [showDeleteDialog, setShowDeleteDialog] = React.useState(false);
    const canStartPlacing = placingSwitchLinkingState == undefined && layoutSwitch != undefined;

    function isOfficial(): boolean {
        return publishType === 'OFFICIAL';
    }

    function openEditSwitchDialog() {
        setShowEditDialog(true);
        onDataChange();
    }

    function getOwnerName(ownerId: SwitchOwnerId | undefined) {
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
    const handleSwitchSave = refreshSwitchSelection('DRAFT', onSelect, onUnselect);

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
                                        presentationJoint={structure?.presentationJointNumber}
                                    />
                                )) || <Spinner />
                            }
                        />
                        <InfoboxField
                            qaId="switch-oid"
                            label={t('tool-panel.switch.layout.oid')}
                            value={
                                layoutSwitch.externalId || t('tool-panel.switch.layout.unpublished')
                            }
                        />
                        <InfoboxField
                            qaId="switch-name"
                            label={t('tool-panel.switch.layout.name')}
                            value={layoutSwitch.name}
                            onEdit={openEditSwitchDialog}
                            iconDisabled={isOfficial()}
                        />
                        <InfoboxField
                            qaId="switch-state-category"
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
                                qa-id="zoom-to-switch"
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
                            publishType={publishType}
                        />
                    )}
                    <WriteAccessRequired>
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
                    </WriteAccessRequired>
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
                        qaId="switch-owner"
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
                    {switchChangeTimes && (
                        <React.Fragment>
                            <InfoboxField
                                label={t('tool-panel.created')}
                                value={formatDateShort(switchChangeTimes.created)}
                            />
                            <InfoboxField
                                label={t('tool-panel.changed')}
                                value={formatDateShort(switchChangeTimes.changed)}
                            />
                        </React.Fragment>
                    )}
                    {layoutSwitch?.draftType === 'NEW_DRAFT' && (
                        <InfoboxButtons>
                            <Button
                                onClick={() => setShowDeleteDialog(true)}
                                icon={Icons.Delete}
                                variant={ButtonVariant.WARNING}
                                size={ButtonSize.SMALL}>
                                {t('button.delete-draft')}
                            </Button>
                        </InfoboxButtons>
                    )}
                </InfoboxContent>
            </Infobox>
            {showDeleteDialog && (
                <SwitchDeleteConfirmationDialog
                    switchId={switchId}
                    onClose={() => setShowDeleteDialog(false)}
                    onSave={handleSwitchSave}
                />
            )}

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
