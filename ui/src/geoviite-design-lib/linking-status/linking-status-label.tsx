import * as React from 'react';
import styles from './linking-status-label.scss';
import { createClassName } from 'vayla-design-lib/utils';
import { useTranslation } from 'react-i18next';

type LinkingStatusLabelProps = {
    isLinked: boolean;
};

export const LinkingStatusLabel: React.FC<LinkingStatusLabelProps> = ({
    isLinked = false,
}: LinkingStatusLabelProps) => {
    const { t } = useTranslation();
    const classes = createClassName(
        styles['linking-status-label'],
        styles[isLinked ? 'linking-status-label--linked' : 'linking-status-label--unlinked'],
    );

    return (
        <span className={classes}>
            <span>{isLinked ? t('yes') : t('no')}</span>
        </span>
    );
};
