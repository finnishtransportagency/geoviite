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

    React.useEffect(() => {
        getOwnUser().then((user) => {
            const userHasWriteRoleFromBackend = user.role.privileges.some(
                (privilege) => privilege.code === 'all-write',
            );

            delegates.setUserHasWriteRole(userHasWriteRoleFromBackend);
        });
    }, []);

    const userHasWriteRoleFromStore = useCommonDataAppSelector((state) => state.userHasWriteRole);

    return <React.Fragment>{userHasWriteRoleFromStore && children}</React.Fragment>;
};
