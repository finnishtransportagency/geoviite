import React from 'react';
import styles from './infobox.module.scss';
import { createClassName } from 'vayla-design-lib/utils';

export enum InfoBoxVariant {
    BLUE = 'infobox--blue',
}

export type InfoboxProps = {
    title: string;
    children?: React.ReactNode;
    variant?: InfoBoxVariant;
    className?: string;
};

const Infobox: React.FC<InfoboxProps> = ({ title, variant, className, ...props }: InfoboxProps) => {
    const classes = createClassName(styles.infobox, variant && styles[variant], className);
    return (
        <div className={classes} {...props}>
            <div className={styles['infobox__title']}>{title}</div>
            {props.children}
        </div>
    );
};

export default Infobox;
