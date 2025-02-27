import React from 'react';
import { Link, LinkProps } from 'react-router-dom';
import { createClassName } from 'vayla-design-lib/utils';
import styles from 'vayla-design-lib/link/link.scss';

export const RouterLink: React.FC<React.AnchorHTMLAttributes<HTMLAnchorElement> & LinkProps> = ({
    children,
    ...props
}) => {
    const className = createClassName(styles.link, props.className);

    return (
        <Link className={className} {...props}>
            {children}
        </Link>
    );
};
