import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { getSortDirectionIcon, TableSorting } from 'utils/table-utils';
import { Th } from 'vayla-design-lib/table/table';

export type SortableTableHeaderProps<T> = {
    prop: keyof T;
    translationKey: string;
    className: string;
    sortInfo: TableSorting<T>;
    sortByProp: (prop: keyof T) => void;
};

export const SortableTableHeader = <T,>({
    prop,
    translationKey,
    className,
    sortInfo,
    sortByProp,
}: SortableTableHeaderProps<T>) => {
    const { t } = useTranslation();
    return (
        <Th
            className={className}
            onClick={() => sortByProp(prop)}
            qa-id={translationKey}
            icon={
                sortInfo.propName === prop ? getSortDirectionIcon(sortInfo.direction) : undefined
            }>
            {t(translationKey)}
        </Th>
    );
};
