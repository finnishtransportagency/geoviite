import * as React from 'react';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';

export const CheckboxExamples: React.FC = () => {
    return (
        <div>
            <h2>Checkbox</h2>

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
                            <Checkbox />
                        </td>
                        <td>
                            <Checkbox
                                checked={true}
                                onChange={(e) => console.log(e.target.value)}
                            />
                        </td>
                        <td>
                            <Checkbox disabled />
                        </td>
                        <td>
                            <Checkbox checked={true} disabled />
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
    );
};
