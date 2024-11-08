import * as React from 'react';
import {
    ProgressIndicatorType,
    ProgressIndicatorWrapper,
} from 'vayla-design-lib/progress/progress-indicator-wrapper';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import { LOREMIPSUM } from 'vayla-design-lib/demo/demo';

export const ProgressIndicatorWrapperExamples: React.FC = () => {
    const [inProgress, setInProgress] = React.useState(true);
    const [changeAutomatically, setChangeAutomatically] = React.useState(true);
    const [largeContentSize, setLargeContentSize] = React.useState(200);

    React.useEffect(() => {
        if (changeAutomatically) {
            const timeoutId = setTimeout(
                () => {
                    const newInProgress = !inProgress;
                    if (!newInProgress) {
                        setLargeContentSize((0.3 + Math.random() * 0.7) * 500);
                    }
                    setInProgress(newInProgress);
                },
                inProgress ? 4000 : 2000,
            );
            return () => clearTimeout(timeoutId);
        } else {
            return () => undefined;
        }
    }, [inProgress, changeAutomatically]);

    return (
        <div>
            <h2>Progress indicator wrapper</h2>
            <p>
                Progress indicator wrapper is used to show a progress indicator during content is
                prepared (usually during loading data from backend) and then progress indicator is
                hidden and content is shown using smooth transitions. Using this progress indicator
                makes UI more stable, element sizes do not change that much and quick visibility
                changes are smoothen, this improves readability.
            </p>
            <p>
                <Checkbox
                    checked={changeAutomatically}
                    onClick={() => setChangeAutomatically(!changeAutomatically)}>
                    Toggle &quot;in progress&quot; state automatically
                </Checkbox>
            </p>
            <h3>Default indicator</h3>
            <p>Use default indicator when there is a need to get user&apos;s focus</p>
            <ProgressIndicatorWrapper inProgress={inProgress}>Data saved</ProgressIndicatorWrapper>

            <h3>Subtle indicator</h3>
            <p>
                Use subtle indicator when there is no need to get user&apos;s focus but to inform
                user that something is coming if he/she happens to look at this part of UI
            </p>
            <ProgressIndicatorWrapper
                indicator={ProgressIndicatorType.Subtle}
                inProgress={inProgress}>
                Status is OK
            </ProgressIndicatorWrapper>

            <h3>Area indicator</h3>
            <p>
                This is also a kind of subtle indicator but for larger content areas. In overall it
                seems that it is more pleasing to show current large content during new content is
                loading.
            </p>

            <ProgressIndicatorWrapper
                indicator={ProgressIndicatorType.Area}
                inProgress={inProgress}>
                <div style={{ width: 300 }}>
                    {LOREMIPSUM.substring(largeContentSize % 20, largeContentSize)}
                </div>
            </ProgressIndicatorWrapper>
        </div>
    );
};
