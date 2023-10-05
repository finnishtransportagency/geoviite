import * as React from 'react';
import styles from './breadcrumb.scss';
import { createClassName } from 'vayla-design-lib/utils';
import { Link } from 'vayla-design-lib/link/link';

export type BreadcrumbProps = {
    some?: string;
    children?: React.ReactNode;
};

export const Breadcrumb: React.FC<BreadcrumbProps> = (props: BreadcrumbProps) => {
    const className = createClassName(styles.breadcrumb);
    return <div className={className}>{props.children}</div>;
};

export type BreadcrumbItemProps = {
    href?: string;
    onClick?: () => void;
    children?: React.ReactNode;
};

export const BreadcrumbItem: React.FC<BreadcrumbItemProps> = (props: BreadcrumbItemProps) => {
    const className = createClassName(
        styles.breadcrumb__item,
        props.onClick && styles['breadcrumb__item--has-action'],
    );
    return (
        <div className={className} onClick={props.onClick}>
            {props.href ? <Link href={props.href}>{props.children}</Link> : props.children}
        </div>
    );
};
