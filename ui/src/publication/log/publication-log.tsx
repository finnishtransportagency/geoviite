import * as React from 'react';
import styles from './publication-log.scss';
import { useTranslation } from 'react-i18next';
import {
    DatePicker,
    DatePickerDateSource,
    END_OF_CENTURY,
    START_OF_2022,
} from 'vayla-design-lib/datepicker/datepicker';
import { daysBetween, parseISOOrUndefined } from 'utils/date-utils';
import { endOfDay, startOfDay } from 'date-fns';
import {
    getPublicationsAsTableItems,
    getPublicationsCsvUri,
    MAX_RETURNED_PUBLICATION_LOG_ROWS,
    PublishableObjectIdAndType,
} from 'publication/publication-api';
import PublicationTable from 'publication/table/publication-table';
import { Button } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import {
    SortablePublicationTableProps,
    SortedByTimeDesc,
} from 'publication/table/publication-table-utils';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';
import { PublicationTableItem } from 'publication/publication-model';
import { Page } from 'api/api-fetch';
import { PrivilegeRequired } from 'user/privilege-required';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';
import { useTrackLayoutAppSelector } from 'store/hooks';
import { useAppNavigate } from 'common/navigate';
import { defaultPublicationSearch } from 'publication/publication-utils';
import { DOWNLOAD_PUBLICATION } from 'user/user-model';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { debounceAsync } from 'utils/async-utils';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { SortDirection, TableSorting } from 'utils/table-utils';
import { AnchorLink } from 'geoviite-design-lib/link/anchor-link';
import { SearchDropdown, SearchItemType, SearchItemValue } from 'asset-search/search-dropdown';
import { LayoutContext, officialMainLayoutContext } from 'common/common-model';
import { DropdownSize } from 'vayla-design-lib/dropdown/dropdown';
import { LayoutTrackNumber } from 'track-layout/track-layout-model';
import { useTrackNumbersIncludingDeleted } from 'track-layout/track-layout-react-utils';
import { TFunction } from 'i18next';
import {
    kmPostSearchItemName,
    locationTrackSearchItemName,
} from 'asset-search/search-dropdown-item';

const MAX_SEARCH_DAYS = 180;

type TableFetchFn = (
    from?: Date,
    to?: Date,
    specificItem?: PublishableObjectIdAndType,
    sortBy?: keyof SortablePublicationTableProps,
    order?: SortDirection,
) => Promise<Page<PublicationTableItem>>;

let fetchId = 0;
const debouncedGetPublicationsAsTableItems = debounceAsync(getPublicationsAsTableItems, 500);

type DataSourceChangeMethod = DatePickerDateSource | 'SORTING_CHANGED';

const publicationTableFetchFunctionByChangeMethod = (
    changeMethod: DataSourceChangeMethod,
): TableFetchFn => {
    switch (changeMethod) {
        case 'TEXT':
            return debouncedGetPublicationsAsTableItems;
        case 'PICKER':
            return getPublicationsAsTableItems;
        case 'SORTING_CHANGED':
            return getPublicationsAsTableItems;
        default:
            return exhaustiveMatchingGuard(changeMethod);
    }
};

type PublicationLogTableHeadingProps = {
    isLoading: boolean;
    isTruncated: boolean;
    publicationAmount: number;
};

const PublicationLogTableHeading: React.FC<PublicationLogTableHeadingProps> = ({
    isLoading,
    isTruncated,
    publicationAmount,
}) => {
    const { t } = useTranslation();

    return (
        <React.Fragment>
            {!isLoading && (
                <span
                    title={
                        isTruncated
                            ? t('publication-table.truncated', {
                                  number: publicationAmount,
                              })
                            : ''
                    }>
                    {t('publication-table.count-header', {
                        number: publicationAmount,
                        truncated: isTruncated ? '+' : '',
                    })}
                </span>
            )}
            {isLoading && (
                <React.Fragment>
                    {t('publication-table.loading')}
                    <Spinner inline={true} />
                </React.Fragment>
            )}
        </React.Fragment>
    );
};

export type SearchablePublicationLogItem =
    | SearchItemType.LOCATION_TRACK
    | SearchItemType.TRACK_NUMBER
    | SearchItemType.SWITCH
    | SearchItemType.KM_POST;

function searchableItemIdAndType(
    item: SearchItemValue<SearchablePublicationLogItem>,
): PublishableObjectIdAndType {
    switch (item.type) {
        case SearchItemType.LOCATION_TRACK:
            return { type: item.type, id: item.locationTrack.id };
        case SearchItemType.TRACK_NUMBER:
            return { type: item.type, id: item.trackNumber.id };
        case SearchItemType.SWITCH:
            return { type: item.type, id: item.layoutSwitch.id };
        case SearchItemType.KM_POST:
            return { type: item.type, id: item.kmPost.id };
        default:
            return exhaustiveMatchingGuard(item);
    }
}

function getSearchableItemName(
    item: SearchItemValue<SearchablePublicationLogItem>,
    trackNumbers: LayoutTrackNumber[],
    t: TFunction<'translation', undefined>,
): string {
    switch (item.type) {
        case SearchItemType.LOCATION_TRACK:
            return locationTrackSearchItemName(item.locationTrack, t);
        case SearchItemType.TRACK_NUMBER:
            return item.trackNumber.number;
        case SearchItemType.SWITCH:
            return item.layoutSwitch.name;
        case SearchItemType.KM_POST:
            return kmPostSearchItemName(item.kmPost, trackNumbers, t);
        default:
            return exhaustiveMatchingGuard(item);
    }
}

type PublicationLogProps = {
    layoutContext: LayoutContext;
};

const PublicationLog: React.FC<PublicationLogProps> = ({ layoutContext }) => {
    const { t } = useTranslation();
    const navigate = useAppNavigate();
    const trackNumbers = useTrackNumbersIncludingDeleted(layoutContext) ?? [];

    const selectedPublicationSearch = useTrackLayoutAppSelector(
        (state) => state.selection.publicationSearch,
    );

    const trackLayoutActionDelegates = React.useMemo(
        () => createDelegates(trackLayoutActionCreators),
        [],
    );

    const storedStartDate = parseISOOrUndefined(selectedPublicationSearch?.startDate);
    const storedEndDate = parseISOOrUndefined(selectedPublicationSearch?.endDate);
    const storedSpecificItem = selectedPublicationSearch?.specificItem;

    const [sortInfo, setSortInfo] =
        React.useState<TableSorting<SortablePublicationTableProps>>(SortedByTimeDesc);
    const [isLoading, setIsLoading] = React.useState(false);
    const [pagedPublications, setPagedPublications] = React.useState<Page<PublicationTableItem>>();

    React.useEffect(() => {
        if (!selectedPublicationSearch) {
            trackLayoutActionDelegates.setSelectedPublicationSearch(defaultPublicationSearch);
        }

        updatePublicationsTable(
            storedStartDate ?? parseISOOrUndefined(defaultPublicationSearch.startDate),
            storedEndDate ?? parseISOOrUndefined(defaultPublicationSearch.endDate),
            storedSpecificItem,
            sortInfo,
            getPublicationsAsTableItems,
        );
    }, []);

    const updateTableSorting = (updatedSort: TableSorting<SortablePublicationTableProps>) => {
        if (pagedPublications?.items.length === MAX_RETURNED_PUBLICATION_LOG_ROWS) {
            updatePublicationsTable(
                storedStartDate,
                storedEndDate,
                storedSpecificItem,
                updatedSort,
                publicationTableFetchFunctionByChangeMethod('SORTING_CHANGED'),
            ).then(() => setSortInfo(updatedSort));
        } else {
            setSortInfo(updatedSort);
        }
    };

    const setSpecificItem = (
        newSpecificItem: SearchItemValue<SearchablePublicationLogItem> | undefined,
    ) => {
        trackLayoutActionDelegates.setSelectedPublicationSearchSearchableItem(newSpecificItem);
        updatePublicationsTable(
            storedStartDate,
            storedEndDate,
            newSpecificItem,
            sortInfo,
            publicationTableFetchFunctionByChangeMethod('PICKER'),
        );
    };

    const setStartDate = (newStartDate: Date | undefined, source: DataSourceChangeMethod) => {
        trackLayoutActionDelegates.setSelectedPublicationSearchStartDate(
            newStartDate?.toISOString(),
        );
        updatePublicationsTable(
            newStartDate,
            storedEndDate,
            storedSpecificItem,
            sortInfo,
            publicationTableFetchFunctionByChangeMethod(source),
        );
    };

    const setEndDate = (newEndDate: Date | undefined, source: DataSourceChangeMethod) => {
        trackLayoutActionDelegates.setSelectedPublicationSearchEndDate(newEndDate?.toISOString());
        updatePublicationsTable(
            storedStartDate,
            newEndDate,
            storedSpecificItem,
            sortInfo,
            publicationTableFetchFunctionByChangeMethod(source),
        );
    };

    const isValidPublicationLogSearchRange = (
        specificItem: SearchItemValue<SearchablePublicationLogItem> | undefined,
        start: Date | undefined,
        end: Date | undefined,
    ): boolean => {
        return (
            specificItem !== undefined || // go nuts with single-item searches, they're cheap
            (start !== undefined && end !== undefined && daysBetween(start, end) < MAX_SEARCH_DAYS)
        );
    };

    const isStoredSearchRangeValid = isValidPublicationLogSearchRange(
        storedSpecificItem,
        storedStartDate,
        storedEndDate,
    );

    const updatePublicationsTable = (
        startDate: Date | undefined,
        endDate: Date | undefined,
        specificItem: SearchItemValue<SearchablePublicationLogItem> | undefined,
        sortInfo: TableSorting<SortablePublicationTableProps>,
        fetchFn: TableFetchFn,
    ): Promise<Page<PublicationTableItem> | undefined> => {
        if (!isValidPublicationLogSearchRange(specificItem, startDate, endDate)) {
            clearPublicationsTable();
            return Promise.resolve(undefined);
        }

        setIsLoading(true);
        const currentFetchId = ++fetchId;

        return fetchFn(
            startDate && startOfDay(startDate),
            endDate && endOfDay(endDate),
            specificItem === undefined ? undefined : searchableItemIdAndType(specificItem),
            sortInfo.propName,
            sortInfo.direction,
        ).then((r) => {
            if (fetchId === currentFetchId) {
                r && setPagedPublications(r);
                setIsLoading(false);
            }

            return r;
        });
    };

    const clearPublicationsTable = () => {
        setPagedPublications({
            totalCount: 0,
            items: [],
            start: 0,
        });
    };

    const endDateErrors =
        storedStartDate && storedEndDate && storedStartDate > storedEndDate
            ? [t('publication-log.end-before-start')]
            : [];

    const isTruncated =
        pagedPublications !== undefined &&
        pagedPublications.totalCount !== pagedPublications.items.length;

    return (
        <div className={styles['publication-log']}>
            <div className={styles['publication-log__title']}>
                <AnchorLink
                    onClick={() => {
                        trackLayoutActionDelegates.setSelectedPublicationSearch(undefined);
                        navigate('frontpage');
                    }}>
                    {t('frontpage.frontpage-link')}
                </AnchorLink>
                <span className={styles['publication-log__breadcrumbs']}>
                    {' > ' + t('publication-log.breadcrumbs-text')}
                </span>
            </div>
            <div className={styles['publication-log__content']}>
                <div className={styles['publication-log__actions']}>
                    <FieldLayout
                        label={t('publication-log.start-date')}
                        value={
                            <DatePicker
                                value={storedStartDate}
                                onChange={(date, source) => setStartDate(date, source)}
                                minDate={START_OF_2022}
                                maxDate={END_OF_CENTURY}
                                qa-id={'publication-log-start-date-input'}
                            />
                        }
                    />
                    <FieldLayout
                        label={t('publication-log.end-date')}
                        value={
                            <DatePicker
                                value={storedEndDate}
                                onChange={(date, source) => setEndDate(date, source)}
                                minDate={START_OF_2022}
                                maxDate={END_OF_CENTURY}
                                qa-id={'publication-log-end-date-input'}
                            />
                        }
                        errors={endDateErrors}
                    />
                    <FieldLayout
                        label={t('publication-log.specific-object')}
                        value={
                            <SearchDropdown
                                layoutContext={officialMainLayoutContext()}
                                splittingState={undefined}
                                placeholder={t('publication-log.search-specific-object')}
                                onItemSelected={setSpecificItem}
                                value={storedSpecificItem}
                                getName={(name) => getSearchableItemName(name, trackNumbers, t)}
                                disabled={false}
                                size={DropdownSize.LARGE}
                                searchTypes={[
                                    SearchItemType.LOCATION_TRACK,
                                    SearchItemType.SWITCH,
                                    SearchItemType.TRACK_NUMBER,
                                    SearchItemType.KM_POST,
                                ]}
                                wide={false}
                                useAnchorElementWidth={true}
                                clearable
                                includeDeletedAssets={true}
                            />
                        }
                    />
                    <PrivilegeRequired privilege={DOWNLOAD_PUBLICATION}>
                        <div className={styles['publication-log__export_button']}>
                            <Button
                                icon={Icons.Download}
                                disabled={!isStoredSearchRangeValid}
                                title={
                                    isStoredSearchRangeValid
                                        ? undefined
                                        : t('publication-log.search-range-too-long', {
                                              maxDays: MAX_SEARCH_DAYS,
                                          })
                                }
                                onClick={() =>
                                    (location.href = getPublicationsCsvUri(
                                        storedStartDate,
                                        storedEndDate && endOfDay(storedEndDate),
                                        storedSpecificItem === undefined
                                            ? undefined
                                            : {
                                                  idAndType:
                                                      searchableItemIdAndType(storedSpecificItem),
                                                  name: getSearchableItemName(
                                                      storedSpecificItem,
                                                      trackNumbers,
                                                      t,
                                                  ),
                                              },
                                        sortInfo?.propName,
                                        sortInfo?.direction,
                                    ))
                                }>
                                {t('publication-log.export-csv')}
                            </Button>
                        </div>
                    </PrivilegeRequired>
                </div>
                <div className={styles['publication-log__count-header']}>
                    {isStoredSearchRangeValid ? (
                        <PublicationLogTableHeading
                            isLoading={isLoading}
                            isTruncated={isTruncated}
                            publicationAmount={pagedPublications?.items?.length || 0}
                        />
                    ) : (
                        <span className={styles['publication-log__table-header-error']}>
                            {t('publication-log.search-range-too-long', {
                                maxDays: MAX_SEARCH_DAYS,
                            })}
                        </span>
                    )}
                </div>
                <PublicationTable
                    layoutContext={layoutContext}
                    isLoading={isLoading}
                    items={pagedPublications?.items || []}
                    sortInfo={sortInfo}
                    onSortChange={updateTableSorting}
                    displaySingleItemHistory={setSpecificItem}
                />
            </div>
        </div>
    );
};

export default PublicationLog;
