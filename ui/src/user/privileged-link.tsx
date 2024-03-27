import * as React from 'react';
import { useCommonDataAppSelector } from 'store/hooks';
import { PrivilegeCode } from './user-model';
import { Link } from 'vayla-design-lib/link/link';

type PrivilegedLinkProps = {
    privilege: PrivilegeCode;
    children: React.ReactNode;
} & React.HTMLProps<HTMLAnchorElement>;

export const PrivilegedLink: React.FC<PrivilegedLinkProps> = (props: PrivilegedLinkProps) => {
    const userHasPrivilege = useCommonDataAppSelector(
        (state) => state.user?.role.privileges.some((p) => p.code === props.privilege) ?? false,
    );

    if (userHasPrivilege) {
        return <Link {...props}>{props.children}</Link>;
    } else {
        return <React.Fragment>{props.children}</React.Fragment>;
    }
};
