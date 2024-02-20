import * as React from 'react';
import { FileInput } from 'vayla-design-lib/file-input/file-input';
import { Button } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';

export const FileInputExamples: React.FC = () => {
    return (
        <div>
            <h2>File input</h2>

            <h3>Text as a visualization</h3>
            <FileInput
                onChange={(e) =>
                    alert(e.target.files?.[0] ? e.target.files[0].name : 'no file chosen')
                }>
                Upload
            </FileInput>

            <h3>Button as a visualization</h3>
            <FileInput
                onChange={(e) =>
                    alert(e.target.files?.[0] ? e.target.files[0].name : 'no file chosen')
                }>
                <Button icon={Icons.Download}>Upload a file</Button>
            </FileInput>
        </div>
    );
};
