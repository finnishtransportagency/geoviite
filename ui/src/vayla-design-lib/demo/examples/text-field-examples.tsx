import * as React from 'react';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { Icons } from 'vayla-design-lib/icon/Icon';

export const TextFieldExamples: React.FC = () => {
    return (
        <div>
            <h2>Text field</h2>

            <table>
                <thead>
                    <tr>
                        <th>Default</th>
                        <th>Disabled</th>
                        <th>With icon</th>
                        <th>With icon + disabled</th>
                        <th>Has error</th>
                        <th>Has error + with icon</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td>
                            <TextField
                                value={'John Doe'}
                                onChange={(e) => console.log(e.target.value)}
                            />
                        </td>
                        <td>
                            <TextField value={'John Doe'} disabled={true} />
                        </td>
                        <td>
                            <TextField
                                value={'John Doe'}
                                Icon={Icons.Download}
                                onChange={(e) => console.log(e.target.value)}
                            />
                        </td>
                        <td>
                            <TextField value={'John Doe'} Icon={Icons.Download} disabled={true} />
                        </td>
                        <td>
                            <TextField
                                value={'John Doe'}
                                hasError={true}
                                onChange={(e) => console.log(e.target.value)}
                            />
                        </td>
                        <td>
                            <TextField
                                value={'John Doe'}
                                hasError={true}
                                Icon={Icons.Download}
                                onChange={(e) => console.log(e.target.value)}
                            />
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
    );
};
