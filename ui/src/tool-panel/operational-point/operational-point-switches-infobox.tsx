import React from 'react';
import {
    LayoutSwitch,
    LayoutSwitchId,
    OperationalPoint,
    OperationalPointId,
} from 'track-layout/track-layout-model';
import { LayoutContext } from 'common/common-model';
import {
    findOperationalPointSwitches,
    getSwitches,
    linkSwitchToOperationalPoint,
    SwitchWithOperationalPointPolygonInclusions,
    unlinkSwitchFromOperationalPoint,
} from 'track-layout/layout-switch-api';
import Infobox from 'tool-panel/infobox/infobox';
import { SwitchBadge } from 'geoviite-design-lib/switch/switch-badge';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';
import { useTranslation } from 'react-i18next';
import { ChangeTimes } from 'common/common-slice';
import { compareIgnoreCase } from 'utils/string-utils';
import { ShowMoreButton } from 'show-more-button/show-more-button';
import { useOperationalPoint } from 'track-layout/track-layout-react-utils';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import {
    createUseLinkingHook,
    getSuccessToastMessageParams,
    LinkingDirection,
} from 'tool-panel/operational-point/operational-point-utils';
import { updateSwitchChangeTime } from 'common/change-time-api';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import styles from './operational-point-infobox.scss';

type OperationalPointSwitchesInfoboxProps = {
    contentVisible: boolean;
    onVisibilityChange: (key: 'switches') => void;
    layoutContext: LayoutContext;
    operationalPoint: OperationalPoint;
    changeTimes: ChangeTimes;
};

const maxSwitchesToDisplay = 8;

export const OperationalPointSwitchesInfobox: React.FC<OperationalPointSwitchesInfoboxProps> = ({
    contentVisible,
    onVisibilityChange,
    layoutContext,
    operationalPoint,
    changeTimes,
}) => {
    const { t } = useTranslation();

    const { isInitializing, itemAssociation, linkedItems, unlinkedItems, linkItems, unlinkItems } =
        useLinkingSwitches(layoutContext, operationalPoint, changeTimes);

    const polygonInclusion: Map<LayoutSwitchId, OperationalPointId[]> = (
        itemAssociation ?? []
    ).reduce((map, obj) => map.set(obj.switchId, obj.withinPolygon), new Map());

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
                        switches={linkedItems}
                        linkingAction={unlinkItems}
                        linkingDirection={'unlinking'}
                        polygonInclusion={polygonInclusion}
                    />
                    <OperationalPointSwitchesDirectionInfobox
                        layoutContext={layoutContext}
                        operationalPoint={operationalPoint}
                        changeTimes={changeTimes}
                        switches={unlinkedItems}
                        linkingAction={linkItems}
                        linkingDirection={'linking'}
                        polygonInclusion={polygonInclusion}
                    />
                </>
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
    linkSwitchToOperationalPoint,
    unlinkSwitchFromOperationalPoint,
);

type OperationalPointSwitchesDirectionInfoboxProps = {
    layoutContext: LayoutContext;
    operationalPoint: OperationalPoint;
    changeTimes: ChangeTimes;
    switches: LayoutSwitch[];
    polygonInclusion: Map<LayoutSwitchId, OperationalPointId[]>;
    linkingAction: (
        switches: {
            id: LayoutSwitchId;
            name: string;
        }[],
        idsToImmediatelyToss: LayoutSwitchId[],
    ) => Promise<string[]>;
    linkingDirection: LinkingDirection;
};

const OperationalPointSwitchesDirectionInfobox: React.FC<
    OperationalPointSwitchesDirectionInfoboxProps
> = ({
    layoutContext,
    operationalPoint,
    switches,
    linkingAction,
    linkingDirection,
    polygonInclusion,
}) => {
    const { t } = useTranslation();
    const [showAll, setShowAll] = React.useState(false);

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
    const idsToImmediatelyToss = validatedSwitches
        .filter(({ issues }) => issues.some((issue) => issue.type === 'switch-not-in-polygon'))
        .map(({ item }) => item.id);

    const linkAndToast: (
        switches: {
            id: LayoutSwitchId;
            name: string;
        }[],
    ) => void = async (switches) => {
        const linkedSwitches = await linkingAction(switches, idsToImmediatelyToss);
        const toastParams = getSuccessToastMessageParams(
            'switch',
            operationalPoint.name,
            linkedSwitches,
            linkingDirection,
        );
        if (toastParams) {
            Snackbar.success(t(toastParams[0], toastParams[1]));
        }
    };

    return (
        <InfoboxContent>
            <>
                <div className={styles['operational-point-linking-infobox__direction-title']}>
                    {t(
                        `tool-panel.operational-point.switch-links.${linkingDirection === 'linking' ? 'detached-in-polygon' : 'attached'}-header`,
                        { count: switches.length },
                    )}
                </div>
                {switches.length > 0 && (
                    <>
                        <div
                            className={
                                styles['operational-point-linking-infobox__direction-content']
                            }>
                            {validatedSwitches
                                .slice(0, showAll ? undefined : maxSwitchesToDisplay)
                                .map(({ item, issues }) => (
                                    <OperationalPointSwitchRow
                                        key={item.id}
                                        layoutContext={layoutContext}
                                        switchItem={item}
                                        issues={issues}
                                        linkingAction={linkAndToast}
                                        linkingDirection={linkingDirection}
                                    />
                                ))}
                        </div>
                        <div>
                            {switches.length > maxSwitchesToDisplay && (
                                <ShowMoreButton
                                    expanded={showAll}
                                    onShowMore={() => setShowAll((v) => !v)}
                                    showMoreText={t(
                                        'tool-panel.operational-point.switch-links.show-all',
                                        {
                                            count: switches.length,
                                        },
                                    )}
                                />
                            )}
                            <Button
                                variant={ButtonVariant.GHOST}
                                size={ButtonSize.SMALL}
                                onClick={() => linkAndToast(switches)}>
                                {t(
                                    `tool-panel.operational-point.switch-links.${linkingDirection === 'linking' ? 'attach' : 'detach'}-all`,
                                    { count: switches.length },
                                )}
                            </Button>
                        </div>
                    </>
                )}
            </>
        </InfoboxContent>
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
        <>
            <SwitchBadge switchItem={switchItem} />
            <Button
                variant={ButtonVariant.GHOST}
                size={ButtonSize.SMALL}
                icon={linkingDirection === 'linking' ? Icons.Add : Icons.Subtract}
                onClick={() => linkingAction([switchItem])}
            />
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
