import * as React from 'react';
import Infobox from 'tool-panel/infobox/infobox';
import InfoboxContent, { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';
import { useTranslation } from 'react-i18next';
import styles from './switch-infobox.scss';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import {
    GeometrySwitchSuggestionFailureReason,
    LinkingState,
    LinkingSwitch,
    SuggestedSwitch,
} from 'linking/linking-model';
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';
import { SwitchEditDialogContainer } from './dialog/switch-edit-dialog';
import { LayoutSwitch } from 'track-layout/track-layout-model';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { draftLayoutContext, LayoutContext, TimeStamp } from 'common/common-model';
import { SWITCH_SHOW } from 'map/layers/utils/layer-visibility-limits';
import { getSuggestedSwitchForGeometrySwitch, linkSwitch } from 'linking/linking-api';
import * as SnackBar from 'geoviite-design-lib/snackbar/snackbar';
import { GeometryPlanId, GeometrySwitchId } from 'geometry/geometry-model';
import {
    refreshSwitchSelection,
    useSwitch,
    useSwitchStructure,
} from 'track-layout/track-layout-react-utils';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { LinkingStatus } from 'linking/linking-status';
import { GeometrySwitchLinkingInitiation } from 'tool-panel/switch/geometry-switch-linking-initiation';
import { GeometrySwitchLinkingCandidates } from 'tool-panel/switch/geometry-switch-linking-candidates';
import { SwitchJointInfoboxContainer } from 'tool-panel/switch/switch-joint-infobox-container';
import { GeometrySwitchLinkingErrors } from 'tool-panel/switch/geometry-switch-linking-errors';
import { SwitchTypeMatch } from 'linking/linking-utils';
import { GeometrySwitchLinkingInfoboxVisibilities } from 'track-layout/track-layout-slice';
import { OnSelectFunction, OptionalUnselectableItemCollections } from 'selection/selection-model';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { SwitchBadge, SwitchBadgeStatus } from 'geoviite-design-lib/switch/switch-badge';
import { MessageBox } from 'geoviite-design-lib/message-box/message-box';
import { useUserHasPrivilege } from 'store/hooks';
import { PrivilegeRequired } from 'user/privilege-required';
import { VIEW_LAYOUT_DRAFT } from 'user/user-model';

type GeometrySwitchLinkingInfoboxProps = {
    geometrySwitchId?: GeometrySwitchId;
    selectedSuggestedSwitch?: SuggestedSwitch;
    switchId?: GeometrySwitchId;
    linkingState?: LinkingSwitch;
    onLinkingStart: (suggestedSwitch: SuggestedSwitch) => void;
    onSelect: OnSelectFunction;
    onUnselect: (items: OptionalUnselectableItemCollections) => void;
    switchChangeTime: TimeStamp;
    locationTrackChangeTime: TimeStamp;
    layoutSwitch?: LayoutSwitch;
    resolution: number;
    onStopLinking: () => void;
    onSuggestedSwitchChange?: (suggestedSwitch: SuggestedSwitch) => void;
    planId?: GeometryPlanId;
    layoutContext: LayoutContext;
    visibilities: GeometrySwitchLinkingInfoboxVisibilities;
    onVisibilityChange: (visibilities: GeometrySwitchLinkingInfoboxVisibilities) => void;
};

const isLinkingStarted = (linkingState: LinkingState) =>
    linkingState.state === 'setup' || linkingState.state === 'allSet';

const GeometrySwitchLinkingInfobox: React.FC<GeometrySwitchLinkingInfoboxProps> = ({
    geometrySwitchId,
    linkingState,
    switchId,
    onLinkingStart,
    selectedSuggestedSwitch,
    onSelect,
    onUnselect,
    switchChangeTime,
    locationTrackChangeTime,
    layoutSwitch,
    resolution,
    onStopLinking,
    onSuggestedSwitchChange,
    planId,
    layoutContext,
    visibilities,
    onVisibilityChange,
}) => {
    const { t } = useTranslation();
    const canViewDraft = useUserHasPrivilege(VIEW_LAYOUT_DRAFT);
    const [suggestedSwitchResult, suggestedSwitchFetchStatus] = useLoaderWithStatus(() => {
        if (!canViewDraft) {
            return undefined;
        } else if (selectedSuggestedSwitch) {
            return Promise.resolve({ switch: selectedSuggestedSwitch });
        } else if (geometrySwitchId !== undefined) {
            return getSuggestedSwitchForGeometrySwitch(layoutContext.branch, geometrySwitchId);
        }
        return undefined;
    }, [geometrySwitchId, selectedSuggestedSwitch]);
    const suggestedSwitch =
        suggestedSwitchResult === undefined
            ? undefined
            : 'switch' in suggestedSwitchResult
              ? suggestedSwitchResult.switch
              : undefined;
    const switchStructure = useSwitchStructure(suggestedSwitch?.switchStructureId);
    const [showAddSwitchDialog, setShowAddSwitchDialog] = React.useState(false);
    const [linkingCallInProgress, setLinkingCallInProgress] = React.useState(false);
    const selectedLayoutSwitch = useSwitch(
        linkingState?.layoutSwitchId,
        layoutContext,
        switchChangeTime,
    );
    const selectedLayoutSwitchStructure = useSwitchStructure(
        selectedLayoutSwitch?.switchStructureId,
    );
    const switchTypeMatch =
        suggestedSwitch &&
        switchStructure &&
        selectedLayoutSwitch &&
        switchStructure.id === selectedLayoutSwitch.switchStructureId
            ? SwitchTypeMatch.Exact
            : suggestedSwitch &&
                selectedLayoutSwitchStructure &&
                switchStructure &&
                switchStructure.baseType === selectedLayoutSwitchStructure.baseType &&
                switchStructure.hand === selectedLayoutSwitchStructure.hand
              ? SwitchTypeMatch.Similar
              : SwitchTypeMatch.Invalid;

    const [switchTypeDifferenceIsConfirmed, setSwitchTypeDifferenceIsConfirmed] =
        React.useState(false);
    const geometrySwitchInvalidityReason: GeometrySwitchSuggestionFailureReason | undefined =
        suggestedSwitchResult !== undefined && 'failure' in suggestedSwitchResult
            ? suggestedSwitchResult.failure
            : undefined;
    const isValidLayoutSwitch =
        suggestedSwitch !== undefined &&
        selectedLayoutSwitch &&
        (switchTypeMatch === SwitchTypeMatch.Exact ||
            (switchTypeMatch === SwitchTypeMatch.Similar && switchTypeDifferenceIsConfirmed));
    const canLink =
        selectedLayoutSwitch &&
        isValidLayoutSwitch &&
        linkingState?.state === 'allSet' &&
        !linkingCallInProgress &&
        selectedLayoutSwitch.stateCategory !== 'NOT_EXISTING';

    React.useEffect(() => setSwitchTypeDifferenceIsConfirmed(false), [selectedLayoutSwitch]);

    function startLinking() {
        if (suggestedSwitch) {
            onSuggestedSwitchChange && onSuggestedSwitchChange(suggestedSwitch);
            onLinkingStart(suggestedSwitch);
        }
    }

    const handleSwitchSave = refreshSwitchSelection(
        draftLayoutContext(layoutContext),
        onSelect,
        onUnselect,
    );

    async function link() {
        if (linkingState?.suggestedSwitch && linkingState.layoutSwitchId) {
            setLinkingCallInProgress(true);
            try {
                await linkSwitch(
                    layoutContext.branch,
                    linkingState.suggestedSwitch,
                    linkingState.layoutSwitchId,
                );
                SnackBar.success('tool-panel.switch.geometry.linking-succeed-msg');
                onStopLinking();
            } finally {
                setLinkingCallInProgress(false);
            }
        }
    }

    const linkingDisabledReason = () => {
        if (selectedLayoutSwitch?.stateCategory === 'NOT_EXISTING') {
            return t('tool-panel.switch.geometry.layout-switch-not-existing');
        }
        return undefined;
    };

    return (
        <React.Fragment>
            <Infobox
                contentVisible={visibilities.linking}
                onContentVisibilityChange={() =>
                    onVisibilityChange({
                        ...visibilities,
                        linking: !visibilities.linking,
                    })
                }
                title={t('tool-panel.switch.geometry.linking-header')}
                qa-id="geometry-switch-linking-infobox">
                <InfoboxContent>
                    {switchId && planId && (
                        <React.Fragment>
                            <LinkingStatus
                                switchId={switchId}
                                planId={planId}
                                layoutContext={layoutContext}
                                switchChangeTime={switchChangeTime}
                                locationTrackChangeTime={locationTrackChangeTime}
                            />
                            <PrivilegeRequired privilege={VIEW_LAYOUT_DRAFT}>
                                {suggestedSwitchFetchStatus === LoaderStatus.Ready ? (
                                    <GeometrySwitchLinkingInitiation
                                        onStartLinking={startLinking}
                                        geometrySwitchInvalidityReason={
                                            geometrySwitchInvalidityReason
                                        }
                                        hasSuggestedSwitch={!!suggestedSwitch}
                                        linkingState={linkingState}
                                        layoutContext={layoutContext}
                                    />
                                ) : (
                                    <Spinner />
                                )}
                            </PrivilegeRequired>
                        </React.Fragment>
                    )}
                    {linkingState && (
                        <React.Fragment>
                            <div className={styles['geometry-switch-infobox__linking-container']}>
                                {linkingState.switchSource === 'PREDEFINED' ? (
                                    layoutSwitch && (
                                        <InfoboxField
                                            label={t(
                                                'tool-panel.switch.geometry.predefined-switch',
                                            )}>
                                            <SwitchBadge
                                                switchItem={layoutSwitch}
                                                status={SwitchBadgeStatus.SELECTED}
                                            />
                                        </InfoboxField>
                                    )
                                ) : (
                                    <React.Fragment>
                                        <span
                                            className={
                                                styles['geometry-switch-infobox__info-text']
                                            }>
                                            {t('tool-panel.switch.geometry.select-switch-msg')}
                                        </span>
                                        <GeometrySwitchLinkingCandidates
                                            layoutContext={layoutContext}
                                            onSelectSwitch={(s) => onSelect({ switches: [s.id] })}
                                            selectedSwitchId={layoutSwitch?.id}
                                            switchChangeTime={switchChangeTime}
                                            suggestedSwitch={suggestedSwitch}
                                            onShowAddSwitchDialog={() =>
                                                setShowAddSwitchDialog(true)
                                            }
                                        />
                                    </React.Fragment>
                                )}
                                {suggestedSwitch && switchStructure && (
                                    <SwitchJointInfoboxContainer
                                        suggestedSwitch={suggestedSwitch}
                                        suggestedSwitchStructure={switchStructure}
                                        layoutContext={layoutContext}
                                    />
                                )}
                            </div>
                            {isLinkingStarted(linkingState) && resolution > SWITCH_SHOW && (
                                <div className={styles['geometry-switch-infobox__zoom-warning']}>
                                    <Icons.Info color={IconColor.INHERIT} />
                                    {t('tool-panel.switch.geometry.zoom-closer')}
                                </div>
                            )}
                            {suggestedSwitch && switchStructure && (
                                <GeometrySwitchLinkingErrors
                                    selectedLayoutSwitchStructure={selectedLayoutSwitchStructure}
                                    suggestedSwitchStructure={switchStructure}
                                    switchTypeMatch={switchTypeMatch}
                                    onConfirmChanged={(confirmed) =>
                                        setSwitchTypeDifferenceIsConfirmed(confirmed)
                                    }
                                />
                            )}
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
                                    title={linkingDisabledReason()}
                                    isProcessing={linkingCallInProgress}
                                    qa-id="link-geometry-switch"
                                    onClick={link}>
                                    {t('tool-panel.switch.geometry.save-link')}
                                </Button>
                            </InfoboxButtons>
                            {linkingState.switchSource === 'PREDEFINED' &&
                                layoutSwitch?.stateCategory === 'NOT_EXISTING' && (
                                    <InfoboxContentSpread>
                                        <MessageBox type={'ERROR'}>
                                            {t('tool-panel.switch.layout.cant-link-deleted')}
                                        </MessageBox>
                                    </InfoboxContentSpread>
                                )}
                        </React.Fragment>
                    )}
                </InfoboxContent>
            </Infobox>

            {selectedSuggestedSwitch && showAddSwitchDialog && (
                <SwitchEditDialogContainer
                    onClose={() => setShowAddSwitchDialog(false)}
                    onSave={handleSwitchSave}
                    prefilledSwitchStructureId={selectedSuggestedSwitch.switchStructureId}
                />
            )}
        </React.Fragment>
    );
};

export default GeometrySwitchLinkingInfobox;
