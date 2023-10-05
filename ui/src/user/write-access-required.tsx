import * as React from 'react';
import { useCommonDataAppSelector } from 'store/hooks';

type WriteAccessRequiredProps = {
    children: React.ReactNode;
};

export const WriteAccessRequired: React.FC<WriteAccessRequiredProps> = ({
    children,
}: WriteAccessRequiredProps) => {
    const userHasWriteAccessFromStore = useCommonDataAppSelector((state) =>
        state.userPrivileges.some((privilege) => privilege.code === 'all-write'),
    );

    return <React.Fragment>{userHasWriteAccessFromStore && children}</React.Fragment>;
};
