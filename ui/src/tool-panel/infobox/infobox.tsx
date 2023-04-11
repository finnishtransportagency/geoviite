import React from 'react';
import styles from './infobox.module.scss';
import { createClassName } from 'vayla-design-lib/utils';
import { Icons, IconSize } from 'vayla-design-lib/icon/Icon';

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
    const [visible, setVisible] = React.useState(false);

    const classes = createClassName(styles.infobox, variant && styles[variant], className);
    const titleClasses = createClassName('infobox__title', visible && 'infobox__title--visible');

    return (
        <div className={classes} {...props}>
            <div className={titleClasses} onClick={() => setVisible(!visible)}>
                <Icons.Chevron size={IconSize.SMALL}></Icons.Chevron>
                {title}
            </div>
            {visible && props.children}
        </div>
    );
};

export default Infobox;
