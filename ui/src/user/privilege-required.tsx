import * as React from 'react';
import { useCommonDataAppSelector } from 'store/hooks';
import { PrivilegeCode } from './user-model';

type PrivilegeRequiredProps = {
    privilege: PrivilegeCode;
    children: React.ReactNode;
};

export const PrivilegeRequired: React.FC<PrivilegeRequiredProps> = ({
    privilege,
    children,
}: PrivilegeRequiredProps) => {
    const userHasPrivilege = useCommonDataAppSelector((state) =>
        state.userPrivileges.some((p) => p.code === privilege),
    );

    return <React.Fragment>{userHasPrivilege && children}</React.Fragment>;
};
