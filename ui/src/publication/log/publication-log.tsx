import * as React from 'react';
import styles from './publication-log.scss';
import { useTranslation } from 'react-i18next';
import { DatePicker, END_OF_CENTURY, START_OF_2022 } from 'vayla-design-lib/datepicker/datepicker';
import { daysBetween, parseISOOrUndefined } from 'utils/date-utils';
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
import { DOWNLOAD_PUBLICATION } from 'user/user-model';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { TableSorting } from 'utils/table-utils';
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
import { useMemoizedDate, useRateLimitedTwoPartEffect } from 'utils/react-utils';
import { endOfDay, startOfDay } from 'date-fns';

const MAX_SEARCH_DAYS = 180;

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

const isValidPublicationLogSearchRange = (
    specificItem: SearchItemValue<SearchablePublicationLogItem> | undefined,
    start: Date | undefined,
    end: Date | undefined,
): boolean => {
    return (
        specificItem !== undefined || // go nuts with single-item searches, they're cheap
        (start !== undefined && daysBetween(start, end ?? new Date()) < MAX_SEARCH_DAYS)
    );
};

type SearchParams = {
    startDate: Date | undefined;
    endDate: Date | undefined;
    specificItem: SearchItemValue<SearchablePublicationLogItem> | undefined;
    sortInfo: TableSorting<SortablePublicationTableProps>;
};

function searchParamsAffectingVisibleRowsDiffer(
    pagedPublications: Page<PublicationTableItem> | undefined,
    a: SearchParams | undefined,
    b: SearchParams | undefined,
) {
    if ((a === undefined) !== (b === undefined)) {
        return true;
    } else if (a === undefined || b === undefined) {
        return false;
    } else {
        const searchPredicateDiffers =
            a.startDate !== b.startDate ||
            a.endDate !== b.endDate ||
            a.specificItem !== b.specificItem;
        const sortDiffersAndCanAffectVisibleRows =
            (pagedPublications?.items.length ?? 0) >= MAX_RETURNED_PUBLICATION_LOG_ROWS &&
            a.sortInfo !== b.sortInfo;
        return searchPredicateDiffers || sortDiffersAndCanAffectVisibleRows;
    }
}

function usePublicationLogSearch(
    startDate: Date | undefined,
    endDate: Date | undefined,
    specificItem: SearchItemValue<SearchablePublicationLogItem> | undefined,
    sortInfo: TableSorting<SortablePublicationTableProps>,
): { pagedPublications: Page<PublicationTableItem> | undefined; isLoading: boolean } {
    const [pagedPublications, setPagedPublications] = React.useState<Page<PublicationTableItem>>();
    const [lastSearch, setLastSearch] = React.useState<SearchParams | undefined>(undefined);

    const clearPublicationsTable = () => {
        setPagedPublications({
            totalCount: 0,
            items: [],
            start: 0,
        });
    };

    const search = {
        startDate,
        endDate,
        specificItem,
        sortInfo,
    };

    useRateLimitedTwoPartEffect(
        () => {
            if (!isValidPublicationLogSearchRange(specificItem, startDate, endDate)) {
                clearPublicationsTable();
                setLastSearch(search);
                return undefined;
            } else if (
                !searchParamsAffectingVisibleRowsDiffer(pagedPublications, lastSearch, search)
            ) {
                setLastSearch(search);
                return undefined;
            } else {
                return getPublicationsAsTableItems(
                    startDate && startOfDay(startDate),
                    endDate && endOfDay(endDate),
                    specificItem === undefined ? undefined : searchableItemIdAndType(specificItem),
                    sortInfo.propName,
                    sortInfo.direction,
                );
            }
        },
        (results) => {
            setPagedPublications(results);
            setLastSearch(search);
        },
        500,
        [startDate, endDate, specificItem, sortInfo],
    );

    return {
        isLoading: searchParamsAffectingVisibleRowsDiffer(pagedPublications, search, lastSearch),
        pagedPublications,
    };
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

    const specificItem = selectedPublicationSearch?.specificItem;
    const startDate = useMemoizedDate(
        parseISOOrUndefined(
            specificItem === undefined
                ? selectedPublicationSearch?.globalStartDate
                : selectedPublicationSearch?.specificItemStartDate,
        ),
    );
    const endDate = useMemoizedDate(
        parseISOOrUndefined(
            specificItem === undefined
                ? selectedPublicationSearch?.globalEndDate
                : selectedPublicationSearch?.specificItemEndDate,
        ),
    );

    const [sortInfo, setSortInfo] =
        React.useState<TableSorting<SortablePublicationTableProps>>(SortedByTimeDesc);

    const { isLoading, pagedPublications } = usePublicationLogSearch(
        startDate,
        endDate,
        specificItem,
        sortInfo,
    );

    const setStartDate = (newStartDate: Date | undefined) => {
        trackLayoutActionDelegates.setSelectedPublicationSearchStartDate(
            newStartDate?.toISOString(),
        );
    };

    const setEndDate = (newEndDate: Date | undefined) => {
        trackLayoutActionDelegates.setSelectedPublicationSearchEndDate(newEndDate?.toISOString());
    };

    const isSearchRangeValid = isValidPublicationLogSearchRange(specificItem, startDate, endDate);

    const endDateErrors =
        startDate && endDate && startDate > endDate ? [t('publication-log.end-before-start')] : [];

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
                        label={
                            specificItem === undefined
                                ? t('publication-log.start-date')
                                : t('publication-log.start-date-with-specific-object')
                        }
                        value={
                            <DatePicker
                                value={startDate}
                                onChange={setStartDate}
                                minDate={START_OF_2022}
                                maxDate={END_OF_CENTURY}
                                qa-id={'publication-log-start-date-input'}
                                isClearable
                            />
                        }
                    />
                    <FieldLayout
                        label={
                            specificItem === undefined
                                ? t('publication-log.end-date')
                                : t('publication-log.end-date-with-specific-object')
                        }
                        value={
                            <DatePicker
                                value={endDate}
                                onChange={setEndDate}
                                minDate={START_OF_2022}
                                maxDate={END_OF_CENTURY}
                                qa-id={'publication-log-end-date-input'}
                                isClearable
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
                                onItemSelected={
                                    trackLayoutActionDelegates.setSelectedPublicationSearchSearchableItem
                                }
                                value={specificItem}
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
                                disabled={!isSearchRangeValid}
                                title={
                                    isSearchRangeValid
                                        ? undefined
                                        : t('publication-log.search-range-too-long', {
                                              maxDays: MAX_SEARCH_DAYS,
                                          })
                                }
                                onClick={() =>
                                    (location.href = getPublicationsCsvUri(
                                        startDate,
                                        endDate && endOfDay(endDate),
                                        specificItem === undefined
                                            ? undefined
                                            : {
                                                  idAndType: searchableItemIdAndType(specificItem),
                                                  name: getSearchableItemName(
                                                      specificItem,
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
                    {isSearchRangeValid ? (
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
                    onSortChange={setSortInfo}
                    displaySingleItemHistory={
                        trackLayoutActionDelegates.startFreshSpecificItemPublicationLogSearch
                    }
                />
            </div>
        </div>
    );
};

export default PublicationLog;
