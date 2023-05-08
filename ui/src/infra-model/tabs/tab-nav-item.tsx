import React from 'react';
import { InfraModelTabType } from 'common/common-slice';

export type TabNavItemProps = {
    id: InfraModelTabType;
    title: string;
    activeTab: InfraModelTabType;
    setActiveTab: (id: InfraModelTabType) => void;
};

const TabNavItem: React.FC<TabNavItemProps> = ({ id, title, activeTab, setActiveTab }) => {
    const handleClick = () => {
        setActiveTab(id);
    };
    return (
        <li onClick={handleClick} className={activeTab === id ? 'active' : ''}>
            {title}
        </li>
    );
};
export default TabNavItem;
