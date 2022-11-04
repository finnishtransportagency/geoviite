import * as React from 'react';
import styles from './file-input.scss';
import { createClassName } from 'vayla-design-lib/utils';

export type FileInputProps = {
    children?: React.ReactNode;
    onChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
    accept?: string;
} & Pick<React.HTMLProps<HTMLInputElement>, 'disabled'>;

export const FileInput: React.FC<FileInputProps> = ({
    children,
    onChange,
    accept = '',
    ...props
}: FileInputProps) => {
    const className = createClassName(styles['file-input']);

    return (
        <div className={className}>
            {children}
            <input
                className={styles['file-input__file-input']}
                type="file"
                {...props}
                onChange={onChange}
                accept={accept}
            />
        </div>
    );
};
