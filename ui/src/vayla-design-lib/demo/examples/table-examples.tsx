import * as React from 'react';
import { Table, Td, TdVariant } from 'vayla-design-lib/table/table';
import examplePersonsData from 'vayla-design-lib/demo/example-persons.json';
import { Button, ButtonSize } from 'vayla-design-lib/button/button';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import { AnchorLink } from 'geoviite-design-lib/link/anchor-link';

type ExamplePerson = {
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

export const TableExamples: React.FC = () => {
    return (
        <div>
            <h2>Tables</h2>
            <h3>Default table</h3>
            <Table>
                <thead>
                    <tr>
                        <th></th>
                        <th>Name</th>
                        <th>Title</th>
                        <th>Salary €</th>
                        <th>Address</th>
                        <th>Email</th>
                        <th>Website</th>
                        <th>Company</th>
                        <th>IBAN</th>
                        <th>Story</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody>
                    {examplePersons.slice(0, 6).map((person) => {
                        return (
                            <tr key={person.id}>
                                <td>
                                    <Checkbox />
                                </td>
                                <td>{person.name}</td>
                                <td>{person.title}</td>
                                <Td variant={TdVariant.NUMBER}>{person.salary}</Td>
                                <td>{person.address}</td>
                                <td>{person.email}</td>
                                <td>
                                    <AnchorLink href={person.website || undefined}>
                                        {person.website}
                                    </AnchorLink>
                                </td>
                                <td>{person.company}</td>
                                <td>{person.iban}</td>
                                <td>{person.story}</td>
                                <td>
                                    <Button size={ButtonSize.SMALL}>Edit</Button>
                                </td>
                            </tr>
                        );
                    })}
                </tbody>
            </Table>
        </div>
    );
};
