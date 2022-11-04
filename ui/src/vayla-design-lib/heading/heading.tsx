import * as React from 'react';
import styles from './heading.scss';
import { createClassName } from 'vayla-design-lib/utils';

export enum HeadingSize {
    SUB = 'heading--sub',
    SMALL = 'heading--small',
}

export type HeadingProps = {
    size?: HeadingSize;
    children?: React.ReactNode;
};

export const Heading: React.FC<HeadingProps> = ({ size, ...props }: HeadingProps) => {
    const className = createClassName(styles.heading, size && styles[size]);
    return <h2 className={className}>{props.children}</h2>;
};
