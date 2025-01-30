import { TimeStamp } from 'common/common-model';
import { maxOf, minOf } from 'utils/array-utils';
import { format, getYear, parseISO, startOfToday } from 'date-fns';

export const currentDay = startOfToday();

export const currentYear = getYear(currentDay);

function isDate(date: Date | TimeStamp): date is Date {
    return typeof date !== 'string';
}

export function formatDateFull(date: Date | TimeStamp): string {
    return formatDate(date, 'dd.MM.yyyy HH.mm');
}

export function formatISODate(date: Date | TimeStamp): string {
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
    return compareDates(d1, d2);
}

export function compareDates(d1: Date | undefined, d2: Date | undefined): number {
    if (!d1 && !d2) {
        return 0;
    } else if (!d1) {
        return 1; // consider undefined more heavyweight
    } else if (!d2) {
        return -1; // consider undefined more heavyweight
    } else if (d1.getTime() === d2.getTime()) {
        return 0;
    } else if (d1 < d2) {
        return -1;
    } else if (d1 > d2) {
        return 1;
    }
    // should never happen, compiler does not understand this
    throw new Error('Logic of compareTimestamps fails!');
}

export function getMinTimestamp(time1: TimeStamp, ...others: TimeStamp[]) {
    return minOf([time1, ...others], compareTimestamps) as TimeStamp;
}

export const getMaxTimestamp = (time1: TimeStamp, ...others: TimeStamp[]) =>
    getMaxTimestampFromArray([time1, ...others]);

export const getMaxTimestampFromArray = (timestamps: TimeStamp[]) =>
    maxOf(timestamps, compareTimestamps) as TimeStamp;

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

export function parseISOOrUndefined(timestamp: TimeStamp | undefined): Date | undefined {
    if (!timestamp || timestamp.length === 0) {
        return undefined;
    }

    return parseISO(timestamp);
}

export function daysBetween(date1: Date, date2: Date): number {
    const oneDayInMilliseconds = 24 * 60 * 60 * 1000;
    const differenceInMilliseconds = Math.abs(date2.getTime() - date1.getTime());
    return Math.ceil(differenceInMilliseconds / oneDayInMilliseconds);
}
