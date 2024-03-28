import { asyncCache } from 'cache/cache';
import { RoleCode, User } from 'user/user-model';
import { API_URI, getNonNull, postNonNull } from 'api/api-fetch';

const AUTHORIZATION_URI = `${API_URI}/authorization`;

const userCache = asyncCache<string, User>();

export async function getOwnUser(): Promise<User> {
    return userCache.getImmutable('own-details', () =>
        getNonNull<User>(`${AUTHORIZATION_URI}/own-details`),
    );
}

export async function postDesiredRole(code: RoleCode): Promise<RoleCode> {
    return postNonNull<undefined, RoleCode>(
        `${AUTHORIZATION_URI}/desired-role?code=${code}`,
        undefined,
    );
}
