import React from 'react';
import styles from './infobox.module.scss';

type InfoboxContentProps = {
    children: React.ReactNode;
};

const InfoboxContent: React.FC<InfoboxContentProps> = ({ ...props }: InfoboxContentProps) => {
    return <div className={styles['infobox__content']}>{props.children}</div>;
};

export const InfoboxContentSpread: React.FC<InfoboxContentProps> = ({
    ...props
}: InfoboxContentProps) => {
    return <div className={styles['infobox__content-spread']}>{props.children}</div>;
};

export default InfoboxContent;
