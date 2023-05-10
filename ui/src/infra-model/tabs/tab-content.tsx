import React from 'react';
import { InfraModelTabType } from 'infra-model/infra-model-slice';

export type TabContentProps = {
    tabId: InfraModelTabType;
    activeTab: InfraModelTabType;
    children: React.ReactNode;
};

const TabContent: React.FC<TabContentProps> = ({ tabId, activeTab, children }) => {
    return activeTab === tabId ? <div className="TabContent">{children}</div> : null;
};

export default TabContent;
