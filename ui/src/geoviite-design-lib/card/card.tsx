import * as React from 'react';
import styles from './card.scss';
import { createClassName } from 'vayla-design-lib/utils';

type CardProps = {
    content: React.ReactNode;
    className?: string;
};

const Card: React.FC<CardProps> = ({ content, className }: CardProps) => {
    const classes = createClassName(styles['card'], className);

    return <div className={classes}>{content}</div>;
};

export default Card;
