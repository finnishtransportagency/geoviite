import React from 'react';
import styles from 'tool-panel/infobox/infobox.module.scss';
import { createClassName } from 'vayla-design-lib/utils';

type InfoboxListProps = {
    children: React.ReactNode;
    className?: string;
};

export const InfoboxList: React.FC<InfoboxListProps> = ({ className, children }) => {
    const containerClassName = createClassName(styles['infobox__list'], className);

    return <div className={containerClassName}>{children}</div>;
};

type InfoboxListRowProps = {
    label: React.ReactNode;
    content: React.ReactNode;
} & Pick<React.HTMLProps<HTMLDivElement>, 'key' | 'onMouseOver' | 'onMouseOut'>;

export const InfoboxListRow: React.FC<InfoboxListRowProps> = ({ label, content, ...props }) => {
    return (
        <div className={styles['infobox__list-row']} {...props}>
            <div className="infobox__list-cell infobox__list-cell--label">{label}</div>
            <div className="infobox__list-cell infobox__list-cell--stretch">{content}</div>
        </div>
    );
};
