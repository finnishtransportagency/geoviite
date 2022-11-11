import * as React from 'react';

const environmentSettingsPromise = fetch('/api/environment').then((r) => r.json());

export type EnvironmentInfo = {
    releaseVersion: string;
    environmentName: Environment;
};

export type Environment = 'local' | 'dev' | 'test' | 'prod';

export function getEnvironmentInfo() {
    const [info, setInfo] = React.useState<EnvironmentInfo>();
    React.useMemo(() => {
        environmentSettingsPromise.then((response) => setInfo(response));
    }, []);

    return info;
}
