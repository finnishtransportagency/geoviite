import { asyncCache } from 'cache/cache';
import { User } from 'user/user-model';
import { API_URI, getThrowError } from 'api/api-fetch';

const AUTHORIZATION_URI = `${API_URI}/authorization`;

const userCache = asyncCache<string, User>();

export async function getOwnUser(): Promise<User> {
    return userCache.getImmutable('own-details', () =>
        getThrowError<User>(`${AUTHORIZATION_URI}/own-details`),
    );
}
