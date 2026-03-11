import React, { useMemo, useState } from 'react';
import {
    LayoutSwitchId,
    OperationalPoint,
    OperationalPointId,
} from 'track-layout/track-layout-model';
import { LayoutContext } from 'common/common-model';
import {
    findOperationalPointSwitches,
    getSwitches,
    linkSwitchesToOperationalPoint,
    unlinkSwitchesFromOperationalPoint,
} from 'track-layout/layout-switch-api';
import Infobox from 'tool-panel/infobox/infobox';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { useTranslation } from 'react-i18next';
import { ChangeTimes } from 'common/common-slice';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import {
    formatLinkingToast,
    OperationalPointSwitchLinkingInfo,
} from 'tool-panel/operational-point/operational-point-utils';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import InfoboxContent, { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import styles from './operational-point-infobox.scss';
import InfoboxText from 'tool-panel/infobox/infobox-text';
import {
    ProgressIndicatorType,
    ProgressIndicatorWrapper,
} from 'vayla-design-lib/progress/progress-indicator-wrapper';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { MessageBox, MessageBoxType } from 'geoviite-design-lib/message-box/message-box';
import { useTrackLayoutAppSelector } from 'store/hooks';
import { LinkingType } from 'linking/linking-model';
import { deduplicate, filterNotEmpty } from 'utils/array-utils';
import { updateAllChangeTimes } from 'common/change-time-api';
import { OperationalPointSwitchesDirectionInfobox } from 'tool-panel/operational-point/operational-point-switches-direction-infobox';

type OperationalPointSwitchesInfoboxProps = {
    contentVisible: boolean;
    onVisibilityChange: (key: 'switches') => void;
    layoutContext: LayoutContext;
    operationalPoint: OperationalPoint;
    changeTimes: ChangeTimes;
    onSelectSwitch: (switchId: LayoutSwitchId) => void;
    operationalPointFetchStatus: LoaderStatus;
};

const getSwitchesForOperationalPoint = async (
    context: LayoutContext,
    operationalPointId: OperationalPointId,
): Promise<OperationalPointSwitchLinkingInfo[]> => {
    const switchesWithinPolygons = await findOperationalPointSwitches(context, operationalPointId);
    const switchIds = switchesWithinPolygons.map(({ switchId }) => switchId);
    const switches = await getSwitches(switchIds, context);
    return switchesWithinPolygons
        .map((switchWithinPolygon) => {
            const layoutSwitch = switches.find((sw) => sw.id === switchWithinPolygon.switchId);

            return layoutSwitch
                ? {
                      ...switchWithinPolygon,
                      layoutSwitch,
                  }
                : undefined;
        })
        ?.filter(filterNotEmpty);
};

export const OperationalPointSwitchesInfobox: React.FC<OperationalPointSwitchesInfoboxProps> = ({
    contentVisible,
    onVisibilityChange,
    layoutContext,
    operationalPoint,
    changeTimes,
    onSelectSwitch,
    operationalPointFetchStatus,
}) => {
    const { t } = useTranslation();
    const delegates = useMemo(() => createDelegates(trackLayoutActionCreators), []);
    const linkingState = useTrackLayoutAppSelector((state) => state.linkingState);
    const operationalPointSwitchLinkingState =
        linkingState?.type === LinkingType.LinkingOperationalPointSwitches
            ? linkingState
            : undefined;

    const [switchLinkings, linkedSwitchesFetchStatus] = useLoaderWithStatus(
        () => getSwitchesForOperationalPoint(layoutContext, operationalPoint.id),
        [
            operationalPoint?.id,
            layoutContext.publicationState,
            layoutContext.branch,
            changeTimes.operationalPoints,
            changeTimes.layoutSwitch,
        ],
    );

    const originallyLinkedSwitches =
        switchLinkings?.filter((sw) => sw.isLinked)?.map((sw) => sw.switchId) ?? [];

    const linkedItems = operationalPointSwitchLinkingState
        ? operationalPointSwitchLinkingState.linkedSwitches
        : originallyLinkedSwitches;
    const linkedSwitches =
        switchLinkings
            ?.filter(({ switchId }) => linkedItems.includes(switchId))
            ?.map((sw) => sw.layoutSwitch) ?? [];
    const unlinkedSwitches =
        switchLinkings
            ?.filter(({ switchId }) => !linkedItems.includes(switchId))
            ?.map((sw) => sw.layoutSwitch) ?? [];

    const [isSaving, setIsSaving] = useState(false);

    const handleSave = async () => {
        setIsSaving(true);
        try {
            if (operationalPointSwitchLinkingState) {
                const newSwitchesToLink = linkedItems.filter(
                    (sw) => !originallyLinkedSwitches.includes(sw),
                );
                const switchesToUnlink = originallyLinkedSwitches.filter(
                    (sw) => !operationalPointSwitchLinkingState.linkedSwitches.includes(sw),
                );

                const newlyLinkedIds =
                    newSwitchesToLink.length > 0
                        ? await linkSwitchesToOperationalPoint(
                              layoutContext.branch,
                              newSwitchesToLink,
                              operationalPoint.id,
                          )
                        : [];
                const newlyUnlinkedIds =
                    switchesToUnlink.length > 0
                        ? await unlinkSwitchesFromOperationalPoint(
                              layoutContext.branch,
                              switchesToUnlink,
                          )
                        : [];

                const toastMessage = formatLinkingToast(
                    newlyLinkedIds,
                    newlyUnlinkedIds,
                    t,
                    'tool-panel.operational-point.switch-links',
                    operationalPoint.name,
                );
                await updateAllChangeTimes();
                await getSwitchesForOperationalPoint(
                    layoutContext,
                    operationalPointSwitchLinkingState.operationalPoint,
                );
                delegates.stopLinking();
                if (toastMessage) {
                    Snackbar.success(toastMessage);
                }
            }
        } finally {
            setIsSaving(false);
        }
    };

    const linkSwitch = (switchId: LayoutSwitchId) => {
        if (operationalPointSwitchLinkingState) {
            const newLinkedIds = deduplicate([
                ...operationalPointSwitchLinkingState.linkedSwitches,
                switchId,
            ]);
            delegates.setOperationalPointLinkedSwitches(newLinkedIds);
        }
    };
    const unlinkSwitch = (switchId: LayoutSwitchId) => {
        if (operationalPointSwitchLinkingState) {
            const newLinkedIds = operationalPointSwitchLinkingState.linkedSwitches.filter(
                (id) => id !== switchId,
            );
            delegates.setOperationalPointLinkedSwitches(newLinkedIds);
        }
    };

    return (
        <Infobox
            title={t('tool-panel.operational-point.switch-links.infobox-header')}
            contentVisible={contentVisible}
            onContentVisibilityChange={() => onVisibilityChange('switches')}>
            <InfoboxContent>
                <ProgressIndicatorWrapper
                    indicator={ProgressIndicatorType.Area}
                    inProgress={
                        linkedSwitchesFetchStatus !== LoaderStatus.Ready ||
                        operationalPointFetchStatus !== LoaderStatus.Ready
                    }>
                    {operationalPoint.polygon || linkedItems.length > 0 ? (
                        <React.Fragment>
                            {!operationalPoint.polygon && linkedItems.length > 0 && (
                                <InfoboxContentSpread>
                                    <MessageBox type={MessageBoxType.ERROR}>
                                        {t(
                                            'tool-panel.operational-point.switch-links.no-area-but-has-switches-linked',
                                        )}
                                    </MessageBox>
                                </InfoboxContentSpread>
                            )}
                            <OperationalPointSwitchesDirectionInfobox
                                layoutContext={layoutContext}
                                operationalPoint={operationalPoint}
                                switches={linkedSwitches}
                                linkingDirection={'unlinking'}
                                polygonInclusion={switchLinkings ?? []}
                                isEditing={!!operationalPointSwitchLinkingState}
                                linkingAction={unlinkSwitch}
                                massLinkingAction={() =>
                                    delegates.setOperationalPointLinkedSwitches([])
                                }
                                showArea={delegates.showArea}
                                onSelectSwitch={onSelectSwitch}
                            />
                            <OperationalPointSwitchesDirectionInfobox
                                layoutContext={layoutContext}
                                operationalPoint={operationalPoint}
                                switches={unlinkedSwitches}
                                linkingDirection={'linking'}
                                polygonInclusion={switchLinkings ?? []}
                                isEditing={!!operationalPointSwitchLinkingState}
                                linkingAction={linkSwitch}
                                massLinkingAction={(ids) =>
                                    delegates.setOperationalPointLinkedSwitches(ids)
                                }
                                showArea={delegates.showArea}
                                onSelectSwitch={onSelectSwitch}
                            />
                            <div
                                className={
                                    styles['operational-point-linking-infobox__edit-buttons']
                                }>
                                {!operationalPointSwitchLinkingState ? (
                                    <Button
                                        variant={ButtonVariant.SECONDARY}
                                        size={ButtonSize.SMALL}
                                        disabled={
                                            layoutContext.publicationState === 'OFFICIAL' ||
                                            !!linkingState
                                        }
                                        title={
                                            layoutContext.publicationState === 'OFFICIAL'
                                                ? t(
                                                      'tool-panel.disabled.activity-disabled-in-official-mode',
                                                  )
                                                : ''
                                        }
                                        onClick={() =>
                                            delegates.startLinkingOperationalPointSwitches({
                                                operationalPoint: operationalPoint.id,
                                                linkedSwitches: linkedSwitches.map((sw) => sw.id),
                                            })
                                        }>
                                        {t('tool-panel.operational-point.edit-links')}
                                    </Button>
                                ) : (
                                    <>
                                        <Button
                                            variant={ButtonVariant.SECONDARY}
                                            size={ButtonSize.SMALL}
                                            disabled={isSaving}
                                            onClick={() => delegates.stopLinking()}>
                                            {t('tool-panel.operational-point.cancel-editing-links')}
                                        </Button>
                                        <Button
                                            variant={ButtonVariant.PRIMARY}
                                            size={ButtonSize.SMALL}
                                            disabled={isSaving}
                                            onClick={handleSave}>
                                            {t('tool-panel.operational-point.save-links')}
                                        </Button>
                                        {isSaving && <Spinner />}
                                    </>
                                )}
                            </div>
                        </React.Fragment>
                    ) : layoutContext.publicationState === 'DRAFT' ? (
                        <InfoboxContentSpread>
                            <MessageBox>
                                {t('tool-panel.operational-point.switch-links.no-area')}
                            </MessageBox>
                        </InfoboxContentSpread>
                    ) : (
                        <InfoboxText
                            value={t('tool-panel.operational-point.switch-links.no-switches')}
                        />
                    )}
                </ProgressIndicatorWrapper>
            </InfoboxContent>
        </Infobox>
    );
};
