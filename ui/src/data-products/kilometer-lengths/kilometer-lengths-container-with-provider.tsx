import { connect } from 'react-redux';
import React from 'react';
import { KilometerLengthsView } from 'data-products/kilometer-lengths/kilometer-lengths-view';

const DataProductsMainContainer = connect()(KilometerLengthsView);

export const KilometerLengthsContainerWithProvider: React.FC = () => {
    return <DataProductsMainContainer />;
};
