import * as React from 'react';
import styles from './link.scss';
import { createClassName } from 'vayla-design-lib/utils';

export type LinkProps = {
    foo?: string;
} & React.HTMLProps<HTMLAnchorElement>;

export const Link: React.FC<LinkProps> = (props: LinkProps) => {
    const className = createClassName(styles.link);
    return (
        <a className={className} {...props}>
            {props.children}
        </a>
    );
};
