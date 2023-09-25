export function isNilOrBlank(str: string | undefined): boolean {
    return !str || !str.trim();
}

export function isEqualWithoutWhitespace(str1: string, str2: string): boolean {
    return (
        str1.toLocaleLowerCase().replace(/\s/g, '') === str2.toLocaleLowerCase().replace(/\s/g, '')
    );
}

export function isEmpty(str: string) {
    return str.length == 0 || isNilOrBlank(str);
}
