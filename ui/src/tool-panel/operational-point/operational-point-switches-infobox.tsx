import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import React from 'react';
import {
    LayoutSwitch,
    LayoutSwitchId,
    OperationalPoint,
    OperationalPointId,
} from 'track-layout/track-layout-model';
import { LayoutContext } from 'common/common-model';
import {
    assignOperationalPoint,
    findOperationalPointSwitches,
    getSwitches,
    SwitchWithOperationalPointPolygonInclusions,
} from 'track-layout/layout-switch-api';
import { useRateLimitedTwoPartEffect, useSetState } from 'utils/react-utils';
import Infobox from 'tool-panel/infobox/infobox';
import { SwitchBadge } from 'geoviite-design-lib/switch/switch-badge';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { useTranslation } from 'react-i18next';
import { filterNotEmpty, partitionBy } from 'utils/array-utils';
import { getMaxTimestamp } from 'utils/date-utils';
import { ChangeTimes } from 'common/common-slice';
import { compareIgnoreCase } from 'utils/string-utils';
import { updateSwitchChangeTime } from 'common/change-time-api';
import { ShowMoreButton } from 'show-more-button/show-more-button';
import { useOperationalPoint } from 'track-layout/track-layout-react-utils';
import { Spinner } from 'vayla-design-lib/spinner/spinner';

type OperationalPointSwitchesInfoboxProps = {
    contentVisible: boolean;
    onVisibilityChange: (key: 'switches') => void;
    layoutContext: LayoutContext;
    operationalPoint: OperationalPoint;
    changeTimes: ChangeTimes;
};

const maxSwitchesToDisplay = 8;

type LinkingDirection = 'linking' | 'unlinking';

export const OperationalPointSwitchesInfobox: React.FC<OperationalPointSwitchesInfoboxProps> = ({
    contentVisible,
    onVisibilityChange,
    layoutContext,
    operationalPoint,
    changeTimes,
}) => {
    const { t } = useTranslation();

    const { isInitializing, linkedSwitches, unlinkedSwitches, linkSwitches, unlinkSwitches } =
        useLinkingSwitches(layoutContext, operationalPoint, changeTimes);

    return (
        <Infobox
            title={t('tool-panel.operational-point.switch-links.infobox-header')}
            contentVisible={contentVisible}
            onContentVisibilityChange={() => onVisibilityChange('switches')}>
            {isInitializing ? (
                <Spinner />
            ) : (
                <>
                    <OperationalPointSwitchesDirectionInfobox
                        layoutContext={layoutContext}
                        operationalPoint={operationalPoint}
                        changeTimes={changeTimes}
                        switches={linkedSwitches}
                        linkingAction={unlinkSwitches}
                        linkingDirection={'unlinking'}
                    />
                    <OperationalPointSwitchesDirectionInfobox
                        layoutContext={layoutContext}
                        operationalPoint={operationalPoint}
                        changeTimes={changeTimes}
                        switches={unlinkedSwitches}
                        linkingAction={linkSwitches}
                        linkingDirection={'linking'}
                    />
                </>
            )}
        </Infobox>
    );
};

function useLinkingSwitches(
    layoutContext: LayoutContext,
    operationalPoint: OperationalPoint,
    changeTimes: ChangeTimes,
): {
    isInitializing: boolean;
    linkedSwitches: { switchItem: LayoutSwitch; polygonInclusions: OperationalPointId[] }[];
    unlinkedSwitches: { switchItem: LayoutSwitch; polygonInclusions: OperationalPointId[] }[];
    linkSwitches: (switches: { id: LayoutSwitchId; name: string }[]) => void;
    unlinkSwitches: (switches: { id: LayoutSwitchId; name: string }[]) => void;
} {
    const { t } = useTranslation();

    const [isInitializing, setIsInitializing] = React.useState(true);
    const [linkingInFlight, addLinkingInFlight, deleteFromLinkingInFlight] =
        useSetState<LayoutSwitchId>();
    const [unlinkingInFlight, addUnlinkingInFlight, deleteFromUnlinkingInFlight] =
        useSetState<LayoutSwitchId>();

    const [preliminaryLinked, addPreliminaryLinked, deletePreliminaryLinked, setPreliminaryLinked] =
        useSetState<LayoutSwitchId>();
    const [
        preliminaryUnlinked,
        addPreliminaryUnlinked,
        deletePreliminaryUnlinked,
        setPreliminaryUnlinked,
    ] = useSetState<LayoutSwitchId>();

    const [operationalPointSwitches, setOperationalPointSwitches] = React.useState<
        SwitchWithOperationalPointPolygonInclusions[]
    >([]);

    const [switchItems, setSwitchItems] = React.useState<LayoutSwitch[]>([]);
    useRateLimitedTwoPartEffect(
        async () => {
            const loadedOperationalPointSwitches = await findOperationalPointSwitches(
                layoutContext,
                operationalPoint.id,
            );
            const loadedSwitchItems = await getSwitches(
                loadedOperationalPointSwitches.map(({ switchId }) => switchId),
                layoutContext,
            );
            return { loadedOperationalPointSwitches, loadedSwitchItems };
        },
        ({ loadedOperationalPointSwitches, loadedSwitchItems }) => {
            setIsInitializing(false);
            setPreliminaryLinked(new Set());
            setPreliminaryUnlinked(new Set());
            setOperationalPointSwitches(loadedOperationalPointSwitches);
            setSwitchItems(loadedSwitchItems);
        },
        1000,
        [
            layoutContext,
            operationalPoint.id,
            getMaxTimestamp(changeTimes.operationalPoints, changeTimes.layoutSwitch),
        ],
    );

    const [linkedSwitchItems, unlinkedSwitchItems] = partitionBy(switchItems, (s) => {
        if (unlinkingInFlight.has(s.id) || preliminaryUnlinked.has(s.id)) {
            return false;
        } else if (linkingInFlight.has(s.id) || preliminaryLinked.has(s.id)) {
            return true;
        } else {
            return s.operationalPointId === operationalPoint.id;
        }
    });

    const linkSwitches = async (switches: { id: LayoutSwitchId; name: string }[]) => {
        switches.forEach(({ id }) => {
            addLinkingInFlight(id);
            deletePreliminaryUnlinked(id);
        });
        try {
            await Promise.all(
                switches.map(({ id }) =>
                    assignOperationalPoint(layoutContext.branch, id, operationalPoint.id),
                ),
            );
            toastLinkingSuccess(t, switches, operationalPoint, 'linking');
            await updateSwitchChangeTime();
            switches.forEach(({ id }) => {
                addPreliminaryLinked(id);
                deleteFromLinkingInFlight(id);
            });
        } finally {
            await updateSwitchChangeTime();
            switches.forEach(({ id }) => {
                deleteFromLinkingInFlight(id);
            });
        }
    };
    const unlinkSwitches = async (switches: { id: LayoutSwitchId; name: string }[]) => {
        switches.forEach(({ id }) => {
            deletePreliminaryLinked(id);
            addUnlinkingInFlight(id);
        });
        try {
            await Promise.all(
                switches.map(({ id }) =>
                    assignOperationalPoint(layoutContext.branch, id, undefined),
                ),
            );
            toastLinkingSuccess(t, switches, operationalPoint, 'unlinking');
            await updateSwitchChangeTime();
            switches.forEach(({ id }) => {
                addPreliminaryUnlinked(id);
                deleteFromUnlinkingInFlight(id);
            });
        } finally {
            await updateSwitchChangeTime();
            switches.forEach(({ id }) => {
                deleteFromUnlinkingInFlight(id);
            });
        }
    };

    const polygonInclusionLookup: Map<LayoutSwitchId, OperationalPointId[]> =
        operationalPointSwitches.reduce(
            (map, obj) => map.set(obj.switchId, obj.withinPolygon),
            new Map(),
        );
    const linkedSwitches = addPolygonInclusions(linkedSwitchItems, polygonInclusionLookup);
    const unlinkedSwitches = addPolygonInclusions(unlinkedSwitchItems, polygonInclusionLookup);

    return { isInitializing, linkedSwitches, unlinkedSwitches, linkSwitches, unlinkSwitches };
}

function toastLinkingSuccess(
    t: ReturnType<typeof useTranslation>['t'],
    switches: { id: LayoutSwitchId; name: string }[],
    operationalPoint: OperationalPoint,
    linkingDirection: LinkingDirection,
) {
    const linkingPrefix = linkingDirection === 'linking' ? '' : 'un';
    if (switches.length === 1 && switches[0]) {
        Snackbar.success(
            t(
                `tool-panel.operational-point.switch-links.single-${linkingPrefix}linking-success-toast`,
                {
                    switchName: switches[0].name,
                    operationalPointName: operationalPoint.name,
                },
            ),
        );
    } else {
        Snackbar.success(
            t(
                `tool-panel.operational-point.switch-links.multiple-${linkingPrefix}linking-success-toast`,
                {
                    count: switches.length,
                    operationalPointName: operationalPoint.name,
                },
            ),
        );
    }
}

function addPolygonInclusions(
    switches: LayoutSwitch[],
    polygonInclusionLookup: Map<LayoutSwitchId, OperationalPointId[]>,
): { switchItem: LayoutSwitch; polygonInclusions: OperationalPointId[] }[] {
    return switches
        .map((switchItem) => {
            const polygonInclusions = polygonInclusionLookup.get(switchItem.id);
            return polygonInclusions === undefined ? undefined : { switchItem, polygonInclusions };
        })
        .filter(filterNotEmpty);
}

type OperationalPointSwitchesDirectionInfoboxProps = {
    layoutContext: LayoutContext;
    operationalPoint: OperationalPoint;
    changeTimes: ChangeTimes;
    switches: {
        switchItem: LayoutSwitch;
        polygonInclusions: OperationalPointId[];
    }[];
    linkingAction: (
        switches: {
            id: LayoutSwitchId;
            name: string;
        }[],
    ) => void;
    linkingDirection: LinkingDirection;
};

const OperationalPointSwitchesDirectionInfobox: React.FC<
    OperationalPointSwitchesDirectionInfoboxProps
> = ({ layoutContext, operationalPoint, switches, linkingAction, linkingDirection }) => {
    const { t } = useTranslation();
    const [showAll, setShowAll] = React.useState(false);

    const validatedSwitches = switches
        .map(({ switchItem, polygonInclusions }) => ({
            switchItem,
            issues: validateOperationalPointSwitchRow(
                operationalPoint.id,
                switchItem,
                polygonInclusions,
                linkingDirection,
            ),
        }))
        .sort((a, b) => {
            const issuesComp = (b.issues.length > 0 ? 1 : 0) - (a.issues.length > 0 ? 1 : 0);
            return issuesComp === 0
                ? compareIgnoreCase(a.switchItem.name, b.switchItem.name)
                : issuesComp;
        });

    return (
        <>
            <div>
                {t(
                    `tool-panel.operational-point.switch-links.${linkingDirection === 'linking' ? 'detached-in-polygon' : 'attached'}-header`,
                    { count: switches.length },
                )}
            </div>
            {validatedSwitches
                .slice(0, showAll ? undefined : maxSwitchesToDisplay)
                .map(({ switchItem, issues }) => (
                    <OperationalPointSwitchRow
                        key={switchItem.id}
                        layoutContext={layoutContext}
                        switchItem={switchItem}
                        issues={issues}
                        linkingAction={linkingAction}
                        linkingDirection={linkingDirection}
                    />
                ))}
            {switches.length > maxSwitchesToDisplay && (
                <ShowMoreButton
                    expanded={showAll}
                    onShowMore={() => setShowAll((v) => !v)}
                    showMoreText={t('tool-panel.operational-point.switch-links.show-all', {
                        count: switches.length,
                    })}
                />
            )}
            {switches.length > 0 && (
                <Button
                    variant={ButtonVariant.GHOST}
                    size={ButtonSize.SMALL}
                    onClick={() => linkingAction(switches.map(({ switchItem }) => switchItem))}>
                    {t(
                        `tool-panel.operational-point.switch-links.${linkingDirection === 'linking' ? 'attach' : 'detach'}-all`,
                        { count: switches.length },
                    )}
                </Button>
            )}
        </>
    );
};

type OperationalPointSwitchRowProps = {
    layoutContext: LayoutContext;
    switchItem: LayoutSwitch;
    issues: SwitchRowValidationIssue[];
    linkingAction: (switches: { id: LayoutSwitchId; name: string }[]) => void;
    linkingDirection: LinkingDirection;
};

const OperationalPointSwitchRow: React.FC<OperationalPointSwitchRowProps> = ({
    layoutContext,
    switchItem,
    issues,
    linkingAction,
    linkingDirection,
}) => {
    return (
        <div>
            <SwitchBadge switchItem={switchItem} />{' '}
            <Button
                variant={ButtonVariant.GHOST}
                size={ButtonSize.SMALL}
                icon={linkingDirection === 'linking' ? Icons.Add : Icons.Subtract}
                onClick={() => linkingAction([switchItem])}
            />
            {issues.slice(0, 1).map((issue, i) => (
                <SwitchRowValidationIssueBadge
                    key={i}
                    issue={issue}
                    layoutContext={layoutContext}
                />
            ))}
        </div>
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
        <>
            <Icons.StatusError />
            {t(`tool-panel.operational-point.switch-links.${issue.type}`, {
                operationalPointName: operationalPoint?.name,
            })}
        </>
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
