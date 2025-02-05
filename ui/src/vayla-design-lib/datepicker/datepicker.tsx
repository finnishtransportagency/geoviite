import * as React from 'react';
import styles from './datepicker.scss';
import ReactDatePicker, { ReactDatePickerCustomHeaderProps } from 'react-datepicker';
import { format, isSameDay, isValid, parse } from 'date-fns';
import { fi } from 'date-fns/locale';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { createClassName } from 'vayla-design-lib/utils';
import { TextField, TextInputIconPosition } from 'vayla-design-lib/text-field/text-field';
import { CloseableModal } from 'vayla-design-lib/closeable-modal/closeable-modal';
import { formatDateShort } from 'utils/date-utils';
import { useCloneRef } from 'utils/react-utils';

export type DatePickerDateSource = 'PICKER' | 'TEXT';

type DatePickerProps = {
    value: Date | undefined;
    onChange: (date: Date | undefined, source: DatePickerDateSource) => void;
    wide?: boolean;
    minDate?: Date;
    maxDate?: Date;
} & Omit<React.InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange'>;

type DatePickerInputProps = {
    openDatePicker: () => void;
    date: Date | undefined;
    setDate: (date: Date | undefined) => void;
    wide: boolean | undefined;
    minDate?: Date;
    maxDate?: Date;
} & Omit<React.InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange'>;

const DATE_FORMAT = 'dd.MM.yyyy';
const DATE_PICKER_POPUP_LEFT_PAD_PX = 14;

export const START_OF_MILLENNIUM = new Date(2000, 0, 1);
export const START_OF_2022 = new Date(2022, 0, 1);
export const END_OF_CENTURY = new Date(2099, 11, 31);

const clampDateToRange = (date: Date, minDate?: Date, maxDate?: Date): Date => {
    if (minDate && date < minDate) {
        return minDate;
    } else if (maxDate && date > maxDate) {
        return maxDate;
    } else {
        return date;
    }
};

const DatePickerInput = React.forwardRef<HTMLInputElement, DatePickerInputProps>(
    ({ openDatePicker, date, setDate, wide, minDate, maxDate, ...props }, ref) => {
        const [value, setValue] = React.useState<string>('');
        const localRef = useCloneRef<HTMLInputElement>(ref);
        React.useEffect(() => {
            if (document.activeElement !== localRef.current) {
                setValue(date ? formatDateShort(date) : '');
            }
        }, [date]);

        function setValueAndSetDateIfValid(e: React.ChangeEvent<HTMLInputElement>): void {
            setValue(e.target.value);

            const newDate = parse(e.target.value, DATE_FORMAT, new Date());
            if (isValid(newDate) && (!date || !isSameDay(date, newDate))) {
                setDate(clampDateToRange(newDate, minDate, maxDate));
            }
        }

        function setDateOrResetIfInvalid(e: React.FocusEvent<HTMLInputElement>): void {
            const newDate = parse(e.target.value, DATE_FORMAT, new Date());
            if (!isValid(newDate)) {
                setValue(date ? formatDateShort(date) : '');
            } else if (!date || !isSameDay(date, newDate)) {
                const clampedDate = clampDateToRange(newDate, minDate, maxDate);
                setValue(formatDateShort(clampedDate));
                setDate(clampedDate);
            }
        }

        return (
            <TextField
                Icon={(iconProps) => <Icons.SetDate {...iconProps} />}
                iconPosition={TextInputIconPosition.RIGHT}
                onClickIcon={() => {
                    localRef.current?.focus();
                    openDatePicker();
                }}
                wide={wide}
                value={value}
                onFocusCapture={openDatePicker}
                onChange={(e) => setValueAndSetDateIfValid(e)}
                onBlur={(e) => setDateOrResetIfInvalid(e)}
                onClick={openDatePicker}
                ref={localRef}
                {...props}
            />
        );
    },
);
DatePickerInput.displayName = 'DatePickerInput';

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

export const DatePicker: React.FC<DatePickerProps> = ({ onChange, value, wide, ...props }) => {
    const [open, setOpen] = React.useState(false);
    const ref = React.useRef<HTMLInputElement>(null);
    const iconRef = React.createRef<SVGSVGElement>();
    const className = createClassName(styles['datepicker'], wide && styles['datepicker--wide']);

    return (
        <div className={className}>
            <DatePickerInput
                openDatePicker={() => setOpen(true)}
                date={value}
                setDate={(date) => onChange(date, 'TEXT')}
                wide={wide}
                ref={ref}
                {...props}
            />
            {open && (
                <CloseableModal
                    onClickOutside={() => undefined}
                    anchorElementRef={ref}
                    openingElementRef={iconRef}
                    className={styles['datepicker__popup-container']}>
                    <ReactDatePicker
                        renderCustomHeader={getHeaderElement}
                        locale={fi}
                        selected={value}
                        onChange={(date) => {
                            onChange(date ?? undefined, 'PICKER');
                        }}
                        onChangeRaw={() => setOpen(false)}
                        onClickOutside={() => {
                            setOpen(false);
                        }}
                        minDate={props.minDate}
                        maxDate={props.maxDate}
                        calendarStartDay={1}
                        showWeekNumbers
                        inline
                        popperModifiers={[
                            {
                                name: 'offset',
                                fn: (state) => ({
                                    ...state,
                                    x: state.x + DATE_PICKER_POPUP_LEFT_PAD_PX,
                                }),
                            },
                        ]}
                    />
                </CloseableModal>
            )}
        </div>
    );
};
