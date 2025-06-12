import * as React from 'react';
import { LayoutSwitchId } from 'track-layout/track-layout-model';
import styles from 'tool-panel/switch/switch-infobox.scss';
import { SwitchBadge, SwitchBadgeStatus } from 'geoviite-design-lib/switch/switch-badge';
import { LoaderStatus, useImmediateLoader, useLoaderWithStatus } from 'utils/react-utils';
import { getSwitchesByBoundingBox } from 'track-layout/layout-switch-api';
import { LinkingGeometrySwitch, SuggestedSwitch } from 'linking/linking-model';
import { draftLayoutContext, LayoutContext, TimeStamp } from 'common/common-model';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { useTranslation } from 'react-i18next';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { getSuggestedSwitchForGeometrySwitch } from 'linking/linking-api';
import { GeometrySwitchId } from 'geometry/geometry-model';
import { GeometrySwitchSuggestionLoadResult } from 'tool-panel/switch/geometry-switch-linking-infobox';

type GeometrySwitchLinkingCandidatesProps = {
    layoutContext: LayoutContext;
    linkingState: LinkingGeometrySwitch;
    suggestedSwitch?: SuggestedSwitch;
    geometrySwitchId: GeometrySwitchId;
    onLoadSuggestedSwitchResult: (result: GeometrySwitchSuggestionLoadResult) => void;
    switchChangeTime: TimeStamp;
    onShowAddSwitchDialog: () => void;
};

export const GeometrySwitchLinkingCandidates: React.FC<GeometrySwitchLinkingCandidatesProps> = ({
    layoutContext,
    linkingState,
    suggestedSwitch,
    geometrySwitchId,
    onLoadSuggestedSwitchResult,
    switchChangeTime,
    onShowAddSwitchDialog,
}) => {
    const { t } = useTranslation();

    const selectedSwitchId = linkingState.layoutSwitchId;
    const suggestedSwitchLoader = useImmediateLoader<GeometrySwitchSuggestionLoadResult>(
        onLoadSuggestedSwitchResult,
    );

    const [switches, loadingStatus] = useLoaderWithStatus(() => {
        const point = suggestedSwitch?.joints.find((joint) => joint.location)?.location;
        if (point) {
            // This is a simple way to select nearby layout switches,
            // can be fine-tuned later
            const bboxSize = 100;
            const bbox = {
                x: { min: point.x - bboxSize, max: point.x + bboxSize },
                y: { min: point.y - bboxSize, max: point.y + bboxSize },
            };

            return getSwitchesByBoundingBox(bbox, draftLayoutContext(layoutContext), point, true);
        } else {
            return undefined;
        }
    }, [suggestedSwitch, switchChangeTime]);

    function onClickSwitchCandidate(layoutSwitchId: LayoutSwitchId): void {
        suggestedSwitchLoader.load(
            getSuggestedSwitchForGeometrySwitch(
                layoutContext.branch,
                geometrySwitchId,
                layoutSwitchId,
            ).then((suggestionResult) => ({
                suggestionResult,
                layoutSwitchId,
            })),
        );
    }

    return (
        <React.Fragment>
            <div className={styles['geometry-switch-infobox__search-container']}>
                <Button
                    variant={ButtonVariant.GHOST}
                    size={ButtonSize.SMALL}
                    icon={Icons.Append}
                    onClick={onShowAddSwitchDialog}
                />
            </div>
            <ul className={styles['geometry-switch-infobox__switches-container']}>
                {switches?.map((s) => {
                    return (
                        <li
                            key={s.id}
                            className={styles['geometry-switch-infobox__switch']}
                            onClick={() => onClickSwitchCandidate(s.id)}>
                            <SwitchBadge
                                switchItem={s}
                                status={
                                    selectedSwitchId === s.id
                                        ? SwitchBadgeStatus.SELECTED
                                        : SwitchBadgeStatus.DEFAULT
                                }
                            />
                        </li>
                    );
                })}

                {loadingStatus === LoaderStatus.Loading && <Spinner />}
                {loadingStatus === LoaderStatus.Ready && switches?.length === 0 && (
                    <span className={styles['geometry-switch-infobox__no-matches']}>
                        {t('tool-panel.switch.geometry.no-linkable-switches')}
                    </span>
                )}
            </ul>
        </React.Fragment>
    );
};
