import { asyncCache } from 'cache/cache';
import { User } from 'user/user-model';
import { API_URI, getNonNull } from 'api/api-fetch';

const AUTHORIZATION_URI = `${API_URI}/authorization`;

const userCache = asyncCache<string, User>();

export async function getOwnUser(): Promise<User> {
    return userCache.getImmutable('own-details', () =>
        getNonNull<User>(`${AUTHORIZATION_URI}/own-details`),
    );
}
