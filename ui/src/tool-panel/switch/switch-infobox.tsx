import * as React from 'react';
import {
    getSwitchPresentationJoint,
    LayoutLocationTrack,
    LayoutSwitch,
    LayoutSwitchId,
    LayoutSwitchJoint,
    LayoutSwitchJointConnection,
    LocationTrackId,
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
import { SwitchEditDialog } from './dialog/switch-edit-dialog';
import SwitchJointInfobox from 'tool-panel/switch/switch-joint-infobox';
import {
    getLocationTrack,
    getSwitch,
    getSwitchJointConnections,
    getTrackAddress,
} from 'track-layout/track-layout-api';
import { PublishType, SwitchOwnerId, SwitchStructure, TrackMeter } from 'common/common-model';
import SwitchDeleteDialog from 'tool-panel/switch/dialog/switch-delete-dialog';
import LayoutStateCategoryLabel from 'geoviite-design-lib/layout-state-category/layout-state-category-label';
import { ChangeTimes } from 'track-layout/track-layout-store';
import { Point } from 'model/geometry';
import { SwitchInfoboxTrackMeters } from 'tool-panel/switch/switch-infobox-track-meters';
import { filterNotEmpty } from 'utils/array-utils';
import { SuggestedSwitch } from 'linking/linking-model';
import { getSuggestedSwitchByPoint } from 'linking/linking-api';

type SwitchInfoboxProps = {
    switchId: LayoutSwitchId;
    onShowOnMap: (location: Point) => void;
    onDataChange: () => void;
    changeTimes: ChangeTimes;
    publishType: PublishType;
    onUnselect: (switchId: LayoutSwitchId) => void;
    clickLocation: Point | null;
    startSwitchLinking: (suggestedSwitch: SuggestedSwitch, layoutSwitch: LayoutSwitch) => void
};

export type SwitchTrackMeter = {
    name: string;
    trackMeter: TrackMeter;
    locationTrackId: LocationTrackId;
};

const getPresentationJoint = (
    officialSwitch: LayoutSwitch | undefined,
    switchStructure: SwitchStructure | undefined,
) => {
    return officialSwitch?.joints.find(
        (j) => j.number === switchStructure?.presentationJointNumber,
    );
};

const getPresentationJointConnection = (
    presentationJoint: LayoutSwitchJoint | undefined,
    switchJointConnections: LayoutSwitchJointConnection[] | undefined,
) => switchJointConnections?.find((jointConn) => jointConn.number === presentationJoint?.number);

const mapToSwitchTrackMeter = (
    track: LayoutLocationTrack,
    trackAddress: TrackMeter,
): SwitchTrackMeter => {
    return {
        name: track.name,
        trackMeter: trackAddress,
        locationTrackId: track.id,
    };
};

const getSwitchTrackMeter = (
    id: string,
    publishType: PublishType,
    changeTimes: ChangeTimes,
    presentationJoint: LayoutSwitchJoint,
) => {
    return getLocationTrack(id, publishType, changeTimes.layoutLocationTrack).then((track) =>
        getTrackAddress(track.trackNumberId, publishType, presentationJoint.location).then(
            (trackAddress) =>
                trackAddress ? mapToSwitchTrackMeter(track, trackAddress) : undefined,
        ),
    );
};

const getSwitchTrackMeters = (
    officialSwitch: LayoutSwitch | undefined,
    switchStructure: SwitchStructure | undefined,
    switchJointConnections: LayoutSwitchJointConnection[] | undefined,
    publishType: PublishType,
    changeTimes: ChangeTimes,
): Promise<(SwitchTrackMeter | undefined)[]> => {
    const presentationJoint: LayoutSwitchJoint | undefined = getPresentationJoint(
        officialSwitch,
        switchStructure,
    );
    const presentationJointConnection = getPresentationJointConnection(
        presentationJoint,
        switchJointConnections,
    );
    const accurateMatches = presentationJointConnection?.accurateMatches ?? [];
    const locationTrackIds =
        accurateMatches.length > 0
            ? accurateMatches.map((match) => match.locationTrackId)
            : presentationJointConnection?.fallbackMatches ?? [];

    return (
        (presentationJoint?.location &&
            Promise.all(
                locationTrackIds.map((id) =>
                    getSwitchTrackMeter(id, publishType, changeTimes, presentationJoint),
                ),
            )) ||
        Promise.resolve([])
    );
};

const SwitchInfobox: React.FC<SwitchInfoboxProps> = ({
    switchId,
    onShowOnMap,
    onDataChange,
    changeTimes,
    publishType,
    onUnselect,
    clickLocation,
    startSwitchLinking,
}: SwitchInfoboxProps) => {
    const {t} = useTranslation();
    const switchOwners = useLoader(() => getSwitchOwners(), []);
    const switchStructures = useLoader(() => getSwitchStructures(), []);
    const layoutSwitch = useLoader(
        () => getSwitch(switchId, publishType),
        [switchId, changeTimes.layoutSwitch, publishType],
    );
    const officialSwitch: LayoutSwitch | undefined = useLoader(
        () => getSwitch(switchId, 'OFFICIAL'),
        [switchId, changeTimes.layoutSwitch],
    );
    const switchStructure = switchStructures?.find(
        (structure) => structure.id === layoutSwitch?.switchStructureId,
    );
    const switchJointConnections: LayoutSwitchJointConnection[] | undefined = useLoader(
        () => getSwitchJointConnections(publishType, switchId),
        [publishType, layoutSwitch],
    );
    const switchTrackMeters = useLoader(
        () =>
            getSwitchTrackMeters(
                officialSwitch,
                switchStructure,
                switchJointConnections,
                publishType,
                changeTimes,
            ),
        [officialSwitch, switchStructure, switchJointConnections, publishType, changeTimes],
    )?.filter(filterNotEmpty);

    const SwitchImage =
        switchStructure && makeSwitchImage(switchStructure.baseType, switchStructure.hand);
    const switchLocation =
        switchStructure &&
        layoutSwitch &&
        getSwitchPresentationJoint(layoutSwitch, switchStructure.presentationJointNumber)?.location;

    const [showEditDialog, setShowEditDialog] = React.useState(false);
    const [showDeleteDialog, setShowDeleteDialog] = React.useState(false);

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

    const getOwnerName = (ownerId: SwitchOwnerId | null | undefined) => {
        const name = switchOwners?.find((o) => o.id == ownerId)?.name;
        return name ?? '-';
    };

    function tryToStartSwitchLinking() {
        if (clickLocation && layoutSwitch) {
            getSuggestedSwitchByPoint(clickLocation, layoutSwitch.switchStructureId)
                .then((suggestedSwitches) => {
                    if (suggestedSwitches.length) {
                        console.log(suggestedSwitches[0]);
                        startSwitchLinking(suggestedSwitches[0], layoutSwitch);
                    }
                });
        }
    }

    return (
        <React.Fragment>
            {layoutSwitch && (
                <Infobox
                    title={t('tool-panel.switch.layout.general-heading')}
                    qa-id="switch-infobox">
                    <InfoboxContent>
                        <InfoboxField
                            label={t('tool-panel.switch.layout.km-m')}
                            value={
                                (
                                    <SwitchInfoboxTrackMeters
                                        switchTrackMeters={switchTrackMeters}
                                    />
                                ) || t('tool-panel.switch.layout.unpublished')
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
                                <LayoutStateCategoryLabel category={layoutSwitch.stateCategory}/>
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
                title={t('tool-panel.switch.layout.structure-heading')}
                qa-id="switch-structure-infobox">
                <InfoboxContent>
                    <p>{switchStructure ? switchStructure.type : ''}</p>
                    {SwitchImage && (
                        <SwitchImage size={IconSize.ORIGINAL} color={IconColor.INHERIT}/>
                    )}
                    <InfoboxField
                        label={t('tool-panel.switch.layout.hand')}
                        value={switchStructure && <SwitchHand hand={switchStructure.hand}/>}
                    />
                    <InfoboxField
                        label={t('tool-panel.switch.layout.trap-point')}
                        value={t(layoutSwitch?.trapPoint ? 'yes' : 'no')}
                    />
                </InfoboxContent>
            </Infobox>
            <Infobox
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
                    <Button size={ButtonSize.SMALL} variant={ButtonVariant.SECONDARY}
                            onClick={tryToStartSwitchLinking}>{t('tool-panel.switch.layout.start-linking')}</Button>
                </InfoboxContent>
            </Infobox>
            <Infobox
                title={t('tool-panel.switch.layout.additional-heading')}
                qa-id="switch-additional-infobox">
                <InfoboxContent>
                    <InfoboxField
                        label={t('tool-panel.switch.layout.owner')}
                        value={getOwnerName(layoutSwitch?.ownerId)}
                    />
                </InfoboxContent>
            </Infobox>
            <Infobox
                title={t('tool-panel.switch.layout.change-info-heading')}
                qa-id="switch-heading-infobox">
                <InfoboxContent>
                    {officialSwitch === undefined && (
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
