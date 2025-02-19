import { TFunction } from 'i18next';

export const enumTranslationKey = (enumKey: string, value: string) => `enum.${enumKey}.${value}`;

export const enumTranslation = (
    t: TFunction<'translation', undefined>,
    enumKey: string,
    value: string,
): string => {
    return t(enumTranslationKey(enumKey, value));
};
