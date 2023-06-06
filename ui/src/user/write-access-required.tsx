import * as React from 'react';
import { useCommonDataAppSelector } from 'store/hooks';
import { getOwnUser } from 'user/user-api';
import { createDelegates } from 'store/store-utils';
import { commonActionCreators } from 'common/common-slice';

type WriteAccessRequiredProps = {
    children: React.ReactNode;
};

export const WriteAccessRequired: React.FC<WriteAccessRequiredProps> = ({
    children,
}: WriteAccessRequiredProps) => {
    const delegates = createDelegates(commonActionCreators);

    React.useEffect(() => {
        getOwnUser().then((user) => {
            delegates.setUserPrivileges(user.role.privileges);
        });
    }, []);

    const userHasWriteAccessFromStore = useCommonDataAppSelector((state) =>
        state.userPrivileges.some((privilege) => privilege.code === 'all-write'),
    );

    return <React.Fragment>{userHasWriteAccessFromStore && children}</React.Fragment>;
};
