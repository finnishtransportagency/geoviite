import * as React from 'react';
import { DatePicker } from 'vayla-design-lib/datepicker/datepicker';

export const DatepickerExamples = () => {
    const [date, setDate] = React.useState<Date>();

    return (
        <section>
            <h2>Datepicker examples</h2>
            <div>
                <DatePicker value={date} onChange={(date) => setDate(date)} />
            </div>
        </section>
    );
};
