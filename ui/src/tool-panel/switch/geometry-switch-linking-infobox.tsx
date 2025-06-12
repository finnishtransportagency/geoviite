import * as React from 'react';
import Infobox from 'tool-panel/infobox/infobox';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import { useTranslation } from 'react-i18next';
import styles from './switch-infobox.scss';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import {
    GeometrySwitchSuggestionFailureReason,
    GeometrySwitchSuggestionResult,
    LinkingState,
    LinkingType,
    SuggestedSwitch,
} from 'linking/linking-model';
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';
import { SwitchEditDialogContainer } from './dialog/switch-edit-dialog';
import { LayoutSwitchId } from 'track-layout/track-layout-model';
import { useImmediateLoader, useTwoPartEffect } from 'utils/react-utils';
import { draftLayoutContext, LayoutContext, TimeStamp } from 'common/common-model';
import { SWITCH_SHOW } from 'map/layers/utils/layer-visibility-limits';
import { getSuggestedSwitchForGeometrySwitch, linkSwitch } from 'linking/linking-api';
import * as SnackBar from 'geoviite-design-lib/snackbar/snackbar';
import { GeometryPlanId, GeometrySwitch } from 'geometry/geometry-model';
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
import { SwitchLinkingInfoboxVisibilities } from 'track-layout/track-layout-slice';
import { OnSelectFunction, OptionalUnselectableItemCollections } from 'selection/selection-model';
import { PrivilegeRequired } from 'user/privilege-required';
import { VIEW_LAYOUT_DRAFT } from 'user/user-model';

type GeometrySwitchLinkingInfoboxProps = {
    geometrySwitch: GeometrySwitch;
    selectCandidateSwitchForLinking: (
        suggestedSwitch: SuggestedSwitch,
        layoutSwitchId: LayoutSwitchId,
    ) => void;
    linkingState: LinkingState | undefined;
    onLinkingStart: (suggestedSwitch: SuggestedSwitch) => void;
    onSelect: OnSelectFunction;
    onUnselect: (items: OptionalUnselectableItemCollections) => void;
    switchChangeTime: TimeStamp;
    locationTrackChangeTime: TimeStamp;
    resolution: number;
    onStopLinking: () => void;
    planId: GeometryPlanId;
    layoutContext: LayoutContext;
    visibilities: SwitchLinkingInfoboxVisibilities;
    onVisibilityChange: (visibilities: SwitchLinkingInfoboxVisibilities) => void;
};

const isLinkingStarted = (linkingState: LinkingState) =>
    linkingState.state === 'setup' || linkingState.state === 'allSet';

export type GeometrySwitchSuggestionLoadResult = {
    suggestionResult: GeometrySwitchSuggestionResult | undefined;
    layoutSwitchId: LayoutSwitchId | undefined;
};

function setLoadedSuggestionResult(
    setGeometrySwitchInvalidityReason: (
        reason: GeometrySwitchSuggestionFailureReason | undefined,
    ) => void,
    selectCandidateSwitchForLinking: (
        suggestedSwitch: SuggestedSwitch,
        layoutSwitchId: LayoutSwitchId,
    ) => void,
): ({ suggestionResult, layoutSwitchId }: GeometrySwitchSuggestionLoadResult) => void {
    return ({ suggestionResult, layoutSwitchId }) => {
        if (suggestionResult) {
            setGeometrySwitchInvalidityReason(
                'failure' in suggestionResult ? suggestionResult.failure : undefined,
            );
            if ('switch' in suggestionResult && suggestionResult.switch) {
                layoutSwitchId &&
                    selectCandidateSwitchForLinking(suggestionResult.switch, layoutSwitchId);
            }
        }
    };
}

const GeometrySwitchLinkingInfobox: React.FC<GeometrySwitchLinkingInfoboxProps> = ({
    geometrySwitch,
    linkingState,
    onLinkingStart,
    selectCandidateSwitchForLinking,
    onSelect,
    onUnselect,
    switchChangeTime,
    locationTrackChangeTime,
    resolution,
    onStopLinking,
    planId,
    layoutContext,
    visibilities,
    onVisibilityChange,
}) => {
    const { t } = useTranslation();
    const [geometrySwitchInvalidityReason, setGeometrySwitchInvalidityReason] =
        React.useState<GeometrySwitchSuggestionFailureReason>();
    const { load: loadSwitchSuggestion, isLoading: isLoadingSwitchSuggestion } =
        useImmediateLoader<GeometrySwitchSuggestionLoadResult>(
            setLoadedSuggestionResult(
                setGeometrySwitchInvalidityReason,
                selectCandidateSwitchForLinking,
            ),
        );

    const layoutSwitchId =
        linkingState?.type === LinkingType.LinkingGeometrySwitch
            ? linkingState.layoutSwitchId
            : undefined;
    const [initialSwitchSuggestionResult, setInitialSwitchSuggestionResult] =
        React.useState<GeometrySwitchSuggestionResult>();

    useTwoPartEffect(
        () =>
            getSuggestedSwitchForGeometrySwitch(
                layoutContext.branch,
                geometrySwitch.id,
                layoutSwitchId,
            ),
        (result: GeometrySwitchSuggestionResult | undefined) => {
            setGeometrySwitchInvalidityReason(result?.failure);
            // initial suggestion lives only in this component, but once we've started linking, we have to put
            // it in the global state to display it on the map
            if (linkingState?.type === LinkingType.LinkingGeometrySwitch) {
                if (result && result.switch && layoutSwitchId) {
                    selectCandidateSwitchForLinking(result.switch, layoutSwitchId);
                }
            } else {
                setInitialSwitchSuggestionResult(result);
            }
        },
        [geometrySwitch.id, layoutSwitchId],
    );

    const suggestedSwitch =
        linkingState?.type === LinkingType.LinkingGeometrySwitch
            ? linkingState.suggestedSwitch
            : undefined;

    const switchStructure = useSwitchStructure(geometrySwitch.switchStructureId);
    const [showAddSwitchDialog, setShowAddSwitchDialog] = React.useState(false);
    const [linkingCallInProgress, setLinkingCallInProgress] = React.useState(false);
    const selectedLayoutSwitch = useSwitch(
        linkingState?.type === LinkingType.LinkingGeometrySwitch
            ? linkingState.layoutSwitchId
            : undefined,
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
        selectedLayoutSwitch.stateCategory !== 'NOT_EXISTING' &&
        !isLoadingSwitchSuggestion;

    React.useEffect(() => setSwitchTypeDifferenceIsConfirmed(false), [selectedLayoutSwitch]);

    const handleSwitchSave = refreshSwitchSelection(
        draftLayoutContext(layoutContext),
        onSelect,
        onUnselect,
    );

    async function link() {
        if (
            linkingState?.type === LinkingType.LinkingGeometrySwitch &&
            linkingState.layoutSwitchId &&
            linkingState.suggestedSwitch &&
            !isLoadingSwitchSuggestion
        ) {
            setLinkingCallInProgress(true);
            try {
                await linkSwitch(
                    layoutContext.branch,
                    linkingState.suggestedSwitch,
                    linkingState.layoutSwitchId,
                    linkingState.geometrySwitchId,
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
                    <React.Fragment>
                        <LinkingStatus
                            switchId={geometrySwitch.id}
                            planId={planId}
                            layoutContext={layoutContext}
                            switchChangeTime={switchChangeTime}
                            locationTrackChangeTime={locationTrackChangeTime}
                        />
                        <PrivilegeRequired privilege={VIEW_LAYOUT_DRAFT}>
                            {linkingState?.type !== LinkingType.LinkingGeometrySwitch &&
                                (isLoadingSwitchSuggestion ? (
                                    <Spinner />
                                ) : (
                                    <GeometrySwitchLinkingInitiation
                                        onStartLinking={onLinkingStart}
                                        initialSuggestedSwitch={
                                            initialSwitchSuggestionResult !== undefined &&
                                            'switch' in initialSwitchSuggestionResult
                                                ? initialSwitchSuggestionResult.switch
                                                : undefined
                                        }
                                        geometrySwitchInvalidityReason={
                                            geometrySwitchInvalidityReason
                                        }
                                        linkingState={linkingState}
                                        layoutContext={layoutContext}
                                    />
                                ))}
                        </PrivilegeRequired>
                    </React.Fragment>
                    {linkingState?.type === LinkingType.LinkingGeometrySwitch && (
                        <React.Fragment>
                            <div className={styles['geometry-switch-infobox__linking-container']}>
                                <span className={styles['geometry-switch-infobox__info-text']}>
                                    {t('tool-panel.switch.geometry.select-switch-msg')}
                                </span>
                                <GeometrySwitchLinkingCandidates
                                    layoutContext={layoutContext}
                                    linkingState={linkingState}
                                    geometrySwitchId={geometrySwitch.id}
                                    switchChangeTime={switchChangeTime}
                                    suggestedSwitch={suggestedSwitch}
                                    onLoadSuggestedSwitchResult={(result) =>
                                        loadSwitchSuggestion(Promise.resolve(result))
                                    }
                                    onShowAddSwitchDialog={() => setShowAddSwitchDialog(true)}
                                />
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
                        </React.Fragment>
                    )}
                </InfoboxContent>
            </Infobox>

            {showAddSwitchDialog && (
                <SwitchEditDialogContainer
                    onClose={() => setShowAddSwitchDialog(false)}
                    onSave={handleSwitchSave}
                    prefilledSwitchStructureId={geometrySwitch.switchStructureId}
                />
            )}
        </React.Fragment>
    );
};

export default GeometrySwitchLinkingInfobox;
