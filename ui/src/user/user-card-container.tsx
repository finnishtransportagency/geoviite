import * as React from 'react';
import { UserCard } from 'user/user-card';
import { useCommonDataAppSelector } from 'store/hooks';

export const UserCardContainer: React.FC = () => {
    const user = useCommonDataAppSelector((state) => state.user);

    return <React.Fragment>{user && <UserCard user={user}></UserCard>}</React.Fragment>;
};
