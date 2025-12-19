import React from 'react';
import InfraModelTabNavItem from 'infra-model/tabs/infra-model-tab-nav-item';
import InfraModelTabContent from 'infra-model/tabs/infra-model-tab-content';
import { useCommonDataAppSelector, useInfraModelAppSelector } from 'store/hooks';
import { InfraModelListContainer } from 'infra-model/list/infra-model-list-container';
import { useTranslation } from 'react-i18next';
import { infraModelActionCreators, InfraModelTabType } from 'infra-model/infra-model-slice';
import { PVFileListContainer } from 'infra-model/projektivelho/pv-file-list';
import styles from 'infra-model/tabs/infra-model-tabs.scss';
import { useLoader } from 'utils/react-utils';
import { getPVDocumentCount } from 'infra-model/infra-model-api';
import { getGeometryPlanHeadersBySearchTerms } from 'geometry/geometry-api';
import { createDelegates } from 'store/store-utils';
import { PrivilegeRequired } from 'user/privilege-required';
import { DOWNLOAD_GEOMETRY } from 'user/user-model';

export type TabsProps = {
    activeTab: InfraModelTabType;
};

const InfraModelTabs: React.FC<TabsProps> = ({ activeTab }) => {
    const { t } = useTranslation();
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);
    const changeTime = useCommonDataAppSelector((state) => state.changeTimes.pvDocument);
    const infraModelListDelegates = React.useMemo(
        () => createDelegates(infraModelActionCreators),
        [],
    );
    const state = useInfraModelAppSelector((state) => state.infraModelList);
    const numberOfInfraModelFiles = useInfraModelAppSelector(
        (state) => state.infraModelList.totalCount,
    );
    const documentCounts = useLoader(() => getPVDocumentCount(), [changeTime]);

    // Handle search plans side effect
    React.useEffect(() => {
        if (state.searchState === 'start') {
            infraModelListDelegates.onPlanFetchStart();
            getGeometryPlanHeadersBySearchTerms(
                state.pageSize,
                state.page * state.pageSize,
                undefined,
                state.searchParams.sources,
                state.searchParams.trackNumbers,
                state.searchParams.freeText,
                state.searchParams.sortBy,
                state.searchParams.sortOrder,
            )
                .then((result) => {
                    infraModelListDelegates.onPlansFetchReady({
                        plans: result.planHeaders.items,
                        searchParams: state.searchParams,
                        totalCount: result.planHeaders.totalCount,
                    });
                })
                .catch((e) => infraModelListDelegates.onPlanFetchError(e?.toString()));
        }
    }, [state.searchState]);

    React.useEffect(() => {
        infraModelListDelegates.onPlanChangeTimeChange();
    }, [changeTimes.geometryPlan]);

    return (
        <div className={styles['tabs__tab-container']}>
            <ul className={styles['tabs__nav']} qa-id="im-form.tabs-bar">
                <InfraModelTabNavItem
                    title={t('im-form.tabs.plans', { number: numberOfInfraModelFiles })}
                    tabId={InfraModelTabType.PLAN}
                    activeTab={activeTab}
                    exclamationPointVisible={false}
                />
                <PrivilegeRequired privilege={DOWNLOAD_GEOMETRY}>
                    <InfraModelTabNavItem
                        title={t('im-form.tabs.projektivelho-files-waiting', {
                            number: documentCounts?.suggested,
                        })}
                        tabId={InfraModelTabType.WAITING}
                        activeTab={activeTab}
                        exclamationPointVisible={!!documentCounts && documentCounts?.suggested > 0}
                    />
                    <InfraModelTabNavItem
                        title={t('im-form.tabs.projektivelho-files-rejected', {
                            number: documentCounts?.rejected,
                        })}
                        tabId={InfraModelTabType.REJECTED}
                        activeTab={activeTab}
                        exclamationPointVisible={false}
                    />
                </PrivilegeRequired>
            </ul>
            <div className={styles['tabs__outlet']}>
                <InfraModelTabContent tabId={InfraModelTabType.PLAN} activeTab={activeTab}>
                    <InfraModelListContainer
                        changeTimes={changeTimes}
                        clearInfraModelState={infraModelListDelegates.clearInfraModelState}
                        onSearchParamsChange={infraModelListDelegates.onSearchParamsChange}
                        onPrevPage={infraModelListDelegates.onPrevPage}
                        onNextPage={infraModelListDelegates.onNextPage}
                    />
                </InfraModelTabContent>
                <InfraModelTabContent tabId={InfraModelTabType.WAITING} activeTab={activeTab}>
                    <PVFileListContainer
                        listMode={'SUGGESTED'}
                        changeTime={changeTime}
                        sortPersistorFn={infraModelListDelegates.onSortPVSuggestedList}
                        sorting={state.pvListState.suggested.sortedBy}
                    />
                </InfraModelTabContent>
                <InfraModelTabContent tabId={InfraModelTabType.REJECTED} activeTab={activeTab}>
                    <PVFileListContainer
                        listMode={'REJECTED'}
                        changeTime={changeTime}
                        sortPersistorFn={infraModelListDelegates.onSortPVRejectedList}
                        sorting={state.pvListState.rejected.sortedBy}
                    />
                </InfraModelTabContent>
            </div>
        </div>
    );
};

export default InfraModelTabs;
