import * as React from 'react';
import styles from './datepicker.scss';
import ReactDatePicker, { ReactDatePickerCustomHeaderProps } from 'react-datepicker';
import { format } from 'date-fns';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { createClassName } from 'vayla-design-lib/utils';
import { TextField, TextInputIconPosition } from 'vayla-design-lib/text-field/text-field';

type DatePickerProps = {
    value: Date | undefined;
    onChange?: (date: Date) => void;
} & Omit<React.InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange'>;

function getHeaderElement({
    date,
    decreaseMonth,
    increaseMonth,
    prevMonthButtonDisabled,
    nextMonthButtonDisabled,
}: ReactDatePickerCustomHeaderProps) {
    return (
        <div className={styles['datepicker__navigation']}>
            <div
                className={createClassName(
                    styles['datepicker__navigate-month'],
                    styles['datepicker__navigate-month--previous'],
                )}
                onClick={() => {
                    prevMonthButtonDisabled || decreaseMonth();
                }}>
                <Icons.Chevron
                    size={IconSize.SMALL}
                    color={prevMonthButtonDisabled ? IconColor.DISABLED : IconColor.ORIGINAL}
                />
            </div>
            <span className={styles['datepicker__current-month']}>
                {format(date, 'MMMM')} {date.getFullYear()}
            </span>
            <div
                className={createClassName(
                    styles['datepicker__navigate-month'],
                    styles['datepicker__navigate-month--next'],
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

const DatePickerInput = React.forwardRef<
    HTMLInputElement,
    React.DetailedHTMLProps<React.InputHTMLAttributes<HTMLInputElement>, HTMLInputElement>
>((props, ref) => (
    <TextField
        {...props}
        Icon={Icons.SetDate}
        iconPosition={TextInputIconPosition.RIGHT}
        ref={ref}
    />
));

DatePickerInput.displayName = 'DatePickerInput';

export const DatePicker: React.FC<DatePickerProps> = ({ onChange, value, ...props }) => {
    return (
        <div className={styles['datepicker']}>
            <ReactDatePicker
                renderCustomHeader={getHeaderElement}
                dateFormat="dd.MM.yyyy"
                selected={value}
                onChange={(date: Date) => onChange && onChange(date)}
                calendarStartDay={1}
                showWeekNumbers
                popperModifiers={[
                    {
                        name: 'offset',
                        options: {
                            offset: [0, 6],
                        },
                    },
                ]}
                customInput={<DatePickerInput {...props} />}
            />
        </div>
    );
};
