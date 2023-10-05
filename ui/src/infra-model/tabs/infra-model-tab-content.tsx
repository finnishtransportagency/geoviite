import React from 'react';
import { InfraModelTabType } from 'infra-model/infra-model-slice';

export type TabContentProps = {
    tabId: InfraModelTabType;
    activeTab: InfraModelTabType;
    children: React.ReactNode;
};

const InfraModelTabContent: React.FC<TabContentProps> = ({ tabId, activeTab, children }) => {
    return activeTab === tabId ? <div>{children}</div> : <React.Fragment />;
};

export default InfraModelTabContent;
