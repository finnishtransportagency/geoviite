import * as React from 'react';
import { LayoutSwitch, LayoutSwitchId } from 'track-layout/track-layout-model';
import styles from 'tool-panel/switch/switch-infobox.scss';
import { SwitchBadge, SwitchBadgeStatus } from 'geoviite-design-lib/switch/switch-badge';
import { useLoader } from 'utils/react-utils';
import { getSwitchesByBoundingBox } from 'track-layout/layout-switch-api';
import { SuggestedSwitch } from 'linking/linking-model';
import { TimeStamp } from 'common/common-model';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';

type GeometrySwitchLinkingCandidateListingProps = {
    suggestedSwitch: SuggestedSwitch | null | undefined;
    selectedSwitchId: LayoutSwitchId | undefined;
    onSelectSwitch: (selectedSwitch: LayoutSwitch) => void;
    switchChangeTime: TimeStamp;
    onShowAddSwitchDialog: () => void;
};

export const GeometrySwitchLinkingCandidates: React.FC<
    GeometrySwitchLinkingCandidateListingProps
> = ({
    suggestedSwitch,
    selectedSwitchId,
    onSelectSwitch,
    switchChangeTime,
    onShowAddSwitchDialog,
}) => {
    const switches = useLoader(() => {
        const point = suggestedSwitch?.joints.find((joint) => joint.location)?.location;
        if (point) {
            // This is a simple way to select nearby layout switches,
            // can be fine tuned later
            const bboxSize = 100;
            const bbox = {
                x: { min: point.x - bboxSize, max: point.x + bboxSize },
                y: { min: point.y - bboxSize, max: point.y + bboxSize },
            };

            return getSwitchesByBoundingBox(bbox, 'DRAFT', point, true);
        } else {
            return undefined;
        }
    }, [suggestedSwitch, switchChangeTime]);

    return (
        <React.Fragment>
            <div className={styles['geometry-switch-infobox__search-container']}>
                <Button
                    variant={ButtonVariant.GHOST}
                    size={ButtonSize.SMALL}
                    icon={Icons.Append}
                    onClick={onShowAddSwitchDialog}
                    qa-id=""
                />
            </div>
            <ul className={styles['geometry-switch-infobox__switches-container']}>
                {switches?.map((s) => {
                    return (
                        <li
                            key={s.id}
                            className={styles['geometry-switch-infobox__switch']}
                            onClick={() => onSelectSwitch(s)}>
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
            </ul>
        </React.Fragment>
    );
};
