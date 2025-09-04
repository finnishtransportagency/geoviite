import { useLoader } from 'utils/react-utils';
import { getNonNull } from 'api/api-fetch';
import { asyncCache } from 'cache/cache';

export type EnvironmentInfo = {
    releaseVersion: string;
    environmentName: Environment;
    geoviiteSupportEmailAddress: string;
    ratkoSupportEmailAddress: string;
};

export type Environment = 'local' | 'dev' | 'test' | 'prod';

const ENVIRONMENT_API = '/api/environment';
const environmentCache = asyncCache<string, EnvironmentInfo>();

export function useEnvironmentInfo(): EnvironmentInfo | undefined {
    return useLoader<EnvironmentInfo>(
        () =>
            environmentCache.getImmutable('environment', () =>
                getNonNull<EnvironmentInfo>(ENVIRONMENT_API),
            ),
        [],
    );
}
