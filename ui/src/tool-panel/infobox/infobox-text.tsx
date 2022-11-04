import React from 'react';
import styles from './infobox.module.scss';

type InfoboxTextProps = {
    value?: string;
};

const InfoboxText: React.FC<InfoboxTextProps> = ({ value }: InfoboxTextProps) => {
    return <div className={styles['infobox__text']}>{value}</div>;
};

export default InfoboxText;
