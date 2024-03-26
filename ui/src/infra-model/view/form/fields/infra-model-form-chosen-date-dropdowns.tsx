import * as React from 'react';
import monthsData from 'infra-model/view/form/fields/months.json';
import { Dropdown, DropdownSize } from 'vayla-design-lib/dropdown/dropdown';
import { createYearRangeFromCurrentYear } from 'utils/date-utils';
import styles from '../infra-model-form.module.scss';

type InfraModelFormChosenDateDropDownsProps = {
    date: Date;
    handleDayChange: (date: Date) => void;
};

export const monthNames = monthsData.map((month) => month.name);

const InfraModelFormChosenDateDropDowns: React.FC<InfraModelFormChosenDateDropDownsProps> = (
    props: InfraModelFormChosenDateDropDownsProps,
) => {
    const date = new Date(props.date);
    const yearRangeBackwards = 45;
    const yearRangeForwards = 5;

    const handleMonthChange = (month: number) => {
        const d = new Date(date.getFullYear(), month, 1, 12);
        props.handleDayChange(d);
    };

    const handleYearChange = (year: number) => {
        const d = new Date(year, date ? date.getMonth() : 0, 1, 12);
        props.handleDayChange(d);
    };

    return (
        <div className={styles['infra-model-upload__date-selection']}>
            <Dropdown
                size={DropdownSize.SMALL}
                wideList
                placeholder={monthNames[date.getMonth()]}
                value={date.getMonth()}
                options={monthsData.map((month) => ({
                    name: month.name,
                    value: month.value,
                    qaId: `month-${month.value}`,
                }))}
                onChange={(month: number) => handleMonthChange(month)}
                canUnselect={false}
                searchable
            />
            <Dropdown
                size={DropdownSize.SMALL}
                wideList
                placeholder={date.getFullYear().toString()}
                value={date.getFullYear()}
                options={createYearRangeFromCurrentYear(yearRangeBackwards, yearRangeForwards).map(
                    (year) => ({
                        name: year.toString(),
                        value: year,
                        qaId: `year-${year}`,
                    }),
                )}
                onChange={(year: number) => handleYearChange(year)}
                canUnselect={false}
                searchable
            />
        </div>
    );
};

export default InfraModelFormChosenDateDropDowns;
