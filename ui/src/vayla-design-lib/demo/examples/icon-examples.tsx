import * as React from 'react';
import { IconRotation, Icons, IconSize } from 'vayla-design-lib/icon/Icon';

export const IconExamples: React.FC = () => {
    return (
        <div>
            <h2>Icons</h2>

            <h3>Glyphs</h3>
            <table>
                <thead>
                    <tr>
                        <th>Name</th>
                        <th>Visualization</th>
                    </tr>
                </thead>
                <tbody>
                    {Object.keys(Icons).map((iconName: keyof typeof Icons) => {
                        const Icon = Icons[iconName];
                        return (
                            <tr key={iconName}>
                                <td>{iconName}</td>
                                <td>
                                    <Icon />
                                </td>
                            </tr>
                        );
                    })}
                </tbody>
            </table>

            <h3>Sizes</h3>
            <table>
                <thead>
                    <tr>
                        <th>Small</th>
                        <th>Medium (default)</th>
                        <th>Large</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td>
                            <Icons.Append size={IconSize.SMALL} />
                        </td>
                        <td>
                            <Icons.Append />
                        </td>
                        <td>
                            <Icons.Append size={IconSize.LARGE} />
                        </td>
                    </tr>
                </tbody>
            </table>

            <h3>Rotation</h3>
            <table>
                <thead>
                    <tr>
                        <th>No rotation</th>
                        <th>180</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td>
                            <Icons.Filter />
                        </td>
                        <td>
                            <Icons.Filter rotation={IconRotation.ROTATE_180} />
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
    );
};
