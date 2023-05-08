import React from 'react';
import TabNavItem from 'infra-model/tabs/tab-nav-item';
import TabContent from 'infra-model/tabs/tab-content';
import { useCommonDataAppSelector } from 'store/hooks';
import { InfraModelListContainer } from 'infra-model/list/infra-model-list-container';
import { useTranslation } from 'react-i18next';
import { commonActionCreators, InfraModelTabType } from 'common/common-slice';
import { createDelegates } from 'store/store-utils';
import { VelhoFileListContainer } from 'infra-model/velho/velho-file-list';

export type TabsProps = {
    activeTab: InfraModelTabType;
};

const Tabs: React.FC<TabsProps> = ({ activeTab }) => {
    const { t } = useTranslation();
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);
    const delegates = createDelegates(commonActionCreators);

    //erottele omiin komponentteihin 1) tab-navigointi 2) tabeja vastaavat sisällöt?
    return (
        <div className="Tabs">
            <ul className="nav">
                <TabNavItem
                    title={t('im-form.tabs.plans')}
                    id={InfraModelTabType.PLAN}
                    activeTab={activeTab}
                    setActiveTab={delegates.setInfraModelActiveTab}
                />
                <TabNavItem
                    title={t('im-form.tabs.velho-files-waiting')}
                    id={InfraModelTabType.WAITING}
                    activeTab={activeTab}
                    setActiveTab={delegates.setInfraModelActiveTab}
                />
                <TabNavItem
                    title={t('im-form.tabs.velho-files-rejected')}
                    id={InfraModelTabType.REJECTED}
                    activeTab={activeTab}
                    setActiveTab={delegates.setInfraModelActiveTab}
                />
            </ul>

            <div className="outlet">
                <TabContent id={InfraModelTabType.PLAN} activeTab={activeTab}>
                    <InfraModelListContainer changeTimes={changeTimes} />
                </TabContent>
                <TabContent id={InfraModelTabType.WAITING} activeTab={activeTab}>
                    <VelhoFileListContainer />
                </TabContent>
                <TabContent id={InfraModelTabType.REJECTED} activeTab={activeTab}>
                    <p>Hello from rejected tab!</p>
                </TabContent>
            </div>
        </div>
    );
};

export default Tabs;
