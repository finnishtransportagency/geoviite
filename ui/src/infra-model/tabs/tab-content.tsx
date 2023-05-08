import React from 'react';
import { InfraModelTabType } from 'common/common-slice';

export type TabContentProps = {
    id: InfraModelTabType;
    activeTab: InfraModelTabType;
    children: React.ReactNode;
};

const TabContent: React.FC<TabContentProps> = ({ id, activeTab, children }) => {
    return activeTab === id ? <div className="TabContent">{children}</div> : null;
};

export default TabContent;
