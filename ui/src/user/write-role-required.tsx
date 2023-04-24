import * as React from 'react';
import { useCommonDataAppSelector } from 'store/hooks';
import { getOwnUser } from 'user/user-api';
import { createDelegates } from 'store/store-utils';
import { commonActionCreators } from 'common/common-slice';

type WriteRoleRequiredProps = {
    children: React.ReactNode;
};

export const WriteRoleRequired: React.FC<WriteRoleRequiredProps> = ({
    children,
}: WriteRoleRequiredProps) => {
    const delegates = createDelegates(commonActionCreators);
    const userHasWriteRole = useCommonDataAppSelector((state) => state.userHasWriteRole);

    React.useEffect(() => {
        getOwnUser().then((user) => {
            const userHasWriteAccess = user.role.privileges.some(
                (privilege) => privilege.code === 'all-write',
            );

            delegates.setUserHasWriteRole(userHasWriteAccess);
        });
    }, []);

    return <React.Fragment>{userHasWriteRole && children}</React.Fragment>;
};
