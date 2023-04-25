import React from 'react';

export interface TranslateProps {
    x?: number;
    y?: number;
}

export const Translate: React.FC<TranslateProps> = ({ x, y, children }) =>
    x === undefined && y === undefined ? (
        <>{children}</>
    ) : (
        <g transform={`translate(${x ?? 0},${y ?? 0})`}>{children}</g>
    );
