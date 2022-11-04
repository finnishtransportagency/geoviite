import * as React from 'react';
import './infra-model-list.module.scss';

export type TagProps = {
    name: string;
};

export const Tag: React.FC<TagProps> = ({ name }: TagProps) => {
    return <div className="tag">{name}</div>;
};

export default Tag;
