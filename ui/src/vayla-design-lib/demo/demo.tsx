import * as React from 'react';
import styles from './demo.scss';
import { ButtonExamples } from 'vayla-design-lib/demo/examples/button-examples';
import { SwitchExamples } from 'vayla-design-lib/demo/examples/switch-examples';
import { TextFieldExamples } from 'vayla-design-lib/demo/examples/text-field-examples';
import { FileInputExamples } from 'vayla-design-lib/demo/examples/file-input-examples';
import { IconExamples } from 'vayla-design-lib/demo/examples/icon-examples';
import { TableExamples } from 'vayla-design-lib/demo/examples/table-examples';
import { CheckboxExamples } from 'vayla-design-lib/demo/examples/checkbox-examples';
import { RadioExamples } from 'vayla-design-lib/demo/examples/radio-examples';
import { TextAreaExamples } from 'vayla-design-lib/demo/examples/text-area-examples';
import { DropdownExamples } from 'vayla-design-lib/demo/examples/dropdown-examples';
import { MenuExample } from 'vayla-design-lib/demo/examples/menu-example';
import { SpinnerExamples } from 'vayla-design-lib/demo/examples/spinner-examples';
import { DatepickerExamples } from 'vayla-design-lib/demo/examples/datepicker-examples';

export const DesignLibDemo: React.FC = () => {
    return (
        <div className={styles['design-demo']}>
            <h1>Väylä Design Library Demo</h1>
            <DatepickerExamples />
            <ButtonExamples />
            <CheckboxExamples />
            <RadioExamples />
            <SwitchExamples />
            <TextFieldExamples />
            <TextAreaExamples />
            <FileInputExamples />
            <IconExamples />
            <TableExamples />
            <DropdownExamples />
            <MenuExample />
            <SpinnerExamples />
        </div>
    );
};
