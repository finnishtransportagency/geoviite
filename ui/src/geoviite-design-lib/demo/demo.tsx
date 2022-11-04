import * as React from 'react';
import { DesignLibDemo } from 'vayla-design-lib/demo/demo';
import styles from './demo.scss';
import { SnackbarExamples } from 'geoviite-design-lib/demo/examples/snackbar-examples';
import { BadgeExamples } from 'geoviite-design-lib/demo/examples/badge-examples';
import { MessageBoxExamples } from 'geoviite-design-lib/demo/examples/message-box-examples';

export const GeoviiteLibDemo: React.FC = () => {
    return (
        <div className={styles['geoviite-design-demo']}>
            <h1>Geoviite Design Library Demo</h1>
            <BadgeExamples />
            <SnackbarExamples />
            <MessageBoxExamples />

            <DesignLibDemo />
        </div>
    );
};
