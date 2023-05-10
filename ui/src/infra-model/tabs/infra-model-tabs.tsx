import React from 'react';
import InfraModelTabNavItem from 'infra-model/tabs/infra-model-tab-nav-item';
import InfraModelTabContent from 'infra-model/tabs/infra-model-tab-content';
import { useCommonDataAppSelector } from 'store/hooks';
import { InfraModelListContainer } from 'infra-model/list/infra-model-list-container';
import { useTranslation } from 'react-i18next';
import { createDelegates } from 'store/store-utils';
import { infraModelActionCreators, InfraModelTabType } from 'infra-model/infra-model-slice';

export type TabsProps = {
    activeTab: InfraModelTabType;
};

const InfraModelTabs: React.FC<TabsProps> = ({ activeTab: activeTab }) => {
    const { t } = useTranslation();
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);
    const delegates = createDelegates(infraModelActionCreators);

    return (
        <div className="Tabs">
            <ul className="nav">
                <InfraModelTabNavItem
                    title={t('im-form.tabs.plans')}
                    tabId={InfraModelTabType.PLAN}
                    activeTab={activeTab}
                    setActiveTab={delegates.setInfraModelActiveTab}
                />
                <InfraModelTabNavItem
                    title={t('im-form.tabs.velho-files-waiting')}
                    tabId={InfraModelTabType.WAITING}
                    activeTab={activeTab}
                    setActiveTab={delegates.setInfraModelActiveTab}
                />
                <InfraModelTabNavItem
                    title={t('im-form.tabs.velho-files-rejected')}
                    tabId={InfraModelTabType.REJECTED}
                    activeTab={activeTab}
                    setActiveTab={delegates.setInfraModelActiveTab}
                />
            </ul>

            <div className="outlet">
                <InfraModelTabContent tabId={InfraModelTabType.PLAN} activeTab={activeTab}>
                    <InfraModelListContainer changeTimes={changeTimes} />
                </InfraModelTabContent>
                <InfraModelTabContent tabId={InfraModelTabType.WAITING} activeTab={activeTab}>
                    <p>VELHO-taulukko tähän</p>
                </InfraModelTabContent>
                <InfraModelTabContent tabId={InfraModelTabType.REJECTED} activeTab={activeTab}>
                    <p>Hello from rejected tab!</p>
                </InfraModelTabContent>
            </div>
        </div>
    );
};

export default InfraModelTabs;
