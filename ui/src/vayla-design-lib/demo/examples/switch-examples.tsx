import * as React from 'react';
import { Switch } from 'vayla-design-lib/switch/switch';

export const SwitchExamples: React.FC = () => {
    return (
        <div>
            <h2>Switch</h2>

            <table>
                <thead>
                    <tr>
                        <th>Not checked</th>
                        <th>Checked</th>
                        <th>Not checked + disabled</th>
                        <th>Checked + disabled</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td>
                            <Switch checked={false} />
                        </td>
                        <td>
                            <Switch checked={true} />
                        </td>
                        <td>
                            <Switch checked={false} disabled={true} />
                        </td>
                        <td>
                            <Switch checked={true} disabled={true} />
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
    );
};
