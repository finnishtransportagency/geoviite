import React from 'react';

export type TabContentProps = {
    id: string;
    activeTab: string;
    children: React.ReactNode;
};

const TabContent: React.FC<TabContentProps> = ({ id, activeTab, children }) => {
    return activeTab === id ? <div className="TabContent">{children}</div> : null;
};

export default TabContent;
