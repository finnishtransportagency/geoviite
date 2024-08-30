import React from 'react';
import { useTranslation } from 'react-i18next';
import { LayoutStateCategory } from 'track-layout/track-layout-model';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

type LayoutStateCategoryLabelProps = {
    category: LayoutStateCategory;
};

function getTranslationKey(category: LayoutStateCategory) {
    switch (category) {
        case 'EXISTING':
        case 'NOT_EXISTING':
            return category;
        default:
            return exhaustiveMatchingGuard(category);
    }
}

const LayoutStateCategoryLabel: React.FC<LayoutStateCategoryLabelProps> = ({
    category,
}: LayoutStateCategoryLabelProps) => {
    const { t } = useTranslation();

    return (
        <React.Fragment>
            {t(`enum.layout-state-category.${getTranslationKey(category)}`)}
        </React.Fragment>
    );
};

export default LayoutStateCategoryLabel;
