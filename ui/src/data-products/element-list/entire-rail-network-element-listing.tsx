import * as React from 'react';
import { useTranslation } from 'react-i18next';
import styles from 'data-products/data-product-view.scss';
import { getEntireRailNetworkElementsCsv } from 'geometry/geometry-api';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button } from 'vayla-design-lib/button/button';
import { Dialog } from 'vayla-design-lib/dialog/dialog';
import dialogStyles from 'vayla-design-lib/dialog/dialog.scss';

export const EntireRailNetworkElementListing = () => {
    const { t } = useTranslation();
    const [loading, setLoading] = React.useState(false);
    const [abortController, setAbortController] = React.useState<AbortController>();

    return (
        <React.Fragment>
            <p className={styles['data-product__search-legend']}>
                {t('data-products.element-list.entire-rail-network-legend')}
            </p>
            <p className={styles['data-product__search-legend']}>
                {t('data-products.element-list.entire-rail-network-length-warning')}
            </p>
            <div className={styles['data-products__search']}>
                <Button
                    className={styles['element-list__download-button--left-aligned']}
                    onClick={() => {
                        const abortController = new AbortController();
                        setAbortController(abortController);
                        setLoading(true);
                        getEntireRailNetworkElementsCsv(abortController.signal).then((data) => {
                            setLoading(false);
                            if (data) {
                                const dataUrl = URL.createObjectURL(data);
                                const link = document.createElement('a');
                                link.href = dataUrl;
                                link.download = 'Elementtilistaus (koko rataverkko).csv';
                                link.click();
                                URL.revokeObjectURL(dataUrl);
                            }
                        });
                    }}
                    isProcessing={loading}
                    icon={Icons.Download}>
                    {t(`data-products.search.download-csv`)}
                </Button>
            </div>
            {loading && (
                <Dialog
                    className={dialogStyles['dialog--normal']}
                    allowClose={false}
                    title={t('data-products.element-list.creating-file')}
                    footerContent={
                        <Button
                            onClick={() => {
                                abortController && abortController.abort();
                                setLoading(false);
                            }}>
                            {t('button.cancel')}
                        </Button>
                    }>
                    <span>{t('data-products.element-list.long-operation')}</span>
                </Dialog>
            )}
        </React.Fragment>
    );
};
