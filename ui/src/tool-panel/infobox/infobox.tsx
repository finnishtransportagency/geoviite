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
    contentVisible: boolean;
    onContentVisibilityChange: () => void;
};

const Infobox: React.FC<InfoboxProps> = ({
    title,
    variant,
    className,
    contentVisible,
    onContentVisibilityChange,
    ...props
}: InfoboxProps) => {
    const classes = createClassName(styles.infobox, variant && styles[variant], className);
    const titleClasses = createClassName(
        'infobox__title',
        contentVisible && 'infobox__title--visible',
    );

    return (
        <div className={classes} {...props}>
            <div className={titleClasses} onClick={onContentVisibilityChange}>
                <Icons.Chevron size={IconSize.SMALL}></Icons.Chevron>
                {title}
            </div>
            {contentVisible && props.children}
        </div>
    );
};

export default Infobox;
