import * as React from 'react';
import styles from './datepicker.scss';
import ReactDatePicker, { ReactDatePickerCustomHeaderProps } from 'react-datepicker';
import { format } from 'date-fns';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { createClassName } from 'vayla-design-lib/utils';

type DatePickerProps = {
    onChange?: () => void;
    hasError?: () => void;
} & React.InputHTMLAttributes<HTMLInputElement>;

function getHeaderElement({
    date,
    decreaseMonth,
    increaseMonth,
    prevMonthButtonDisabled,
    nextMonthButtonDisabled,
}: ReactDatePickerCustomHeaderProps) {
    return (
        <div className={styles['react-datepicker__navigation']}>
            <div
                className={createClassName(
                    styles['react-datepicker__navigate-month'],
                    styles['react-datepicker__navigate-month--previous'],
                )}
                onClick={() => {
                    prevMonthButtonDisabled || decreaseMonth();
                }}>
                <Icons.Chevron
                    size={IconSize.SMALL}
                    color={prevMonthButtonDisabled ? IconColor.DISABLED : IconColor.ORIGINAL}
                />
            </div>
            <span className={styles['react-datepicker__current-month']}>
                {format(date, 'MMMM')} {date.getFullYear()}
            </span>
            <div
                className={createClassName(
                    styles['react-datepicker__navigate-month'],
                    styles['react-datepicker__navigate-month--next'],
                )}
                onClick={() => {
                    nextMonthButtonDisabled || increaseMonth();
                }}>
                <Icons.Chevron
                    size={IconSize.SMALL}
                    color={nextMonthButtonDisabled ? IconColor.DISABLED : IconColor.ORIGINAL}
                />
            </div>
        </div>
    );
}

export const DatePicker: React.FC<DatePickerProps> = () => {
    const [startDate, setStartDate] = React.useState(new Date());

    return (
        <div className={styles['datepicker']}>
            <ReactDatePicker
                renderCustomHeader={getHeaderElement}
                dateFormat="dd.MM.yyyy"
                selected={startDate}
                onChange={(date: Date) => setStartDate(date)}
                calendarStartDay={1}
                popperModifiers={[
                    {
                        name: 'offset',
                        options: {
                            offset: [0, 6],
                        },
                    },
                ]}
            />
        </div>
    );
};
