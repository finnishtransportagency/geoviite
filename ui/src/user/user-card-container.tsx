import * as React from 'react';
import { UserCard } from 'user/user-card';
import { useLoader } from 'utils/react-utils';
import { getOwnUser } from 'user/user-api';

export const UserCardContainer: React.FC = () => {
    const user = useLoader(getOwnUser, []);

    return <React.Fragment>{user && <UserCard user={user}></UserCard>}</React.Fragment>;
};
