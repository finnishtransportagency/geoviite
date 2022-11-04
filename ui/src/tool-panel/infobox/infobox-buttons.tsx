import React from 'react';
import styles from './infobox.module.scss';

type InfoboxButtonsProps = {
    children: React.ReactNode;
};

const InfoboxButtons: React.FC<InfoboxButtonsProps> = ({ children }: InfoboxButtonsProps) => {
    return <div className={styles['infobox__buttons']}>{children}</div>;
};

export default InfoboxButtons;
