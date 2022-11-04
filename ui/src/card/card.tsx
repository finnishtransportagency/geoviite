import * as React from 'react';
import styles from './card.scss';
import { createClassName } from 'vayla-design-lib/utils';

type CardProps = {
    header: string;
    content: React.ReactNode;
    className?: string;
};

const Card: React.FC<CardProps> = ({ header, content, className }: CardProps) => {
    const classes = createClassName(styles['card'], className);

    return (
        <div className={classes}>
            <h2>{header}</h2>
            {content}
        </div>
    );
};

export default Card;
