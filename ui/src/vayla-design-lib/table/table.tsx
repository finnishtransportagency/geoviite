import * as React from 'react';
import styles from './table.scss';
import { createClassName } from 'vayla-design-lib/utils';
import { IconComponent, IconSize } from 'vayla-design-lib/icon/Icon';

export type TableProps = {
    wide?: boolean;
    isLoading?: boolean;
} & React.HTMLProps<HTMLTableElement>;

export const Table: React.FC<TableProps> = (props: TableProps) => {
    const className = createClassName(
        props.className,
        styles.table,
        props.wide && styles['table--wide'],
    );

    const containerClassName = createClassName(
        props.isLoading && styles['table__container--loading'],
    );
    return (
        <div className={containerClassName}>
            <table className={className}>{props.children}</table>
            {props.isLoading && <div className={styles['table--loading']} />}
        </div>
    );
};

export enum TdVariant {
    NUMBER = 'table__td--number',
    STRETCHED_CONTAINER = 'table__td--has-stretched-container',
}

export type TdProps = {
    variant?: TdVariant;
    narrow?: boolean;
    nowrap?: boolean;
} & React.HTMLProps<HTMLTableCellElement>;

export const Td: React.FC<TdProps> = ({ variant, narrow, nowrap, ...props }: TdProps) => {
    const className = createClassName(
        styles.table__td,
        variant && styles[variant],
        narrow && styles['table__td--narrow'],
        nowrap && styles['table__td--nowrap'],
    );
    return (
        <td {...props} className={className}>
            {variant === TdVariant.STRETCHED_CONTAINER ? (
                <div className={styles['table__td-stretched-container']}>{props.children}</div>
            ) : (
                props.children
            )}
        </td>
    );
};

export enum ThVariant {
    SINGLE_LINE,
    MULTILINE_TOP,
    MULTILINE_BOTTOM,
}

export enum ThContentAlignment {
    VERTICALLY_ALIGNED,
}

export type ThProps = {
    variant?: ThVariant;
    contentAlignment?: ThContentAlignment;
    narrow?: boolean;
    icon?: IconComponent;
    transparent?: boolean;
} & React.HTMLProps<HTMLTableCellElement>;

export const Th: React.FC<ThProps> = ({
    narrow,
    icon,
    variant = ThVariant.SINGLE_LINE,
    contentAlignment = undefined,
    transparent = false,
    ...props
}: ThProps) => {
    const Icon = icon;
    const className = createClassName(
        styles.table__th,
        narrow && styles['table__td--narrow'],
        props.onClick && styles['table__th--clickable'],
        styles['table__th--align-left'],
        props.className,
        transparent ? undefined : styles['table__th--has-background'],
        variant === ThVariant.SINGLE_LINE && styles['table__th--regular'],
        variant === ThVariant.MULTILINE_BOTTOM && styles['table__th--multiline-bottom'],
        variant === ThVariant.MULTILINE_TOP && styles['table__th--multiline-top'],
    );

    const childrenClassName = createClassName(
        styles['table__th-children'],
        contentAlignment === ThContentAlignment.VERTICALLY_ALIGNED &&
            styles['table__th-children--vertically-aligned'],
    );

    return (
        <th {...props} className={className}>
            <span className={styles['table__th-content']}>
                <span className={childrenClassName}>{props.children}</span>
                {Icon && (
                    <span className="table__th-icons">
                        <Icon size={IconSize.SMALL} />
                    </span>
                )}
            </span>
        </th>
    );
};
