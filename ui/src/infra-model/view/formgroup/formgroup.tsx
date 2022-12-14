import React from 'react';
import { createClassName } from 'vayla-design-lib/utils';

export type FormGroupProps = {
    children?: React.ReactNode;
};

const Formgroup: React.FC<FormGroupProps> = (props: FormGroupProps) => {
    const className = createClassName('formgroup');
    return (
        <div className={className} {...props}>
            {props.children}
        </div>
    );
};

export default Formgroup;
