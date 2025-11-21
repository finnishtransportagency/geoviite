import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { Button, ButtonProps, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';

type ShowMoreButtonProps = {
    expanded: boolean;
    onShowMore: () => void;
    showMoreText?: string;
} & ButtonProps;

export const ShowMoreButton: React.FC<ShowMoreButtonProps> = ({
    expanded,
    onShowMore,
    showMoreText,
    ...props
}) => {
    const { t } = useTranslation();
    return (
        <Button
            variant={ButtonVariant.GHOST}
            size={ButtonSize.SMALL}
            {...props}
            onClick={() => onShowMore()}>
            {expanded ? t('button.show-less') : (showMoreText ?? t('button.show-more'))}
        </Button>
    );
};
