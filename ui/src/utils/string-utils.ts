import i18next from 'i18next';

export function isNilOrBlank(str: string | undefined): boolean {
    return !str || !str.trim();
}

export function isEqualWithoutWhitespace(str1: string, str2: string): boolean {
    return (
        str1.toLocaleLowerCase().replace(/\s/g, '') === str2.toLocaleLowerCase().replace(/\s/g, '')
    );
}

export function isEmpty(str: string) {
    return str.length === 0 || isNilOrBlank(str);
}

export const isEqualIgnoreCase = (str1: string, str2: string): boolean =>
    str1.localeCompare(str2, i18next.language, { sensitivity: 'accent' }) === 0;

export function parseFloatOrUndefined(str: string): number | undefined {
    if (!str) {
        return undefined;
    } else {
        const parsed = parseFloat(str);
        return isNaN(parsed) ? undefined : parsed;
    }
}
