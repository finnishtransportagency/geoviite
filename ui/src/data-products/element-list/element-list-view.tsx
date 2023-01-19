import * as React from 'react';
import { useTranslation } from 'react-i18next';

const ElementListView = () => {
    const { t } = useTranslation();

    return <div>{t('data-products.element-list.element-list-title')}</div>;
};

export default ElementListView;
