import React, { useMemo, useState } from 'react';
import {
    getSwitchLocation,
    LayoutSwitch,
    LayoutSwitchId,
    OperationalPoint,
    OperationalPointId,
} from 'track-layout/track-layout-model';
import { LayoutContext } from 'common/common-model';
import { BoundingBox } from 'model/geometry';
import {
    findOperationalPointSwitches,
    getSwitches,
    linkSwitchesToOperationalPoint,
    unlinkSwitchesFromOperationalPoint,
} from 'track-layout/layout-switch-api';
import Infobox from 'tool-panel/infobox/infobox';
import { SwitchBadge } from 'geoviite-design-lib/switch/switch-badge';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { useTranslation } from 'react-i18next';
import { ChangeTimes } from 'common/common-slice';
import { compareIgnoreCase } from 'utils/string-utils';
import { useOperationalPoint } from 'track-layout/track-layout-react-utils';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import {
    formatLinkingToast,
    Hide,
    LinkingDirection,
} from 'tool-panel/operational-point/operational-point-utils';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import InfoboxContent, { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';
import {
    calculateBoundingBoxToShowAroundLocation,
    MAP_POINT_DEFAULT_BBOX_OFFSET,
} from 'map/map-utils';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import styles from './operational-point-infobox.scss';
import { createClassName } from 'vayla-design-lib/utils';
import InfoboxText from 'tool-panel/infobox/infobox-text';
import {
    ProgressIndicatorType,
    ProgressIndicatorWrapper,
} from 'vayla-design-lib/progress/progress-indicator-wrapper';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { MessageBox, MessageBoxType } from 'geoviite-design-lib/message-box/message-box';
import { useTrackLayoutAppSelector } from 'store/hooks';
import { LinkingType } from 'linking/linking-model';
import { deduplicate } from 'utils/array-utils';
import { updateAllChangeTimes } from 'common/change-time-api';

type OperationalPointSwitchesInfoboxProps = {
    contentVisible: boolean;
    onVisibilityChange: (key: 'switches') => void;
    layoutContext: LayoutContext;
    operationalPoint: OperationalPoint;
    changeTimes: ChangeTimes;
    onSelectSwitch: (switchId: LayoutSwitchId) => void;
    operationalPointFetchStatus: LoaderStatus;
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

    const getSwitchesForOperationalPoint = async (
        context: LayoutContext,
        operationalPointId: OperationalPointId,
    ) => {
        const switchesWithinPolygons = await findOperationalPointSwitches(
            context,
            operationalPointId,
        );
        const switchIds = (switchesWithinPolygons ?? []).map(({ switchId }) => switchId);
        const switches = await getSwitches(switchIds, context);
        return { switchesWithinPolygons, switches };
    };
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

    const switchesInPolygons: Map<LayoutSwitchId, OperationalPointId[]> = (
        switchLinkings?.switchesWithinPolygons ?? []
    ).reduce((map, obj) => map.set(obj.switchId, obj.allOperationalPoints), new Map());
    const originallyLinkedSwitches =
        switchLinkings?.switchesWithinPolygons
            ?.filter((sw) => sw.isLinked)
            ?.map((sw) => sw.switchId) ?? [];

    const linkedItems = operationalPointSwitchLinkingState
        ? operationalPointSwitchLinkingState.linkedSwitches
        : originallyLinkedSwitches;
    const linkedSwitches =
        switchLinkings?.switches?.filter((sw) => linkedItems.includes(sw.id)) ?? [];
    const unlinkedSwitches =
        switchLinkings?.switches?.filter((sw) => !linkedItems.includes(sw.id)) ?? [];

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
                                polygonInclusion={switchesInPolygons}
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
                                polygonInclusion={switchesInPolygons}
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

type OperationalPointSwitchesDirectionInfoboxProps = {
    layoutContext: LayoutContext;
    operationalPoint: OperationalPoint;
    switches: LayoutSwitch[];
    polygonInclusion: Map<LayoutSwitchId, OperationalPointId[]>;
    linkingDirection: LinkingDirection;
    isEditing: boolean;
    linkingAction: (id: LayoutSwitchId) => void;
    massLinkingAction: (ids: LayoutSwitchId[]) => void;
    showArea: (area: BoundingBox) => void;
    onSelectSwitch: (switchId: LayoutSwitchId) => void;
};

const OperationalPointSwitchesDirectionInfobox: React.FC<
    OperationalPointSwitchesDirectionInfoboxProps
> = ({
    layoutContext,
    operationalPoint,
    switches,
    linkingDirection,
    polygonInclusion,
    isEditing,
    linkingAction,
    massLinkingAction,
    showArea,
    onSelectSwitch,
}) => {
    const { t } = useTranslation();

    const validatedSwitches = switches
        .map((item) => ({
            item,
            issues: validateOperationalPointSwitchRow(
                operationalPoint.id,
                item,
                polygonInclusion.get(item.id) ?? [],
                linkingDirection,
            ),
        }))
        .sort((a, b) => {
            const issuesComp = (b.issues.length > 0 ? 1 : 0) - (a.issues.length > 0 ? 1 : 0);
            return issuesComp === 0 ? compareIgnoreCase(a.item.name, b.item.name) : issuesComp;
        });

    const handleSetLinksAll = () => {
        massLinkingAction(switches.map((s) => s.id));
    };
    const infoboxContentStyles = createClassName(
        styles['operational-point-linking-infobox__switch-direction-content'],
        validatedSwitches.length === 0 &&
            styles['operational-point-linking-infobox__switch-direction-content--empty'],
        isEditing && styles['operational-point-linking-infobox__switch-direction-content--editing'],
    );

    return (
        <>
            <div className={styles['operational-point-linking-infobox__direction-title']}>
                {t(
                    `tool-panel.operational-point.switch-links.${linkingDirection === 'linking' ? 'detached-in-polygon' : 'attached'}-header`,
                    { count: switches.length },
                )}
            </div>
            <>
                <div className={infoboxContentStyles}>
                    {validatedSwitches.length === 0 ? (
                        <InfoboxText
                            value={t(
                                `tool-panel.operational-point.switch-links.none-for-${linkingDirection}`,
                            )}
                        />
                    ) : (
                        validatedSwitches.map(({ item, issues }) => (
                            <OperationalPointSwitchRow
                                key={item.id}
                                layoutContext={layoutContext}
                                switchItem={item}
                                issues={issues}
                                linkingDirection={linkingDirection}
                                isEditing={isEditing}
                                linkingAction={linkingAction}
                                showArea={showArea}
                                onSelectSwitch={onSelectSwitch}
                            />
                        ))
                    )}
                </div>
                <Hide when={!isEditing}>
                    <Button
                        variant={ButtonVariant.GHOST}
                        size={ButtonSize.SMALL}
                        onClick={handleSetLinksAll}>
                        {t(
                            `tool-panel.operational-point.switch-links.${linkingDirection === 'linking' ? 'attach' : 'detach'}-all`,
                            { count: switches.length },
                        )}
                    </Button>
                </Hide>
            </>
        </>
    );
};

type OperationalPointSwitchRowProps = {
    layoutContext: LayoutContext;
    switchItem: LayoutSwitch;
    issues: SwitchRowValidationIssue[];
    linkingDirection: LinkingDirection;
    isEditing: boolean;
    linkingAction: (id: LayoutSwitchId) => void;
    showArea: (area: BoundingBox) => void;
    onSelectSwitch: (switchId: LayoutSwitchId) => void;
};

const OperationalPointSwitchRow: React.FC<OperationalPointSwitchRowProps> = ({
    layoutContext,
    switchItem,
    issues,
    linkingDirection,
    isEditing,
    linkingAction,
    showArea,
    onSelectSwitch,
}) => {
    const switchLocation = getSwitchLocation(switchItem);

    const handleZoomToSwitch = () => {
        if (switchLocation) {
            showArea(
                calculateBoundingBoxToShowAroundLocation(
                    switchLocation,
                    MAP_POINT_DEFAULT_BBOX_OFFSET,
                ),
            );
        }
    };

    return (
        <>
            <Hide when={!isEditing}>
                <Button
                    variant={ButtonVariant.GHOST}
                    size={ButtonSize.SMALL}
                    icon={linkingDirection === 'linking' ? Icons.Ascending : Icons.Descending}
                    onClick={() => linkingAction(switchItem.id)}
                />
            </Hide>
            <div className={styles['operational-point-linking-infobox__switch-cell']}>
                <SwitchBadge
                    switchItem={switchItem}
                    onClick={() => onSelectSwitch(switchItem.id)}
                />
                {switchLocation ? (
                    <a
                        className={styles['operational-point-linking-infobox__position-pin']}
                        onClick={handleZoomToSwitch}>
                        <Icons.Target size={IconSize.SMALL} />
                    </a>
                ) : (
                    <span />
                )}
            </div>
            <div>
                {issues.slice(0, 1).map((issue, i) => (
                    <SwitchRowValidationIssueBadge
                        key={i}
                        issue={issue}
                        layoutContext={layoutContext}
                    />
                ))}
            </div>
        </>
    );
};

const SwitchRowValidationIssueBadge: React.FC<{
    layoutContext: LayoutContext;
    issue: SwitchRowValidationIssue;
}> = ({ layoutContext, issue }) => {
    const { t } = useTranslation();
    const operationalPoint = useOperationalPoint(
        'operationalPointId' in issue ? issue.operationalPointId : undefined,
        layoutContext,
    );
    return (
        <div className={styles['operational-point-linking-infobox__validation-issue-badge']}>
            <Icons.StatusError color={IconColor.INHERIT} />
            {t(`tool-panel.operational-point.switch-links.${issue.type}`, {
                operationalPointName: operationalPoint?.name,
            })}
        </div>
    );
};

type SwitchRowValidationIssue =
    | { type: 'switch-linked-to-wrong-point'; operationalPointId: OperationalPointId }
    | {
          type: 'switch-in-wrong-polygon';
          operationalPointId: OperationalPointId;
      }
    | { type: 'switch-not-in-polygon' };

function validateOperationalPointSwitchRow(
    operationalPointId: OperationalPointId,
    switchItem: LayoutSwitch,
    polygonInclusions: OperationalPointId[],
    linkingDirection: LinkingDirection,
): SwitchRowValidationIssue[] {
    const wrongPolygonProblems = polygonInclusions
        .filter((inPolygon) => inPolygon !== operationalPointId)
        .map((i) => ({ type: 'switch-in-wrong-polygon', operationalPointId: i }) as const);
    const linkedToWrongPointProblem =
        linkingDirection === 'linking' &&
        switchItem.operationalPointId !== undefined &&
        switchItem.operationalPointId !== operationalPointId
            ? ([
                  {
                      type: 'switch-linked-to-wrong-point',
                      operationalPointId: switchItem.operationalPointId,
                  },
              ] as const)
            : [];
    const notInPolygonProblem =
        linkingDirection === 'unlinking' && !polygonInclusions.some((i) => i === operationalPointId)
            ? [{ type: 'switch-not-in-polygon' } as const]
            : [];

    return [...wrongPolygonProblems, ...linkedToWrongPointProblem, ...notInPolygonProblem];
}
