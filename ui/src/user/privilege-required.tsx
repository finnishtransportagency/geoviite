import * as React from 'react';
import { PrivilegeCode, userHasPrivilege } from './user-model';
import { useCommonDataAppSelector } from 'store/hooks';

type PrivilegeRequiredProps = {
    privilege: PrivilegeCode;
    children: React.ReactNode;
};

export const PrivilegeRequired: React.FC<PrivilegeRequiredProps> = ({
    privilege,
    children,
}: PrivilegeRequiredProps) => {
    const privileges = useCommonDataAppSelector((state) => state.user?.role.privileges ?? []).map(
        (p) => p.code,
    );
    return <React.Fragment>{userHasPrivilege(privileges, privilege) && children}</React.Fragment>;
};
