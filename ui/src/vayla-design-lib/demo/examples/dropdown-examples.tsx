import * as React from 'react';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import examplePersonsData from 'vayla-design-lib/demo/example-persons.json';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';

export type ExamplePerson = {
    id: number;
    name: string;
    company: string;
    title: string;
    salary: number;
    email: string;
    address: string;
    story: string;
    website?: string;
    iban: string;
};

const examplePersons: ExamplePerson[] = examplePersonsData;

export const DropdownExamples: React.FC = () => {
    const [chosenItem, setChosenItem] = React.useState<ExamplePerson | undefined>(undefined);

    function handleOnChange(person: ExamplePerson | undefined) {
        setChosenItem(person);
    }

    return (
        <div>
            <h2 id="dropdown">Dropdowns</h2>

            <p>
                Selected person: {chosenItem?.name} &nbsp;
                {chosenItem && (
                    <Button
                        variant={ButtonVariant.SECONDARY}
                        size={ButtonSize.SMALL}
                        onClick={() => setChosenItem(undefined)}>
                        clear
                    </Button>
                )}
            </p>

            <p>Basic dropdown</p>

            <Dropdown
                placeholder="Select person"
                value={chosenItem}
                options={examplePersons.map((person) => ({
                    name: person.name,
                    value: person,
                    disabled: person.salary > 6000,
                }))}
                onChange={(person) => handleOnChange(person)}
                canUnselect={true}
            />

            <p>Searchable dropdown</p>

            <Dropdown
                placeholder="Select person"
                value={chosenItem}
                options={examplePersons.map((person) => ({
                    name: person.name,
                    value: person,
                }))}
                onChange={(person) => handleOnChange(person)}
                canUnselect={true}
                searchable
            />

            <p>Just few items</p>

            <Dropdown
                placeholder="Select person"
                value={chosenItem}
                options={examplePersons.slice(0, 3).map((person) => ({
                    name: person.name,
                    value: person,
                }))}
                onChange={(person) => handleOnChange(person)}
            />

            <p>Long item names</p>

            <Dropdown
                placeholder="Select person"
                value={chosenItem}
                options={examplePersons.slice(0, 3).map((person) => ({
                    name: person.name.repeat(3),
                    value: person,
                }))}
                onChange={(person) => handleOnChange(person)}
            />

            <p>Long placeholder</p>

            <Dropdown
                placeholder="Select person to do something in the future"
                value={chosenItem}
                options={examplePersons.slice(0, 3).map((person) => ({
                    name: person.name,
                    value: person,
                }))}
                onChange={(person) => handleOnChange(person)}
            />

            <p>Short placeholder</p>

            <Dropdown
                placeholder="Select"
                value={chosenItem}
                options={examplePersons.slice(0, 3).map((person) => ({
                    name: person.name,
                    value: person,
                }))}
                onChange={(person) => handleOnChange(person)}
            />

            <p>Dropdown with an &ldquo;add new&ldquo; button</p>

            <Dropdown
                placeholder="Select or add"
                value={chosenItem}
                options={examplePersons.map((person) => ({
                    name: person.name,
                    value: person,
                }))}
                onChange={(person) => handleOnChange(person)}
                onAddClick={() => alert('Add new clicked!')}
            />

            <p>Async dropdown</p>

            <Dropdown
                placeholder="Select person"
                value={chosenItem}
                options={(searchTerm) => {
                    return new Promise((resolve) => {
                        setTimeout(() => {
                            resolve(
                                searchTerm
                                    ? examplePersons
                                          .filter((person) => person.name.includes(searchTerm))
                                          .map((person) => ({
                                              name: person.name,
                                              value: person,
                                          }))
                                    : [],
                            );
                        }, 1000);
                    });
                }}
                getName={(person) => person.name}
                searchable
                onChange={(person) => handleOnChange(person)}
                canUnselect={true}
            />

            <p>Disabled dropdown</p>

            <Dropdown
                placeholder="Select"
                value={chosenItem}
                options={examplePersons.slice(0, 3).map((person) => ({
                    name: person.name,
                    value: person,
                }))}
                searchable
                onChange={(person) => handleOnChange(person)}
                disabled
            />
        </div>
    );
};
