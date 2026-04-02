import React from 'react';
import { useTranslation } from 'react-i18next';
import { LayoutContext } from 'common/common-model';
import {
    getSwitchLocation,
    LayoutSwitch,
    LayoutSwitchId,
    OperationalPoint,
    OperationalPointId,
} from 'track-layout/track-layout-model';
import {
    Hide,
    LinkingDirection,
    OperationalPointSwitchLinkingInfo,
} from 'tool-panel/operational-point/operational-point-utils';
import { BoundingBox } from 'model/geometry';
import { compareIgnoreCase } from 'utils/string-utils';
import { createClassName } from 'vayla-design-lib/utils';
import styles from './operational-point-infobox.scss';
import InfoboxText from 'tool-panel/infobox/infobox-text';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import {
    calculateBoundingBoxToShowAroundLocation,
    MAP_POINT_DEFAULT_BBOX_OFFSET,
} from 'map/map-utils';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { SwitchBadge } from 'geoviite-design-lib/switch/switch-badge';
import { useOperationalPoint } from 'track-layout/track-layout-react-utils';

type OperationalPointSwitchesDirectionInfoboxProps = {
    layoutContext: LayoutContext;
    operationalPoint: OperationalPoint;
    switches: LayoutSwitch[];
    polygonInclusion: OperationalPointSwitchLinkingInfo[];
    olpOperationalPointIds: ReadonlySet<OperationalPointId>;
    linkingDirection: LinkingDirection;
    isEditing: boolean;
    linkingAction: (id: LayoutSwitchId) => void;
    massLinkingAction: () => void;
    showArea: (area: BoundingBox) => void;
    onSelectSwitch: (switchId: LayoutSwitchId) => void;
};

export const OperationalPointSwitchesDirectionInfobox: React.FC<
    OperationalPointSwitchesDirectionInfoboxProps
> = ({
    layoutContext,
    operationalPoint,
    switches,
    linkingDirection,
    polygonInclusion,
    olpOperationalPointIds,
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
                polygonInclusion.find((obj) => obj.switchId === item.id)?.allOperationalPoints ??
                    [],
                linkingDirection,
                olpOperationalPointIds,
            ),
        }))
        .sort((a, b) => {
            const issuesComp = (b.issues.length > 0 ? 1 : 0) - (a.issues.length > 0 ? 1 : 0);
            return issuesComp === 0 ? compareIgnoreCase(a.item.name, b.item.name) : issuesComp;
        });

    const infoboxContentStyles = createClassName(
        styles['operational-point-linking-infobox__switch-direction-content'],
        validatedSwitches.length === 0 &&
            styles['operational-point-linking-infobox__switch-direction-content--empty'],
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
                        onClick={massLinkingAction}>
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
            <Hide when={!isEditing}>
                <Button
                    variant={ButtonVariant.GHOST}
                    size={ButtonSize.SMALL}
                    icon={linkingDirection === 'linking' ? Icons.Ascending : Icons.Descending}
                    onClick={() => linkingAction(switchItem.id)}
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
    olpOperationalPointIds: ReadonlySet<OperationalPointId>,
): SwitchRowValidationIssue[] {
    const wrongPolygonProblems = polygonInclusions
        .filter((inPolygon) => inPolygon !== operationalPointId)
        .filter((inPolygon) => !olpOperationalPointIds.has(inPolygon))
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
