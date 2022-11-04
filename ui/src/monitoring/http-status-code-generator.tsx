import React from 'react';
import { API_URI, getIgnoreError } from 'api/api-fetch';
import styles from './http-statuscode-generator.module.scss';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';

export const HttpStatusCodeGenerator: React.FC = () => {
    const MOCK_URI = `${API_URI}/mock-http`;

    async function getStatusCode(httpStatusCode: number): Promise<void> {
        await sleep(2500);
        await getIgnoreError(MOCK_URI + '/status?code=' + httpStatusCode).then();
    }

    function sleep(ms: number) {
        return new Promise((resolve) => setTimeout(resolve, ms));
    }

    const statusCodes = [200, 201, 204, 304, 400, 404, 500];
    const buttons = [];

    for (const statusCodesKey of statusCodes) {
        buttons.push(
            <Button
                variant={ButtonVariant.PRIMARY}
                onClick={() => {
                    getStatusCode(statusCodesKey);
                }}>
                {statusCodesKey}
            </Button>,
        );
    }
    return <div className={styles['http-statuscode-generator']}>{buttons}</div>;
};
