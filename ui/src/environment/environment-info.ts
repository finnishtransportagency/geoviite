import { useLoader } from 'utils/react-utils';
import { getNonNull } from 'api/api-fetch';

export type EnvironmentInfo = {
    releaseVersion: string;
    environmentName: Environment;
};

export type Environment = 'local' | 'dev' | 'test' | 'prod';

export function useEnvironmentInfo(): EnvironmentInfo | undefined {
    return useLoader<EnvironmentInfo>(() => getNonNull<EnvironmentInfo>('/api/environment'), []);
}
