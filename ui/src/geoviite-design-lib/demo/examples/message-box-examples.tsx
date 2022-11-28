import * as React from 'react';
import { MessageBox } from 'geoviite-design-lib/message-box/message-box';
import { Button } from 'vayla-design-lib/button/button';
import styles from '../demo.scss'

export const MessageBoxExamples: React.FC = () => {
    const [pop, setPop] = React.useState<boolean>(false);

    return (
        <div>
            <h2>Message box</h2>

            <h3>Default version</h3>
            <div className={styles['messagebox-demo']}>
                <MessageBox>Don&apos;t climb too high, you might fall!</MessageBox>
            </div>

            <h3>Poppable version</h3>
            <p>
                This version is more user friendly when message should emerge into already rendered
                layout.
            </p>
            <Button onClick={() => setPop(!pop)}>Toggle</Button>
            <div className={styles['messagebox-demo']}>
                <MessageBox pop={pop}>Don&apos;t climb too high, you might fall!</MessageBox>
            </div>
        </div>
    );
};
