import * as React from 'react';
import styles from './switch-panel.scss';
import { LayoutSwitch, LayoutSwitchId } from 'track-layout/track-layout-model';
import { SwitchBadge, SwitchBadgeStatus } from 'geoviite-design-lib/switch/switch-badge';
import { compareByFields } from 'utils/array-utils';
import { useTranslation } from 'react-i18next';

type SwitchPanelProps = {
    switches: LayoutSwitch[];
    onToggleSwitchSelection: (layoutSwitchId: LayoutSwitchId) => void;
    selectedSwitches?: LayoutSwitchId[];
    max?: number;
};

const SwitchPanel: React.FC<SwitchPanelProps> = ({
    switches,
    onToggleSwitchSelection,
    selectedSwitches,
    max = 32,
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

    return (
        <div>
            <ol className={styles['switch-panel__switches']}>
                {switches.length <= max &&
                    sortedSwitches.map((switchItem) => {
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
            {switches.length > max && (
                <span className={styles['switch-panel__subtitle']}>{`${t(
                    'selection-panel.zoom-closer',
                )}`}</span>
            )}

            {switches.length === 0 && (
                <span className={styles['switch-panel__subtitle']}>{`${t(
                    'selection-panel.no-results',
                )}`}</span>
            )}
        </div>
    );
};

export default React.memo(SwitchPanel);
