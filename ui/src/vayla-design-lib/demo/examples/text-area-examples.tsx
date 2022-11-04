import * as React from 'react';
import { TextArea } from 'vayla-design-lib/text-area/text-area';
import { Icons } from 'vayla-design-lib/icon/Icon';

export const TextAreaExamples: React.FC = () => {
    const texts = [
        'Lorem ipsum dolor sit amet, consectetur adipiscing elit',
        'Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum',
    ];
    return (
        <div>
            <h2>Text area</h2>

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
                    {texts.map((text, index) => (
                        <tr key={index}>
                            <td>
                                <TextArea value={text} onChange={(e) => console.log(e.target)} />
                            </td>
                            <td>
                                <TextArea value={text} disabled={true} />
                            </td>
                            <td>
                                <TextArea
                                    value={text}
                                    Icon={Icons.Download}
                                    onChange={(e) => console.log(e.target)}
                                />
                            </td>
                            <td>
                                <TextArea value={text} Icon={Icons.Download} disabled={true} />
                            </td>
                            <td>
                                <TextArea
                                    value={text}
                                    hasError={true}
                                    onChange={(e) => console.log(e.target)}
                                />
                            </td>
                            <td>
                                <TextArea
                                    value={text}
                                    hasError={true}
                                    Icon={Icons.StatusError}
                                    onChange={(e) => console.log(e.target)}
                                />
                            </td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
};
