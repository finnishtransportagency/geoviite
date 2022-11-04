import React from 'react';
import styles from './infobox.module.scss';

type InfoboxRowProps = {
    children?: React.ReactNode;
};

const InfoboxRow: React.FC<InfoboxRowProps> = ({ children }: InfoboxRowProps) => {
    return <div className={styles['infobox__row']}>{children}</div>;
};

export default InfoboxRow;
