import * as React from 'react';
import styles from './link.scss';
import { createClassName } from 'vayla-design-lib/utils';

type LinkProps = React.HTMLProps<HTMLAnchorElement> & { disabled?: boolean };

export const Link: React.FC<LinkProps> = ({ disabled, onClick, href, ...props }: LinkProps) => {
    const className = createClassName(
        styles.link,
        disabled && styles['link--disabled'],
        props.className,
    );
    const overriddenHref = disabled ? undefined : href;

    return (
        <a
            {...props}
            href={overriddenHref}
            className={className}
            onClick={disabled ? undefined : onClick}>
            {props.children}
        </a>
    );
};
