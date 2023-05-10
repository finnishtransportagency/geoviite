import React from 'react';
import { useAppNavigate } from 'common/navigate';
import { InfraModelTabType } from 'infra-model/infra-model-slice';

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

    return (
        <li onClick={handleClick} className={activeTab === tabId ? 'active' : ''}>
            {title}
        </li>
    );
};
export default InfraModelTabNavItem;
