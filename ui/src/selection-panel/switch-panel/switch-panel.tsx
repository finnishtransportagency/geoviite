import * as React from 'react';
import styles from './switch-panel.scss';
import { LayoutSwitch, LayoutSwitchId } from 'track-layout/track-layout-model';
import { SwitchBadge, SwitchBadgeStatus } from 'geoviite-design-lib/switch/switch-badge';
import { compareByFields } from 'utils/array-utils';
import { useTranslation } from 'react-i18next';

type SwitchPanelProps = {
    switchCount: number;
    switches: LayoutSwitch[];
    onToggleSwitchSelection: (layoutSwitchId: LayoutSwitchId) => void;
    selectedSwitches?: LayoutSwitchId[];
};

const SwitchPanel: React.FC<SwitchPanelProps> = ({
    switches,
    switchCount,
    onToggleSwitchSelection,
    selectedSwitches,
}: SwitchPanelProps) => {
    const { t } = useTranslation();
    const switchComparator = (s1: LayoutSwitch, s2: LayoutSwitch) =>
        compareByFields(
            s1,
            s2,
            (s) => s.name,
            (s) => s.id,
        );
    const sortedSwitches = [...switches].sort(switchComparator);
    const shouldShowZoomMessage = switchCount > 0 && switches.length === 0;
    const shouldShowNoResults = switchCount === 0;

    return (
        <div>
            <ol className={styles['switch-panel__switches']}>
                {sortedSwitches.map((switchItem) => {
                    const isSelected = selectedSwitches?.some((p) => p === switchItem.id);
                    return (
                        <li key={switchItem.id}>
                            <SwitchBadge
                                switchItem={switchItem}
                                onClick={() => onToggleSwitchSelection(switchItem.id)}
                                status={isSelected ? SwitchBadgeStatus.SELECTED : undefined}
                            />
                        </li>
                    );
                })}
            </ol>
            {shouldShowZoomMessage && (
                <span className={styles['switch-panel__subtitle']}>{`${t(
                    'selection-panel.zoom-closer',
                )}`}</span>
            )}

            {shouldShowNoResults && (
                <span className={styles['switch-panel__subtitle']}>{`${t(
                    'selection-panel.no-results',
                )}`}</span>
            )}
        </div>
    );
};

export default React.memo(SwitchPanel);
