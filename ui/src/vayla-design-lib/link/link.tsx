import * as React from 'react';
import styles from './link.scss';
import { createClassName } from 'vayla-design-lib/utils';

export const Link: React.FC<React.HTMLProps<HTMLAnchorElement>> = (
    props: React.HTMLProps<HTMLAnchorElement>,
) => {
    const className = createClassName(styles.link, props.className);
    return (
        <a {...props} className={className}>
            {props.children}
        </a>
    );
};
