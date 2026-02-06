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
    SwitchWithOperationalPointPolygonInclusions,
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
    createUseLinkingHook,
    formatLinkingToast,
    Hide,
    LinkingDirection,
} from 'tool-panel/operational-point/operational-point-utils';
import { updateSwitchChangeTime } from 'common/change-time-api';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import {
    calculateBoundingBoxToShowAroundLocation,
    MAP_POINT_DEFAULT_BBOX_OFFSET,
} from 'map/map-utils';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import styles from './operational-point-infobox.scss';
import { createClassName } from 'vayla-design-lib/utils';
import InfoboxText from 'tool-panel/infobox/infobox-text';

type OperationalPointSwitchesInfoboxProps = {
    contentVisible: boolean;
    onVisibilityChange: (key: 'switches') => void;
    layoutContext: LayoutContext;
    operationalPoint: OperationalPoint;
    changeTimes: ChangeTimes;
};

export const OperationalPointSwitchesInfobox: React.FC<OperationalPointSwitchesInfoboxProps> = ({
    contentVisible,
    onVisibilityChange,
    layoutContext,
    operationalPoint,
    changeTimes,
}) => {
    const { t } = useTranslation();
    const delegates = useMemo(() => createDelegates(trackLayoutActionCreators), []);

    const linkingHook = useLinkingSwitches(layoutContext, operationalPoint, changeTimes);
    const {
        isInitializing,
        itemAssociation,
        linkedItems,
        unlinkedItems,
        isEditing,
        hasChanges,
        startEditing,
        cancelEditing,
        saveEdits,
        setLinks,
    } = linkingHook;

    const polygonInclusion: Map<LayoutSwitchId, OperationalPointId[]> = (
        itemAssociation ?? []
    ).reduce((map, obj) => map.set(obj.switchId, obj.withinPolygon), new Map());

    const [isSaving, setIsSaving] = useState(false);

    const handleSave = async () => {
        setIsSaving(true);
        try {
            const { linkedNames, unlinkedNames } = await saveEdits();
            const toastMessage = formatLinkingToast(
                linkedNames,
                unlinkedNames,
                t,
                'tool-panel.operational-point.switch-links',
                operationalPoint.name,
            );
            if (toastMessage) {
                Snackbar.success(toastMessage);
            }
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <Infobox
            title={t('tool-panel.operational-point.switch-links.infobox-header')}
            contentVisible={contentVisible}
            onContentVisibilityChange={() => onVisibilityChange('switches')}>
            {isInitializing ? (
                <Spinner />
            ) : (
                <InfoboxContent>
                    <OperationalPointSwitchesDirectionInfobox
                        layoutContext={layoutContext}
                        operationalPoint={operationalPoint}
                        switches={linkedItems}
                        linkingDirection={'unlinking'}
                        polygonInclusion={polygonInclusion}
                        isEditing={isEditing}
                        setLinks={setLinks}
                        showArea={delegates.showArea}
                    />
                    <OperationalPointSwitchesDirectionInfobox
                        layoutContext={layoutContext}
                        operationalPoint={operationalPoint}
                        switches={unlinkedItems}
                        linkingDirection={'linking'}
                        polygonInclusion={polygonInclusion}
                        isEditing={isEditing}
                        setLinks={setLinks}
                        showArea={delegates.showArea}
                    />
                    <div className={styles['operational-point-linking-infobox__edit-buttons']}>
                        {!isEditing ? (
                            <Button
                                variant={ButtonVariant.SECONDARY}
                                size={ButtonSize.SMALL}
                                onClick={startEditing}>
                                {t('tool-panel.operational-point.edit-links')}
                            </Button>
                        ) : (
                            <>
                                <Button
                                    variant={ButtonVariant.SECONDARY}
                                    size={ButtonSize.SMALL}
                                    disabled={isSaving}
                                    onClick={cancelEditing}>
                                    {t('tool-panel.operational-point.cancel-editing-links')}
                                </Button>
                                <Button
                                    variant={ButtonVariant.PRIMARY}
                                    size={ButtonSize.SMALL}
                                    disabled={!hasChanges || isSaving}
                                    onClick={handleSave}>
                                    {t('tool-panel.operational-point.save-links')}
                                </Button>
                                {isSaving && <Spinner />}
                            </>
                        )}
                    </div>
                </InfoboxContent>
            )}
        </Infobox>
    );
};

const useLinkingSwitches = createUseLinkingHook<
    LayoutSwitchId,
    LayoutSwitch,
    SwitchWithOperationalPointPolygonInclusions[]
>(
    async (context: LayoutContext, operationalPointId: OperationalPointId) => {
        const itemAssociation = await findOperationalPointSwitches(context, operationalPointId);
        const switchIds = (itemAssociation ?? []).map(({ switchId }) => switchId);
        const items = await getSwitches(switchIds, context);
        return { itemAssociation, items };
    },
    (s) => (s.operationalPointId === undefined ? [] : [s.operationalPointId]),
    (changeTimes) => changeTimes.layoutSwitch,
    updateSwitchChangeTime,
    linkSwitchesToOperationalPoint,
    unlinkSwitchesFromOperationalPoint,
);

type OperationalPointSwitchesDirectionInfoboxProps = {
    layoutContext: LayoutContext;
    operationalPoint: OperationalPoint;
    switches: LayoutSwitch[];
    polygonInclusion: Map<LayoutSwitchId, OperationalPointId[]>;
    linkingDirection: LinkingDirection;
    isEditing: boolean;
    setLinks: (ids: LayoutSwitchId[], direction: LinkingDirection) => void;
    showArea: (area: BoundingBox) => void;
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
    setLinks,
    showArea,
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
        setLinks(
            switches.map((s) => s.id),
            linkingDirection,
        );
    };

    return (
        <>
            <div className={styles['operational-point-linking-infobox__direction-title']}>
                {t(
                    `tool-panel.operational-point.switch-links.${linkingDirection === 'linking' ? 'detached-in-polygon' : 'attached'}-header`,
                    { count: switches.length },
                )}
            </div>
            <>
                <div
                    className={createClassName(
                        styles['operational-point-linking-infobox__switch-direction-content'],
                        validatedSwitches.length === 0 &&
                            styles[
                                'operational-point-linking-infobox__switch-direction-content--empty'
                            ],
                    )}>
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
                                setLinks={setLinks}
                                showArea={showArea}
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
    setLinks: (ids: LayoutSwitchId[], direction: LinkingDirection) => void;
    showArea: (area: BoundingBox) => void;
};

const OperationalPointSwitchRow: React.FC<OperationalPointSwitchRowProps> = ({
    layoutContext,
    switchItem,
    issues,
    linkingDirection,
    isEditing,
    setLinks,
    showArea,
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
            <SwitchBadge switchItem={switchItem} />
            {switchLocation ? (
                <a
                    className={styles['operational-point-linking-infobox__position-pin']}
                    onClick={handleZoomToSwitch}>
                    <Icons.Target size={IconSize.SMALL} />
                </a>
            ) : (
                <div />
            )}
            <Hide when={!isEditing}>
                <Button
                    variant={ButtonVariant.GHOST}
                    size={ButtonSize.SMALL}
                    icon={linkingDirection === 'linking' ? Icons.Add : Icons.Subtract}
                    onClick={() => setLinks([switchItem.id], linkingDirection)}
                />
            </Hide>
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
