import * as React from 'react';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import Infobox from 'tool-panel/infobox/infobox';
import InfoboxContent, { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';
import { useTranslation } from 'react-i18next';
import styles from './switch-infobox.scss';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { LinkingSwitch, SuggestedSwitch } from 'linking/linking-model';
import { TextField, TextFieldVariant } from 'vayla-design-lib/text-field/text-field';
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';
import { SwitchBadge, SwitchBadgeStatus } from 'geoviite-design-lib/switch/switch-badge';
import { SwitchEditDialog } from './dialog/switch-edit-dialog';
import {
    LayoutSwitch,
    LayoutSwitchId,
    LayoutSwitchJointConnection,
} from 'track-layout/track-layout-model';
import { getSwitch, getSwitchesByBoundingBox } from 'track-layout/layout-switch-api';
import { useDebouncedState, useLoader } from 'utils/react-utils';
import { PublishType, TimeStamp } from 'common/common-model';
import { SWITCH_SHOW } from 'map/layers/layer-visibility-limits';
import { getPlanLinkStatus, getSuggestedSwitchesByTile, linkSwitch } from 'linking/linking-api';
import * as SnackBar from 'geoviite-design-lib/snackbar/snackbar';
import GeometrySwitchLinkingSuggestedInfobox from 'tool-panel/switch/geometry-switch-linking-suggested-infobox';
import { GeometryPlanId, GeometrySwitchId } from 'geometry/geometry-model';
import { getGeometrySwitch, getGeometrySwitchLayout } from 'geometry/geometry-api';
import { boundingBoxAroundPoints, expandBoundingBox } from 'model/geometry';
import SwitchJointInfobox from 'tool-panel/switch/switch-joint-infobox';
import { asTrackLayoutSwitchJointConnection } from 'linking/linking-utils';
import { useSwitch } from 'track-layout/track-layout-react-utils';
import { MessageBox } from 'geoviite-design-lib/message-box/message-box';

type GeometrySwitchLinkingInfoboxProps = {
    geometrySwitchId?: GeometrySwitchId;
    selectedSuggestedSwitch?: SuggestedSwitch;
    switchId?: GeometrySwitchId;
    linkingState?: LinkingSwitch;
    onLinkingStart: (suggestedSwitch: SuggestedSwitch) => void;
    onSwitchSelect: (switchItem: LayoutSwitch) => void;
    switchChangeTime: TimeStamp;
    locationTrackChangeTime: TimeStamp;
    layoutSwitch?: LayoutSwitch;
    resolution: number;
    onStopLinking: () => void;
    onSuggestedSwitchChange?: (suggestedSwitch: SuggestedSwitch) => void;
    planId?: GeometryPlanId;
    publishType: PublishType;
};

function isValidSwitchForLinking(
    _suggestedSwitch: SuggestedSwitch,
    _layoutSwitch: LayoutSwitch,
): boolean {
    return true; //suggestedSwitch.switchStructure.id == layoutSwitch.switchStructureId;
}

const GeometrySwitchLinkingInfobox: React.FC<GeometrySwitchLinkingInfoboxProps> = ({
    geometrySwitchId,
    linkingState,
    switchId,
    onLinkingStart,
    selectedSuggestedSwitch,
    onSwitchSelect,
    switchChangeTime,
    locationTrackChangeTime,
    layoutSwitch,
    resolution,
    onStopLinking,
    onSuggestedSwitchChange,
    planId,
    publishType,
}) => {
    const { t } = useTranslation();
    const geometrySwitch = useLoader(
        () => (geometrySwitchId ? getGeometrySwitch(geometrySwitchId) : undefined),
        [geometrySwitchId],
    );
    const geometrySwitchLayout = useLoader(
        () => (geometrySwitchId ? getGeometrySwitchLayout(geometrySwitchId) : undefined),
        [geometrySwitchId],
    );
    const suggestedSwitch = useLoader(() => {
        if (selectedSuggestedSwitch) {
            return Promise.resolve(selectedSuggestedSwitch);
        } else if (geometrySwitch && geometrySwitchLayout) {
            const boundingBox = expandBoundingBox(
                boundingBoxAroundPoints(geometrySwitchLayout.joints.map((joint) => joint.location)),
                200,
            );
            const fakeMapTile = {
                id: `geom-switch-${geometrySwitch.id}`,
                area: boundingBox,
                resolution: 0,
            };
            return getSuggestedSwitchesByTile(fakeMapTile).then(
                (suggestedSwitches) =>
                    suggestedSwitches.find(
                        (suggestedSwitch) => suggestedSwitch.geometrySwitchId == geometrySwitch.id,
                    ) || null,
            );
        }
        return undefined;
    }, [geometrySwitchId, selectedSuggestedSwitch, geometrySwitchLayout]);
    const showAlignmentNotLinkedMsg = suggestedSwitch === null;
    const [searchTerm, setSearchTerm] = React.useState('');
    const debouncedSearchTerm = useDebouncedState(searchTerm, 200);
    const [showAddSwitchDialog, setShowAddSwitchDialog] = React.useState(false);
    const planStatus = useLoader(
        () => (planId ? getPlanLinkStatus(planId, publishType) : undefined),
        [planId, publishType, switchChangeTime, locationTrackChangeTime],
    );
    const switches = useLoader(() => {
        const point = selectedSuggestedSwitch?.joints.find((joint) => joint.location)?.location;
        if (point) {
            // This is a simple way to select nearby layout switches,
            // can be fine tuned later
            const bboxSize = 100;
            const bbox = {
                x: { min: point.x - bboxSize, max: point.x + bboxSize },
                y: { min: point.y - bboxSize, max: point.y + bboxSize },
            };

            return getSwitchesByBoundingBox(bbox, 'DRAFT', debouncedSearchTerm, point, true);
        } else {
            return undefined;
        }
    }, [selectedSuggestedSwitch, debouncedSearchTerm, switchChangeTime]);
    const switchJointConnections: LayoutSwitchJointConnection[] | undefined = suggestedSwitch
        ? suggestedSwitch.joints.map((joint) => asTrackLayoutSwitchJointConnection(joint))
        : undefined;
    const isGeometrySwitchLinking = !!switchId;
    const isSwitchLinked = planStatus?.switches.find((s) => s.id === switchId)?.isLinked;
    const linkingIsStarted = linkingState?.state === 'setup' || linkingState?.state === 'allSet';
    const [linkingCallInProgress, setLinkingCallInProgress] = React.useState(false);
    const selectedLayoutSwitch = useSwitch(linkingState?.layoutSwitchId, publishType);
    const isLayoutSwitchSelected = selectedLayoutSwitch != undefined;
    const isValidLayoutSwitch =
        suggestedSwitch != undefined &&
        selectedLayoutSwitch != undefined &&
        isValidSwitchForLinking(suggestedSwitch, selectedLayoutSwitch);
    const canLink =
        isLayoutSwitchSelected &&
        isValidLayoutSwitch &&
        linkingState?.state === 'allSet' &&
        !linkingCallInProgress;
    const showInvalidSwitchError = isLayoutSwitchSelected && !isValidLayoutSwitch;

    function startLinking() {
        if (suggestedSwitch) {
            onSuggestedSwitchChange && onSuggestedSwitchChange(suggestedSwitch);
            onLinkingStart(suggestedSwitch);
        }
    }

    function handleSwitchInsert(id: LayoutSwitchId) {
        getSwitch(id, 'DRAFT').then((s) => onSwitchSelect(s));
        setShowAddSwitchDialog(false);
    }

    async function link() {
        if (linkingState?.suggestedSwitch && linkingState.layoutSwitchId) {
            const params = {
                switchStructureId: linkingState.suggestedSwitch.switchStructure.id,
                geometrySwitchId: linkingState.suggestedSwitch.geometrySwitchId,
                layoutSwitchId: linkingState.layoutSwitchId,
                joints: linkingState.suggestedSwitch.joints.map((joint) => {
                    return {
                        jointNumber: joint.number,
                        location: joint.location,
                        segments: joint.matches,
                        locationAccuracy: joint.locationAccuracy,
                    };
                }),
            };
            setLinkingCallInProgress(true);
            try {
                await linkSwitch(params);
                SnackBar.success(t('tool-panel.switch.geometry.linking-succeed-msg'));
                onStopLinking();
            } finally {
                setLinkingCallInProgress(false);
            }
        }
    }

    return (
        <React.Fragment>
            {selectedSuggestedSwitch?.alignmentEndPoint && onSuggestedSwitchChange && (
                <GeometrySwitchLinkingSuggestedInfobox
                    suggestedSwitch={selectedSuggestedSwitch}
                    alignmentEndPoint={selectedSuggestedSwitch.alignmentEndPoint}
                    onSuggestedSwitchChange={onSuggestedSwitchChange}
                />
            )}
            <Infobox
                title={t('tool-panel.switch.geometry.linking-header')}
                qa-id="geometry-switch-linking-infobox">
                <InfoboxContent>
                    {isGeometrySwitchLinking && (
                        <React.Fragment>
                            <InfoboxField
                                label={t('tool-panel.switch.geometry.is-linked')}
                                className={styles['geometry-switch-infobox__linked-status']}
                                value={
                                    isSwitchLinked ? (
                                        <span
                                            className={
                                                styles['geometry-switch-infobox__linked-text']
                                            }>
                                            {t('yes')}
                                        </span>
                                    ) : (
                                        <span
                                            className={
                                                styles['geometry-switch-infobox__not-linked-text']
                                            }>
                                            {t('no')}
                                        </span>
                                    )
                                }
                            />
                            {!isSwitchLinked && (
                                <React.Fragment>
                                    {showAlignmentNotLinkedMsg && (
                                        <InfoboxContentSpread>
                                            <MessageBox>
                                                {t(
                                                    'tool-panel.switch.geometry.cannot-start-switch-linking-related-tracks-not-linked-msg',
                                                )}
                                            </MessageBox>
                                        </InfoboxContentSpread>
                                    )}
                                </React.Fragment>
                            )}
                        </React.Fragment>
                    )}
                    {linkingState && (
                        <div className={styles['geometry-switch-infobox__linking-container']}>
                            <span className={styles['geometry-switch-infobox__info-text']}>
                                {t('tool-panel.switch.geometry.select-switch-msg')}
                            </span>
                            <div className={styles['geometry-switch-infobox__search-container']}>
                                <div className={styles['geometry-switch-infobox__search-input']}>
                                    <TextField
                                        variant={TextFieldVariant.NO_BORDER}
                                        Icon={Icons.Search}
                                        wide
                                        value={searchTerm}
                                        onChange={(e) => {
                                            setSearchTerm(e.target.value);
                                        }}
                                    />
                                </div>
                                <Button
                                    variant={ButtonVariant.GHOST}
                                    size={ButtonSize.SMALL}
                                    icon={Icons.Append}
                                    onClick={() => setShowAddSwitchDialog(true)}
                                    qa-id=""
                                />
                            </div>
                            <ul className={styles['geometry-switch-infobox__switches-container']}>
                                {switches?.map((s) => {
                                    return (
                                        <li
                                            key={s.id}
                                            className={styles['geometry-switch-infobox__switch']}
                                            onClick={() => onSwitchSelect(s)}>
                                            <SwitchBadge
                                                switchItem={s}
                                                status={
                                                    layoutSwitch?.id === s.id
                                                        ? SwitchBadgeStatus.SELECTED
                                                        : SwitchBadgeStatus.DEFAULT
                                                }
                                            />
                                        </li>
                                    );
                                })}
                            </ul>
                            {suggestedSwitch && switchJointConnections && (
                                <SwitchJointInfobox
                                    switchAlignments={suggestedSwitch.switchStructure.alignments}
                                    jointConnections={switchJointConnections}
                                    publishType={publishType}
                                />
                            )}
                        </div>
                    )}
                    {linkingIsStarted && resolution > SWITCH_SHOW && (
                        <div className={styles['geometry-switch-infobox__zoom-warning']}>
                            <Icons.Info color={IconColor.INHERIT} />
                            {t('tool-panel.switch.geometry.zoom-closer')}
                        </div>
                    )}
                    {suggestedSwitch && linkingState === undefined && (
                        <InfoboxButtons>
                            <Button size={ButtonSize.SMALL} onClick={startLinking}>
                                {t('tool-panel.switch.geometry.start-setup')}
                            </Button>
                        </InfoboxButtons>
                    )}
                    {linkingState && (
                        <div>
                            <InfoboxContentSpread>
                                <MessageBox pop={showInvalidSwitchError}>
                                    {t(
                                        'tool-panel.switch.geometry.cannot-link-invalid-switch-type',
                                    )}
                                </MessageBox>
                            </InfoboxContentSpread>
                            <InfoboxButtons>
                                <Button
                                    size={ButtonSize.SMALL}
                                    variant={ButtonVariant.SECONDARY}
                                    disabled={linkingCallInProgress}
                                    onClick={onStopLinking}>
                                    {t('tool-panel.switch.geometry.cancel')}
                                </Button>
                                <Button
                                    size={ButtonSize.SMALL}
                                    disabled={!canLink}
                                    isProcessing={linkingCallInProgress}
                                    onClick={link}>
                                    {t('tool-panel.switch.geometry.save-link')}
                                </Button>
                            </InfoboxButtons>
                        </div>
                    )}
                </InfoboxContent>
            </Infobox>

            {selectedSuggestedSwitch && showAddSwitchDialog && (
                <SwitchEditDialog
                    onClose={() => setShowAddSwitchDialog(false)}
                    onInsert={handleSwitchInsert}
                    prefilledSwitchStructureId={selectedSuggestedSwitch.switchStructure.id}
                />
            )}
        </React.Fragment>
    );
};

export default GeometrySwitchLinkingInfobox;
