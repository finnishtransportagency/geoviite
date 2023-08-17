import * as React from 'react';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { Button } from 'vayla-design-lib/button/button';
import { TextField } from 'vayla-design-lib/text-field/text-field';

export const SnackbarExamples: React.FC = () => {
    const [header, setHeader] = React.useState<string>('');
    const [body, setBody] = React.useState<string>('');

    const infoSnackbar = () => {
        Snackbar.info(header, body);
    };

    const successSnackbar = () => {
        Snackbar.success(header, { body });
    };

    const errorSnackbar = () => {
        Snackbar.error(header, { body });
    };

    return (
        <div>
            <h2>Snackbar demo</h2>
            <table>
                <thead>
                    <tr>
                        <th>Header</th>
                        <th>Body</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td>
                            <TextField
                                value={header}
                                onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                                    setHeader(e.target.value)
                                }
                            />
                        </td>
                        <td>
                            <TextField
                                value={body}
                                onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                                    setBody(e.target.value)
                                }
                            />
                        </td>
                    </tr>
                </tbody>
            </table>
            <table>
                <tbody>
                    <tr>
                        <td>
                            <Button onClick={infoSnackbar}>Info</Button>
                        </td>
                        <td>
                            <Button onClick={successSnackbar}>Success</Button>
                        </td>
                        <td>
                            <Button onClick={errorSnackbar}>Error</Button>
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
    );
};
