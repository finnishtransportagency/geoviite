import { TimeStamp } from 'common/common-model';
import { maxOf } from 'utils/array-utils';
import { format } from 'date-fns';

export const currentYear = new Date().getFullYear();
export const currentMonth = new Date().getMonth();

export const currentDay = new Date(new Date().setHours(0, 0, 0, 0));

function isDate(date: Date | TimeStamp): date is Date {
    return typeof date != 'string';
}

export function getTomorrow(date: Date): Date {
    const tomorrow = new Date(date);
    return new Date(tomorrow.setHours(24, 0, 0, 0));
}

export function formatDateFull(date: Date | TimeStamp): string {
    return formatDate(date, 'dd.MM.yyyy HH.mm');
}

export function formatGMTDateFull(date: Date | TimeStamp): string {
    return formatDate(date, "yyyy-MM-dd'T'HH:mm:ss'Z'");
}

export function formatDateShort(date: Date | TimeStamp): string {
    return formatDate(date, 'dd.MM.yyyy');
}

function formatDate(date: Date | TimeStamp, dateFormat: string): string {
    const actualDate = isDate(date) ? date : toDateOrUndefined(date);
    return actualDate ? format(actualDate, dateFormat) : '';
}

export function toDate(timestamp: TimeStamp): Date {
    const date = toDateOrUndefined(timestamp);
    if (!date) {
        throw new Error(`Invalid date value "${timestamp}"!`);
    }
    return date;
}

export function toDateOrUndefined(timestampStr: TimeStamp): Date | undefined {
    const timestamp = Date.parse(timestampStr);
    if (!isNaN(timestamp)) {
        return new Date(timestamp);
    }
    return undefined;
}

export function compareTimestamps(t1: TimeStamp, t2: TimeStamp): number {
    const d1 = toDateOrUndefined(t1);
    const d2 = toDateOrUndefined(t2);
    if (!d1 && !d2) {
        return 0;
    } else if (!d1) {
        return 1; // consider undefined more heavyweight
    } else if (!d2) {
        return -1; // consider undefined more heavyweight
    } else if (d1.getTime() == d2.getTime()) {
        return 0;
    } else if (d1 < d2) {
        return -1;
    } else if (d1 > d2) {
        return 1;
    }
    // should never happen, compiler does not understand this
    throw new Error('Logic of compareTimestamps fails!');
}

export function getMaxTimestamp(time1: TimeStamp, ...others: TimeStamp[]) {
    return maxOf([time1, ...others], compareTimestamps) as TimeStamp;
}

export function createYearRange(fromYear: number, toYear: number): number[] {
    const years = [];
    for (let i = fromYear; i <= toYear; i += 1) {
        years.push(i);
    }
    return years;
}

export function createYearRangeFromCurrentYear(backwards: number, forwards: number): number[] {
    return createYearRange(currentYear - backwards, currentYear + forwards);
}
