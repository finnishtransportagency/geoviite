import * as React from 'react';

export type BadgeProps = {
    foo?: string;
} & React.InputHTMLAttributes<HTMLInputElement>;

export const Badge: React.FC<BadgeProps> = (_: BadgeProps) => {
    return <div>Badge</div>;
};
