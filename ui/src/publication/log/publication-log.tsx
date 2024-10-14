import * as React from 'react';
import styles from './publication-log.scss';
import { useTranslation } from 'react-i18next';
import { Link } from 'vayla-design-lib/link/link';
import {
    DatePicker,
    DatePickerDateSource,
    END_OF_CENTURY,
    START_OF_2022,
} from 'vayla-design-lib/datepicker/datepicker';
import { parseISOOrUndefined } from 'utils/date-utils';
import { endOfDay, startOfDay } from 'date-fns';
import {
    getPublicationsAsTableItems,
    getPublicationsCsvUri,
    MAX_RETURNED_PUBLICATION_LOG_ROWS,
} from 'publication/publication-api';
import PublicationTable from 'publication/table/publication-table';
import { Button } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import {
    InitiallyUnsorted,
    PublicationDetailsTableSortField,
    PublicationDetailsTableSortInformation,
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
import { SortDirection } from 'utils/table-utils';

type TableFetchFn = (
    from?: Date,
    to?: Date,
    sortBy?: PublicationDetailsTableSortField,
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

const PublicationLog: React.FC = () => {
    const { t } = useTranslation();
    const navigate = useAppNavigate();

    const selectedPublicationSearch = useTrackLayoutAppSelector(
        (state) => state.selection.publicationSearch,
    );

    const trackLayoutActionDelegates = React.useMemo(
        () => createDelegates(trackLayoutActionCreators),
        [],
    );

    const storedStartDate = parseISOOrUndefined(selectedPublicationSearch?.startDate);
    const storedEndDate = parseISOOrUndefined(selectedPublicationSearch?.endDate);

    const [sortInfo, setSortInfo] =
        React.useState<PublicationDetailsTableSortInformation>(InitiallyUnsorted);
    const [isLoading, setIsLoading] = React.useState(true);
    const [pagedPublications, setPagedPublications] = React.useState<Page<PublicationTableItem>>();

    React.useEffect(() => {
        if (!selectedPublicationSearch) {
            trackLayoutActionDelegates.setSelectedPublicationSearch(defaultPublicationSearch);
        }

        updatePublicationsTable(
            storedStartDate ?? parseISOOrUndefined(defaultPublicationSearch.startDate),
            storedEndDate ?? parseISOOrUndefined(defaultPublicationSearch.endDate),
            getPublicationsAsTableItems,
        );
    }, []);

    const updateTableSorting = (updatedSort: PublicationDetailsTableSortInformation) => {
        setSortInfo(updatedSort);

        if (pagedPublications?.items.length === MAX_RETURNED_PUBLICATION_LOG_ROWS) {
            updatePublicationsTable(
                storedStartDate,
                storedEndDate,
                publicationTableFetchFunctionByChangeMethod('SORTING_CHANGED'),
            );
        }
    };

    const setStartDate = (newStartDate: Date | undefined, source: DataSourceChangeMethod) => {
        trackLayoutActionDelegates.setSelectedPublicationSearchStartDate(
            newStartDate?.toISOString(),
        );
        updatePublicationsTable(
            newStartDate,
            storedEndDate,
            publicationTableFetchFunctionByChangeMethod(source),
        );
    };

    const setEndDate = (newEndDate: Date | undefined, source: DataSourceChangeMethod) => {
        trackLayoutActionDelegates.setSelectedPublicationSearchEndDate(newEndDate?.toISOString());
        updatePublicationsTable(
            storedStartDate,
            newEndDate,
            publicationTableFetchFunctionByChangeMethod(source),
        );
    };

    const updatePublicationsTable = (
        startDate: Date | undefined,
        endDate: Date | undefined,
        fetchFn: TableFetchFn,
    ) => {
        const datesAreValid = startDate && endDate;
        if (!datesAreValid) {
            clearPublicationsTable();
            return;
        }

        setIsLoading(true);
        const currentFetchId = ++fetchId;

        fetchFn(
            startDate && startOfDay(startDate),
            endDate && endOfDay(endDate),
            sortInfo.propName,
            sortInfo.direction,
        ).then((r) => {
            if (fetchId === currentFetchId) {
                r && setPagedPublications(r);
                setIsLoading(false);
            }
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

    const truncated =
        pagedPublications !== undefined &&
        pagedPublications.totalCount !== pagedPublications.items.length;

    return (
        <div className={styles['publication-log']}>
            <div className={styles['publication-log__title']}>
                <Link
                    onClick={() => {
                        trackLayoutActionDelegates.setSelectedPublicationSearch(undefined);
                        navigate('frontpage');
                    }}>
                    {t('frontpage.frontpage-link')}
                </Link>
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
                    <PrivilegeRequired privilege={DOWNLOAD_PUBLICATION}>
                        <div className={styles['publication-log__export_button']}>
                            <Button
                                icon={Icons.Download}
                                onClick={() =>
                                    (location.href = getPublicationsCsvUri(
                                        storedStartDate,
                                        storedEndDate && endOfDay(storedEndDate),
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
                    <span
                        title={
                            truncated
                                ? t('publication-table.truncated', {
                                      number: pagedPublications?.items?.length || 0,
                                  })
                                : ''
                        }>
                        {t('publication-table.count-header', {
                            number: pagedPublications?.items?.length || 0,
                            truncated: truncated ? '+' : '',
                        })}
                    </span>
                    {isLoading && <Spinner inline={true} />}
                </div>
                <PublicationTable
                    isLoading={isLoading}
                    items={pagedPublications?.items || []}
                    sortInfo={sortInfo}
                    onSortChange={updateTableSorting}
                />
            </div>
        </div>
    );
};

export default PublicationLog;
