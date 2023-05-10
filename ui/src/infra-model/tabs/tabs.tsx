import React from 'react';
import TabNavItem from 'infra-model/tabs/tab-nav-item';
import TabContent from 'infra-model/tabs/tab-content';
import { useCommonDataAppSelector } from 'store/hooks';
import { InfraModelListContainer } from 'infra-model/list/infra-model-list-container';
import { useTranslation } from 'react-i18next';
import { createDelegates } from 'store/store-utils';
import { infraModelActionCreators, InfraModelTabType } from 'infra-model/infra-model-slice';

export type TabsProps = {
    activeTab: InfraModelTabType;
};

const Tabs: React.FC<TabsProps> = ({ activeTab: activeTab }) => {
    const { t } = useTranslation();
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);
    const delegates = createDelegates(infraModelActionCreators);

    return (
        <div className="Tabs">
            <ul className="nav">
                <TabNavItem
                    title={t('im-form.tabs.plans')}
                    tabId={InfraModelTabType.PLAN}
                    activeTab={activeTab}
                    setActiveTab={delegates.setInfraModelActiveTab}
                />
                <TabNavItem
                    title={t('im-form.tabs.velho-files-waiting')}
                    tabId={InfraModelTabType.WAITING}
                    activeTab={activeTab}
                    setActiveTab={delegates.setInfraModelActiveTab}
                />
                <TabNavItem
                    title={t('im-form.tabs.velho-files-rejected')}
                    tabId={InfraModelTabType.REJECTED}
                    activeTab={activeTab}
                    setActiveTab={delegates.setInfraModelActiveTab}
                />
            </ul>

            <div className="outlet">
                <TabContent tabId={InfraModelTabType.PLAN} activeTab={activeTab}>
                    <InfraModelListContainer changeTimes={changeTimes} />
                </TabContent>
                <TabContent tabId={InfraModelTabType.WAITING} activeTab={activeTab}>
                    <p>VELHO-taulukko tähän</p>
                </TabContent>
                <TabContent tabId={InfraModelTabType.REJECTED} activeTab={activeTab}>
                    <p>Hello from rejected tab!</p>
                </TabContent>
            </div>
        </div>
    );
};

export default Tabs;
