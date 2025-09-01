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

const equalsIgnoreCaseCollator = new Intl.Collator(i18next.language, { sensitivity: 'accent' });

export const isEqualIgnoreCase = (str1: string, str2: string): boolean =>
    equalsIgnoreCaseCollator.compare(str1, str2) === 0;

export function parseFloatOrUndefined(str: string): number | undefined {
    if (!str) {
        return undefined;
    } else {
        const parsed = parseFloat(str);
        return isNaN(parsed) ? undefined : parsed;
    }
}
