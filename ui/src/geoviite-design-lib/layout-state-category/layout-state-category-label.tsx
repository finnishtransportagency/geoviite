import React from 'react';
import { useTranslation } from 'react-i18next';
import { LayoutStateCategory } from 'track-layout/track-layout-model';

type LayoutStateCategoryLabelProps = {
    category: LayoutStateCategory | null;
};

function getTranslationKey(category: LayoutStateCategory | null) {
    switch (category) {
        case 'EXISTING':
        case 'NOT_EXISTING':
        case 'FUTURE_EXISTING':
            return category;
        default:
            return 'UNKNOWN';
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
