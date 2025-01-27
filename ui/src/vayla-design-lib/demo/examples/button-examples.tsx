import * as React from 'react';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import { TextField } from 'vayla-design-lib/text-field/text-field';
import { Dropdown, dropdownOption } from 'vayla-design-lib/dropdown/dropdown';
import { ExamplePerson } from 'vayla-design-lib/demo/examples/dropdown-examples';
import examplePersonsData from 'vayla-design-lib/demo/example-persons.json';
import { SplitButton } from 'geoviite-design-lib/split-button/split-button';
import { menuOption } from 'vayla-design-lib/menu/menu';

export const ButtonExamples: React.FC = () => {
    const [isProcessing, setProcessing] = React.useState(false);
    const [buttonProcessing, setButtonProcessing] = React.useState<string | undefined>();
    const [person, setPerson] = React.useState<ExamplePerson | undefined>();

    function toggleProcessing(key: string) {
        setButtonProcessing(key === buttonProcessing ? undefined : key);
    }

    return (
        <div>
            <h2>Buttons</h2>

            <p>
                <Checkbox checked={isProcessing} onChange={(e) => setProcessing(e.target.checked)}>
                    Show processing animation
                </Checkbox>
            </p>

            <table>
                <thead>
                    <tr>
                        <th>Variant</th>
                        <th>Size</th>
                        <th>Normal</th>
                        <th>Disabled</th>
                        <th>With icon</th>
                        <th>With icon + disabled</th>
                        <th>Icon only</th>
                        <th>Icon only + disabled</th>
                        <th>Split button</th>
                        <th>Split button + disabled</th>
                    </tr>
                </thead>

                <tbody>
                    {Object.keys(ButtonVariant).map((variantName: keyof typeof ButtonVariant) => {
                        const variant = ButtonVariant[variantName];
                        return [undefined, ...Object.keys(ButtonSize)].map(
                            (sizeName: keyof typeof ButtonSize | undefined) => {
                                const size = sizeName && ButtonSize[sizeName];
                                const key = variant + size;
                                return (
                                    <tr key={key}>
                                        <td>{variantName}</td>
                                        <td>{sizeName || 'default'}</td>
                                        <td>
                                            <Button
                                                size={size}
                                                variant={variant}
                                                isProcessing={
                                                    isProcessing || buttonProcessing === key
                                                }
                                                onClick={() => toggleProcessing(key)}>
                                                Button
                                            </Button>
                                        </td>
                                        <td>
                                            <Button
                                                size={size}
                                                variant={variant}
                                                disabled
                                                isProcessing={isProcessing}>
                                                Button
                                            </Button>
                                        </td>
                                        <td>
                                            <Button
                                                size={size}
                                                variant={variant}
                                                icon={Icons.Append}
                                                isProcessing={
                                                    isProcessing ||
                                                    buttonProcessing === key + 'icon'
                                                }
                                                onClick={() => toggleProcessing(key + 'icon')}>
                                                Button
                                            </Button>
                                        </td>
                                        <td>
                                            <Button
                                                size={size}
                                                variant={variant}
                                                disabled
                                                icon={Icons.Append}
                                                isProcessing={isProcessing}>
                                                Button
                                            </Button>
                                        </td>
                                        <td>
                                            <Button
                                                size={size}
                                                variant={variant}
                                                icon={Icons.Append}
                                                isProcessing={
                                                    isProcessing ||
                                                    buttonProcessing === key + 'icon-only'
                                                }
                                                onClick={() => toggleProcessing(key + 'icon-only')}
                                            />
                                        </td>
                                        <td>
                                            <Button
                                                size={size}
                                                variant={variant}
                                                disabled
                                                icon={Icons.Append}
                                                isProcessing={
                                                    isProcessing ||
                                                    buttonProcessing === key + 'icon-only'
                                                }
                                            />
                                        </td>
                                        <td>
                                            <SplitButton
                                                size={size}
                                                variant={variant}
                                                icon={Icons.Append}
                                                menuItems={[
                                                    menuOption(
                                                        () => console.log(`I'm doing something!`),
                                                        'Item 1',
                                                        'Item 1',
                                                    ),
                                                ]}>
                                                Button
                                            </SplitButton>
                                        </td>
                                        <td>
                                            <SplitButton
                                                size={size}
                                                variant={variant}
                                                disabled
                                                icon={Icons.Append}
                                                isProcessing={isProcessing}
                                                menuItems={[]}>
                                                Button
                                            </SplitButton>
                                        </td>
                                    </tr>
                                );
                            },
                        );
                    })}
                </tbody>
            </table>

            <h3>Attached button</h3>
            <div>
                <TextField
                    attachRight
                    value={'some value'}
                    onChange={(e) => console.log(e.target.value)}
                />
                <Button variant={ButtonVariant.SECONDARY} icon={Icons.Append} attachLeft />
            </div>
            <div>
                <Dropdown
                    placeholder="Select person"
                    value={person}
                    onChange={(person) => setPerson(person)}
                    options={examplePersonsData.map((person) =>
                        dropdownOption(person, person.name, person.name),
                    )}
                    attachRight
                />
                <Button variant={ButtonVariant.SECONDARY} icon={Icons.Append} attachLeft />
            </div>
            <div>
                <TextField
                    attachRight
                    value={'some value'}
                    onChange={(e) => console.log(e.target.value)}
                />
                <Button attachLeft>Go</Button>
            </div>
            <div>
                Some text to test inlining{' '}
                <TextField
                    attachRight
                    value={'some value'}
                    onChange={(e) => console.log(e.target.value)}
                />
                <Button variant={ButtonVariant.SECONDARY} icon={Icons.Append} attachLeft />
                and some text after
            </div>
            <div>
                Some text to test inlining <Button>Test me</Button>
            </div>
        </div>
    );
};
