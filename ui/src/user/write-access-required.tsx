import * as React from 'react';
import { PrivilegeRequired } from './privilege-required';

type WriteAccessRequiredProps = {
    children: React.ReactNode;
};

export const WriteAccessRequired: React.FC<WriteAccessRequiredProps> = ({
    children,
}: WriteAccessRequiredProps) => {
    return <PrivilegeRequired privilege="all-write">{children}</PrivilegeRequired>;
};
