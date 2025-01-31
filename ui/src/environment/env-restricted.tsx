import * as React from 'react';
import { Environment, useEnvironmentInfo } from 'environment/environment-info';

type EnvRestrictedProps = {
    restrictTo: Environment;
    defaultShow?: boolean;
    strict?: boolean;
    children: React.ReactNode;
};

//Local is only shown in local environment,
//Test is only shown in local, dev and test environment etc.
const slackRestrictionRules: { [key in Environment]: Environment[] } = {
    local: ['local'],
    dev: ['local', 'dev'],
    test: ['local', 'dev', 'test'],
    prod: ['local', 'dev', 'test', 'prod'],
};

export const EnvRestricted: React.FC<EnvRestrictedProps> = ({
    restrictTo,
    children,
    strict = false,
    defaultShow = false,
}: EnvRestrictedProps) => {
    const envName = useEnvironmentInfo()?.environmentName;

    const show = envName
        ? strict
            ? envName === restrictTo
            : slackRestrictionRules[restrictTo].some((e) => e === envName)
        : defaultShow;

    return <React.Fragment>{show && children}</React.Fragment>;
};
