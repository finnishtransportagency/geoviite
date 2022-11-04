import * as React from 'react';
import styles from './title.scss';
import { createClassName } from 'vayla-design-lib/utils';

export type TitleProps = {
    children?: React.ReactNode;
};

export const Title: React.FC<TitleProps> = (props: TitleProps) => {
    const className = createClassName(styles.title);
    return <h1 className={className}>{props.children}</h1>;
};
