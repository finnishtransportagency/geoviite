import React from 'react';
import { useAppNavigate } from 'common/navigate';
import { InfraModelTabType } from 'infra-model/infra-model-slice';
import { ExclamationPoint } from 'geoviite-design-lib/exclamation-point/exclamation-point';
import styles from './infra-model-tabs.scss';

export type TabNavItemProps = {
    tabId: InfraModelTabType;
    title: string;
    activeTab: InfraModelTabType;
    setActiveTab: (id: InfraModelTabType) => void;
};

const InfraModelTabNavItem: React.FC<TabNavItemProps> = ({
    tabId,
    title,
    activeTab,
    setActiveTab,
}) => {
    const navigate = useAppNavigate();

    const handleClick = () => {
        setActiveTab(tabId);
        if (tabId === InfraModelTabType.PLAN) {
            return navigate('inframodel-plans');
        } else if (tabId === InfraModelTabType.WAITING) {
            navigate('inframodel-waiting');
        } else if (tabId === InfraModelTabType.REJECTED) {
            navigate('inframodel-rejected');
        }
    };
    //TODO huutomerkki näytetään sen mukaan onko Velho aineistoja odottamassa
    return (
        <li
            onClick={handleClick}
            className={activeTab === tabId ? styles['active'] : styles['inactive']}>
            {title}
            {tabId === InfraModelTabType.WAITING && (
                <span className={styles['exclamation-point-container']}>
                    <ExclamationPoint />
                </span>
            )}
        </li>
    );
};
export default InfraModelTabNavItem;
