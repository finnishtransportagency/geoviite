import * as React from 'react';
import styles from './datepicker.scss';
import ReactDatePicker, { ReactDatePickerCustomHeaderProps } from 'react-datepicker';
import { format } from 'date-fns';
import { fi } from 'date-fns/locale';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { createClassName } from 'vayla-design-lib/utils';
import { TextField, TextInputIconPosition } from 'vayla-design-lib/text-field/text-field';
import { useCloneRef } from 'utils/react-utils';

type DatePickerProps = {
    value: Date | undefined;
    onChange?: (date: Date | undefined) => void;
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
                {format(date, 'LLLL', { locale: fi })} {date.getFullYear()}
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
>((props, ref) => {
    const localRef = useCloneRef(ref);
    return (
        <TextField
            {...props}
            Icon={(iconProps) => (
                <Icons.SetDate {...iconProps} onClick={() => localRef.current?.focus()} />
            )}
            iconPosition={TextInputIconPosition.RIGHT}
            ref={localRef}
        />
    );
});

DatePickerInput.displayName = 'DatePickerInput';

export const DatePicker: React.FC<DatePickerProps> = ({ onChange, value, ...props }) => {
    return (
        <div className={'datepicker'}>
            <ReactDatePicker
                renderCustomHeader={getHeaderElement}
                dateFormat="dd.MM.yyyy"
                locale={fi}
                selected={value}
                onChange={(date) => onChange && onChange(date ?? undefined)}
                calendarStartDay={1}
                showWeekNumbers
                popperModifiers={[
                    {
                        name: 'offset',
                        fn: (state) => ({
                            ...state,
                            x: state.x + 14,
                        }),
                    },
                ]}
                customInput={<DatePickerInput {...props} />}
            />
        </div>
    );
};
