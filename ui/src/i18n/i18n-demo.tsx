import React from 'react';
import { useTranslation } from 'react-i18next';
import { FieldLayout } from 'vayla-design-lib/field-layout/field-layout';

export const I18nDemo: React.FC = () => {
    const { t, i18n } = useTranslation();

    const changeLanguageHandler = (e: React.ChangeEvent<HTMLSelectElement>) => {
        const languageValue = e.target.value;
        i18n.changeLanguage(languageValue);
    };

    return (
        <div>
            <select className="custom-select" onChange={changeLanguageHandler}>
                <option value="en">English</option>
                <option value="fi">Finnish</option>
            </select>
            <h1>{t('test.text')}</h1>
            <h2>{t('test.title')}</h2>
            <p>{t('test.description.part1')}</p>
            <p>{t('test.description.part2')}</p>
            <p>some other text {t('test.text')}</p>
            <FieldLayout
                label={`concatenation example: ${t('test.text')} *`}
                value={'test.text'}></FieldLayout>
        </div>
    );
};
