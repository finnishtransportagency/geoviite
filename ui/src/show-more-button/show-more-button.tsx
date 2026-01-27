import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { Button, ButtonProps, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import styles from './show-more-button.scss';

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
            className={styles['show-more-button']}
            variant={ButtonVariant.GHOST}
            size={ButtonSize.SMALL}
            {...props}
            onClick={() => onShowMore()}>
            {expanded ? t('button.show-less') : (showMoreText ?? t('button.show-more'))}
        </Button>
    );
};
