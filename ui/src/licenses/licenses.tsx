import * as React from 'react';
import styles from './licenses.scss';
import { useTranslation } from 'react-i18next';

type OssLicense = {
    name: string;
    version: string;
    author?: string;
    repository: string;
    license: string;
    licenseText: string;
};

const Licenses: React.FC = () => {
    const [ossLicenses, setOssLicenses] = React.useState<OssLicense[]>();
    const [geoviiteLicense, setGeoviiteLicense] = React.useState<string>();

    const { t } = useTranslation(undefined, { keyPrefix: 'licenses-page' });

    React.useEffect(() => {
        fetch('oss-licenses.json')
            .then((r) => r.json())
            .then(setOssLicenses);
    }, []);

    React.useEffect(() => {
        fetch('LICENSE.txt')
            .then((r) => r.text())
            .then(setGeoviiteLicense);
    }, []);

    return (
        <div className={styles['licenses']}>
            <h1>{t('title')}</h1>
            <pre>{geoviiteLicense}</pre>

            <h2>{t('third-party-licenses-title')}</h2>
            <table>
                <thead>
                    <tr>
                        <th>{t('name')}</th>
                        <th>{t('version')}</th>
                        <th>{t('author')}</th>
                        <th>{t('repository')}</th>
                        <th colSpan={2}>{t('license')}</th>
                    </tr>
                </thead>
                <tbody>
                    {ossLicenses?.map((l) => {
                        return (
                            <tr key={l.name + l.version}>
                                <td>{l.name}</td>
                                <td>{l.version}</td>
                                <td>{l.author ?? '-'}</td>
                                <td>{l.repository}</td>
                                <td>{l.license}</td>
                                <td>{l.licenseText}</td>
                            </tr>
                        );
                    })}
                </tbody>
            </table>
        </div>
    );
};

export default Licenses;
