import * as React from 'react';
import { Radio } from 'vayla-design-lib/radio/radio';

export const RadioExamples: React.FC = () => {
    return (
        <div>
            <h2>Radiobutton</h2>

            <table>
                <thead>
                    <tr>
                        <th>Not checked</th>
                        <th>Checked</th>
                        <th>Disabled</th>
                        <th>Disabled + checked</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td>
                            <Radio name="foo" />
                        </td>
                        <td>
                            <Radio
                                checked={true}
                                onChange={(e) => console.log(e.target.value)}
                                name="foo"
                            />
                        </td>
                        <td>
                            <Radio disabled />
                        </td>
                        <td>
                            <Radio checked={true} disabled />
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
    );
};
